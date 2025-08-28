package org.gw.nearmanager.listeners;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

public class CommandListener implements Listener {

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        LuckPerms luckPerms = LuckPermsProvider.get();
        User user = luckPerms.getPlayerAdapter(Player.class).getUser(event.getPlayer());

        boolean hasNearPermission = user.getCachedData().getPermissionData().checkPermission("nearmanager.near").asBoolean();
        if (!hasNearPermission) {
            event.getCommands().remove("near");
            event.getCommands().remove("nearmanager:near");
        }

        boolean hasNmPermission = user.getCachedData().getPermissionData().checkPermission("nearmanager.reload").asBoolean() ||
                user.getCachedData().getPermissionData().checkPermission("nearmanager.bossbar").asBoolean() ||
                user.getCachedData().getPermissionData().checkPermission("nearmanager.near-buttons.bossbar").asBoolean();
        if (!hasNmPermission) {
            event.getCommands().remove("nm");
            event.getCommands().remove("nearmanager:nm");
        }
    }
}