package org.fliff.lifeSteal.utils;

import org.fliff.lifeSteal.LifeSteal;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AuditLogger {

    private static final String LOGS_DIR_NAME = "logs";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private final LifeSteal plugin;
    private File logsDir;
    private String currentLogFile;

    public AuditLogger(LifeSteal plugin) {
        this.plugin = plugin;
        this.logsDir = new File(plugin.getDataFolder(), LOGS_DIR_NAME);
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        this.currentLogFile = getLogFileName();
    }

    private String getLogFileName() {
        return DATE_FORMAT.format(new Date()) + ".log";
    }

    private File getLogFile() {
        return new File(logsDir, currentLogFile);
    }

    /**
     * Log an audit event to the daily log file.
     * Format: [HH:mm:ss] EVENT player=NAME world=WORLD uuid=UUID
     */
    public synchronized void log(String event, String player, String world, String uuid) {
        if (!isLoggingEnabled()) {
            return;
        }

        String timestamp = TIME_FORMAT.format(new Date());
        String logLine = "[" + timestamp + "] " + event;

        if (player != null && !player.isEmpty()) {
            logLine += " player=" + player;
        }
        if (world != null && !world.isEmpty()) {
            logLine += " world=" + world;
        }
        if (uuid != null && !uuid.isEmpty()) {
            logLine += " uuid=" + uuid;
        }

        appendToFile(logLine);
    }

    /**
     * Log an event without player/world/uuid context.
     */
    public synchronized void log(String event) {
        if (!isLoggingEnabled()) {
            return;
        }

        String timestamp = TIME_FORMAT.format(new Date());
        String logLine = "[" + timestamp + "] " + event;
        appendToFile(logLine);
    }

    private void appendToFile(String line) {
        File logFile = getLogFile();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write audit log: " + e.getMessage());
        }
    }

    private boolean isLoggingEnabled() {
        try {
            return plugin.getConfig().getBoolean("audit-logging.enabled", true);
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Reload the current log file reference (called on config reload).
     */
    public void reload() {
        this.currentLogFile = getLogFileName();
    }
}
