package org.fliff.lifeSteal.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fliff.lifeSteal.utils.ConfigManager;
import org.fliff.lifeSteal.utils.NBTUtils;

public class RightClickListener implements Listener {

    private final ConfigManager configManager = new ConfigManager();

    @EventHandler
    public void onPlayerRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!configManager.isEnabledWorld(player.getWorld())) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !NBTUtils.hasNBTTag(meta, "HeartItem")) {
            return;
        }

        double maxHealth = player.getMaxHealth();
        double maxAllowedHealth = configManager.getMaxHealth() * 2;

        if (maxHealth >= maxAllowedHealth) {
            player.sendMessage(configManager.formatMessage("&cYou can't redeem more hearts!"));
            return;
        }

        int amount = player.isSneaking() ? item.getAmount() : 1;
        int redeemableAmount = Math.min(amount, (int) ((maxAllowedHealth - maxHealth) / 2));

        if (redeemableAmount <= 0) {
            player.sendMessage(configManager.formatMessage("&cYou can't redeem any hearts from this item!"));
            return;
        }

        int finalAmount = redeemableAmount;
        item.setAmount(item.getAmount() - finalAmount);
        double newMaxHealth = Math.min(maxAllowedHealth, maxHealth + finalAmount * 2);
        player.setMaxHealth(newMaxHealth);
        if (player.getHealth() > newMaxHealth) {
            player.setHealth(newMaxHealth);
        }

        player.sendMessage(configManager.formatMessage(
                "&aYou successfully redeemed " + finalAmount + " heart(s)!"));
    }
}
