package org.gw.nearmanager.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.gw.nearmanager.NearManager;
import org.gw.nearmanager.managers.BossBarManager;
import org.gw.nearmanager.managers.ConfigManager;
import org.gw.nearmanager.managers.NearDisplayManager;
import org.gw.nearmanager.managers.NearPlayerManager;
import org.gw.nearmanager.managers.RadiusManager;

public final class CommandsHandler implements CommandExecutor {

    private final NearCommand nearCommand;
    private final NmCommand nmCommand;

    public CommandsHandler(NearManager plugin, ConfigManager configManager, RadiusManager radiusManager,
                           NearPlayerManager nearPlayerManager, BossBarManager bossBarManager,
                           NearDisplayManager nearDisplayManager) {
        this.nearCommand = new NearCommand(plugin, configManager, radiusManager, nearPlayerManager, bossBarManager, nearDisplayManager);
        this.nmCommand = new NmCommand(plugin, configManager, bossBarManager, radiusManager);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();

        if (name.equals("near")) {
            return nearCommand.onCommand(sender, command, label, args);
        }
        if (name.equals("nm")) {
            return nmCommand.onCommand(sender, command, label, args);
        }
        return true;
    }
}