package org.fliff.lifeSteal.listeners;

import org.bukkit.BanList.Type;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fliff.lifeSteal.LifeSteal;
import org.fliff.lifeSteal.utils.AuditLogger;
import org.fliff.lifeSteal.utils.ConfigManager;
import org.fliff.lifeSteal.utils.NBTUtils;

public class PlayerDeathListener implements Listener {

    private final ConfigManager configManager = new ConfigManager();

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = event.getEntity().getKiller();

        if (!(killer instanceof Player)) {
            return;
        }

        if (!configManager.isEnabledWorld(victim.getWorld())) {
            return;
        }

        if (!configManager.isEnabledWorld(killer.getWorld())) {
            return;
        }

        if (configManager.isAntiAltEnabled()) {
            if (!checkSameVictimCooldown(killer, victim)) {
                killer.sendMessage(configManager.getMessage("cooldown-active"));
                return;
            }
            if (!checkMinimumPlaytime(victim)) {
                killer.sendMessage(configManager.getMessage("minimum-playtime"));
                return;
            }
        }

        double minHealth = configManager.getMinHealth() * 2;
        double maxHearts = configManager.getMaxHealth() * 2;

        AttributeInstance victimAttribute = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (victimAttribute != null) {
            double victimMaxHealth = victimAttribute.getBaseValue();
            if (victimMaxHealth > minHealth) {
                double newVictimMax = Math.max(minHealth, Math.min(victimMaxHealth - 2, victimMaxHealth));
                victimAttribute.setBaseValue(newVictimMax);
                if (victim.getHealth() > newVictimMax) {
                    victim.setHealth(newVictimMax);
                }
            } else if (victimMaxHealth <= minHealth && configManager.isMinHealthActionEnabled()) {
                // Player is at or below minimum health - trigger configured action
                executeMinHealthAction(victim, configManager.getMinHealthAction());
            }
        }

        AttributeInstance killerAttribute = killer.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (killerAttribute != null) {
            double killerMaxHealth = killerAttribute.getBaseValue();
            if (killerMaxHealth < maxHearts) {
                double newKillerMax = Math.min(maxHearts, Math.max(killerMaxHealth + 2, killerMaxHealth));
                killerAttribute.setBaseValue(newKillerMax);
                if (killer.getHealth() > newKillerMax) {
                    killer.setHealth(newKillerMax);
                }
            }
        }

        if (configManager.isAntiAltEnabled()) {
            recordSteal(killer, victim);
        }

        // Cache victim and killer data after heart steal
        cachePlayerDataAfterDeath(victim, killer);

        // Prevent heart item drops on death
        if (configManager.getConfig().getBoolean("prevent-heart-drops", true)) {
            preventHeartDrops(event, victim);
        }
    }

    private boolean checkSameVictimCooldown(Player killer, Player victim) {
        return !LifeSteal.getInstance().isCooldownActive(
                killer.getUniqueId().toString(),
                victim.getUniqueId().toString(),
                configManager.getSameVictimCooldownMinutes());
    }

    private boolean checkMinimumPlaytime(Player victim) {
        int minimumPlaytimeMinutes = configManager.getMinimumPlaytimeMinutes();
        if (minimumPlaytimeMinutes <= 0) {
            return true;
        }

        long playtimeSeconds = victim.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20L;
        long minimumPlaytimeSeconds = (long) minimumPlaytimeMinutes * 60L;

        return playtimeSeconds >= minimumPlaytimeSeconds;
    }

    private void recordSteal(Player killer, Player victim) {
        LifeSteal.getInstance().saveCooldownToConfig(
                killer.getUniqueId().toString(),
                victim.getUniqueId().toString());
    }

    /**
     * Execute the configured minimum health action.
     */
    private void executeMinHealthAction(Player player, String action) {
        String actionUpper = action.toUpperCase();
        String playerName = player.getName();

        // Audit log the action
        if (LifeSteal.getInstance().getAuditLogger() != null) {
            LifeSteal.getInstance().getAuditLogger().log(
                    "MIN_HEALTH_ACTION",
                    playerName,
                    player.getWorld().getName(),
                    actionUpper);
        }

        switch (actionUpper) {
            case "BAN":
                org.bukkit.Bukkit.getBanList(Type.NAME).addBan(playerName, "You ran out of hearts!", null, null);
                break;
            case "SPECTATOR":
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage(configManager.getMessage("min-health-action-spectator"));
                break;
            case "RESET":
                AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (attr != null) {
                    attr.setBaseValue(20.0);
                    player.setHealth(20.0);
                }
                player.sendMessage(configManager.getMessage("min-health-action-reset"));
                break;
            case "NONE":
            default:
                break;
        }
    }

    /**
     * Remove heart items from death drops to prevent duplication exploits.
     */
    private void preventHeartDrops(PlayerDeathEvent event, Player victim) {
        java.util.List<ItemStack> drops = event.getDrops();
        java.util.Iterator<ItemStack> iterator = drops.iterator();
        int removedCount = 0;
        while (iterator.hasNext()) {
            ItemStack drop = iterator.next();
            if (drop != null && drop.getType() == Material.NETHER_STAR && drop.hasItemMeta()) {
                ItemMeta meta = drop.getItemMeta();
                if (meta != null && NBTUtils.isValidHeartItem(meta)) {
                    iterator.remove();
                    removedCount++;
                }
            }
        }
        if (removedCount > 0) {
            LifeSteal.getInstance().getLogger()
                    .info(victim.getName() + "'s heart items were removed from death drops.");
        }
    }

    private void cachePlayerDataAfterDeath(Player victim, Player killer) {
        org.fliff.lifeSteal.utils.PlayerDataManager pDataMgr = org.fliff.lifeSteal.utils.PlayerDataManager
                .getInstance();

        // Cache victim data (they lost health)
        AttributeInstance victimAttr = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (victimAttr != null) {
            long victimPlaytime = victim.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20L / 60L;
            pDataMgr.cachePlayerData(
                    victim.getUniqueId(),
                    victim.getName(),
                    victimAttr.getBaseValue(),
                    victim.getHealth(),
                    victimPlaytime);
        }

        // Cache killer data (they gained health)
        AttributeInstance killerAttr = killer.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (killerAttr != null) {
            long killerPlaytime = killer.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20L / 60L;
            pDataMgr.cachePlayerData(
                    killer.getUniqueId(),
                    killer.getName(),
                    killerAttr.getBaseValue(),
                    killer.getHealth(),
                    killerPlaytime);
        }
    }
}
