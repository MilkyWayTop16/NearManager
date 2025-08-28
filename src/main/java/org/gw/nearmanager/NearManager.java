package org.gw.nearmanager;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.node.NodeAddEvent;
import net.luckperms.api.event.node.NodeRemoveEvent;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.gw.nearmanager.commands.NearCommand;
import org.gw.nearmanager.commands.NmCommand;
import org.gw.nearmanager.commands.NmTabCompleter;
import org.gw.nearmanager.listeners.CommandListener;
import org.gw.nearmanager.listeners.WorldChangeListener;
import org.gw.nearmanager.managers.ConfigManager;
import org.gw.nearmanager.managers.SoundsManager;
import org.gw.nearmanager.utils.HexColors;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NearManager extends JavaPlugin {
    private ConfigManager configManager;
    private SoundsManager soundsManager;
    private final Map<UUID, Double> playerRadiusCache = new HashMap<>();

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        if (getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            getLogger().severe(HexColors.colorize("&cLuckPerms не найден! Этот плагин требуется для работы NearManager"));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        initializePlugin();
        registerLuckPermsListener();
        long loadTime = System.currentTimeMillis() - startTime;
        logStartupInfo(loadTime);
    }

    @Override
    public void onDisable() {
        long startTime = System.currentTimeMillis();

        if (configManager != null) {
            configManager.reloadConfigs();
        }
        playerRadiusCache.clear();

        long unloadTime = System.currentTimeMillis() - startTime;
        logShutdownInfo(unloadTime);
    }

    private void initializePlugin() {
        configManager = new ConfigManager(this);
        soundsManager = new SoundsManager(this);
        registerCommands();
        getServer().getPluginManager().registerEvents(new CommandListener(), this);
        getServer().getPluginManager().registerEvents(new WorldChangeListener(this), this);
    }

    private void registerCommands() {
        registerCommand("near", new NearCommand(this));
        registerCommand("nm", new NmCommand(this));
    }

    private void registerCommand(String commandName, CommandExecutor executor) {
        PluginCommand command = getCommand(commandName);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(new NmTabCompleter(this));
            getLogger().fine("Registered command: " + commandName);
        } else {
            getLogger().warning("Failed to register command: " + commandName);
        }
    }

    private void registerLuckPermsListener() {
        LuckPerms luckPerms = LuckPermsProvider.get();
        EventBus eventBus = luckPerms.getEventBus();
        eventBus.subscribe(this, NodeAddEvent.class, this::onNodeAdd);
        eventBus.subscribe(this, NodeRemoveEvent.class, this::onNodeRemove);
    }

    private void onNodeAdd(NodeAddEvent event) {
        if (event.isUser() && event.getNode().getKey().startsWith("nearmanager.near-radius.")) {
            UUID playerUUID = ((User) event.getTarget()).getUniqueId();
            String permission = event.getNode().getKey();
            try {
                double radius = Double.parseDouble(permission.replace("nearmanager.near-radius.", ""));
                playerRadiusCache.put(playerUUID, radius);
                getLogger().info("Updated radius for player " + playerUUID + " to " + radius);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void onNodeRemove(NodeRemoveEvent event) {
        if (event.isUser() && event.getNode().getKey().startsWith("nearmanager.near-radius.")) {
            UUID playerUUID = ((User) event.getTarget()).getUniqueId();
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getPlayerAdapter(Player.class).getUser(Bukkit.getPlayer(playerUUID));
            if (user == null) {
                playerRadiusCache.remove(playerUUID);
                getLogger().info("Removed radius for offline player " + playerUUID);
                return;
            }

            double newRadius = -1.0;
            for (net.luckperms.api.node.Node node : user.getNodes()) {
                String perm = node.getKey();
                if (perm.startsWith("nearmanager.near-radius.")) {
                    try {
                        double radius = Double.parseDouble(perm.replace("nearmanager.near-radius.", ""));
                        newRadius = radius;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            if (newRadius >= 0) {
                playerRadiusCache.put(playerUUID, newRadius);
                getLogger().info("Updated radius for player " + playerUUID + " to " + newRadius + " after permission removal");
            } else {
                playerRadiusCache.remove(playerUUID);
                getLogger().info("Removed radius for player " + playerUUID + " as no radius permissions remain");
            }
        }
    }

    public double getPlayerRadius(UUID playerUUID) {
        return playerRadiusCache.getOrDefault(playerUUID, -1.0);
    }

    private void logStartupInfo(long loadTime) {
        getLogger().info(HexColors.colorize("&e "));
        getLogger().info(HexColors.colorize("&e█▄░█ █▀▀ ▄▀█ █▀█ █▀▄▀█ ▄▀█ █▄░█ ▄▀█ █▀▀ █▀▀ █▀█"));
        getLogger().info(HexColors.colorize("&e█░▀█ ██▄ █▀█ █▀▄ █░▀░█ █▀█ █░▀█ █▀█ █▄█ ██▄ █▀▄"));
        getLogger().info(HexColors.colorize("&e "));
        getLogger().info(HexColors.colorize("&a▶ Плагин успешно загружен!"));
        getLogger().info(HexColors.colorize("&e "));
        getLogger().info(HexColors.colorize("&e◆ &fВерсия плагина: &e" + getDescription().getVersion()));
        getLogger().info(HexColors.colorize("&e◆ &fВерсия сервера: &e" + Bukkit.getMinecraftVersion()));
        getLogger().info(HexColors.colorize("&e◆ &fВремя загрузки: &e" + loadTime + " мс"));
        getLogger().info(HexColors.colorize("&e "));
    }

    private void logShutdownInfo(long unloadTime) {
        getLogger().info(HexColors.colorize("&e "));
        getLogger().info(HexColors.colorize("&e█▄░█ █▀▀ ▄▀█ █▀█ █▀▄▀█ ▄▀█ █▄░█ ▄▀█ █▀▀ █▀▀ █▀█"));
        getLogger().info(HexColors.colorize("&e█░▀█ ██▄ █▀█ █▀▄ █░▀░█ █▀█ █░▀█ █▀█ █▄█ ██▄ █▀▄"));
        getLogger().info(HexColors.colorize("&e "));
        getLogger().info(HexColors.colorize("&c▶ Плагин успешно выгружен!"));
        getLogger().info(HexColors.colorize("&e "));
        getLogger().info(HexColors.colorize("&e◆ &fВерсия плагина: &e" + getDescription().getVersion()));
        getLogger().info(HexColors.colorize("&e◆ &fВерсия сервера: &e" + Bukkit.getMinecraftVersion()));
        getLogger().info(HexColors.colorize("&e◆ &fВремя выгрузки: &e" + unloadTime + " мс"));
        getLogger().info(HexColors.colorize("&e "));
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public SoundsManager getSoundsManager() {
        return soundsManager;
    }
}