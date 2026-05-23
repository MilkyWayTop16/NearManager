package org.gw.nearmanager.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.gw.nearmanager.managers.ConfigManager;

public class CommandPermissionListener implements Listener {

    private final ConfigManager configManager;

    public CommandPermissionListener(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();

        if (!player.hasPermission("nearmanager.near")) {
            event.getCommands().removeIf(cmd ->
                    cmd.equalsIgnoreCase("near") ||
                            cmd.equalsIgnoreCase("near:near") ||
                            cmd.equalsIgnoreCase("nearmanager:near")
            );
        }

        if (!player.hasPermission("nearmanager.reload") &&
                !player.hasPermission("nearmanager.bossbar")) {
            event.getCommands().removeIf(cmd ->
                    cmd.equalsIgnoreCase("nm") ||
                            cmd.equalsIgnoreCase("nearmanager:nm")
            );
        }
    }
}