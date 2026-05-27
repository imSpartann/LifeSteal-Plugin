package org.fliff.lifeSteal.listeners;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fliff.lifeSteal.LifeSteal;
import org.fliff.lifeSteal.utils.ConfigManager;
import org.fliff.lifeSteal.utils.NBTUtils;

public class RightClickListener implements Listener {

    private final ConfigManager configManager = new ConfigManager();
    private final LifeSteal plugin = LifeSteal.getInstance();

    @EventHandler
    public void onPlayerRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!configManager.isEnabledWorld(player.getWorld())) {
            return;
        }

        // Only process main-hand interactions to prevent offhand redemption
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        // Only process right-click actions
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
                event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // DEBUG: Checkpoint 1 - event triggered
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("DEBUG: interact event triggered");
        }

        // Use event.getItem() instead of getItemInMainHand() for reliability
        // during rapid hotbar switching. event.getItem() returns the item
        // the event system determined was being interacted with, which is
        // more reliable than querying inventory state when slot changes
        // and right-clicks happen in rapid succession.
        ItemStack item = event.getItem();

        // DEBUG: Checkpoint 2 - item exists
        if (item == null || !item.hasItemMeta()) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("DEBUG: item is null or has no item meta");
            }
            return;
        }
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("DEBUG: item exists");
        }

        ItemMeta meta = item.getItemMeta();
        // DEBUG: Checkpoint 3 - item meta exists
        if (meta == null) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("DEBUG: item meta is null");
            }
            return;
        }
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("DEBUG: item meta exists");
        }

        // Validate heart item using secure NBT check
        if (!NBTUtils.isValidHeartItem(meta)) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("DEBUG: HeartItem tag invalid");
            }
            return;
        }
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("DEBUG: HeartItem tag valid");
        }

        // Reject stacked heart items - each must be amount 1
        if (item.getAmount() > 1) {
            player.sendMessage(configManager.formatMessage("&cThis heart item is invalid!"));
            if (LifeSteal.getInstance().getAuditLogger() != null) {
                LifeSteal.getInstance().getAuditLogger().log(
                        "INVALID_REDEEM_ATTEMPT",
                        player.getName(),
                        player.getWorld().getName(),
                        "");
            }
            return;
        }

        // Get the HeartUUID from the item
        String heartUUID = NBTUtils.getHeartUUID(meta);
        // DEBUG: Checkpoint 5 - HeartUUID value
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("DEBUG: HeartUUID=" + heartUUID);
        }
        if (heartUUID == null || heartUUID.isEmpty()) {
            player.sendMessage(configManager.formatMessage("&cThis heart item is invalid!"));
            return;
        }

        // Validate UUID exists in server-side tracking
        boolean uuidValid = LifeSteal.getInstance().isHeartUUIDValid(heartUUID);
        // DEBUG: Checkpoint 6 - UUID valid
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("DEBUG: UUID valid in hearts.yml=" + uuidValid);
        }
        if (!uuidValid) {
            player.sendMessage(configManager.formatMessage("&cThis heart item has already been used or is invalid!"));
            // Audit log invalid redeem attempt
            if (LifeSteal.getInstance().getAuditLogger() != null) {
                LifeSteal.getInstance().getAuditLogger().log(
                        "INVALID_REDEEM_ATTEMPT",
                        player.getName(),
                        player.getWorld().getName(),
                        heartUUID);
            }
            return;
        }

        AttributeInstance playerAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (playerAttribute == null) {
            player.sendMessage(configManager.formatMessage("&cThis heart item is invalid!"));
            return;
        }

        double maxHealth = playerAttribute.getBaseValue();
        double maxAllowedHealth = configManager.getMaxHealth();

        // DEBUG: Checkpoint 7 - redeem processing started
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("DEBUG: redeem processing started");
        }

        // DEBUG: Checkpoint 8 - max health before
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("DEBUG: max health before=" + maxHealth);
        }

        if (maxHealth >= maxAllowedHealth) {
            player.sendMessage(configManager.formatMessage("&cYou can't redeem more hearts!"));
            return;
        }

        // Each heart item redeems exactly 1 heart
        int finalAmount = 1;

        // ALL VALIDATIONS PASSED - apply state changes
        // 1. Remove the item from inventory
        item.setAmount(0);
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

        // 2. Remove the UUID from server-side tracking (prevents reuse/cloning)
        LifeSteal.getInstance().removeIssuedHeartUUID(heartUUID);

        // 3. Audit log redeem and invalidate
        if (LifeSteal.getInstance().getAuditLogger() != null) {
            LifeSteal.getInstance().getAuditLogger().log(
                    "REDEEM",
                    player.getName(),
                    player.getWorld().getName(),
                    heartUUID);
            LifeSteal.getInstance().getAuditLogger().log(
                    "INVALIDATE",
                    player.getName(),
                    player.getWorld().getName(),
                    heartUUID);
        }

        // 4. Apply health increase
        double newMaxHealth = Math.min(maxAllowedHealth, maxHealth + finalAmount * 2);
        playerAttribute.setBaseValue(newMaxHealth);
        if (player.getHealth() > newMaxHealth) {
            player.setHealth(newMaxHealth);
        }

        // DEBUG: Checkpoint 9 - max health after
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("DEBUG: max health after=" + playerAttribute.getBaseValue());
        }

        // DEBUG: Checkpoint 10 - redeem completed
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("DEBUG: redeem completed");
        }

        player.sendMessage(configManager.formatMessage(
                "&aYou successfully redeemed " + finalAmount + " heart(s)!"));
    }
}
