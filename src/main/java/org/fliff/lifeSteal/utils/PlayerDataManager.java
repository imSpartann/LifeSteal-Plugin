package org.fliff.lifeSteal.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.fliff.lifeSteal.LifeSteal;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerDataManager {

    private static PlayerDataManager instance;
    private final LifeSteal plugin = LifeSteal.getInstance();
    private File playerDataFile;
    private FileConfiguration playerDataConfig;

    public static PlayerDataManager getInstance() {
        if (instance == null) {
            instance = new PlayerDataManager();
        }
        return instance;
    }

    public void init() {
        playerDataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!playerDataFile.exists()) {
            try {
                playerDataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create playerdata.yml: " + e.getMessage());
            }
        }
        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
        plugin.getLogger().info("Loaded playerdata.yml with " + getPlayerCount() + " cached players.");
    }

    public void save() {
        if (playerDataConfig == null)
            return;
        try {
            playerDataConfig.save(playerDataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save playerdata.yml: " + e.getMessage());
        }
    }

    public void reload() {
        if (playerDataConfig != null && playerDataFile != null) {
            playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
        }
    }

    public int getPlayerCount() {
        if (playerDataConfig == null || !playerDataConfig.contains("players"))
            return 0;
        return playerDataConfig.getConfigurationSection("players").getKeys(false).size();
    }

    public List<String> getCachedPlayerNames() {
        List<String> names = new ArrayList<>();
        if (playerDataConfig == null || !playerDataConfig.contains("players"))
            return names;
        for (String key : playerDataConfig.getConfigurationSection("players").getKeys(false)) {
            if (playerDataConfig.contains("players." + key + ".name")) {
                names.add(playerDataConfig.getString("players." + key + ".name"));
            }
        }
        return names;
    }

    public void cachePlayerData(UUID playerUUID, String playerName, double maxHealth, double currentHealth,
            long playtimeMinutes) {
        if (playerDataConfig == null)
            return;

        String path = "players." + playerUUID;
        if (!playerDataConfig.contains(path)) {
            playerDataConfig.set(path + ".name", playerName);
        }
        playerDataConfig.set(path + ".max-health", maxHealth);
        playerDataConfig.set(path + ".visible-hearts", (int) (maxHealth / 2));
        playerDataConfig.set(path + ".last-health", currentHealth);
        playerDataConfig.set(path + ".playtime-minutes", playtimeMinutes);
        playerDataConfig.set(path + ".last-seen", System.currentTimeMillis() / 1000L);

        save();
    }

    public void updateLastSeen(UUID playerUUID, String playerName) {
        if (playerDataConfig == null)
            return;

        String path = "players." + playerUUID;
        if (!playerDataConfig.contains(path)) {
            playerDataConfig.set(path + ".name", playerName);
        }
        playerDataConfig.set(path + ".last-seen", System.currentTimeMillis() / 1000L);

        save();
    }

    public FileConfiguration getPlayerDataConfig() {
        return playerDataConfig;
    }

    public String formatTimeAgo(long timestampSeconds) {
        long now = System.currentTimeMillis() / 1000L;
        long diff = now - timestampSeconds;

        if (diff < 0)
            return "Just now";

        long days = diff / 86400;
        long hours = (diff % 86400) / 3600;
        long minutes = (diff % 3600) / 60;

        if (days > 0) {
            return days + "d " + hours + "h ago";
        } else if (hours > 0) {
            return hours + "h " + minutes + "m ago";
        } else {
            return minutes + "m ago";
        }
    }
}
