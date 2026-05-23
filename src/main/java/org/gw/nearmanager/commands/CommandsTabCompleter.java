package org.gw.nearmanager.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

public final class CommandsTabCompleter implements TabCompleter {

    private static final List<String> NM_SUBCOMMANDS = List.of("reload", "bossbar");
    private static final List<String> BOSSBAR_SUBCOMMANDS = List.of("on", "off");
    private static final List<String> BOSSBAR_FLAGS = List.of("-nodist");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("near")) {
            if (args.length == 1 && sender instanceof Player player &&
                    player.hasPermission("nearmanager.near.custom-radius")) {
                completions.add("<Радиус>");
            }
            return completions;
        }

        if (cmdName.equals("nm")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], NM_SUBCOMMANDS, completions);
            } else if (args.length == 2 && args[0].equalsIgnoreCase("bossbar")) {
                List<String> playerNames = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    playerNames.add(p.getName());
                }
                StringUtil.copyPartialMatches(args[1], playerNames, completions);
            } else if (args.length == 3 && args[0].equalsIgnoreCase("bossbar")) {
                StringUtil.copyPartialMatches(args[2], BOSSBAR_SUBCOMMANDS, completions);
            } else if ((args.length == 4 || args.length == 5) && args[0].equalsIgnoreCase("bossbar")) {
                List<String> flags = new ArrayList<>();
                boolean hasNoDist = false;
                boolean hasNoTime = false;

                for (int i = 3; i < args.length - 1; i++) {
                    if (args[i].equalsIgnoreCase("-nodist")) hasNoDist = true;
                    if (args[i].equalsIgnoreCase("-notime")) hasNoTime = true;
                }

                if (!hasNoDist) flags.add("-nodist");
                if (!hasNoTime) flags.add("-notime");

                StringUtil.copyPartialMatches(args[args.length - 1], flags, completions);
            }
            return completions;
        }

        return completions;
    }
}