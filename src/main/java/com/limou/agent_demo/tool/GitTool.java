package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Component
public class GitTool {

    private final Path projectRoot;

    public GitTool() {
        this.projectRoot = Path.of(System.getProperty("user.dir"));
    }

    @Tool(description = "Show current git status (modified, added, deleted files)")
    public String gitStatus() {
        return runGit("status", "--porcelain");
    }

    @Tool(description = "Show git diff of uncommitted changes")
    public String gitDiff(
            @ToolParam(description = "If true, show staged changes only. If false, show unstaged changes") boolean staged) {
        if (staged) {
            return runGit("diff", "--staged");
        }
        return runGit("diff");
    }

    @Tool(description = "Show recent git commit log")
    public String gitLog(
            @ToolParam(description = "Number of recent commits, default 10 if empty or zero") int n) {
        if (n <= 0) n = 10;
        return runGit("log", "--oneline", "-" + Math.min(n, 30));
    }

    @Tool(description = "Show which files changed in the last N commits")
    public String gitShowChangedFiles(
            @ToolParam(description = "Number of commits to check, default 5") int n) {
        if (n <= 0) n = 5;
        return runGit("diff", "--name-only", "HEAD~" + Math.min(n, 20));
    }

    @Tool(description = "Show current branch name")
    public String gitBranch() {
        return runGit("branch", "--show-current");
    }

    private String runGit(String... args) {
        if (!Files.isDirectory(projectRoot.resolve(".git"))) {
            return "Not a git repository at: " + projectRoot;
        }

        try {
            String[] cmd = new String[args.length + 1];
            cmd[0] = "git";
            System.arraycopy(args, 0, cmd, 1, args.length);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(projectRoot.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return "Git command timed out";
            }

            String output = new String(process.getInputStream().readAllBytes());
            if (output.length() > 4000) {
                output = output.substring(0, 4000) + "\n... (truncated)";
            }
            if (output.isBlank()) {
                return "(no output)";
            }
            return output;
        } catch (IOException | InterruptedException e) {
            return "Git command failed: " + e.getMessage();
        }
    }
}
