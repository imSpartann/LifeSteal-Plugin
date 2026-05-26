package org.fliff.lifeSteal.utils;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.fliff.lifeSteal.LifeSteal;

import java.util.List;

public class ConfigManager {

    private final FileConfiguration config;

    public ConfigManager() {
        this.config = LifeSteal.getInstance().getConfig();
    }

    public void reload() {
        LifeSteal.getInstance().reloadConfig();
    }

    public String getHeartItemName() {
        return ChatColor.translateAlternateColorCodes('&', config.getString("heart-item-name", "&c&lHeart"));
    }

    public int getMaxHealth() {
        return config.getInt("max-health", 20);
    }

    public int getMinHealth() {
        return config.getInt("min-health", 1);
    }

    public String formatMessage(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Retrieves a message from config and translates & color codes.
     * Falls back to the key itself if the message is not found.
     */
    public String getMessage(String path) {
        String fallback = path;
        String message = config.getString("messages." + path, fallback);
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Checks if the given world is in the enabled-worlds list.
     * If enabled-worlds is empty or missing, all worlds are enabled.
     */
    public boolean isEnabledWorld(World world) {
        if (world == null) {
            return false;
        }
        List<String> enabledWorlds = config.getStringList("enabled-worlds");
        if (enabledWorlds == null || enabledWorlds.isEmpty()) {
            return true;
        }
        return enabledWorlds.contains(world.getName());
    }

    public boolean isAntiAltEnabled() {
        return config.getBoolean("anti-alt.enabled", true);
    }

    public int getSameVictimCooldownHours() {
        return config.getInt("anti-alt.same-victim-cooldown-hours", 12);
    }

    public int getMinimumPlaytimeHours() {
        return config.getInt("anti-alt.minimum-playtime-hours", 3);
    }
}
