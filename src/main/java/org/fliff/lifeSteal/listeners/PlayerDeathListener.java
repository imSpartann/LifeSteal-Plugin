package org.fliff.lifeSteal.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.fliff.lifeSteal.LifeSteal;
import org.fliff.lifeSteal.utils.ConfigManager;

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

        double victimMaxHealth = victim.getMaxHealth();
        if (victimMaxHealth > minHealth) {
            double newVictimMax = Math.max(minHealth, Math.min(victimMaxHealth - 2, victimMaxHealth));
            victim.setMaxHealth(newVictimMax);
            if (victim.getHealth() > newVictimMax) {
                victim.setHealth(newVictimMax);
            }
        }

        double killerMaxHealth = killer.getMaxHealth();
        if (killerMaxHealth < maxHearts) {
            double newKillerMax = Math.min(maxHearts, Math.max(killerMaxHealth + 2, killerMaxHealth));
            killer.setMaxHealth(newKillerMax);
            if (killer.getHealth() > newKillerMax) {
                killer.setHealth(newKillerMax);
            }
        }

        if (configManager.isAntiAltEnabled()) {
            recordSteal(killer, victim);
        }
    }

    private boolean checkSameVictimCooldown(Player killer, Player victim) {
        return !LifeSteal.getInstance().isCooldownActive(
                killer.getUniqueId().toString(),
                victim.getUniqueId().toString(),
                configManager.getSameVictimCooldownHours());
    }

    private boolean checkMinimumPlaytime(Player victim) {
        int minimumPlaytimeHours = configManager.getMinimumPlaytimeHours();
        if (minimumPlaytimeHours <= 0) {
            return true;
        }

        long playtimeSeconds = victim.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20L;
        long minimumPlaytimeSeconds = (long) minimumPlaytimeHours * 60L * 60L;

        return playtimeSeconds >= minimumPlaytimeSeconds;
    }

    private void recordSteal(Player killer, Player victim) {
        LifeSteal.getInstance().saveCooldownToConfig(
                killer.getUniqueId().toString(),
                victim.getUniqueId().toString());
    }
}
