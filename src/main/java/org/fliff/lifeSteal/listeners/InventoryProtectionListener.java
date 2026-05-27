package org.fliff.lifeSteal.listeners;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fliff.lifeSteal.utils.ConfigManager;
import org.fliff.lifeSteal.utils.NBTUtils;

public class InventoryProtectionListener implements Listener {

    private final ConfigManager configManager = new ConfigManager();

    /**
     * Prevent clicking to merge heart items with other stacks.
     * If the clicked item is a heart item, cancel the click to prevent stacking.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        ItemStack hotbarCursor = event.getCursor();

        // Check if the clicked item is a heart item
        if (isHeartItem(clicked)) {
            // If cursor is also a heart item with same UUID, allow moving single item
            if (isHeartItem(hotbarCursor)) {
                // Allow right-click to move single item, but prevent stacking
                if (event.isRightClick() && hotbarCursor.getAmount() == 1) {
                    // Allow moving single heart item
                    return;
                }
                // Prevent left-click merge or stacking
                event.setCancelled(true);
                return;
            }
            // Prevent any click on heart item that could cause stacking
            // Allow normal single-item pickup
            if (event.isShiftClick() || event.isRightClick()) {
                // Check if this would cause a merge
                ItemStack result = event.getCurrentItem();
                if (result != null && isHeartItem(result) && result.getAmount() > 1) {
                    event.setCancelled(true);
                }
            }
            return;
        }

        // Check if cursor is a heart item being placed into a stack
        if (isHeartItem(hotbarCursor) && hotbarCursor.getAmount() > 1) {
            // Prevent placing stacked heart items
            event.setCancelled(true);
            return;
        }

        // Prevent shift-click merging of heart items
        if (event.isShiftClick() && isHeartItem(hotbarCursor)) {
            event.setCancelled(true);
            return;
        }
    }

    /**
     * Prevent inventory drag from creating stacked heart items.
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        // Check if any dragged items are heart items
        for (ItemStack item : event.getNewItems().values()) {
            if (item != null && isHeartItem(item) && item.getAmount() > 1) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Prevent hopper/container moving of heart items between inventories.
     */
    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        ItemStack item = event.getItem();
        if (isHeartItem(item)) {
            // Prevent automatic movement of heart items by hoppers/containers
            event.setCancelled(true);
        }
    }

    /**
     * Check if an item is a valid heart item.
     */
    private boolean isHeartItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        if (item.getAmount() <= 0) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return false;
        }
        return NBTUtils.isValidHeartItem(item.getItemMeta());
    }
}
