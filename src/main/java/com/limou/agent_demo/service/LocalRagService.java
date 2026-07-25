package com.limou.agent_demo.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limou.agent_demo.config.RagProperties;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class LocalRagService {

    private static final Logger log = LoggerFactory.getLogger(LocalRagService.class);

    private final RagProperties properties;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<VectorStore> vectorStoreProvider;

    private volatile boolean ready;

    public LocalRagService(RagProperties properties,
                           ObjectMapper objectMapper,
                           ObjectProvider<VectorStore> vectorStoreProvider) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.vectorStoreProvider = vectorStoreProvider;
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("RAG is disabled.");
            return;
        }
        VectorStore vectorStore = getVectorStore();
        if (vectorStore == null) {
            log.warn("No VectorStore bean found. RAG vector search is unavailable.");
            return;
        }
        if (!properties.isAutoIndexOnStartup()) {
            ready = true;
            log.info("RAG auto indexing is disabled. Existing vector store data will be used.");
            return;
        }

        try {
            IndexResult result = indexDocuments();
            ready = true;
            log.info("RAG vector store sync finished: addedChunks={}, deletedChunks={}, skippedDocs={}.",
                    result.addedChunks(), result.deletedChunks(), result.skippedDocs());
        } catch (Exception e) {
            ready = false;
            log.warn("RAG vector store sync failed. Chat will continue without retrieval: {}", e.getMessage());
        }
    }

    public synchronized IndexResult indexDocuments() throws IOException {
        return indexDocuments(properties.isRebuildOnStartup());
    }

    public synchronized IndexResult reindexDocuments() throws IOException {
        return indexDocuments(true);
    }

    private IndexResult indexDocuments(boolean rebuild) throws IOException {
        VectorStore vectorStore = getVectorStore();
        if (vectorStore == null) {
            throw new IllegalStateException("VectorStore is not available. Please check vector database configuration.");
        }

        List<DocumentSnapshot> currentSnapshots = scanDocuments();
        RagManifest previousManifest = loadManifest();
        Map<String, IndexedDocument> previousDocs = previousManifest.getDocuments().stream()
                .collect(Collectors.toMap(IndexedDocument::getPath, item -> item, (left, right) -> left));

        if (rebuild) {
            deleteChunks(vectorStore, previousManifest.allChunkIds());
            previousDocs.clear();
        }

        int addedChunks = 0;
        int deletedChunks = 0;
        int skippedDocs = 0;
        List<IndexedDocument> nextDocs = new ArrayList<>();

        for (DocumentSnapshot snapshot : currentSnapshots) {
            IndexedDocument previous = previousDocs.remove(snapshot.getPath());
            if (!rebuild && previous != null && previous.sameSnapshot(snapshot)) {
                nextDocs.add(previous);
                skippedDocs++;
                continue;
            }

            if (previous != null) {
                deletedChunks += deleteChunks(vectorStore, previous.getChunkIds());
            }

            List<Document> chunks = buildVectorDocuments(snapshot);
            addInBatches(vectorStore, chunks);
            addedChunks += chunks.size();

            IndexedDocument indexed = IndexedDocument.from(snapshot);
            indexed.setChunkIds(chunks.stream().map(Document::getId).collect(Collectors.toList()));
            nextDocs.add(indexed);
        }

        for (IndexedDocument removed : previousDocs.values()) {
            deletedChunks += deleteChunks(vectorStore, removed.getChunkIds());
        }

        RagManifest nextManifest = new RagManifest();
        nextManifest.setDocsPath(properties.getDocsPath());
        nextManifest.setGeneratedAt(Instant.now().toString());
        nextManifest.setDocuments(nextDocs);
        saveManifest(nextManifest);

        ready = true;
        return new IndexResult(addedChunks, deletedChunks, skippedDocs);
    }

    public List<RagReference> search(String query) {
        VectorStore vectorStore = getVectorStore();
        if (!ready || vectorStore == null || !StringUtils.hasText(query)) {
            return List.of();
        }

        try {
            List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(query)
                    .topK(properties.getTopK())
                    .similarityThreshold(properties.getMinScore())
                    .build());

            return documents.stream()
                    .map(document -> new RagReference(
                            String.valueOf(document.getMetadata().getOrDefault("source", "")),
                            ((Number) document.getMetadata().getOrDefault("chunkIndex", 0)).intValue(),
                            document.getText(),
                            document.getScore() == null ? 0.0 : document.getScore()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("RAG vector search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private VectorStore getVectorStore() {
        try {
            return vectorStoreProvider.getIfAvailable();
        } catch (RuntimeException e) {
            log.warn("VectorStore is unavailable: {}", e.getMessage());
            return null;
        }
    }

    public String buildContext(List<RagReference> references) {
        if (references == null || references.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < references.size(); i++) {
            RagReference ref = references.get(i);
            context.append("[")
                    .append(i + 1)
                    .append("] source=")
                    .append(ref.source())
                    .append(", chunk=")
                    .append(ref.chunkIndex())
                    .append(", score=")
                    .append(String.format(Locale.ROOT, "%.4f", ref.score()))
                    .append("\n")
                    .append(ref.content())
                    .append("\n\n");
        }
        return context.toString().trim();
    }

    private List<Document> buildVectorDocuments(DocumentSnapshot snapshot) throws IOException {
        String content = Files.readString(Path.of(snapshot.getPath()), StandardCharsets.UTF_8);
        List<String> textChunks = split(content);
        List<Document> documents = new ArrayList<>();

        for (int i = 0; i < textChunks.size(); i++) {
            String text = textChunks.get(i);
            if (!StringUtils.hasText(text)) {
                continue;
            }

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("source", snapshot.getPath());
            metadata.put("fileName", Path.of(snapshot.getPath()).getFileName().toString());
            metadata.put("chunkIndex", i);
            metadata.put("size", snapshot.getSize());
            metadata.put("lastModified", snapshot.getLastModified());

            documents.add(Document.builder()
                    .id(chunkId(snapshot.getPath(), i))
                    .text(text)
                    .metadata(metadata)
                    .build());
        }
        return documents;
    }

    private void addInBatches(VectorStore vectorStore, List<Document> documents) {
        int batchSize = Math.max(1, properties.getBatchSize());
        for (int start = 0; start < documents.size(); start += batchSize) {
            int end = Math.min(start + batchSize, documents.size());
            vectorStore.add(documents.subList(start, end));
        }
    }

    private int deleteChunks(VectorStore vectorStore, List<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return 0;
        }
        vectorStore.delete(chunkIds);
        return chunkIds.size();
    }

    private RagManifest loadManifest() throws IOException {
        Path manifestPath = Path.of(properties.getManifestPath());
        if (!Files.exists(manifestPath)) {
            return new RagManifest();
        }
        return objectMapper.readValue(manifestPath.toFile(), RagManifest.class);
    }

    private void saveManifest(RagManifest manifest) throws IOException {
        Path manifestPath = Path.of(properties.getManifestPath());
        if (manifestPath.getParent() != null) {
            Files.createDirectories(manifestPath.getParent());
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifest);
    }

    private List<DocumentSnapshot> scanDocuments() throws IOException {
        Path docsPath = Path.of(properties.getDocsPath());
        if (!Files.exists(docsPath)) {
            Files.createDirectories(docsPath);
        }

        Set<String> extensions = properties.getExtensions().stream()
                .map(ext -> ext.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(HashSet::new));
        Path manifestPath = Path.of(properties.getManifestPath()).toAbsolutePath().normalize();

        try (Stream<Path> paths = Files.walk(docsPath)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> !path.toAbsolutePath().normalize().equals(manifestPath))
                    .filter(path -> extensions.contains(extensionOf(path)))
                    .sorted(Comparator.comparing(Path::toString))
                    .map(path -> {
                        try {
                            DocumentSnapshot snapshot = new DocumentSnapshot();
                            snapshot.setPath(path.toAbsolutePath().normalize().toString());
                            snapshot.setSize(Files.size(path));
                            snapshot.setLastModified(Files.getLastModifiedTime(path).toMillis());
                            return snapshot;
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to read document metadata: " + path, e);
                        }
                    })
                    .collect(Collectors.toList());
        }
    }

    private List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return chunks;
        }

        int chunkSize = Math.max(200, properties.getChunkSize());
        int overlap = Math.max(0, Math.min(properties.getChunkOverlap(), chunkSize / 2));
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();

        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());
            int adjustedEnd = adjustChunkEnd(normalized, start, end);
            String chunk = normalized.substring(start, adjustedEnd).trim();
            if (StringUtils.hasText(chunk)) {
                chunks.add(chunk);
            }
            if (adjustedEnd >= normalized.length()) {
                break;
            }
            start = Math.max(adjustedEnd - overlap, start + 1);
        }
        return chunks;
    }

    private int adjustChunkEnd(String text, int start, int end) {
        if (end >= text.length()) {
            return text.length();
        }
        int paragraphBreak = text.lastIndexOf("\n\n", end);
        if (paragraphBreak > start + properties.getChunkSize() / 2) {
            return paragraphBreak;
        }
        int lineBreak = text.lastIndexOf('\n', end);
        if (lineBreak > start + properties.getChunkSize() / 2) {
            return lineBreak;
        }
        return end;
    }

    private String extensionOf(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot) : "";
    }

    private String chunkId(String source, int chunkIndex) {
        String raw = source + "#" + chunkIndex;
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }

    public record RagReference(String source, int chunkIndex, String content, double score) {
    }

    public record IndexResult(int addedChunks, int deletedChunks, int skippedDocs) {
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RagManifest {
        private String docsPath;
        private String generatedAt;
        private List<IndexedDocument> documents = new ArrayList<>();

        public List<String> allChunkIds() {
            return documents.stream()
                    .flatMap(document -> document.getChunkIds().stream())
                    .collect(Collectors.toList());
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IndexedDocument {
        private String path;
        private long size;
        private long lastModified;
        private List<String> chunkIds = new ArrayList<>();

        public static IndexedDocument from(DocumentSnapshot snapshot) {
            IndexedDocument document = new IndexedDocument();
            document.setPath(snapshot.getPath());
            document.setSize(snapshot.getSize());
            document.setLastModified(snapshot.getLastModified());
            return document;
        }

        public boolean sameSnapshot(DocumentSnapshot snapshot) {
            return path.equals(snapshot.getPath())
                    && size == snapshot.getSize()
                    && lastModified == snapshot.getLastModified();
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DocumentSnapshot {
        private String path;
        private long size;
        private long lastModified;
    }
}
