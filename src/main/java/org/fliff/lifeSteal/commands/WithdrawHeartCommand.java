package org.fliff.lifeSteal.commands;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fliff.lifeSteal.utils.ConfigManager;
import org.fliff.lifeSteal.utils.NBTUtils;

import java.util.ArrayList;
import java.util.List;

public class WithdrawHeartCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager configManager = new ConfigManager();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(configManager.formatMessage("&cOnly players can use this command!"));
            return true;
        }

        Player player = (Player) sender;

        // Block command usage in CREATIVE mode
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            player.sendMessage(configManager.getMessage("creative-disabled"));
            return true;
        }

        if (!configManager.isEnabledWorld(player.getWorld())) {
            player.sendMessage(configManager.getMessage("disabled-world"));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(configManager.formatMessage("&cUsage: /withdrawheart <amount>"));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(configManager.formatMessage("&cInvalid number!"));
            return true;
        }

        double playerMaxHealth = player.getMaxHealth();
        double minHealth = configManager.getMinHealth() * 2;

        if (amount <= 0 || playerMaxHealth - (amount * 2) < minHealth) {
            player.sendMessage(configManager.formatMessage("&cYou don't have enough hearts to withdraw!"));
            return true;
        }

        ItemStack heartItem = createHeartItem(amount);
        if (!player.getInventory().addItem(heartItem).isEmpty()) {
            player.sendMessage(configManager.formatMessage("&cYou don't have enough space in your inventory!"));
            return true;
        }

        double newMaxHealth = Math.max(minHealth, playerMaxHealth - (amount * 2));
        player.setMaxHealth(newMaxHealth);
        if (player.getHealth() > newMaxHealth) {
            player.setHealth(newMaxHealth);
        }
        player.sendMessage(configManager.formatMessage("&aSuccessfully withdrew " + amount + " heart(s)."));
        return true;
    }

    private ItemStack createHeartItem(int amount) {
        ItemStack heartItem = new ItemStack(Material.NETHER_STAR, amount);
        ItemMeta meta = heartItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(configManager.getHeartItemName());
            NBTUtils.addNBTTag(meta, "HeartItem", "1");
            heartItem.setItemMeta(meta);
        }
        return heartItem;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return null;
        }

        Player player = (Player) sender;

        // Only show tab completion for the first argument
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            double playerMaxHealth = player.getMaxHealth();
            double minHealth = configManager.getMinHealth() * 2;

            int maxWithdrawableHearts = (int) ((playerMaxHealth - minHealth) / 2);
            for (int i = 1; i <= maxWithdrawableHearts; i++) {
                suggestions.add(String.valueOf(i));
            }

            return suggestions;
        }

        return null;
    }
}
