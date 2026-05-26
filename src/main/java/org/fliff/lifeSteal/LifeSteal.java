package org.fliff.lifeSteal;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.fliff.lifeSteal.commands.ResetHeartsCommand;
import org.fliff.lifeSteal.commands.WithdrawHeartCommand;
import org.fliff.lifeSteal.listeners.PlayerDeathListener;
import org.fliff.lifeSteal.listeners.RightClickListener;
import org.fliff.lifeSteal.utils.ConfigManager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LifeSteal extends JavaPlugin {

    private static LifeSteal instance;

    private final Map<String, Long> antiAltCooldowns = new ConcurrentHashMap<>();

    private File cooldownFile;
    private FileConfiguration cooldownConfig;
    private ConfigManager configManager;

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

        // Load persistent cooldowns
        loadCooldowns();
        cleanupExpiredCooldowns(configManager.getSameVictimCooldownHours());

        // Register Commands
        getCommand("resethearts").setExecutor(new ResetHeartsCommand());
        getCommand("withdrawheart").setExecutor(new WithdrawHeartCommand());
        getCommand("lifestealreload").setExecutor((sender, cmd, label, args) -> {
            if (!sender.hasPermission("lifesteal.reload")) {
                sender.sendMessage(new ConfigManager().getMessage("no-permission"));
                return true;
            }
            reloadPlugin();
            sender.sendMessage(new ConfigManager().getMessage("reload-success"));
            return true;
        });

        // Register Listeners
        Bukkit.getPluginManager().registerEvents(new PlayerDeathListener(), this);
        Bukkit.getPluginManager().registerEvents(new RightClickListener(), this);

        getLogger().info("LifeSteal Plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        // Save cooldowns on disable
        saveCooldowns();
        getLogger().info("LifeSteal Plugin has been disabled!");
    }

    public void saveCooldowns() {
        if (cooldownConfig == null)
            return;

        if (configManager != null) {
            cleanupExpiredCooldowns(configManager.getSameVictimCooldownHours());
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

    public void cleanupExpiredCooldowns(int cooldownHours) {
        if (antiAltCooldowns.isEmpty()) {
            return;
        }

        long cooldownMillis = (long) cooldownHours * 60L * 60L * 1000L;
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
        cleanupExpiredCooldowns(configManager.getSameVictimCooldownHours());

        getLogger().info("LifeSteal configuration reloaded!");
    }

    public void saveCooldownToConfig(String killerUUID, String victimUUID) {
        String key = killerUUID + "_" + victimUUID;
        antiAltCooldowns.put(key, System.currentTimeMillis());
        saveCooldowns();
    }

    public boolean isCooldownActive(String killerUUID, String victimUUID, int cooldownHours) {
        String key = killerUUID + "_" + victimUUID;
        Long lastStealTime = antiAltCooldowns.get(key);
        if (lastStealTime != null) {
            long now = System.currentTimeMillis();
            long cooldownMillis = (long) cooldownHours * 60L * 60L * 1000L;
            return (now - lastStealTime) < cooldownMillis;
        }
        return false;
    }

    public void removeCooldown(String killerUUID, String victimUUID) {
        String key = killerUUID + "_" + victimUUID;
        antiAltCooldowns.remove(key);
    }
}
