package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Audio and TTS tool using Windows built-in SAPI via PowerShell.
 */
@Component
public class AudioTool {

    private final ToolSafety safety;

    public AudioTool(ToolSafety safety) {
        this.safety = safety;
    }

    @Tool(description = "Use Windows text-to-speech to speak text aloud." +
            " Useful for alerting the user when a task is done, e.g. 'Analysis complete, exported to D:\\output.xlsx'")
    public String speak(@ToolParam(description = "Text to speak aloud") String text) {
        try {
            // Escape special characters for PowerShell
            String escaped = text.replace("'", "''");
            String ps = "Add-Type -AssemblyName System.Speech; " +
                    "$speak = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                    "$speak.Speak('" + escaped + "')";
            ProcessBuilder pb = new ProcessBuilder("powershell", "-Command", ps);
            pb.start();
            return "Speaking: " + (text.length() > 80 ? text.substring(0, 80) + "..." : text);
        } catch (Exception e) {
            return "TTS failed: " + e.getMessage();
        }
    }

    @Tool(description = "Play an audio file (.wav, .mp3, etc.) using the system default player." +
            " The player will run in the background and return immediately")
    public String playSound(@ToolParam(description = "Full path to the audio file") String filePath) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            java.io.File file = new java.io.File(filePath);
            if (!file.exists()) return "File does not exist: " + filePath;
            java.awt.Desktop.getDesktop().open(file);
            return "Playing: " + filePath;
        } catch (Exception e) {
            return "Failed to play sound: " + e.getMessage();
        }
    }
}