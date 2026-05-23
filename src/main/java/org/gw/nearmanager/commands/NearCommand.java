package org.gw.nearmanager.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.gw.nearmanager.NearManager;
import org.gw.nearmanager.managers.BossBarManager;
import org.gw.nearmanager.managers.ConfigManager;
import org.gw.nearmanager.managers.NearDisplayManager;
import org.gw.nearmanager.managers.NearPlayerManager;
import org.gw.nearmanager.managers.RadiusManager;

import java.util.List;
import java.util.Map;

public final class NearCommand implements CommandExecutor {

    private final NearManager plugin;
    private final ConfigManager configManager;
    private final RadiusManager radiusManager;
    private final NearPlayerManager nearPlayerManager;
    private final BossBarManager bossBarManager;
    private final NearDisplayManager nearDisplayManager;

    public NearCommand(NearManager plugin, ConfigManager configManager, RadiusManager radiusManager,
                       NearPlayerManager nearPlayerManager, BossBarManager bossBarManager,
                       NearDisplayManager nearDisplayManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.radiusManager = radiusManager;
        this.nearPlayerManager = nearPlayerManager;
        this.bossBarManager = bossBarManager;
        this.nearDisplayManager = nearDisplayManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            return executeForPlayer(player, args);
        } else {
            return executeForConsole(sender, args);
        }
    }

    private boolean executeForPlayer(Player player, String[] args) {
        if (!player.hasPermission("nearmanager.near")) {
            configManager.executeActions(player, "errors.no-permission", null);
            return true;
        }

        if (nearPlayerManager.isOnCooldown(player)) {
            configManager.executeActions(player, "near.cooldown",
                    Map.of("time", String.valueOf(nearPlayerManager.getRemainingCooldown(player))));
            return true;
        }

        int radius = resolveRadius(player, args);
        if (radius == -1) return true;

        List<NearPlayerManager.PlayerDistance> nearby = nearPlayerManager.getNearbyPlayers(player, radius);

        if (nearby.isEmpty()) {
            sendEmptyResult(player, radius, false);
            return true;
        }

        if (checkTooManyPlayers(nearby, player, false)) return true;

        sendResult(player, radius, nearby);
        nearPlayerManager.setCooldown(player);
        return true;
    }

    private boolean executeForConsole(CommandSender sender, String[] args) {
        if (args.length < 1) {
            configManager.executeActions(sender, "errors.console-usage", null);
            return true;
        }

        String first = args[0];
        if (first.matches("-?\\d+")) {
            configManager.executeActions(sender, "errors.console-usage", null);
            return true;
        }

        Player target = Bukkit.getPlayer(first);
        if (target == null) {
            configManager.executeActions(sender, "errors.player-not-found", Map.of("player", first));
            return true;
        }

        int radius = radiusManager.getRadius(target);
        List<NearPlayerManager.PlayerDistance> nearby = nearPlayerManager.getNearbyPlayers(target, radius);

        if (nearby.isEmpty()) {
            configManager.executeActions(sender, "near.no-players", Map.of(
                    "radius", configManager.formatNumber(radius),
                    "blocks-word-format", configManager.getBlockDeclension(radius)
            ));
            return true;
        }

        if (configManager.isMaxPlayersEnabled() && nearby.size() > configManager.getMaxPlayers()) {
            configManager.executeActions(sender, "near.too-many-players", null);
            return true;
        }

        configManager.executeActions(sender, "near.near", Map.of(
                "radius", configManager.formatNumber(radius),
                "blocks-word-format", configManager.getBlockDeclension(radius),
                "near-players", ""
        ), () -> {
            for (int i = 0; i < nearby.size(); i++) {
                NearPlayerManager.PlayerDistance pd = nearby.get(i);
                int dist = (int) pd.distance();
                String direction = configManager.getDirection(org.gw.nearmanager.utils.DirectionUtils.getDirectionKey(
                        target.getLocation().getX(),
                        target.getLocation().getZ(),
                        target.getLocation().getYaw(),
                        pd.getPlayer().getLocation().getX(),
                        pd.getPlayer().getLocation().getZ()
                ));
                String filled = configManager.getConfig().getString("actions.near.near-players", "")
                        .replace("{number}", String.valueOf(i + 1))
                        .replace("{player}", pd.name())
                        .replace("{blocks}", configManager.formatNumber(dist))
                        .replace("{direction}", direction)
                        .replace("{blocks-word-format}", configManager.getBlockDeclension(dist))
                        .replace("{open-inventory-button}", "")
                        .replace("{teleport-button}", "")
                        .replace("{bossbar-button}", "")
                        .replaceAll(" {2,}", " ");
                sender.sendMessage(org.gw.nearmanager.utils.HexColors.translateToComponent("  " + filled));
            }
        });
        return true;
    }

    private int resolveRadius(Player player, String[] args) {
        int radius = radiusManager.getRadius(player);

        if (args.length > 0) {
            if (!configManager.isCustomRadiusEnabled() || !player.hasPermission("nearmanager.near.custom-radius")) {
                configManager.executeActions(player, "near.invalid-radius", null);
                return -1;
            }

            try {
                int custom = Integer.parseInt(args[0]);

                if (custom < configManager.getCustomRadiusMin()) {
                    configManager.executeActions(player, "near.invalid-min-radius",
                            Map.of("min-radius", String.valueOf(configManager.getCustomRadiusMin())));
                    return -1;
                }
                if (custom > configManager.getCustomRadiusMax()) {
                    configManager.executeActions(player, "near.invalid-max-radius",
                            Map.of("max-radius", String.valueOf(configManager.getCustomRadiusMax())));
                    return -1;
                }
                radius = custom;
            } catch (NumberFormatException e) {
                configManager.executeActions(player, "near.invalid-radius", null);
                return -1;
            }
        }
        return radius;
    }

    private void sendEmptyResult(Player player, int radius, boolean fromConsole) {
        configManager.executeActions(player, "near.no-players", Map.of(
                "radius", configManager.formatNumber(radius),
                "blocks-word-format", configManager.getBlockDeclension(radius)
        ));

        if (!fromConsole) nearPlayerManager.setCooldown(player);
    }

    private boolean checkTooManyPlayers(List<NearPlayerManager.PlayerDistance> nearby, Player player, boolean fromConsole) {
        if (configManager.isMaxPlayersEnabled() && nearby.size() > configManager.getMaxPlayers()) {
            configManager.executeActions(player, "near.too-many-players", null);
            if (!fromConsole) nearPlayerManager.setCooldown(player);
            return true;
        }
        return false;
    }

    private void sendResult(Player player, int radius, List<NearPlayerManager.PlayerDistance> nearby) {
        configManager.executeActions(player, "near.near", Map.of(
                "radius", configManager.formatNumber(radius),
                "blocks-word-format", configManager.getBlockDeclension(radius),
                "near-players", ""
        ), () -> nearDisplayManager.sendNearList(player, nearby));
    }
}