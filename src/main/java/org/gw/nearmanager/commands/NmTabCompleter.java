package org.gw.nearmanager.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.gw.nearmanager.NearManager;
import org.gw.nearmanager.managers.ConfigManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class NmTabCompleter implements TabCompleter {

    private final NearManager plugin;
    private final ConfigManager configManager;

    public NmTabCompleter(NearManager plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("near") || alias.equalsIgnoreCase("nearmanager:near")) {
            if (!sender.hasPermission("nearmanager.near")) {
                return new ArrayList<>();
            }
            if (args.length == 1 && configManager.getBoolean("custom-radius-selection.enabled", false)) {
                completions.add("<Радиус>");
            }
            return completions;
        }

        if (command.getName().equalsIgnoreCase("nm") || alias.equalsIgnoreCase("nearmanager:nm")) {
            if (!sender.hasPermission("nearmanager.reload") &&
                    !sender.hasPermission("nearmanager.bossbar") &&
                    !sender.hasPermission("nearmanager.near-buttons.bossbar")) {
                return new ArrayList<>();
            }
            if (args.length == 1) {
                if (sender.hasPermission("nearmanager.reload")) {
                    completions.add("reload");
                }
                if ((sender.hasPermission("nearmanager.bossbar") || sender.hasPermission("nearmanager.near-buttons.bossbar"))
                        && configManager.getBoolean("bossbar.enabled", false)) {
                    completions.add("bossbar");
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("bossbar") &&
                    (sender.hasPermission("nearmanager.bossbar") || sender.hasPermission("nearmanager.near-buttons.bossbar")) &&
                    configManager.getBoolean("bossbar.enabled", false)) {
                completions.addAll(plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList()));
            } else if (args.length == 3 && args[0].equalsIgnoreCase("bossbar") &&
                    (sender.hasPermission("nearmanager.bossbar") || sender.hasPermission("nearmanager.near-buttons.bossbar")) &&
                    configManager.getBoolean("bossbar.enabled", false)) {
                completions.addAll(Arrays.asList("on", "off"));
            } else if (args.length == 4 && args[0].equalsIgnoreCase("bossbar") && args[2].equalsIgnoreCase("on") &&
                    (sender.hasPermission("nearmanager.bossbar") || sender.hasPermission("nearmanager.near-buttons.bossbar")) &&
                    configManager.getBoolean("bossbar.enabled", false)) {
                completions.add("-nodist");
            }
            return completions;
        }

        return new ArrayList<>();
    }
}