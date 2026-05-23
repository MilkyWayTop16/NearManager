package org.gw.nearmanager.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.gw.nearmanager.managers.BossBarManager;
import org.gw.nearmanager.managers.NearPlayerManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerCacheInvalidationListener implements Listener {

    private final NearPlayerManager nearPlayerManager;
    private final BossBarManager bossBarManager;
    private final Map<UUID, Location> lastInvalidateLocations = new HashMap<>();

    public PlayerCacheInvalidationListener(NearPlayerManager nearPlayerManager, BossBarManager bossBarManager) {
        this.nearPlayerManager = nearPlayerManager;
        this.bossBarManager = bossBarManager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            nearPlayerManager.invalidateCache(event.getPlayer().getUniqueId());
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Location lastLoc = lastInvalidateLocations.get(uuid);

        if (lastLoc == null || !lastLoc.getWorld().equals(player.getWorld()) || lastLoc.distanceSquared(event.getTo()) > 16) {
            lastInvalidateLocations.put(uuid, player.getLocation());
            nearPlayerManager.invalidateCache(uuid);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        nearPlayerManager.invalidateCache(uuid);
        lastInvalidateLocations.remove(uuid);

        if (bossBarManager.hasActiveBossBar(event.getPlayer())) {
            bossBarManager.removeBossBar(event.getPlayer());
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        nearPlayerManager.invalidateCache(uuid);
        lastInvalidateLocations.put(uuid, event.getTo());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        nearPlayerManager.invalidateCache(uuid);
        lastInvalidateLocations.put(uuid, event.getRespawnLocation());
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        nearPlayerManager.invalidateCache(uuid);
        lastInvalidateLocations.put(uuid, event.getTo());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastInvalidateLocations.remove(event.getPlayer().getUniqueId());
    }
}