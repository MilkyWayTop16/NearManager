package org.gw.nearmanager.commands;

import org.bukkit.command.CommandSender;
import org.gw.nearmanager.NearManager;

import java.util.HashMap;
import java.util.Map;

public class ReloadCommand {

    private final NearManager plugin;

    public ReloadCommand(NearManager plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String[] args) {
        long startTime = System.currentTimeMillis();

        boolean success = plugin.reloadPlugin();

        long reloadTime = System.currentTimeMillis() - startTime;
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("time", String.valueOf(reloadTime));

        if (success) {
            plugin.getConfigManager().executeActions(sender, "plugin.reload", placeholders);
        }

        return true;
    }
}