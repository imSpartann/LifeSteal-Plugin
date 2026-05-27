package org.fliff.lifeSteal.commands;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

        // Validate playtime requirement
        long playtimeMinutes = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L / 60L;
        int requiredPlaytimeMinutes = configManager.getWithdrawMinimumPlaytimeMinutes();
        if (playtimeMinutes < requiredPlaytimeMinutes) {
            player.sendMessage(configManager.getMessage("withdraw-playtime-required"));
            return true;
        }

        // Validate minimum max health requirement
        AttributeInstance playerAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (playerAttribute == null) {
            player.sendMessage(configManager.formatMessage("&cYou don't have enough hearts to withdraw!"));
            return true;
        }

        double currentMaxHealth = playerAttribute.getBaseValue();
        int requiredMinHealth = configManager.getWithdrawMinimumMaxHealth();
        if (currentMaxHealth < requiredMinHealth) {
            player.sendMessage(configManager.getMessage("withdraw-min-health-required").replace("{health}",
                    String.valueOf(requiredMinHealth)));
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

        // Validate amount is positive
        if (amount <= 0) {
            player.sendMessage(configManager.formatMessage("&cYou don't have enough hearts to withdraw!"));
            return true;
        }

        double playerMaxHealth = playerAttribute.getBaseValue();
        double minHealth = configManager.getMinHealth() * 2;

        // Validate player has enough hearts and can go down to minHealth
        if (playerMaxHealth - (amount * 2) < minHealth) {
            player.sendMessage(configManager.formatMessage("&cYou don't have enough hearts to withdraw!"));
            return true;
        }

        // Check inventory space BEFORE any state changes
        // Each heart is a separate item with max stack size 1
        if (!hasInventorySpace(player, amount)) {
            player.sendMessage(configManager.formatMessage("&cYou don't have enough space in your inventory!"));
            return true;
        }

        // ALL VALIDATIONS PASSED - now apply state changes atomically
        // 1. Reduce health first
        double newMaxHealth = Math.max(minHealth, playerMaxHealth - (amount * 2));
        playerAttribute.setBaseValue(newMaxHealth);
        if (player.getHealth() > newMaxHealth) {
            player.setHealth(newMaxHealth);
        }

        // 2. Create individual heart items (1 per heart, each with unique UUID)
        // Collect heart UUIDs for audit logging
        int givenCount = 0;
        String[] heartUUIDs = new String[amount];
        int heartUUIDIndex = 0;
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

            Map<Integer, ItemStack> overflow = player.getInventory().addItem(heartItem);
            if (!overflow.isEmpty()) {
                // Item could not be added - revert health for all given items
                for (ItemStack item : overflow.values()) {
                    player.getInventory().removeItem(item);
                }
                playerAttribute.setBaseValue(playerMaxHealth);
                if (player.getHealth() > playerMaxHealth) {
                    player.setHealth(playerMaxHealth);
                }
                player.sendMessage(configManager.formatMessage("&cFailed to add heart item to inventory!"));
                return true;
            }
            givenCount++;
        }

        // 3. Log the withdrawal
        LifeSteal.getInstance().getLogger().info(player.getName() + " withdrew " + givenCount + " heart(s).");
        player.sendMessage(configManager.formatMessage("&aSuccessfully withdrew " + givenCount + " heart(s)."));

        // 4. Audit log the withdrawal with heart UUIDs (NOT player UUID)
        if (LifeSteal.getInstance().getAuditLogger() != null) {
            // Log each heart UUID separately for traceability
            for (int i = 0; i < givenCount && i < heartUUIDs.length; i++) {
                String heartUUID = heartUUIDs[i];
                if (heartUUID != null) {
                    LifeSteal.getInstance().getAuditLogger().log(
                            "WITHDRAW",
                            player.getName(),
                            player.getWorld().getName(),
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
            // Save UUID to server-side tracking
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

        // Only show tab completion for the first argument
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            AttributeInstance playerAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (playerAttribute == null) {
                return suggestions;
            }

            double playerMaxHealth = playerAttribute.getBaseValue();
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
