package org.fliff.lifeSteal;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.fliff.lifeSteal.commands.CheckHeartCommand;
import org.fliff.lifeSteal.commands.GiveHeartCommand;
import org.fliff.lifeSteal.commands.ResetHeartsCommand;
import org.fliff.lifeSteal.commands.WithdrawHeartCommand;
import org.fliff.lifeSteal.listeners.PlayerDeathListener;
import org.fliff.lifeSteal.listeners.RightClickListener;
import org.fliff.lifeSteal.listeners.InventoryProtectionListener;
import org.fliff.lifeSteal.listeners.PlayerJoinListener;
import org.fliff.lifeSteal.utils.AuditLogger;
import org.fliff.lifeSteal.utils.ConfigManager;
import org.fliff.lifeSteal.utils.PlayerDataManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LifeSteal extends JavaPlugin {

    private static LifeSteal instance;

    private final Map<String, Long> antiAltCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Boolean> issuedHeartUUIDs = new ConcurrentHashMap<>();

    private File cooldownFile;
    private FileConfiguration cooldownConfig;
    private File heartsFile;
    private FileConfiguration heartsConfig;
    private ConfigManager configManager;
    private AuditLogger auditLogger;
    private PlayerDataManager playerDataManager;

    public static LifeSteal getInstance() {
        return instance;
    }

    public Map<String, Long> getAntiAltCooldowns() {
        return antiAltCooldowns;
    }

    @Override
    public void onEnable() {
        instance = this;

        // Load Config
        saveDefaultConfig();
        configManager = new ConfigManager();

        // Initialize cooldowns.yml
        cooldownFile = new File(getDataFolder(), "cooldowns.yml");
        if (!cooldownFile.exists()) {
            try {
                cooldownFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Failed to create cooldowns.yml: " + e.getMessage());
            }
        }
        cooldownConfig = YamlConfiguration.loadConfiguration(cooldownFile);

        // Initialize hearts.yml
        heartsFile = new File(getDataFolder(), "hearts.yml");
        if (!heartsFile.exists()) {
            try {
                heartsFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Failed to create hearts.yml: " + e.getMessage());
            }
        }
        heartsConfig = YamlConfiguration.loadConfiguration(heartsFile);

        // Load persistent cooldowns
        loadCooldowns();
        cleanupExpiredCooldowns(configManager.getSameVictimCooldownMinutes());
        loadIssuedHearts();

        // Initialize Audit Logger
        auditLogger = new AuditLogger(this);

        // Initialize Player Data Manager
        playerDataManager = new PlayerDataManager();
        playerDataManager.init();

        // Register Commands
        getCommand("resethearts").setExecutor(new ResetHeartsCommand());
        getCommand("withdrawheart").setExecutor(new WithdrawHeartCommand());
        getCommand("giveheart").setExecutor(new GiveHeartCommand());
        getCommand("lifesteal").setExecutor((sender, cmd, label, args) -> {
            if (args.length < 1) {
                sender.sendMessage(configManager.formatMessage("&cUsage: /lifesteal <check|reload>"));
                return true;
            }

            String subcommand = args[0].toLowerCase();

            switch (subcommand) {
                case "check":
                    if (!sender.hasPermission("lifesteal.check")) {
                        sender.sendMessage(new ConfigManager().getMessage("no-permission"));
                        return true;
                    }
                    // Forward to CheckHeartCommand logic
                    CheckHeartCommand checkCmd = new CheckHeartCommand();
                    return checkCmd.onCommand(sender, cmd, label,
                            args.length > 1 ? new String[] { args[1] } : new String[] {});

                case "reload":
                    if (!sender.hasPermission("lifesteal.reload")) {
                        sender.sendMessage(new ConfigManager().getMessage("no-permission"));
                        return true;
                    }
                    reloadPlugin();
                    logReload();
                    sender.sendMessage(new ConfigManager().getMessage("reload-success"));
                    return true;

                default:
                    sender.sendMessage(configManager.formatMessage("&cUnknown subcommand: " + args[0]));
                    sender.sendMessage(configManager.formatMessage("&cUsage: /lifesteal <check|reload>"));
                    return true;
            }
        });
        getCommand("lifesteal").setTabCompleter(new org.bukkit.command.TabCompleter() {
            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                List<String> suggestions = new ArrayList<>();

                if (args.length == 1) {
                    // First argument: suggest subcommands
                    String partial = args[0].toLowerCase(Locale.ROOT);
                    if (sender.hasPermission("lifesteal.check") && "check".startsWith(partial)) {
                        suggestions.add("check");
                    }
                    if (sender.hasPermission("lifesteal.reload") && "reload".startsWith(partial)) {
                        suggestions.add("reload");
                    }
                } else if (args.length == 2 && "check".equals(args[0].toLowerCase(Locale.ROOT))) {
                    // Second argument: suggest online player names + cached offline players
                    String partial = args[1].toLowerCase(Locale.ROOT);
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getName().toLowerCase(Locale.ROOT).startsWith(partial)) {
                            suggestions.add(player.getName());
                        }
                    }
                    // Add cached offline players from playerdata.yml
                    for (String cachedName : playerDataManager.getCachedPlayerNames()) {
                        if (!suggestions.contains(cachedName)
                                && cachedName.toLowerCase(Locale.ROOT).startsWith(partial)) {
                            suggestions.add(cachedName);
                        }
                    }
                }

                return suggestions;
            }
        });

        // Register Listeners
        Bukkit.getPluginManager().registerEvents(new PlayerDeathListener(), this);
        Bukkit.getPluginManager().registerEvents(new RightClickListener(), this);
        Bukkit.getPluginManager().registerEvents(new InventoryProtectionListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(), this);

        getLogger().info("LifeSteal Plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        // Save cooldowns, hearts, and player data on disable
        saveCooldowns();
        saveIssuedHearts();
        if (playerDataManager != null) {
            playerDataManager.save();
        }
        getLogger().info("LifeSteal Plugin has been disabled!");
    }

    public void saveCooldowns() {
        if (cooldownConfig == null)
            return;

        if (configManager != null) {
            cleanupExpiredCooldowns(configManager.getSameVictimCooldownMinutes());
        }

        cooldownConfig.set("cooldowns", null);
        Map<String, Long> cooldowns = getAntiAltCooldowns();
        if (cooldowns != null && !cooldowns.isEmpty()) {
            for (Map.Entry<String, Long> entry : cooldowns.entrySet()) {
                cooldownConfig.set("cooldowns." + entry.getKey(), entry.getValue());
            }
        }

        try {
            cooldownConfig.save(cooldownFile);
        } catch (IOException e) {
            getLogger().severe("Failed to save cooldowns.yml: " + e.getMessage());
        }
    }

    public void loadCooldowns() {
        if (cooldownConfig == null)
            return;

        antiAltCooldowns.clear();
        if (cooldownConfig.contains("cooldowns")) {
            for (String key : cooldownConfig.getConfigurationSection("cooldowns").getKeys(false)) {
                Long value = cooldownConfig.getLong("cooldowns." + key);
                if (value > 0) {
                    antiAltCooldowns.put(key, value);
                }
            }
        }

        getLogger().info("Loaded " + antiAltCooldowns.size() + " cooldown entries.");
    }

    public void cleanupExpiredCooldowns(int cooldownMinutes) {
        if (antiAltCooldowns.isEmpty()) {
            return;
        }

        long cooldownMillis = (long) cooldownMinutes * 60L * 1000L;
        long currentTime = System.currentTimeMillis();
        int removedCount = 0;

        for (Map.Entry<String, Long> entry : antiAltCooldowns.entrySet()) {
            Long timestamp = entry.getValue();
            if (timestamp == null || timestamp <= 0) {
                continue;
            }
            if (currentTime - timestamp > cooldownMillis) {
                antiAltCooldowns.remove(entry.getKey());
                removedCount++;
            }
        }

        if (removedCount > 0) {
            getLogger().info("Removed " + removedCount + " expired cooldown entries.");
        }
    }

    public void reloadPlugin() {
        // Reload config.yml
        reloadConfig();
        configManager = new ConfigManager();

        // Reload cooldowns.yml
        cooldownConfig = YamlConfiguration.loadConfiguration(cooldownFile);
        loadCooldowns();
        cleanupExpiredCooldowns(configManager.getSameVictimCooldownMinutes());

        // Reload hearts.yml
        heartsConfig = YamlConfiguration.loadConfiguration(heartsFile);
        loadIssuedHearts();

        // Reload player data
        if (playerDataManager != null) {
            playerDataManager.reload();
        }

        // Reload audit logger
        if (auditLogger != null) {
            auditLogger.reload();
        }

        getLogger().info("LifeSteal configuration reloaded!");
    }

    public void saveCooldownToConfig(String killerUUID, String victimUUID) {
        String key = killerUUID + "_" + victimUUID;
        antiAltCooldowns.put(key, System.currentTimeMillis());
        saveCooldowns();
    }

    public boolean isCooldownActive(String killerUUID, String victimUUID, int cooldownMinutes) {
        String key = killerUUID + "_" + victimUUID;
        Long lastStealTime = antiAltCooldowns.get(key);
        if (lastStealTime != null) {
            long now = System.currentTimeMillis();
            long cooldownMillis = (long) cooldownMinutes * 60L * 1000L;
            return (now - lastStealTime) < cooldownMillis;
        }
        return false;
    }

    public void removeCooldown(String killerUUID, String victimUUID) {
        String key = killerUUID + "_" + victimUUID;
        antiAltCooldowns.remove(key);
    }

    // ========================
    // Hearts.yml Persistence
    // ========================

    public void saveIssuedHearts() {
        if (heartsConfig == null)
            return;

        heartsConfig.set("issued-hearts", null);
        Map<String, Boolean> heartUUIDs = getIssuedHeartUUIDs();
        if (heartUUIDs != null && !heartUUIDs.isEmpty()) {
            for (Map.Entry<String, Boolean> entry : heartUUIDs.entrySet()) {
                heartsConfig.set("issued-hearts." + entry.getKey(), entry.getValue());
            }
        }

        try {
            heartsConfig.save(heartsFile);
        } catch (IOException e) {
            getLogger().severe("Failed to save hearts.yml: " + e.getMessage());
        }
    }

    public void loadIssuedHearts() {
        if (heartsConfig == null)
            return;

        issuedHeartUUIDs.clear();
        try {
            if (heartsConfig.contains("issued-hearts")) {
                for (String key : heartsConfig.getConfigurationSection("issued-hearts").getKeys(false)) {
                    issuedHeartUUIDs.put(key, true);
                }
            }
        } catch (Exception e) {
            getLogger().severe("Failed to load hearts.yml, starting fresh: " + e.getMessage());
            heartsConfig = YamlConfiguration.loadConfiguration(heartsFile);
        }

        getLogger().info("Loaded " + issuedHeartUUIDs.size() + " issued heart entries.");
    }

    public void addIssuedHeartUUID(String heartUUID) {
        issuedHeartUUIDs.put(heartUUID, true);
        saveIssuedHeartsToConfig(heartUUID, true);
    }

    public boolean removeIssuedHeartUUID(String heartUUID) {
        boolean removed = issuedHeartUUIDs.remove(heartUUID) != null;
        if (removed) {
            saveIssuedHeartsToConfig(heartUUID, false);
        }
        return removed;
    }

    /**
     * Save a single heart UUID change to hearts.yml immediately.
     * Adds or removes the UUID from the issued-hearts section.
     */
    private void saveIssuedHeartsToConfig(String heartUUID, boolean present) {
        if (heartsConfig == null)
            return;
        if (present) {
            heartsConfig.set("issued-hearts." + heartUUID, true);
        } else {
            heartsConfig.set("issued-hearts." + heartUUID, null);
        }
        try {
            heartsConfig.save(heartsFile);
        } catch (IOException e) {
            getLogger().severe("Failed to save hearts.yml for UUID " + heartUUID + ": " + e.getMessage());
        }
    }

    public boolean isHeartUUIDValid(String heartUUID) {
        return issuedHeartUUIDs.containsKey(heartUUID);
    }

    public Map<String, Boolean> getIssuedHeartUUIDs() {
        return issuedHeartUUIDs;
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
    }

    public AuditLogger getAuditLogger() {
        return auditLogger;
    }

    public void logReload() {
        if (auditLogger != null) {
            auditLogger.log("RELOAD");
        }
    }
}
