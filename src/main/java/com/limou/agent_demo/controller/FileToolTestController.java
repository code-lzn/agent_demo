package com.limou.agent_demo.controller;

import com.limou.agent_demo.tool.FileTool;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 将 FileTool 的各个方法暴露为 REST 接口，方便在 Knife4j 中手动调试。
 */
@RestController
@RequestMapping("/test/file-tool")
@Tag(name = "文件工具测试", description = "直接调用 FileTool 的各个方法，用于在 Swagger 中手动验证")
public class FileToolTestController {

    private final FileTool fileTool;

    public FileToolTestController(FileTool fileTool) {
        this.fileTool = fileTool;
    }

    // ==================== 读 ====================

    @GetMapping("/read")
    @Operation(summary = "读取文件内容", description = "读取文件的全部文本内容")
    public Map<String, String> readFile(
            @Parameter(description = "文件完整路径") @RequestParam String path) {
        return Map.of("result", fileTool.readFile(path));
    }

    @GetMapping("/read-lines")
    @Operation(summary = "读取前 N 行", description = "读取文件开头的指定行数")
    public Map<String, String> readFileLines(
            @Parameter(description = "文件完整路径") @RequestParam String path,
            @Parameter(description = "要读取的行数") @RequestParam int n) {
        return Map.of("result", fileTool.readFileLines(path, n));
    }

    @GetMapping("/read-range")
    @Operation(summary = "分块读取", description = "从指定偏移位置开始读取 N 行，适合分块读取大文件")
    public Map<String, String> readFileRange(
            @Parameter(description = "文件完整路径") @RequestParam String path,
            @Parameter(description = "起始行号（从 0 开始）") @RequestParam int offset,
            @Parameter(description = "最多读取行数") @RequestParam int limit) {
        return Map.of("result", fileTool.readFileRange(path, offset, limit));
    }

    @GetMapping("/read-last")
    @Operation(summary = "读取末尾 N 行", description = "读取文件最后 N 行，类似于 tail 命令，适合查看日志")
    public Map<String, String> readLastLines(
            @Parameter(description = "文件完整路径") @RequestParam String path,
            @Parameter(description = "从末尾读取的行数") @RequestParam int n) {
        return Map.of("result", fileTool.readLastLines(path, n));
    }

    @GetMapping("/count-lines")
    @Operation(summary = "统计行数", description = "统计文件总共有多少行")
    public Map<String, String> countLines(
            @Parameter(description = "文件完整路径") @RequestParam String path) {
        return Map.of("result", fileTool.countLines(path));
    }

    // ==================== 写 ====================

    @PostMapping("/write")
    @Operation(summary = "写入文件", description = "将内容写入文件（覆盖已有内容，不存在则创建）")
    public Map<String, String> writeFile(
            @Parameter(description = "文件完整路径") @RequestParam String path,
            @Parameter(description = "要写入的内容") @RequestBody String content) {
        return Map.of("result", fileTool.writeFile(path, content));
    }

    @PostMapping("/append")
    @Operation(summary = "追加写入", description = "将内容追加到文件末尾（文件不存在则创建）")
    public Map<String, String> appendFile(
            @Parameter(description = "文件完整路径") @RequestParam String path,
            @Parameter(description = "要追加的内容") @RequestBody String content) {
        return Map.of("result", fileTool.appendFile(path, content));
    }

    // ==================== 目录 ====================

    @GetMapping("/list-dir")
    @Operation(summary = "列出目录", description = "列出指定目录下的所有文件和子目录")
    public Map<String, String> listDir(
            @Parameter(description = "目录完整路径") @RequestParam String path) {
        return Map.of("result", fileTool.listDir(path));
    }

    @PostMapping("/create-dir")
    @Operation(summary = "创建目录", description = "递归创建目录，类似于 mkdir -p（目录已存在也不报错）")
    public Map<String, String> createDir(
            @Parameter(description = "要创建的目录完整路径") @RequestParam String path) {
        return Map.of("result", fileTool.createDir(path));
    }

    // ==================== 删除 ====================

    @DeleteMapping("/delete-file")
    @Operation(summary = "删除文件", description = "删除指定文件（目录请使用删除目录接口）")
    public Map<String, String> deleteFile(
            @Parameter(description = "要删除的文件完整路径") @RequestParam String path) {
        return Map.of("result", fileTool.deleteFile(path));
    }

    @DeleteMapping("/delete-dir")
    @Operation(summary = "删除目录", description = "递归删除目录及其所有内容，请谨慎使用")
    public Map<String, String> deleteDir(
            @Parameter(description = "要删除的目录完整路径") @RequestParam String path) {
        return Map.of("result", fileTool.deleteDir(path));
    }

    // ==================== 复制 & 移动 ====================

    @PostMapping("/copy")
    @Operation(summary = "复制文件", description = "将文件复制到目标路径（目标可以是目录或新文件名，已存在则覆盖）")
    public Map<String, String> copyFile(
            @Parameter(description = "源文件路径") @RequestParam String source,
            @Parameter(description = "目标路径（目录或新文件名）") @RequestParam String target) {
        return Map.of("result", fileTool.copyFile(source, target));
    }

    @PostMapping("/move")
    @Operation(summary = "移动/重命名", description = "移动或重命名文件或目录")
    public Map<String, String> moveFile(
            @Parameter(description = "源路径") @RequestParam String source,
            @Parameter(description = "目标路径") @RequestParam String target) {
        return Map.of("result", fileTool.moveFile(source, target));
    }

    // ==================== 信息 ====================

    @GetMapping("/info")
    @Operation(summary = "查看文件信息", description = "获取文件或目录的详细信息：大小、修改时间、创建时间、读写权限等")
    public Map<String, String> getFileInfo(
            @Parameter(description = "文件或目录的完整路径") @RequestParam String path) {
        return Map.of("result", fileTool.getFileInfo(path));
    }

    // ==================== 打开 ====================

    @PostMapping("/open-file")
    @Operation(summary = "打开文件", description = "用系统默认程序打开文件（.txt→记事本、.pdf→PDF阅读器等）。不支持可执行文件")
    public Map<String, String> openFile(
            @Parameter(description = "文件完整路径") @RequestParam String path) {
        return Map.of("result", fileTool.openFile(path));
    }

    @PostMapping("/open-dir")
    @Operation(summary = "打开目录", description = "在 Windows 资源管理器中打开目录。传文件路径则打开所在目录并选中文件")
    public Map<String, String> openDir(
            @Parameter(description = "目录或文件路径") @RequestParam String path) {
        return Map.of("result", fileTool.openDir(path));
    }

    // ==================== 搜索 ====================

    @GetMapping("/search-in-file")
    @Operation(summary = "文件中搜索", description = "在指定文件中搜索关键字，返回包含该关键字的行及行号")
    public Map<String, String> searchInFile(
            @Parameter(description = "文件完整路径") @RequestParam String path,
            @Parameter(description = "要搜索的关键字") @RequestParam String keyword) {
        return Map.of("result", fileTool.searchInFile(path, keyword));
    }

    @GetMapping("/search-in-dir")
    @Operation(summary = "目录中搜索", description = "递归搜索目录下所有文本文件中的关键字，自动跳过二进制文件")
    public Map<String, String> searchInDir(
            @Parameter(description = "目录完整路径") @RequestParam String path,
            @Parameter(description = "要搜索的关键字") @RequestParam String keyword) {
        return Map.of("result", fileTool.searchInDir(path, keyword));
    }

    @GetMapping("/find-files")
    @Operation(summary = "按文件名查找", description = "按通配符模式查找文件，如 *.java、test*.xml")
    public Map<String, String> findFiles(
            @Parameter(description = "目录完整路径") @RequestParam String path,
            @Parameter(description = "通配符模式，如 *.java") @RequestParam String pattern) {
        return Map.of("result", fileTool.findFiles(path, pattern));
    }
}