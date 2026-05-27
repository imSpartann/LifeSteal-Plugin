package org.fliff.lifeSteal.commands;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fliff.lifeSteal.utils.ConfigManager;

public class ResetHeartsCommand implements CommandExecutor {

    private final ConfigManager configManager = new ConfigManager();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lifesteal.reset")) {
            sender.sendMessage(configManager.formatMessage("&cYou don't have permission to use this command!"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(configManager.formatMessage("&cUsage: /resethearts <player>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(configManager.formatMessage("&cPlayer not found!"));
            return true;
        }

        AttributeInstance attribute = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(20);
            if (target.getHealth() > 20) {
                target.setHealth(20);
            }
        }
        sender.sendMessage(configManager.formatMessage("&aSuccessfully reset " + target.getName() + "'s hearts."));
        return true;
    }
}
