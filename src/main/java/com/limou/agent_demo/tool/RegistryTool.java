package com.limou.agent_demo.tool;

import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Windows Registry manipulation tool using JNA.
 */
@Component
public class RegistryTool {

    @Tool(description = "Read a value from the Windows Registry." +
            " Hive must be one of: HKEY_CURRENT_USER, HKEY_LOCAL_MACHINE, HKEY_CLASSES_ROOT." +
            " Example: hive=HKEY_LOCAL_MACHINE, key=SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion, valueName=ProductName")
    public String readRegistry(
            @ToolParam(description = "Registry hive: HKEY_CURRENT_USER, HKEY_LOCAL_MACHINE, or HKEY_CLASSES_ROOT") String hive,
            @ToolParam(description = "Registry key path, e.g. SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion") String key,
            @ToolParam(description = "Value name, e.g. ProductName") String valueName) {
        try {
            WinReg.HKEY hkey = getHive(hive);
            if (hkey == null) return "Invalid hive: " + hive + ". Use HKEY_CURRENT_USER, HKEY_LOCAL_MACHINE, or HKEY_CLASSES_ROOT";
            String value = Advapi32Util.registryGetStringValue(hkey, key, valueName);
            return valueName + " = " + value;
        } catch (Exception e) {
            return "Failed to read registry: " + e.getMessage();
        }
    }

    @Tool(description = "Write a string value to the Windows Registry." +
            " Hive must be one of: HKEY_CURRENT_USER, HKEY_LOCAL_MACHINE." +
            " WARNING: Modifying the registry can affect system behavior. Use with caution." +
            " Writing to HKEY_LOCAL_MACHINE typically requires admin privileges")
    public String writeRegistry(
            @ToolParam(description = "Registry hive: HKEY_CURRENT_USER or HKEY_LOCAL_MACHINE") String hive,
            @ToolParam(description = "Registry key path") String key,
            @ToolParam(description = "Value name to set") String valueName,
            @ToolParam(description = "String value to set") String value) {
        try {
            WinReg.HKEY hkey = getHive(hive);
            if (hkey == null) return "Invalid hive: " + hive + ". Use HKEY_CURRENT_USER or HKEY_LOCAL_MACHINE";
            Advapi32Util.registrySetStringValue(hkey, key, valueName, value);
            return "Set " + hive + "\\" + key + "\\" + valueName + " = " + value;
        } catch (Exception e) {
            return "Failed to write registry: " + e.getMessage();
        }
    }

    @Tool(description = "List all installed programs found in the registry (from HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall)")
    public String listInstalledPrograms() {
        try {
            String key = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall";
            String[] subkeys = Advapi32Util.registryGetKeys(WinReg.HKEY_LOCAL_MACHINE, key);
            StringBuilder sb = new StringBuilder();
            for (String subkey : subkeys) {
                try {
                    String displayName = Advapi32Util.registryGetStringValue(
                            WinReg.HKEY_LOCAL_MACHINE, key + "\\" + subkey, "DisplayName");
                    if (displayName != null && !displayName.isEmpty()) {
                        sb.append(displayName).append("\n");
                    }
                } catch (Exception ignored) {}
            }
            return sb.isEmpty() ? "No installed programs found" : sb.toString().stripTrailing();
        } catch (Exception e) {
            return "Failed to list installed programs: " + e.getMessage();
        }
    }

    private WinReg.HKEY getHive(String hive) {
        return switch (hive.toUpperCase()) {
            case "HKEY_CURRENT_USER" -> WinReg.HKEY_CURRENT_USER;
            case "HKEY_LOCAL_MACHINE" -> WinReg.HKEY_LOCAL_MACHINE;
            case "HKEY_CLASSES_ROOT" -> WinReg.HKEY_CLASSES_ROOT;
            default -> null;
        };
    }
}