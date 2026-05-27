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

import java.util.ArrayList;
import java.util.List;

public class CheckHeartCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager configManager = new ConfigManager();

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

        if (target == null || !target.isOnline()) {
            sender.sendMessage(configManager.formatMessage("&cPlayer not found!"));
            return true;
        }

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
        long playerPlaytimeMinutes = target.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L / 60L;
        int requiredPlaytimeMinutes = configManager.getWithdrawMinimumPlaytimeMinutes();
        int requiredMinHealth = configManager.getWithdrawMinimumMaxHealth();
        boolean withdrawEligible = playerPlaytimeMinutes >= requiredPlaytimeMinutes
                && currentMaxHealth >= requiredMinHealth;

        // Get cooldown count
        int cooldownCount = 0;
        if (LifeSteal.getInstance().getAntiAltCooldowns() != null) {
            String targetUUID = target.getUniqueId().toString();
            for (String key : LifeSteal.getInstance().getAntiAltCooldowns().keySet()) {
                if (key.startsWith(targetUUID + "_")) {
                    cooldownCount++;
                }
            }
        }

        // Send output
        sender.sendMessage("&b=== LifeSteal Check: " + target.getName() + " ===");
        sender.sendMessage("&7Max Health: &f" + (int) currentMaxHealth);
        sender.sendMessage("&7Visible Hearts: &f" + visibleHearts);
        sender.sendMessage("&7Current Health: &f" + (int) currentHealth);
        sender.sendMessage("&7Playtime: &f" + playtimeHours + "h " + playtimeRemainingMinutes + "m");
        sender.sendMessage("&7Withdraw Eligible: &f" + (withdrawEligible ? "&aYES" : "&cNO"));
        sender.sendMessage("&7Cooldown Entries: &f" + cooldownCount);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return null;
        }

        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    suggestions.add(player.getName());
                }
            }
            return suggestions;
        }

        return null;
    }
}
