package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Image processing tool using JDK built-in ImageIO.
 */
@Component
public class ImageTool {

    private final ToolSafety safety;

    public ImageTool(ToolSafety safety) {
        this.safety = safety;
    }

    @Tool(description = "Get basic information about an image file: format, dimensions, file size")
    public String getImageInfo(@ToolParam(description = "Full path to the image file") String filePath) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            File file = new File(filePath);
            if (!file.exists()) return "File does not exist: " + filePath;
            BufferedImage img = ImageIO.read(file);
            if (img == null) return "Cannot read as image: " + filePath;
            long size = Files.size(Path.of(filePath));
            return "Image: " + filePath + "\n"
                    + "Format: " + getFormat(filePath) + "\n"
                    + "Dimensions: " + img.getWidth() + "x" + img.getHeight() + "\n"
                    + "File size: " + formatSize(size);
        } catch (IOException e) {
            return "Error reading image: " + e.getMessage();
        }
    }

    @Tool(description = "Resize an image to the specified width and height, saving to a new file")
    public String resizeImage(
            @ToolParam(description = "Source image path") String sourcePath,
            @ToolParam(description = "Destination image path") String destPath,
            @ToolParam(description = "New width in pixels") int width,
            @ToolParam(description = "New height in pixels") int height) {
        if (!safety.isPathAllowed(sourcePath)) return "Access denied: " + sourcePath;
        if (!safety.isPathAllowed(destPath)) return "Access denied: " + destPath;
        try {
            BufferedImage original = ImageIO.read(new File(sourcePath));
            if (original == null) return "Cannot read source image: " + sourcePath;

            Image scaled = original.getScaledInstance(width, height, Image.SCALE_SMOOTH);
            BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = result.createGraphics();
            g.drawImage(scaled, 0, 0, null);
            g.dispose();

            String format = getFormat(destPath);
            ImageIO.write(result, format, new File(destPath));
            return "Resized to " + width + "x" + height + " and saved to '" + destPath + "'";
        } catch (IOException e) {
            return "Error resizing image: " + e.getMessage();
        }
    }

    @Tool(description = "Convert an image from one format to another." +
            " Supported formats: jpg, png, gif, bmp. The format is determined by the destination file extension")
    public String convertImage(
            @ToolParam(description = "Source image path") String sourcePath,
            @ToolParam(description = "Destination path with target extension (.jpg, .png, .gif, .bmp)") String destPath) {
        if (!safety.isPathAllowed(sourcePath)) return "Access denied: " + sourcePath;
        if (!safety.isPathAllowed(destPath)) return "Access denied: " + destPath;
        try {
            BufferedImage img = ImageIO.read(new File(sourcePath));
            if (img == null) return "Cannot read source image: " + sourcePath;
            String format = getFormat(destPath);
            // JPEG doesn't support alpha, convert to RGB if needed
            if ("jpg".equalsIgnoreCase(format) || "jpeg".equalsIgnoreCase(format)) {
                BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                g.drawImage(img, 0, 0, Color.WHITE, null);
                g.dispose();
                img = rgb;
            }
            ImageIO.write(img, format, new File(destPath));
            return "Converted to '" + destPath + "'";
        } catch (IOException e) {
            return "Error converting image: " + e.getMessage();
        }
    }

    @Tool(description = "Crop a rectangular region from an image and save to a new file")
    public String cropImage(
            @ToolParam(description = "Source image path") String sourcePath,
            @ToolParam(description = "Destination path") String destPath,
            @ToolParam(description = "X offset of crop region") int x,
            @ToolParam(description = "Y offset of crop region") int y,
            @ToolParam(description = "Width of crop region") int width,
            @ToolParam(description = "Height of crop region") int height) {
        if (!safety.isPathAllowed(sourcePath)) return "Access denied: " + sourcePath;
        if (!safety.isPathAllowed(destPath)) return "Access denied: " + destPath;
        try {
            BufferedImage original = ImageIO.read(new File(sourcePath));
            if (original == null) return "Cannot read source image: " + sourcePath;
            BufferedImage cropped = original.getSubimage(x, y, width, height);
            String format = getFormat(destPath);
            ImageIO.write(cropped, format, new File(destPath));
            return "Cropped region (" + x + "," + y + " " + width + "x" + height + ") saved to '" + destPath + "'";
        } catch (IOException e) {
            return "Error cropping image: " + e.getMessage();
        } catch (Exception e) {
            return "Crop failed (possibly out of bounds): " + e.getMessage();
        }
    }

    private String getFormat(String path) {
        String name = path.toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "jpg";
        if (name.endsWith(".png")) return "png";
        if (name.endsWith(".gif")) return "gif";
        if (name.endsWith(".bmp")) return "bmp";
        return "png"; // default
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}