package de.craftplay.shop.core.logging;

import de.craftplay.shop.CraftplayShopPlugin;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

public class PluginLogService {
    private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CraftplayShopPlugin plugin;
    private final Object debugFileLock = new Object();

    public PluginLogService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void reloadState() {
        if (isFileDebugEnabled()) {
            writeDebugFileLine("SYSTEM", "Debug file logging enabled.");
        }
    }

    public void info(String message) {
        plugin.getLogger().info(message);
        writeDebugFileLine("INFO", message);
    }

    public void warn(String message) {
        plugin.getLogger().warning(message);
        writeDebugFileLine("WARN", message);
    }

    public void error(String message, Throwable throwable) {
        plugin.getLogger().log(Level.SEVERE, message, throwable);
        String combined = throwable == null ? message : message + System.lineSeparator() + stackTrace(throwable);
        writeDebugFileLine("ERROR", combined);
    }

    public void debug(String message) {
        if (plugin.getConfigService() != null && plugin.getConfigService().debug()) {
            plugin.getLogger().info("[Debug] " + message);
        }
        writeDebugFileLine("DEBUG", message);
    }

    public boolean isFileDebugEnabled() {
        return plugin.getConfigService() != null && plugin.getConfigService().debugFileLoggingEnabled();
    }

    public void setFileDebugEnabled(boolean enabled) {
        if (plugin.getConfigService() == null) {
            return;
        }
        boolean previous = isFileDebugEnabled();
        if (!enabled && previous) {
            forceWriteDebugFileLine("SYSTEM", "Debug file logging disabled by command.");
        }
        plugin.getConfigService().setDebugFileLoggingEnabled(enabled);
        if (enabled && !previous) {
            writeDebugFileLine("SYSTEM", "Debug file logging enabled by command.");
        }
    }

    private void writeDebugFileLine(String level, String message) {
        if (!isFileDebugEnabled()) {
            return;
        }
        forceWriteDebugFileLine(level, message);
    }

    private void forceWriteDebugFileLine(String level, String message) {
        synchronized (debugFileLock) {
            try {
                java.io.File folder = new java.io.File(plugin.getDataFolder(), "debuglogs");
                folder.mkdirs();
                java.io.File file = new java.io.File(folder, fileName());
                String sanitized = message == null ? "" : message.replace("\r", "");
                String line = "[" + LINE_TIME.format(LocalDateTime.now()) + "][" + level + "] " + sanitized + System.lineSeparator();
                Files.writeString(file.toPath(), line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            } catch (Exception ignored) {
            }
        }
    }

    private String fileName() {
        String pattern = plugin.getConfigService() == null ? "debug-%date%.txt" : plugin.getConfigService().debugFileNamePattern();
        String resolved = pattern.replace("%date%", LocalDate.now().format(DateTimeFormatter.ISO_DATE));
        if (!resolved.toLowerCase(java.util.Locale.ROOT).endsWith(".txt")) {
            resolved += ".txt";
        }
        return resolved;
    }

    private String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
