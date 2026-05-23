package org.gw.nearmanager.managers;

import lombok.Getter;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.gw.nearmanager.NearManager;
import org.gw.nearmanager.utils.HexColors;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public final class ConfigManager {

    private final NearManager plugin;
    private FileConfiguration config;

    private final Map<String, List<ParsedAction>> parsedActions = new ConcurrentHashMap<>();
    private final java.util.Map<String, String> directions = new java.util.HashMap<>();

    private boolean consoleLogsEnabled = true;

    private int defaultRadius;
    private boolean nearCooldownEnabled;
    private int nearCooldownTime;
    private boolean hideVanished;
    private boolean ignoredPlayersEnabled;
    private List<String> ignoredPlayers;
    private boolean customRadiusEnabled;
    private int customRadiusMin;
    private int customRadiusMax;
    private boolean maxPlayersEnabled;
    private int maxPlayers;
    private boolean nearRadiusGroupsEnabled;
    private final Map<String, Integer> nearRadiusGroups = new HashMap<>();
    private boolean bossBarEnabled;
    private String bossBarColor;
    private String bossBarStyle;
    private String bossBarText;
    private int bossBarUpdateInterval;
    private boolean bossBarMinDistanceEnabled;
    private int bossBarMinDistance;
    private boolean bossBarMaxDistanceEnabled;
    private int bossBarMaxDistance;
    private boolean bossBarTimeoutEnabled;
    private int bossBarTimeout;
    private boolean bossBarProgressEnabled;
    private String bossBarProgressMode;
    private double bossBarMinProgress;
    private double bossBarMaxProgress;
    private boolean useMinDistanceAsZero;
    private BossBar.Color bossBarColorEnum;
    private BossBar.Overlay bossBarOverlayEnum;
    private boolean updateCheckerEnabled;
    private String updateNotifyMode;
    private int updatePeriodicIntervalHours;
    private String numberFormatStyle;
    private String blockDeclensionOne;
    private String blockDeclensionOther;

    private transient ThreadLocal<java.text.DecimalFormat> activeFormatter;

    private record ParsedAction(String type, String content) {}

    public ConfigManager(NearManager plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        InputStream defConfigStream = plugin.getResource("config.yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defConfigStream, StandardCharsets.UTF_8));
            config.setDefaults(defConfig);
        }

        preParseAllActions();
        cacheHotSettings();
        blockDeclensionOne = config.getString("settings.block-declension.one", "блоке");
        blockDeclensionOther = config.getString("settings.block-declension.other", "блоках");
        consoleLogsEnabled = config.getBoolean("settings.console-logs.enabled", true);

        directions.clear();
        ConfigurationSection dirSec = config.getConfigurationSection("settings.directions");
        if (dirSec != null) {
            for (String key : dirSec.getKeys(false)) {
                directions.put(key.toLowerCase(), dirSec.getString(key, ""));
            }
        }
    }

    public boolean reload() {
        try {
            parsedActions.clear();
            loadConfig();
            plugin.log("Конфигурация плагина успешно перезагружена");
            return true;
        } catch (Exception e) {
            plugin.error("Ошибка перезагрузки конфига: " + e.getMessage());
            return false;
        }
    }

    private void preParseAllActions() {
        parsedActions.clear();
        ConfigurationSection section = config.getConfigurationSection("actions");
        if (section == null) return;

        for (String key : section.getKeys(true)) {
            if (section.isList(key)) {
                List<String> rawList = section.getStringList(key);
                List<ParsedAction> parsedList = new ArrayList<>(rawList.size());

                for (String line : rawList) {
                    ParsedAction pa = parseActionLine(line);
                    if (pa != null) {
                        parsedList.add(pa);
                    }
                }
                parsedActions.put(key, parsedList);
            }
        }
    }

    private ParsedAction parseActionLine(String line) {
        if (line == null || line.isEmpty() || !line.startsWith("[")) return null;

        int end = line.indexOf("]");
        if (end == -1) return null;

        String type = line.substring(1, end).toLowerCase().trim();
        String content = line.substring(end + 1).trim();
        return new ParsedAction(type, content);
    }

    private void cacheHotSettings() {
        defaultRadius = config.getInt("settings.default-radius-without-permission.radius", 100);

        nearCooldownEnabled = config.getBoolean("settings.near-cooldown.enabled", true);
        nearCooldownTime = config.getInt("settings.near-cooldown.time", 5);

        hideVanished = config.getBoolean("settings.hide-vanished-players.enabled", true);

        ignoredPlayersEnabled = config.getBoolean("settings.ignored-players.enabled", false);
        ignoredPlayers = config.getStringList("settings.ignored-players.players");

        customRadiusEnabled = config.getBoolean("settings.custom-radius-selection.enabled", true);
        customRadiusMin = config.getInt("settings.custom-radius-selection.min-radius", 0);
        customRadiusMax = config.getInt("settings.custom-radius-selection.max-radius", 100000);

        maxPlayersEnabled = config.getBoolean("settings.max-players-in-radius.enabled", true);
        maxPlayers = config.getInt("settings.max-players-in-radius.max", 10);

        nearRadiusGroupsEnabled = config.getBoolean("settings.near-radius-groups.enabled", false);
        nearRadiusGroups.clear();
        ConfigurationSection groupsSec = config.getConfigurationSection("settings.near-radius-groups.groups");
        if (groupsSec != null) {
            for (String g : groupsSec.getKeys(false)) {
                nearRadiusGroups.put(g, groupsSec.getInt(g));
            }
        }

        bossBarEnabled = config.getBoolean("settings.bossbar.enabled", true);
        bossBarColor = config.getString("settings.bossbar.color", "YELLOW");
        bossBarStyle = config.getString("settings.bossbar.style", "NOTCHED_6");
        bossBarText = config.getString("settings.bossbar.text",
                "&#FFFF00◆ &fИгрок &#FFFF00{player} &fнаходится в &#FFFF00{distance} &fблока(ах) &#FFFF00({direction})");
        bossBarUpdateInterval = config.getInt("settings.bossbar.update-interval", 5);

        bossBarMinDistanceEnabled = config.getBoolean("settings.bossbar.min-distance.enabled", true);
        bossBarMinDistance = config.getInt("settings.bossbar.min-distance.distance", 15);
        bossBarMaxDistanceEnabled = config.getBoolean("settings.bossbar.max-distance.enabled", true);
        bossBarMaxDistance = config.getInt("settings.bossbar.max-distance.distance", 150);

        bossBarTimeoutEnabled = config.getBoolean("settings.bossbar.timeout.enabled", true);
        bossBarTimeout = config.getInt("settings.bossbar.timeout.time", 120);

        bossBarProgressEnabled = config.getBoolean("settings.bossbar.progress.enabled", true);
        bossBarProgressMode = config.getString("settings.bossbar.progress.mode", "distance");
        bossBarMinProgress = config.getDouble("settings.bossbar.progress.min-progress", 0);
        bossBarMaxProgress = config.getDouble("settings.bossbar.progress.max-progress", 100);
        useMinDistanceAsZero = config.getBoolean("settings.bossbar.progress.use-min-distance-as-zero", true);

        validateBossBarEnums();

        updateCheckerEnabled = config.getBoolean("settings.update-checker.enabled", true);
        updateNotifyMode = config.getString("settings.update-checker.notify-mode", "both").toLowerCase();
        updatePeriodicIntervalHours = config.getInt("settings.update-checker.periodic-interval-hours", 6);

        numberFormatStyle = config.getString("settings.number-format.style", "comma");

        String style = numberFormatStyle.toLowerCase();
        java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols(java.util.Locale.US);
        String pattern;

        switch (style) {
            case "dot" -> {
                symbols.setGroupingSeparator('.');
                symbols.setDecimalSeparator(',');
                pattern = "#,##0";
            }
            case "space" -> {
                symbols.setGroupingSeparator(' ');
                symbols.setDecimalSeparator(',');
                pattern = "#,##0";
            }
            case "decimal" -> {
                pattern = "#,##0.00";
            }
            case "comma" -> {
                pattern = "#,##0";
            }
            default -> {
                pattern = null;
            }
        }

        if (pattern != null) {
            activeFormatter = ThreadLocal.withInitial(() -> new java.text.DecimalFormat(pattern, symbols));
        } else {
            activeFormatter = null;
        }

        plugin.log("Успешно загружено " + nearRadiusGroups.size() + " групп радиусов");
    }

    private void validateBossBarEnums() {
        try {
            bossBarColorEnum = BossBar.Color.valueOf(bossBarColor.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.error("Некорректный цвет боссбара в config.yml: " + bossBarColor + ", используется YELLOW по умолчанию");
            bossBarColorEnum = BossBar.Color.YELLOW;
        }

        try {
            bossBarOverlayEnum = BossBar.Overlay.valueOf(bossBarStyle.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.error("Некорректный стиль боссбара в config.yml: " + bossBarStyle + ", используется NOTCHED_6 по умолчанию");
            bossBarOverlayEnum = BossBar.Overlay.NOTCHED_6;
        }
    }

    public void executeActions(Player player, String path, Map<String, String> placeholders) {
        executeActions((CommandSender) player, path, placeholders, null);
    }

    public void executeActions(CommandSender sender, String path, Map<String, String> placeholders) {
        executeActions(sender, path, placeholders, null);
    }

    public void executeActions(CommandSender sender, String path, Map<String, String> placeholders, Runnable nearListInjector) {
        List<ParsedAction> actionList = parsedActions.get(path);
        if (actionList == null || actionList.isEmpty()) return;

        Map<String, String> ph = new HashMap<>(placeholders != null ? placeholders : Map.of());
        if (sender != null && !ph.containsKey("player")) {
            ph.put("player", sender.getName());
        }

        Player player = (sender instanceof Player p) ? p : null;

        for (ParsedAction action : actionList) {
            String originalContent = action.content();
            String content = originalContent;
            for (Map.Entry<String, String> entry : ph.entrySet()) {
                content = content.replace("{" + entry.getKey() + "}", entry.getValue());
            }

            if (originalContent.contains("{near-players}") && nearListInjector != null) {
                nearListInjector.run();
                continue;
            }

            if (player != null) {
                content = org.gw.nearmanager.utils.PlaceholderAPIHook.parse(player, content);
            }

            try {
                switch (action.type()) {
                    case "message" -> {
                        if (sender != null) sender.sendMessage(HexColors.translateToComponent(content));
                    }
                    case "message-console" -> plugin.console(content);
                    case "broadcast" -> Bukkit.broadcastMessage(HexColors.translate(content));
                    case "sound" -> {
                        if (player != null) playSound(player, content);
                    }
                    case "title" -> {
                        if (player != null) showTitle(player, content, false);
                    }
                    case "subtitle" -> {
                        if (player != null) showTitle(player, content, true);
                    }
                    case "actionbar" -> {
                        if (player != null) player.sendActionBar(HexColors.translateToComponent(content));
                    }
                    case "console-command" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), content);
                    case "player-command" -> {
                        if (player != null) player.performCommand(content);
                    }
                    default -> plugin.error("Неизвестный тип действия: [" + action.type() + "]");
                }
            } catch (Exception e) {
                plugin.error("Ошибка выполнения действия [" + action.type() + "]: " + e.getMessage());
            }
        }
    }

    private void playSound(Player player, String data) {
        try {
            String[] parts = data.split(" ");
            Sound sound = Sound.valueOf(parts[0].toUpperCase());
            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception e) {
            plugin.error("Ошибка воспроизведения звука: " + data);
        }
    }

    private void showTitle(Player player, String data, boolean isSubtitle) {
        try {
            String[] parts = data.split(";", 4);
            String main = parts.length > 0 ? parts[0] : "";
            String sub = parts.length > 1 ? parts[1] : "";
            int fadeIn = parts.length > 2 ? Integer.parseInt(parts[2]) : 10;
            int stay = parts.length > 3 ? Integer.parseInt(parts[3]) : 70;

            Title title = Title.title(
                    HexColors.translateToComponent(main),
                    HexColors.translateToComponent(sub),
                    Title.Times.times(
                            Duration.ofMillis(fadeIn * 50L),
                            Duration.ofMillis(stay * 50L),
                            Duration.ofMillis(500)
                    )
            );
            player.showTitle(title);
        } catch (Exception e) {
            plugin.error("Ошибка показа тайтла: " + data);
        }
    }

    public String getBlockDeclension(int number) {
        if (number <= 0) {
            return blockDeclensionOther;
        }
        if (number % 10 == 1 && number % 100 != 11) {
            return blockDeclensionOne;
        }
        return blockDeclensionOther;
    }

    public String formatNumber(int number) {
        if (activeFormatter == null) {
            return String.valueOf(number);
        }
        return activeFormatter.get().format(number);
    }

    public boolean useMinDistanceAsZero() {
        return useMinDistanceAsZero;
    }

    public String getDirection(String key) {
        return directions.getOrDefault(key.toLowerCase(), "⬆");
    }
}