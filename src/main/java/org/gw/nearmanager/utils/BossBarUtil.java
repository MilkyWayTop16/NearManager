package org.gw.nearmanager.utils;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.nearmanager.NearManager;
import org.gw.nearmanager.managers.ConfigManager;
import org.gw.nearmanager.managers.SoundsManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BossBarUtil {
    private static final Map<UUID, BossBarUtil> activeBossBars = new HashMap<>();
    private final NearManager plugin;
    private final ConfigManager configManager;
    private final SoundsManager soundsManager;
    private final Player player;
    private final Player target;
    private BossBar bossBar;
    private final int updateInterval;
    private final boolean minDistanceEnabled;
    private final int minDistance;
    private final boolean maxDistanceEnabled;
    private final int maxDistance;
    private final boolean timeoutEnabled;
    private final long timeoutTicks;
    private final boolean progressEnabled;
    private final String progressMode;
    private final double minProgress;
    private final double maxProgress;
    private final boolean ignoreDistance;
    private final String bossBarText;
    private final double initialDistance;
    private final boolean useMinDistanceAsZero;
    private final long startTime;
    private BukkitRunnable updateTask;
    private BukkitRunnable timeoutTask;
    private String lastWorld;

    public BossBarUtil(NearManager plugin, Player player, Player target, boolean ignoreDistance) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.soundsManager = plugin.getSoundsManager();
        this.player = player;
        this.target = target;
        this.updateInterval = configManager.getInt("bossbar.update-interval", 5);
        this.minDistanceEnabled = configManager.getBoolean("bossbar.min-distance.enabled", true);
        this.minDistance = configManager.getInt("bossbar.min-distance.distance", 15);
        this.maxDistanceEnabled = configManager.getBoolean("bossbar.max-distance.enabled", true);
        this.maxDistance = configManager.getInt("bossbar.max-distance.distance", 150);
        this.timeoutEnabled = configManager.getBoolean("bossbar.timeout.enabled", false);
        this.timeoutTicks = configManager.getInt("bossbar.timeout.time", 0) * 20L;
        this.progressEnabled = configManager.getBoolean("bossbar.progress.enabled", false);
        this.progressMode = configManager.getString("bossbar.progress.mode", "distance").toLowerCase();
        this.minProgress = configManager.getDouble("bossbar.progress.min-progress", 10.0) / 100.0;
        this.maxProgress = configManager.getDouble("bossbar.progress.max-progress", 100.0) / 100.0;
        this.ignoreDistance = ignoreDistance;
        this.bossBarText = configManager.getString("bossbar.text", "&#FFFF00◆ &fИгрок &#FFFF00{player} &fнаходится в &#FFFF00{distance} &fблока(ах) &#FFFF00({direction})");
        this.startTime = System.currentTimeMillis();
        this.initialDistance = player.getLocation().distance(target.getLocation());
        this.useMinDistanceAsZero = configManager.getBoolean("bossbar.progress.use-min-distance-as-zero", false);
        this.lastWorld = target.getWorld().getName();
    }

    public void start() {
        if (!configManager.getBoolean("bossbar.enabled", false)) {
            return;
        }
        if (bossBar != null) {
            bossBar.removeAll();
        }

        BarColor barColor = getBarColor();
        BarStyle barStyle = getBarStyle();
        bossBar = Bukkit.createBossBar("", barColor, barStyle);
        bossBar.addPlayer(player);
        activeBossBars.put(player.getUniqueId(), this);
        updateBossBar();

        if (timeoutEnabled && timeoutTicks > 0) {
            timeoutTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        sendMessage("plugin-messages.bossbar-messages.timeout", Collections.singletonMap("{player}", target.getName()));
                    }
                    stop();
                }
            };
            timeoutTask.runTaskLater(plugin, timeoutTicks);
        }
    }

    private BarColor getBarColor() {
        String barColorStr = configManager.getString("bossbar.color", "YELLOW");
        try {
            return BarColor.valueOf(barColorStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid bossbar color in config.yml: " + barColorStr + ". Defaulting to YELLOW.");
            return BarColor.YELLOW;
        }
    }

    private BarStyle getBarStyle() {
        String barStyleStr = configManager.getString("bossbar.style", "SOLID");
        try {
            return BarStyle.valueOf(barStyleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid bossbar style in config.yml: " + barStyleStr + ". Defaulting to SOLID.");
            return BarStyle.SOLID;
        }
    }

    private void updateBossBar() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !target.isOnline() || player == null || target == null) {
                    if (player.isOnline()) {
                        sendMessage("plugin-messages.bossbar-messages.player-offline", Collections.singletonMap("{player}", target.getName()));
                    }
                    stop();
                    cancel();
                    return;
                }

                if (!target.getWorld().getName().equals(lastWorld)) {
                    if (player.isOnline()) {
                        sendMessage("plugin-messages.bossbar-messages.player-changed-world", Collections.singletonMap("{player}", target.getName()));
                    }
                    stop();
                    cancel();
                    return;
                }

                double distance = player.getLocation().distance(target.getLocation());
                if (!ignoreDistance && minDistanceEnabled && distance <= minDistance) {
                    sendMessage("plugin-messages.bossbar-messages.gone-close", Collections.singletonMap("{player}", target.getName()));
                    stop();
                    cancel();
                    return;
                }

                if (!ignoreDistance && maxDistanceEnabled && distance >= maxDistance) {
                    sendMessage("plugin-messages.bossbar-messages.gone-far", Collections.singletonMap("{player}", target.getName()));
                    stop();
                    cancel();
                    return;
                }

                updateBossBarText(distance);
                updateBossBarProgress(distance);
                lastWorld = target.getWorld().getName();
            }
        };
        updateTask.runTaskTimer(plugin, 0, updateInterval);
    }

    private void updateBossBarText(double distance) {
        String direction = getDirection();
        String text = bossBarText
                .replace("{player}", target.getName())
                .replace("{distance}", String.valueOf((int) distance))
                .replace("{direction}", direction);
        bossBar.setTitle(HexColors.translate(text));
    }

    private void updateBossBarProgress(double distance) {
        if (progressEnabled) {
            double progress;
            if (progressMode.equals("distance") && !ignoreDistance) {
                double effectiveMinDistance = useMinDistanceAsZero ? minDistance : 0;
                if (distance >= initialDistance) {
                    progress = maxProgress;
                } else {
                    double range = initialDistance - effectiveMinDistance;
                    double normalizedDistance = Math.max(0, Math.min(range, distance - effectiveMinDistance));
                    progress = minProgress + (maxProgress - minProgress) * (normalizedDistance / range);
                }
            } else if (progressMode.equals("time") && timeoutEnabled && timeoutTicks > 0) {
                long elapsedTicks = (System.currentTimeMillis() - startTime) / 50;
                double timeFraction = Math.max(0, Math.min(1, (double) elapsedTicks / timeoutTicks));
                progress = maxProgress - (maxProgress - minProgress) * timeFraction;
            } else {
                progress = maxProgress;
            }
            bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        } else {
            bossBar.setProgress(1.0);
        }
    }

    public void sendMessage(String path, Map<String, String> placeholders) {
        String message = configManager.getMessage(path, "");
        if (!message.isEmpty()) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace(entry.getKey(), entry.getValue());
            }
            player.spigot().sendMessage(ChatComponentUtils.parseColoredText(message));
            soundsManager.playSound(player, path);
        }
    }

    public Player getTarget() {
        return target;
    }

    public void stop() {
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
            activeBossBars.remove(player.getUniqueId());
        }
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
    }

    public static boolean hasActiveBossBar(UUID playerUUID) {
        return activeBossBars.containsKey(playerUUID);
    }

    public static BossBarUtil getActiveBossBar(UUID playerUUID) {
        return activeBossBars.get(playerUUID);
    }

    private String getDirection() {
        double dx = target.getLocation().getX() - player.getLocation().getX();
        double dz = target.getLocation().getZ() - player.getLocation().getZ();
        float playerYaw = player.getLocation().getYaw();
        if (playerYaw < 0) playerYaw += 360;

        double targetAngle = Math.toDegrees(Math.atan2(dz, dx)) - 90;
        if (targetAngle < 0) targetAngle += 360;

        double relativeAngle = targetAngle - playerYaw;
        if (relativeAngle < 0) relativeAngle += 360;

        if (relativeAngle >= 337.5 || relativeAngle < 22.5) return configManager.getDirection("north", "⬆");
        if (relativeAngle >= 22.5 && relativeAngle < 67.5) return configManager.getDirection("northeast", "⬈");
        if (relativeAngle >= 67.5 && relativeAngle < 112.5) return configManager.getDirection("east", "➡");
        if (relativeAngle >= 112.5 && relativeAngle < 157.5) return configManager.getDirection("southeast", "⬊");
        if (relativeAngle >= 157.5 && relativeAngle < 202.5) return configManager.getDirection("south", "⬇");
        if (relativeAngle >= 202.5 && relativeAngle < 247.5) return configManager.getDirection("southwest", "⬋");
        if (relativeAngle >= 247.5 && relativeAngle < 292.5) return configManager.getDirection("west", "⬅");
        return configManager.getDirection("northwest", "⬉");
    }
}