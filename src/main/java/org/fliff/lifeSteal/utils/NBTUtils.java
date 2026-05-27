package org.fliff.lifeSteal.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.fliff.lifeSteal.LifeSteal;

import java.util.HashMap;
import java.util.Map;

public class NBTUtils {

    private static final String KEY_HEART_ITEM = "HeartItem";
    private static final String KEY_HEART_UUID = "HeartUUID";

    // Simulates adding an NBT tag
    public static void addNBTTag(ItemMeta meta, String key, String value) {
        meta.getPersistentDataContainer().set(new NamespacedKey(LifeSteal.getInstance(), key),
                PersistentDataType.STRING, value);
    }

    // Simulates checking an NBT tag
    public static boolean hasNBTTag(ItemMeta meta, String key) {
        return meta.getPersistentDataContainer().has(new NamespacedKey(LifeSteal.getInstance(), key),
                PersistentDataType.STRING);
    }

    // Get the HeartItem tag value
    public static String getHeartItemTag(ItemMeta meta) {
        if (meta == null)
            return null;
        NamespacedKey key = new NamespacedKey(LifeSteal.getInstance(), KEY_HEART_ITEM);
        if (meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
            return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        }
        return null;
    }

    // Set the HeartUUID tag
    public static void setHeartUUID(ItemMeta meta, String heartUUID) {
        NamespacedKey key = new NamespacedKey(LifeSteal.getInstance(), KEY_HEART_UUID);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, heartUUID);
    }

    // Get the HeartUUID tag
    public static String getHeartUUID(ItemMeta meta) {
        if (meta == null)
            return null;
        NamespacedKey key = new NamespacedKey(LifeSteal.getInstance(), KEY_HEART_UUID);
        if (meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
            return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        }
        return null;
    }

    // Check if item has valid HeartItem tag with HeartUUID
    public static boolean isValidHeartItem(ItemMeta meta) {
        if (meta == null)
            return false;
        String heartTag = getHeartItemTag(meta);
        String heartUUID = getHeartUUID(meta);
        // heartTag is the VALUE stored ("true"), not the key name ("HeartItem")
        return "true".equals(heartTag) && heartUUID != null && !heartUUID.isEmpty();
    }
}
