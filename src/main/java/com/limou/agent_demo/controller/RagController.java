package com.limou.agent_demo.controller;

import com.limou.agent_demo.service.LocalRagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/rag")
@Tag(name = "RAG", description = "Local document indexing into vector store")
public class RagController {

    private final LocalRagService localRagService;

    public RagController(LocalRagService localRagService) {
        this.localRagService = localRagService;
    }

    @PostMapping("/index")
    @Operation(summary = "Index local documents into vector database")
    public LocalRagService.IndexResult index() throws IOException {
        return localRagService.indexDocuments();
    }

    @PostMapping("/reindex")
    @Operation(summary = "Force rebuild local documents into vector database")
    public LocalRagService.IndexResult reindex() throws IOException {
        return localRagService.reindexDocuments();
    }

    @GetMapping("/search")
    @Operation(summary = "Test RAG vector search")
    public List<LocalRagService.RagReference> search(@RequestParam String query) {
        return localRagService.search(query);
    }
}
