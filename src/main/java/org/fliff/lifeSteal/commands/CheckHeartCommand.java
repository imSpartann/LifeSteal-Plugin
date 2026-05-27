package org.fliff.lifeSteal.commands;

import org.bukkit.Statistic;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.fliff.lifeSteal.LifeSteal;
import org.fliff.lifeSteal.utils.ConfigManager;
import org.fliff.lifeSteal.utils.PlayerDataManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class CheckHeartCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager configManager = new ConfigManager();
    private final PlayerDataManager playerDataManager = PlayerDataManager.getInstance();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lifesteal.check")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(configManager.formatMessage("&cUsage: /lifesteal check <player>"));
            return true;
        }

        String targetName = args[0];
        Player target = org.bukkit.Bukkit.getPlayer(targetName);
        boolean isOnline = (target != null && target.isOnline());

        if (isOnline) {
            sendOnlineCheck(sender, target);
        } else {
            sendOfflineCheck(sender, targetName);
        }

        return true;
    }

    private void sendOnlineCheck(CommandSender sender, Player target) {
        UUID targetUUID = target.getUniqueId();

        // Get max health
        AttributeInstance targetAttribute = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double currentMaxHealth = targetAttribute != null ? targetAttribute.getBaseValue() : 20.0;
        int visibleHearts = (int) (currentMaxHealth / 2);

        // Get current health
        double currentHealth = target.getHealth();

        // Get playtime
        long playtimeTicks = target.getStatistic(Statistic.PLAY_ONE_MINUTE);
        long playtimeMinutes = playtimeTicks / 20L / 60L;
        long playtimeHours = playtimeMinutes / 60L;
        long playtimeRemainingMinutes = playtimeMinutes % 60L;

        // Check withdraw eligibility
        int requiredPlaytimeMinutes = configManager.getWithdrawMinimumPlaytimeMinutes();
        int requiredMinHealth = configManager.getWithdrawMinimumMaxHealth();
        boolean withdrawEligible = playtimeMinutes >= requiredPlaytimeMinutes
                && currentMaxHealth >= requiredMinHealth;

        // Get cooldown count
        int cooldownCount = 0;
        if (LifeSteal.getInstance().getAntiAltCooldowns() != null) {
            String targetUUIDStr = targetUUID.toString();
            for (String key : LifeSteal.getInstance().getAntiAltCooldowns().keySet()) {
                if (key.startsWith(targetUUIDStr + "_")) {
                    cooldownCount++;
                }
            }
        }

        // Send output
        sender.sendMessage(configManager.formatMessage("&b=== LifeSteal Check: " + target.getName() + " ==="));
        sender.sendMessage(configManager.formatMessage("&7Status: &aONLINE"));
        sender.sendMessage(configManager.formatMessage("&7Max Health: &f" + (int) currentMaxHealth));
        sender.sendMessage(configManager.formatMessage("&7Visible Hearts: &f" + visibleHearts));
        sender.sendMessage(configManager.formatMessage("&7Current Health: &f" + (int) currentHealth));
        sender.sendMessage(
                configManager.formatMessage("&7Playtime: &f" + playtimeHours + "h " + playtimeRemainingMinutes + "m"));
        sender.sendMessage(
                configManager.formatMessage("&7Withdraw Eligible: &f" + (withdrawEligible ? "&aYES" : "&cNO")));
        sender.sendMessage(configManager.formatMessage("&7Cooldown Entries: &f" + cooldownCount));

        // Update cached data
        playerDataManager.updateLastSeen(targetUUID, target.getName());
    }

    private void sendOfflineCheck(CommandSender sender, String targetName) {
        UUID targetUUID = null;

        // Try to resolve UUID
        if (org.bukkit.Bukkit.getOfflinePlayer(targetName) != null) {
            targetUUID = org.bukkit.Bukkit.getOfflinePlayer(targetName).getUniqueId();
        }

        if (targetUUID == null) {
            sender.sendMessage(configManager.formatMessage("&cPlayer not found!"));
            return;
        }

        // Check cached data
        org.bukkit.configuration.file.FileConfiguration pData = playerDataManager.getPlayerDataConfig();
        if (pData == null) {
            sender.sendMessage(configManager.formatMessage("&cPlayer data not initialized!"));
            return;
        }
        String path = "players." + targetUUID;

        if (!pData.contains(path)) {
            sender.sendMessage(configManager.formatMessage("&cNo data found for '" + targetName + "'."));
            return;
        }

        String cachedName = pData.getString(path + ".name", targetName);
        double cachedMaxHealth = pData.getDouble(path + ".max-health", 20.0);
        int cachedVisibleHearts = pData.getInt(path + ".visible-hearts", (int) (cachedMaxHealth / 2));
        double cachedHealth = pData.getDouble(path + ".last-health", cachedMaxHealth);
        long cachedPlaytimeMinutes = pData.getLong(path + ".playtime-minutes", 0);
        long lastSeenTimestamp = pData.getLong(path + ".last-seen", 0);

        long cachedPlaytimeHours = cachedPlaytimeMinutes / 60L;
        long cachedPlaytimeRemainingMinutes = cachedPlaytimeMinutes % 60L;

        int requiredPlaytimeMinutes = configManager.getWithdrawMinimumPlaytimeMinutes();
        int requiredMinHealth = configManager.getWithdrawMinimumMaxHealth();
        boolean withdrawEligible = cachedPlaytimeMinutes >= requiredPlaytimeMinutes
                && cachedMaxHealth >= requiredMinHealth;

        int cooldownCount = 0;
        if (LifeSteal.getInstance().getAntiAltCooldowns() != null) {
            String targetUUIDStr = targetUUID.toString();
            for (String key : LifeSteal.getInstance().getAntiAltCooldowns().keySet()) {
                if (key.startsWith(targetUUIDStr + "_")) {
                    cooldownCount++;
                }
            }
        }

        String timeAgo = playerDataManager.formatTimeAgo(lastSeenTimestamp);

        // Send output
        sender.sendMessage(configManager.formatMessage("&b=== LifeSteal Check: " + cachedName + " ==="));
        sender.sendMessage(configManager.formatMessage("&7Status: &cOFFLINE"));
        sender.sendMessage(configManager.formatMessage("&7Last Seen: &f" + timeAgo));
        sender.sendMessage(configManager.formatMessage("&7Max Health: &f" + (int) cachedMaxHealth));
        sender.sendMessage(configManager.formatMessage("&7Visible Hearts: &f" + cachedVisibleHearts));
        sender.sendMessage(configManager.formatMessage("&7Cached Health: &f" + (int) cachedHealth));
        sender.sendMessage(
                configManager.formatMessage(
                        "&7Playtime: &f" + cachedPlaytimeHours + "h " + cachedPlaytimeRemainingMinutes + "m"));
        sender.sendMessage(
                configManager.formatMessage("&7Withdraw Eligible: &f" + (withdrawEligible ? "&aYES" : "&cNO")));
        sender.sendMessage(configManager.formatMessage("&7Cooldown Entries: &f" + cooldownCount));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return null;
        }

        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    suggestions.add(player.getName());
                }
            }
            return suggestions;
        }

        return null;
    }
}
