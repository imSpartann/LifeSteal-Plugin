package org.fliff.lifeSteal.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fliff.lifeSteal.LifeSteal;
import org.fliff.lifeSteal.utils.ConfigManager;
import org.fliff.lifeSteal.utils.NBTUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GiveHeartCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager configManager = new ConfigManager();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(configManager.formatMessage("&cOnly players can use this command!"));
            return true;
        }

        Player admin = (Player) sender;

        if (args.length < 2) {
            admin.sendMessage(configManager.formatMessage("&cUsage: /giveheart <player> <amount>"));
            return true;
        }

        String targetName = args[0];
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            admin.sendMessage(configManager.formatMessage("&cInvalid number!"));
            return true;
        }

        // Validate amount is positive
        if (amount <= 0) {
            admin.sendMessage(configManager.formatMessage("&cAmount must be positive!"));
            return true;
        }

        // Get target player
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
        if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
            admin.sendMessage(configManager.formatMessage("&cPlayer not found!"));
            return true;
        }

        Player target = targetPlayer.getPlayer();
        if (target == null || !target.isOnline()) {
            admin.sendMessage(configManager.formatMessage("&cPlayer is not online!"));
            return true;
        }

        // Check if target is in an enabled world
        if (!configManager.isEnabledWorld(target.getWorld())) {
            admin.sendMessage(configManager.formatMessage("&cTarget player is not in an enabled world!"));
            return true;
        }

        // Check inventory space BEFORE any state changes
        // Each heart is a separate item with max stack size 1
        if (!hasInventorySpace(target, amount)) {
            admin.sendMessage(configManager.formatMessage("&cTarget doesn't have enough space in their inventory!"));
            return true;
        }

        // ALL VALIDATIONS PASSED - now apply state changes atomically
        // Create individual heart items (1 per heart, each with unique UUID)
        // Collect heart UUIDs for audit logging
        int givenCount = 0;
        String[] heartUUIDs = new String[amount];
        int heartUUIDIndex = 0;
        List<ItemStack> createdHearts = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            ItemStack heartItem = createHeartItem();
            heartItem.setAmount(1);

            // Extract the heart UUID from the item for audit logging
            ItemMeta meta = heartItem.getItemMeta();
            if (meta != null) {
                String heartUUID = NBTUtils.getHeartUUID(meta);
                if (heartUUID != null && !heartUUID.isEmpty()) {
                    heartUUIDs[heartUUIDIndex++] = heartUUID;
                }
            }

            createdHearts.add(heartItem);
        }

        // Try to add all hearts at once
        Inventory inv = target.getInventory();
        Map<Integer, ItemStack> overflow = inv.addItem(createdHearts.toArray(new ItemStack[0]));
        if (!overflow.isEmpty()) {
            // Item could not be added - remove any items already added
            for (ItemStack item : createdHearts) {
                inv.removeItem(item);
            }
            admin.sendMessage(configManager.formatMessage("&cFailed to add heart item to inventory!"));
            return true;
        }
        givenCount = createdHearts.size();

        // Log the give action
        LifeSteal.getInstance().getLogger()
                .info(admin.getName() + " gave " + givenCount + " heart(s) to " + target.getName());
        admin.sendMessage(
                configManager.formatMessage("&aSuccessfully gave " + givenCount + " heart(s) to " + target.getName()));
        target.sendMessage(
                configManager.formatMessage("&aYou received " + givenCount + " heart(s) from " + admin.getName()));

        // Audit log the give with heart UUIDs
        if (LifeSteal.getInstance().getAuditLogger() != null) {
            for (int i = 0; i < givenCount && i < heartUUIDs.length; i++) {
                String heartUUID = heartUUIDs[i];
                if (heartUUID != null) {
                    LifeSteal.getInstance().getAuditLogger().log(
                            "ADMIN_GIVE",
                            admin.getName(),
                            target.getWorld().getName(),
                            heartUUID);
                }
            }
        }
        return true;
    }

    private boolean hasInventorySpace(Player player, int amount) {
        // Each heart item has max stack size 1, so we need 'amount' empty slots
        Inventory inv = player.getInventory();
        int emptySlots = 0;
        for (ItemStack item : inv.getStorageContents()) {
            if (item == null || item.getType() == Material.AIR || item.getAmount() == 0) {
                emptySlots++;
            }
        }
        return emptySlots >= amount;
    }

    private ItemStack createHeartItem() {
        Material material = configManager.getHeartItemMaterial();
        ItemStack heartItem = new ItemStack(material, 1);
        heartItem.setAmount(1); // Ensure max stack size 1
        ItemMeta meta = heartItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(configManager.getHeartItemName());
            List<String> lore = configManager.getHeartItemLore();
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            NBTUtils.addNBTTag(meta, "HeartItem", "true");
            // Generate unique UUID for this heart item instance
            String heartUUID = UUID.randomUUID().toString();
            NBTUtils.setHeartUUID(meta, heartUUID);
            // Save UUID to server-side tracking (SAME as withdrawheart)
            LifeSteal.getInstance().addIssuedHeartUUID(heartUUID);
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

        // Only show tab completion for the first argument (player names)
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            String partialName = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().contains(partialName)) {
                    suggestions.add(p.getName());
                }
            }
            return suggestions;
        }

        // Second argument: suggest numbers 1-10
        if (args.length == 2) {
            List<String> suggestions = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                suggestions.add(String.valueOf(i));
            }
            return suggestions;
        }

        return null;
    }
}
