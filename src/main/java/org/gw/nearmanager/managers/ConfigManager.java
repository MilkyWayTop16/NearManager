package org.gw.nearmanager.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.gw.nearmanager.NearManager;
import org.gw.nearmanager.utils.HexColors;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private final NearManager plugin;
    private FileConfiguration config;
    private FileConfiguration soundsConfig;
    private final Map<String, Object> configCache = new HashMap<>();

    public ConfigManager(NearManager plugin) {
        this.plugin = plugin;
        loadConfigs();
    }

    public void loadConfigs() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
        File soundsFile = new File(plugin.getDataFolder(), "sounds.yml");
        if (!soundsFile.exists()) {
            plugin.saveResource("sounds.yml", false);
        }
        soundsConfig = YamlConfiguration.loadConfiguration(soundsFile);
        validateConfig();
        cacheConfig();
    }

    public long reloadConfigs() {
        long startTime = System.currentTimeMillis();
        loadConfigs();
        long timeTaken = System.currentTimeMillis() - startTime;
        String reloadMessage = getMessage("plugin-messages.reload", "");
        if (!reloadMessage.isEmpty()) {
            plugin.getServer().getConsoleSender().sendMessage(HexColors.translate(reloadMessage.replace("{time}", String.valueOf(timeTaken))));
        }
        return timeTaken;
    }

    private void validateConfig() {
        if (config.getInt("settings.bossbar.update-interval", 5) <= 0) {
            plugin.getLogger().warning("Invalid bossbar.update-interval in config.yml, defaulting to 5");
            config.set("settings.bossbar.update-interval", 5);
        }
        if (config.getDouble("settings.custom-radius-selection.min-radius", 0.0) < 0) {
            plugin.getLogger().warning("Invalid custom-radius-selection.min-radius in config.yml, defaulting to 0");
            config.set("settings.custom-radius-selection.min-radius", 0);
        }
        if (config.getDouble("settings.custom-radius-selection.max-radius", 100000.0) < 0) {
            plugin.getLogger().warning("Invalid custom-radius-selection.max-radius in config.yml, defaulting to 100000");
            config.set("settings.custom-radius-selection.max-radius", 100000);
        }
    }

    private void cacheConfig() {
        configCache.clear();
        configCache.put("near-cooldown.enabled", config.getBoolean("settings.near-cooldown.enabled", true));
        configCache.put("near-cooldown.time", config.getInt("settings.near-cooldown.time", 5));
        configCache.put("default-radius-without-permission.radius", config.getDouble("settings.default-radius-without-permission.radius", 100.0));
        configCache.put("custom-radius-selection.enabled", config.getBoolean("settings.custom-radius-selection.enabled", false));
        configCache.put("custom-radius-selection.min-radius", config.getDouble("settings.custom-radius-selection.min-radius", 0.0));
        configCache.put("custom-radius-selection.max-radius", config.getDouble("settings.custom-radius-selection.max-radius", 100000.0));
        configCache.put("hide-vanished-players.enabled", config.getBoolean("settings.hide-vanished-players.enabled", true));
        configCache.put("ignored-players.enabled", config.getBoolean("settings.ignored-players.enabled", true));
        configCache.put("ignored-players.players", config.getStringList("settings.ignored-players.players"));
        configCache.put("max-players-in-radius.enabled", config.getBoolean("settings.max-players-in-radius.enabled", true));
        configCache.put("max-players-in-radius.max", config.getInt("settings.max-players-in-radius.max", 10));
        configCache.put("near-radius-groups.enabled", config.getBoolean("settings.near-radius-groups.enabled", false));
        configCache.put("near-radius-groups.groups", config.getConfigurationSection("settings.near-radius-groups.groups").getValues(false));
    }

    public FileConfiguration getSoundsConfig() {
        return soundsConfig;
    }

    public String getMessage(String path, String def) {
        return config.getString("messages." + path, def);
    }

    public List<String> getMessageList(String path) {
        return config.getStringList("messages." + path);
    }

    public boolean getBoolean(String path, boolean def) {
        return (boolean) configCache.getOrDefault("settings." + path, config.getBoolean("settings." + path, def));
    }

    public int getInt(String path, int def) {
        return (int) configCache.getOrDefault("settings." + path, config.getInt("settings." + path, def));
    }

    public double getDouble(String path, double def) {
        return (double) configCache.getOrDefault("settings." + path, config.getDouble("settings." + path, def));
    }

    public List<String> getStringList(String path) {
        return (List<String>) configCache.getOrDefault("settings." + path, config.getStringList("settings." + path));
    }

    public String getDirection(String direction, String def) {
        return config.getString("settings.directions." + direction, def);
    }

    public String getString(String path, String def) {
        return config.getString("settings." + path, def);
    }

    public boolean isNearRadiusGroupsEnabled() {
        return (boolean) configCache.getOrDefault("settings.near-radius-groups.enabled", config.getBoolean("settings.near-radius-groups.enabled", false));
    }

    public Map<String, Object> getNearRadiusGroups() {
        return (Map<String, Object>) configCache.getOrDefault("settings.near-radius-groups.groups", config.getConfigurationSection("settings.near-radius-groups.groups").getValues(false));
    }
}