package org.gw.nearmanager.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.gw.nearmanager.NearManager;
import org.gw.nearmanager.utils.BossBarUtil;

import java.util.Collections;

public class WorldChangeListener implements Listener {
    private final NearManager plugin;

    public WorldChangeListener(NearManager plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            if (BossBarUtil.hasActiveBossBar(event.getPlayer().getUniqueId())) {
                BossBarUtil bossBarUtil = BossBarUtil.getActiveBossBar(event.getPlayer().getUniqueId());
                if (bossBarUtil != null) {
                    bossBarUtil.sendMessage("plugin-messages.bossbar-messages.self-changed-world", Collections.emptyMap());
                    bossBarUtil.stop();
                }
            }
        }
    }
}