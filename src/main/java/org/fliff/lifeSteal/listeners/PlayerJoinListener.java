package org.fliff.lifeSteal.listeners;

import org.bukkit.Statistic;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.fliff.lifeSteal.utils.PlayerDataManager;

public class PlayerJoinListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        cachePlayerData(player, true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cachePlayerData(player, false);
    }

    private void cachePlayerData(Player player, boolean isJoin) {
        PlayerDataManager pDataMgr = PlayerDataManager.getInstance();
        AttributeInstance playerAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (playerAttr != null) {
            long playtime = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L / 60L;
            pDataMgr.cachePlayerData(
                    player.getUniqueId(),
                    player.getName(),
                    playerAttr.getBaseValue(),
                    player.getHealth(),
                    playtime);
        }
    }
}
