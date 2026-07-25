package com.limou.agent_demo.tool;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileToolTest {

    private static Path testDir;
    private static FileTool fileTool;
    private static ToolSafety toolSafety;

//    @BeforeAll
//    static void setUp() throws IOException {
//        testDir = Files.createTempDirectory("filetool_test_");
//        // 把测试目录加入白名单
//        toolSafety = new ToolSafety(System.getProperty("user.home") + "," + testDir.toString());
//        fileTool = new FileTool(toolSafety);
//
//        // 准备一个测试文件
//        Files.writeString(testDir.resolve("hello.txt"), "line1\nline2\nline3\nline4\nline5\n");
//        // 准备一个带关键字的文件
//        Files.writeString(testDir.resolve("search.txt"), """
//                apple is red
//                banana is yellow
//                cherry is red
//                grape is purple
//                """);
//        // 准备子目录
//        Files.createDirectories(testDir.resolve("sub/nested"));
//        Files.writeString(testDir.resolve("sub/nested/deep.txt"), "deep file content contains apple\n");
//    }

    @AfterAll
    static void tearDown() throws IOException {
        try (var walk = Files.walk(testDir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.delete(p); } catch (IOException ignored) {}
                    });
        }
    }

    // ==================== 读 ====================

    @Test
    void testReadFile() {
        String result = fileTool.readFile(testDir.resolve("hello.txt").toString());
        assertEquals("line1\nline2\nline3\nline4\nline5", result);
    }

    @Test
    void testReadFileLines() {
        String result = fileTool.readFileLines(testDir.resolve("hello.txt").toString(), 2);
        assertEquals("line1\nline2", result);
    }

    @Test
    void testReadFileRange() {
        String result = fileTool.readFileRange(testDir.resolve("hello.txt").toString(), 1, 2);
        assertEquals("line2\nline3", result);
    }

    @Test
    void testReadLastLines() {
        String result = fileTool.readLastLines(testDir.resolve("hello.txt").toString(), 2);
        assertEquals("line4\nline5", result);
    }

    @Test
    void testCountLines() {
        String result = fileTool.countLines(testDir.resolve("hello.txt").toString());
        assertTrue(result.contains("5 lines"));
    }

    // ==================== 写 ====================

    @Test
    void testWriteFile() throws IOException {
        Path p = testDir.resolve("write_test.txt");
        String result = fileTool.writeFile(p.toString(), "hello world");
        assertTrue(result.contains("Successfully wrote"));
        assertEquals("hello world", Files.readString(p));
    }

    @Test
    void testAppendFile() throws IOException {
        Path p = testDir.resolve("append_test.txt");
        fileTool.writeFile(p.toString(), "first line");
        fileTool.appendFile(p.toString(), "\nsecond line");
        assertEquals("first line\nsecond line", Files.readString(p));
    }

    // ==================== 目录 ====================

    @Test
    void testCreateDir() {
        String result = fileTool.createDir(testDir.resolve("new_dir/sub_dir").toString());
        assertTrue(result.contains("Successfully created"));
        assertTrue(Files.isDirectory(testDir.resolve("new_dir/sub_dir")));
    }

    @Test
    void testListDir() {
        String result = fileTool.listDir(testDir.toString());
        assertTrue(result.contains("[FILE] hello.txt"));
        assertTrue(result.contains("[DIR]  sub"));
    }

    // ==================== 删除 ====================

    @Test
    void testDeleteFile() throws IOException {
        Path p = testDir.resolve("to_delete.txt");
        Files.writeString(p, "tmp");
        String result = fileTool.deleteFile(p.toString());
        assertTrue(result.contains("Successfully deleted"));
        assertFalse(Files.exists(p));
    }

    @Test
    void testDeleteDir() throws IOException {
        Path dir = testDir.resolve("dir_to_delete");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("f.txt"), "tmp");
        String result = fileTool.deleteDir(dir.toString());
        assertTrue(result.contains("Successfully deleted"));
        assertFalse(Files.exists(dir));
    }

    // ==================== 复制 & 移动 ====================

    @Test
    void testCopyFile() throws IOException {
        Path src = testDir.resolve("hello.txt");
        Path dst = testDir.resolve("hello_copy.txt");
        String result = fileTool.copyFile(src.toString(), dst.toString());
        assertTrue(result.contains("Successfully copied"));
        assertTrue(Files.exists(dst));
        assertEquals(Files.readString(src), Files.readString(dst));
    }

    @Test
    void testMoveFile() throws IOException {
        Path src = testDir.resolve("move_src.txt");
        Path dst = testDir.resolve("move_dst.txt");
        Files.writeString(src, "moving...");
        String result = fileTool.moveFile(src.toString(), dst.toString());
        assertTrue(result.contains("Successfully moved"));
        assertFalse(Files.exists(src));
        assertTrue(Files.exists(dst));
        assertEquals("moving...", Files.readString(dst));
    }

    // ==================== 信息 ====================

    @Test
    void testGetFileInfo() {
        String result = fileTool.getFileInfo(testDir.resolve("hello.txt").toString());
        assertTrue(result.contains("Type: File"));
        assertTrue(result.contains("Size:"));
        assertTrue(result.contains("Modified:"));
        assertTrue(result.contains("Readable: true"));
        assertTrue(result.contains("Writable: true"));
    }

    @Test
    void testGetFileInfoForDir() {
        String result = fileTool.getFileInfo(testDir.toString());
        assertTrue(result.contains("Type: Directory"));
    }

    // ==================== 搜索 ====================

    @Test
    void testSearchInFile() {
        String result = fileTool.searchInFile(testDir.resolve("search.txt").toString(), "red");
        assertTrue(result.contains("apple is red"));
        assertTrue(result.contains("cherry is red"));
        assertFalse(result.contains("banana"));  // banana 不含 red
    }

    @Test
    void testSearchInDir() {
        String result = fileTool.searchInDir(testDir.toString(), "apple");
        assertTrue(result.contains("search.txt"));
        assertTrue(result.contains("deep.txt"));  // 子目录的文件也匹配
    }

    @Test
    void testFindFiles() {
        // 递归找所有 .txt
        String result = fileTool.findFiles(testDir.toString(), "*.txt");
        assertTrue(result.contains("hello.txt"));
        assertTrue(result.contains("deep.txt"));  // 子目录的也找到
        assertFalse(result.contains(".java"));
    }

    // ==================== 安全 ====================

    @Test
    void testAccessDenied() {
        String result = fileTool.readFile("C:\\Windows\\System32\\something.dll");
        assertTrue(result.contains("Access denied"));
    }
}
