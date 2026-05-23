package org.gw.nearmanager.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.gw.nearmanager.managers.BossBarManager;
import org.gw.nearmanager.managers.NearPlayerManager;
import org.gw.nearmanager.managers.RadiusManager;

public final class PlayerQuitListener implements Listener {

    private final NearPlayerManager nearPlayerManager;
    private final BossBarManager bossBarManager;
    private final RadiusManager radiusManager;

    public PlayerQuitListener(NearPlayerManager nearPlayerManager,
                              BossBarManager bossBarManager,
                              RadiusManager radiusManager) {
        this.nearPlayerManager = nearPlayerManager;
        this.bossBarManager = bossBarManager;
        this.radiusManager = radiusManager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();

        nearPlayerManager.clearCooldown(uuid);
        nearPlayerManager.invalidateCache(uuid);
        radiusManager.invalidate(uuid);

        if (bossBarManager.hasActiveBossBar(event.getPlayer())) {
            bossBarManager.removeBossBar(event.getPlayer());
        }
    }
}