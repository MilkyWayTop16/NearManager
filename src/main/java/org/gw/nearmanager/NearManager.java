package org.gw.nearmanager;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.gw.nearmanager.commands.CommandsHandler;
import org.gw.nearmanager.commands.CommandsTabCompleter;
import org.gw.nearmanager.listeners.CommandPermissionListener;
import org.gw.nearmanager.listeners.PlayerCacheInvalidationListener;
import org.gw.nearmanager.listeners.PlayerQuitListener;
import org.gw.nearmanager.managers.BossBarManager;
import org.gw.nearmanager.managers.ConfigManager;
import org.gw.nearmanager.managers.NearDisplayManager;
import org.gw.nearmanager.managers.NearPlayerManager;
import org.gw.nearmanager.managers.RadiusManager;
import org.gw.nearmanager.utils.BStats;
import org.gw.nearmanager.utils.HexColors;
import org.gw.nearmanager.utils.UpdateChecker;

public final class NearManager extends JavaPlugin {

    @Getter
    private ConfigManager configManager;
    private RadiusManager radiusManager;
    private NearPlayerManager nearPlayerManager;
    private BossBarManager bossBarManager;
    private UpdateChecker updateChecker;
    private CommandsHandler commandsHandler;

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        if (!initializePlugin()) {
            setEnabled(false);
            return;
        }

        new BStats(this);

        long loadTime = System.currentTimeMillis() - startTime;
        logStartupInfo(loadTime);
    }

    private boolean initializePlugin() {
        console("&#ffff00 ");
        console("&#00FF5A◆ NearManager &f| Чтение &#00FF5Aконфигурационных &fфайлов...");
        configManager = new ConfigManager(this);

        console("&#00FF5A◆ NearManager &f| Инициализация &#00FF5Aменеджеров...");
        radiusManager = new RadiusManager(configManager);
        nearPlayerManager = new NearPlayerManager(configManager, radiusManager);
        bossBarManager = new BossBarManager(this, configManager);

        console("&#00FF5A◆ NearManager &f| Регистрация &#00FF5Aсобытий &fи &#00FF5Aкоманд...");

        getServer().getPluginManager().registerEvents(new PlayerQuitListener(nearPlayerManager, bossBarManager, radiusManager), this);
        getServer().getPluginManager().registerEvents(new PlayerCacheInvalidationListener(nearPlayerManager, bossBarManager), this);
        getServer().getPluginManager().registerEvents(new CommandPermissionListener(configManager), this);

        NearDisplayManager nearDisplayManager = new NearDisplayManager(configManager);
        commandsHandler = new CommandsHandler(this, configManager, radiusManager, nearPlayerManager, bossBarManager, nearDisplayManager);

        CommandsTabCompleter tabCompleter = new CommandsTabCompleter();
        getCommand("near").setExecutor(commandsHandler);
        getCommand("near").setTabCompleter(tabCompleter);
        getCommand("nm").setExecutor(commandsHandler);
        getCommand("nm").setTabCompleter(tabCompleter);

        console("&#00FF5A◆ NearManager &f| Инициализация &#00FF5Aсистемы проверки &fобновлений...");
        updateChecker = new UpdateChecker(this);
        getServer().getPluginManager().registerEvents(updateChecker, this);

        org.gw.nearmanager.utils.PlaceholderAPIHook.init();

        return true;
    }

    public boolean reloadConfigs() {
        boolean success = configManager.reload();
        if (!success) return false;

        radiusManager.clearAllCache();
        nearPlayerManager.invalidateAllCache();
        nearPlayerManager.clearCooldowns();

        return true;
    }

    public boolean reloadPlugin() {
        boolean success = reloadConfigs();
        if (!success) return false;

        if (bossBarManager != null) {
            bossBarManager.restart();
        }

        if (updateChecker != null) {
            updateChecker.reload();
        }

        org.gw.nearmanager.utils.PlaceholderAPIHook.init();

        return true;
    }

    private void logStartupInfo(long loadTime) {
        console("&#ffff00 ");
        console("&#FFFF00  █▄░█ █▀▀ ▄▀█ █▀█ █▀▄▀█ ▄▀█ █▄░█ ▄▀█ █▀▀ █▀▀ █▀█");
        console("&#FFFF00  █░▀█ ██▄ █▀█ █▀▄ █░▀░█ █▀█ █░▀█ █▀█ █▄█ ██▄ █▀▄");
        console("&#ffff00 ");
        console("&f             (By MilkyWay for everyone)");
        console("&#ffff00 ");
        console("&#00FF5A       ▶ &fПлагин &#00FF5Aуспешно &fзагружен и включен!");
        console("&#ffff00 ");
        console("&#ffff00               ◆ &fВерсия плагина: &#ffff00v" + getDescription().getVersion());
        console("&#ffff00              ◆ &fВремя загрузки: &#ffff00" + loadTime + " мс.");
        console("&#ffff00 ");
    }

    @Override
    public void onDisable() {
        long startTime = System.currentTimeMillis();

        console("&#00FF5A◆ NearManager &f| Начало &#00FF5Aвыгрузки &fплагина...");

        if (bossBarManager != null) {
            bossBarManager.shutdown();
        }

        long unloadTime = System.currentTimeMillis() - startTime;

        console("&#ffff00 ");
        console("&#FFFF00  █▄░█ █▀▀ ▄▀█ █▀█ █▀▄▀█ ▄▀█ █▄░█ ▄▀█ █▀▀ █▀▀ █▀█");
        console("&#FFFF00  █░▀█ ██▄ █▀█ █▀▄ █░▀░█ █▀█ █░▀█ █▀█ █▄█ ██▄ █▀▄");
        console("&#ffff00 ");
        console("&f             (By MilkyWay for everyone)");
        console("&#ffff00 ");
        console("&#FF5D00      ▶ &fПлагин &#FF5D00успешно &fвыгружен и выключен...");
        console("&#ffff00 ");
        console("&#ffff00               ◆ &fВерсия плагина: &#ffff00v" + getDescription().getVersion());
        console("&#ffff00              ◆ &fВремя выгрузки: &#ffff00" + unloadTime + " мс.");
        console("&#ffff00 ");
    }

    public void console(String message) {
        if (message == null) return;
        Bukkit.getConsoleSender().sendMessage(HexColors.translate(message));
    }

    public void log(String message) {
        if (configManager != null && configManager.isConsoleLogsEnabled()) {
            Bukkit.getConsoleSender().sendMessage(HexColors.translate("&#FFFF00◆ NearManager &f| " + message));
        }
    }

    public void error(String message) {
        Bukkit.getConsoleSender().sendMessage(HexColors.translate("&#FB8808◆ NearManager &f| " + message));
    }
}