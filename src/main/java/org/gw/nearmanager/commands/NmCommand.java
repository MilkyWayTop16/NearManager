package org.gw.nearmanager.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.gw.nearmanager.NearManager;
import org.gw.nearmanager.managers.BossBarManager;
import org.gw.nearmanager.managers.ConfigManager;
import org.gw.nearmanager.managers.RadiusManager;

import java.util.HashMap;
import java.util.Map;

public final class NmCommand implements CommandExecutor {

    private final NearManager plugin;
    private final ConfigManager configManager;
    private final BossBarManager bossBarManager;
    private final RadiusManager radiusManager;
    private final ReloadCommand reloadCommand;

    public NmCommand(NearManager plugin, ConfigManager configManager,
                     BossBarManager bossBarManager, RadiusManager radiusManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.bossBarManager = bossBarManager;
        this.radiusManager = radiusManager;
        this.reloadCommand = new ReloadCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                if (!player.hasPermission("nearmanager.reload") && !player.hasPermission("nearmanager.bossbar")) {
                    configManager.executeActions(player, "errors.no-permission", null);
                    return true;
                }
                configManager.executeActions(player, "help-for-admins", null);
            } else {
                configManager.executeActions(sender, "help-for-admins", null);
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("nearmanager.reload")) {
                configManager.executeActions(sender, "errors.no-permission", null);
                return true;
            }
            return reloadCommand.execute(sender, args);
        }

        if (args[0].equalsIgnoreCase("bossbar")) {
            boolean isFromButton = false;
            for (String arg : args) {
                if (arg.equalsIgnoreCase("-button")) {
                    isFromButton = true;
                    break;
                }
            }

            if (sender instanceof Player viewer) {
                if (isFromButton) {
                    if (!viewer.hasPermission("nearmanager.near-buttons.bossbar")) {
                        configManager.executeActions(viewer, "errors.no-permission", null);
                        return true;
                    }
                } else {
                    if (!viewer.hasPermission("nearmanager.bossbar")) {
                        configManager.executeActions(viewer, "errors.no-permission", null);
                        return true;
                    }
                }
            }

            if (args.length < 2) {
                configManager.executeActions(sender, "help-for-admins", null);
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                Map<String, String> ph = createPh("player", args[1]);
                configManager.executeActions(sender, "bossbar.player-not-found", ph);
                return true;
            }

            boolean isOff = args.length > 2 && args[2].equalsIgnoreCase("off");

            if (isOff) {
                if (sender instanceof Player viewer) {
                    if (bossBarManager.hasActiveBossBar(viewer)) {
                        bossBarManager.removeBossBar(viewer);
                        configManager.executeActions(viewer, "bossbar.turned-off", Map.of("player", target.getName()));
                    } else {
                        configManager.executeActions(viewer, "bossbar.already-off", Map.of("player", target.getName()));
                    }
                } else {
                    if (bossBarManager.hasActiveBossBar(target)) {
                        bossBarManager.removeBossBar(target);
                        configManager.executeActions(sender, "bossbar.turned-off", Map.of("player", target.getName()));
                    } else {
                        configManager.executeActions(sender, "bossbar.already-off", Map.of("player", target.getName()));
                    }
                }
                return true;
            }

            if (sender instanceof Player viewer) {
                if (bossBarManager.hasActiveBossBar(viewer) && target.getUniqueId().equals(bossBarManager.getTargetId(viewer))) {
                    bossBarManager.removeBossBar(viewer);
                    configManager.executeActions(viewer, "bossbar.turned-off", Map.of("player", target.getName()));
                    return true;
                }
            }

            boolean nodist = false;
            boolean notime = false;
            for (int i = 2; i < args.length; i++) {
                if (args[i].equalsIgnoreCase("-nodist")) {
                    nodist = true;
                } else if (args[i].equalsIgnoreCase("-notime")) {
                    notime = true;
                }
            }

            if (nodist && sender instanceof Player viewer && !viewer.hasPermission("nearmanager.near.bossbar.nodist")) {
                configManager.executeActions(viewer, "errors.no-permission", null);
                return true;
            }

            if (notime && sender instanceof Player viewer && !viewer.hasPermission("nearmanager.near.bossbar.notime")) {
                configManager.executeActions(viewer, "errors.no-permission", null);
                return true;
            }

            if (sender instanceof Player viewer) {
                if (!nodist && configManager.isBossBarMinDistanceEnabled()) {
                    double distance = viewer.getLocation().distance(target.getLocation());
                    if (distance <= configManager.getBossBarMinDistance()) {
                        configManager.executeActions(viewer, "bossbar.too-close-to-activate", Map.of("player", target.getName()));
                        return true;
                    }
                }

                if (!nodist && configManager.isBossBarMaxDistanceEnabled()) {
                    double distance = viewer.getLocation().distance(target.getLocation());
                    if (distance >= configManager.getBossBarMaxDistance()) {
                        configManager.executeActions(viewer, "bossbar.too-far-to-activate", Map.of("player", target.getName()));
                        return true;
                    }
                }

                bossBarManager.activateBossBar(viewer, target, nodist, notime);
            } else {
                bossBarManager.activateBossBarFromConsole(target, nodist, notime);
            }
            return true;
        }

        return true;
    }

    private Map<String, String> createPh(String key, String value) {
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        return map;
    }
}