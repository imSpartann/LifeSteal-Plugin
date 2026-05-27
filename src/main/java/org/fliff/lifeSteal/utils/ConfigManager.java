package org.fliff.lifeSteal.utils;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.fliff.lifeSteal.LifeSteal;

import java.util.ArrayList;
import java.util.List;

public class ConfigManager {

    public void reload() {
        LifeSteal.getInstance().reloadConfig();
    }

    public String getHeartItemName() {
        return ChatColor.translateAlternateColorCodes('&',
                LifeSteal.getInstance().getConfig().getString("heart-item.name", "&c&lHeart"));
    }

    public Material getHeartItemMaterial() {
        String material = LifeSteal.getInstance().getConfig().getString("heart-item.material", "NETHER_STAR");
        Material result = Material.matchMaterial(material.toUpperCase());
        if (result == null) {
            result = Material.NETHER_STAR;
        }
        return result;
    }

    public List<String> getHeartItemLore() {
        List<String> rawLore = LifeSteal.getInstance().getConfig().getStringList("heart-item.lore");
        List<String> translatedLore = new ArrayList<>();
        for (String line : rawLore) {
            translatedLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        return translatedLore;
    }

    public int getMaxHealth() {
        return LifeSteal.getInstance().getConfig().getInt("max-health", 20);
    }

    public int getMinHealth() {
        return LifeSteal.getInstance().getConfig().getInt("min-health", 1);
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
        String message = LifeSteal.getInstance().getConfig().getString("messages." + path, fallback);
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
        List<String> enabledWorlds = LifeSteal.getInstance().getConfig().getStringList("enabled-worlds");
        if (enabledWorlds == null || enabledWorlds.isEmpty()) {
            return true;
        }
        return enabledWorlds.contains(world.getName());
    }

    public boolean isAntiAltEnabled() {
        return LifeSteal.getInstance().getConfig().getBoolean("anti-alt.enabled", true);
    }

    public int getSameVictimCooldownMinutes() {
        return LifeSteal.getInstance().getConfig().getInt("anti-alt.same-victim-cooldown-minutes", 720);
    }

    public int getMinimumPlaytimeMinutes() {
        return LifeSteal.getInstance().getConfig().getInt("anti-alt.minimum-playtime-minutes", 180);
    }

    public boolean isDebugEnabled() {
        return LifeSteal.getInstance().getConfig().getBoolean("debug.enabled", false);
    }

    public int getWithdrawMinimumPlaytimeMinutes() {
        return LifeSteal.getInstance().getConfig().getInt("withdraw.minimum-playtime-minutes", 1440);
    }

    public int getWithdrawMinimumMaxHealth() {
        return LifeSteal.getInstance().getConfig().getInt("withdraw.minimum-max-health", 22);
    }

    public boolean isMinHealthActionEnabled() {
        return LifeSteal.getInstance().getConfig().getBoolean("minimum-health-action.enabled", true);
    }

    public String getMinHealthAction() {
        return LifeSteal.getInstance().getConfig().getString("minimum-health-action.action", "BAN");
    }

    public org.bukkit.configuration.file.FileConfiguration getConfig() {
        return LifeSteal.getInstance().getConfig();
    }
}
