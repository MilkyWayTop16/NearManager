package org.gw.nearmanager.commands;

import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.gw.nearmanager.NearManager;
import org.gw.nearmanager.managers.ConfigManager;
import org.gw.nearmanager.managers.SoundsManager;
import org.gw.nearmanager.utils.BossBarUtil;
import org.gw.nearmanager.utils.ChatComponentUtils;
import org.gw.nearmanager.utils.HexColors;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class NmCommand implements CommandExecutor {
    private static final String PERM_RELOAD = "nearmanager.reload";
    private static final String PERM_BOSSBAR = "nearmanager.bossbar";
    private static final String PERM_BOSSBAR_BUTTON = "nearmanager.near-buttons.bossbar";

    private final NearManager plugin;
    private final ConfigManager configManager;
    private final SoundsManager soundsManager;

    public NmCommand(NearManager plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.soundsManager = plugin.getSoundsManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sendMessage(sender, "plugin-messages.no-console", "", null);
            return true;
        }

        Player player = (Player) sender;
        if (args.length == 0) {
            sendHelpMessages(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            return handleReload(player);
        }

        if (args[0].equalsIgnoreCase("bossbar")) {
            return handleBossbar(player, args);
        }

        sendHelpMessages(player);
        return true;
    }

    private void sendHelpMessages(Player player) {
        boolean hasAdminPerms = player.hasPermission(PERM_RELOAD) ||
                (player.hasPermission(PERM_BOSSBAR) && configManager.getBoolean("bossbar.enabled", false)) ||
                (player.hasPermission(PERM_BOSSBAR_BUTTON) && configManager.getBoolean("bossbar.enabled", false));
        List<String> helpMessages = hasAdminPerms
                ? configManager.getMessageList("plugin-messages.help-for-admins")
                : configManager.getMessageList("plugin-messages.help-for-players");

        for (String line : helpMessages) {
            player.sendMessage(HexColors.translate(line));
            soundsManager.playSound(player, hasAdminPerms ? "plugin-messages.help-for-admins" : "plugin-messages.help-for-players");
        }
    }

    private boolean handleReload(Player player) {
        if (!player.hasPermission(PERM_RELOAD) && !player.isOp()) {
            sendMessage(player, "plugin-messages.no-permission", "", null);
            return true;
        }

        File pluginFolder = new File(plugin.getDataFolder().getPath());
        if (!pluginFolder.exists()) {
            pluginFolder.mkdir();
            configManager.loadConfigs();
        }

        long timeTaken = configManager.reloadConfigs();
        sendMessage(player, "plugin-messages.reload", "", Collections.singletonMap("{time}", String.valueOf(timeTaken)));
        return true;
    }

    private boolean handleBossbar(Player player, String[] args) {
        if (!configManager.getBoolean("bossbar.enabled", false)) {
            sendMessage(player, "plugin-messages.bossbar-messages.disabled", "", null);
            return true;
        }

        if (!player.hasPermission(PERM_BOSSBAR) && !player.hasPermission(PERM_BOSSBAR_BUTTON) && !player.isOp()) {
            sendMessage(player, "plugin-messages.no-permission", "", null);
            return true;
        }

        if (args.length < 2 || args.length > 4) {
            sendHelpMessages(player);
            return true;
        }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sendMessage(player, "plugin-messages.bossbar-messages.player-not-found", "", Collections.singletonMap("{player}", args[1]));
            return true;
        }

        if (target.equals(player)) {
            sendMessage(player, "plugin-messages.bossbar-messages.self-target", "", null);
            return true;
        }

        boolean toggleOn = args.length >= 3 ? args[2].equalsIgnoreCase("on") : !BossBarUtil.hasActiveBossBar(player.getUniqueId());
        boolean ignoreDistance = args.length == 4 && args[3].equalsIgnoreCase("-nodist");

        if (toggleOn) {
            return handleBossbarOn(player, target, ignoreDistance);
        } else {
            return handleBossbarOff(player, target);
        }
    }

    private boolean handleBossbarOn(Player player, Player target, boolean ignoreDistance) {
        if (BossBarUtil.hasActiveBossBar(player.getUniqueId())) {
            sendMessage(player, "plugin-messages.bossbar-messages.already-active", "", null);
            return true;
        }

        if (!ignoreDistance) {
            double distance = player.getLocation().distance(target.getLocation());
            if (configManager.getBoolean("bossbar.min-distance.enabled", true) && distance <= configManager.getInt("bossbar.min-distance.distance", 15)) {
                sendMessage(player, "plugin-messages.bossbar-messages.close", "", Collections.singletonMap("{player}", target.getName()));
                return true;
            }
            if (configManager.getBoolean("bossbar.max-distance.enabled", true) && distance >= configManager.getInt("bossbar.max-distance.distance", 150)) {
                sendMessage(player, "plugin-messages.bossbar-messages.too-far", "", Collections.singletonMap("{player}", target.getName()));
                return true;
            }
        }

        BossBarUtil bossBarUtil = new BossBarUtil(plugin, player, target, ignoreDistance);
        bossBarUtil.start();
        sendMessage(player, "plugin-messages.bossbar-messages.activated", "", Collections.singletonMap("{player}", target.getName()));
        return true;
    }

    private boolean handleBossbarOff(Player player, Player target) {
        if (!BossBarUtil.hasActiveBossBar(player.getUniqueId())) {
            sendMessage(player, "plugin-messages.bossbar-messages.already-off", "", Collections.singletonMap("{player}", target.getName()));
            return true;
        }

        BossBarUtil bossBarUtil = BossBarUtil.getActiveBossBar(player.getUniqueId());
        if (bossBarUtil != null && bossBarUtil.getTarget().equals(target)) {
            bossBarUtil.stop();
            sendMessage(player, "plugin-messages.bossbar-messages.turned-off", "", Collections.singletonMap("{player}", target.getName()));
        } else {
            sendMessage(player, "plugin-messages.bossbar-messages.already-off", "", Collections.singletonMap("{player}", target.getName()));
        }
        return true;
    }

    private void sendMessage(CommandSender sender, String path, String def, Map<String, String> placeholders) {
        String message = configManager.getMessage(path, def);
        if (!message.isEmpty()) {
            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    message = message.replace(entry.getKey(), entry.getValue());
                }
            }
            sender.sendMessage(ChatComponentUtils.parseColoredText(message));
            soundsManager.playSound(sender instanceof Player ? (Player) sender : null, path);
        }
    }
}