package org.gw.nearmanager.managers;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.gw.nearmanager.NearManager;
import org.gw.nearmanager.utils.DirectionUtils;
import org.gw.nearmanager.utils.HexColors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BossBarManager {

    private final NearManager plugin;
    private final ConfigManager configManager;
    private final Map<UUID, BossBarData> activeBossBars = new ConcurrentHashMap<>();
    private record BossBarData(BossBar bossBar, UUID targetId, long startTime, boolean nodist, boolean notime) {}
    private record BossBarTaskSnapshot(
            UUID viewerId,
            String baseText,
            double viewerX,
            double viewerY,
            double viewerZ,
            float viewerYaw,
            double targetX,
            double targetY,
            double targetZ,
            String targetName,
            long startTime,
            boolean nodist,
            boolean notime,
            BossBar bossBar,
            int state
    ) {}

    private BukkitTask updateTask;

    public BossBarManager(NearManager plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;

        if (configManager.isBossBarEnabled()) {
            startGlobalUpdateTask();
        }
    }

    private void startGlobalUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        int interval = Math.max(1, configManager.getBossBarUpdateInterval());

        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (activeBossBars.isEmpty()) return;

                List<BossBarTaskSnapshot> snapshots = new ArrayList<>();
                for (Map.Entry<UUID, BossBarData> entry : activeBossBars.entrySet()) {
                    UUID viewerId = entry.getKey();
                    BossBarData data = entry.getValue();

                    Player viewer = Bukkit.getPlayer(viewerId);
                    if (viewer == null || !viewer.isOnline()) {
                        snapshots.add(new BossBarTaskSnapshot(viewerId, null, 0.0, 0.0, 0.0, 0.0f, 0.0, 0.0, 0.0, "", data.startTime(), data.nodist(), data.notime(), data.bossBar(), 0));
                        continue;
                    }

                    Player target = Bukkit.getPlayer(data.targetId());
                    if (target == null || !target.isOnline()) {
                        snapshots.add(new BossBarTaskSnapshot(viewerId, null, 0.0, 0.0, 0.0, 0.0f, 0.0, 0.0, 0.0, "", data.startTime(), data.nodist(), data.notime(), data.bossBar(), 1));
                        continue;
                    }

                    if (!viewer.getWorld().equals(target.getWorld())) {
                        snapshots.add(new BossBarTaskSnapshot(viewerId, null, 0.0, 0.0, 0.0, 0.0f, 0.0, 0.0, 0.0, target.getName(), data.startTime(), data.nodist(), data.notime(), data.bossBar(), 2));
                        continue;
                    }

                    String baseText = org.gw.nearmanager.utils.PlaceholderAPIHook.parse(viewer, configManager.getBossBarText());
                    Location vLoc = viewer.getLocation();
                    Location tLoc = target.getLocation();

                    snapshots.add(new BossBarTaskSnapshot(
                            viewerId, baseText, vLoc.getX(), vLoc.getY(), vLoc.getZ(), vLoc.getYaw(),
                            tLoc.getX(), tLoc.getY(), tLoc.getZ(), target.getName(),
                            data.startTime(), data.nodist(), data.notime(), data.bossBar(), 3
                    ));
                }

                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> processBossBarsAsync(snapshots));
            }
        }.runTaskTimer(plugin, 0L, interval);
    }

    private void processBossBarsAsync(List<BossBarTaskSnapshot> snapshots) {
        if (activeBossBars.isEmpty()) return;

        List<UUID> toRemove = new ArrayList<>();
        boolean isTimeMode = "time".equals(configManager.getBossBarProgressMode());

        for (BossBarTaskSnapshot snap : snapshots) {
            UUID viewerId = snap.viewerId();

            if (snap.state() == 0) {
                toRemove.add(viewerId);
                continue;
            }

            if (snap.state() == 1) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player viewer = Bukkit.getPlayer(viewerId);
                    if (viewer != null) {
                        plugin.log("Боссбар для &#ffff00" + viewer.getName() + " &fснят, так как цель вышла из сети...");
                    }
                });
                toRemove.add(viewerId);
                continue;
            }

            if (snap.state() == 2) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player viewer = Bukkit.getPlayer(viewerId);
                    if (viewer != null) {
                        configManager.executeActions(viewer, "bossbar.player-changed-world", Map.of("player", snap.targetName()));
                        plugin.log("Боссбар для &#ffff00" + viewer.getName() + " &fснят, так как цель &#ffff00" + snap.targetName() + " &fсменила мир...");
                    }
                });
                toRemove.add(viewerId);
                continue;
            }

            double dx = snap.viewerX() - snap.targetX();
            double dy = snap.viewerY() - snap.targetY();
            double dz = snap.viewerZ() - snap.targetZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            String direction = configManager.getDirection(DirectionUtils.getDirectionKey(
                    snap.viewerX(), snap.viewerZ(), snap.viewerYaw(),
                    snap.targetX(), snap.targetZ()
            ));

            if (!snap.nodist()) {
                if (configManager.isBossBarMinDistanceEnabled() && distance <= configManager.getBossBarMinDistance()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Player viewer = Bukkit.getPlayer(viewerId);
                        if (viewer != null) {
                            configManager.executeActions(viewer, "bossbar.gone-close", Map.of("player", snap.targetName()));
                            plugin.log("Боссбар для &#ffff00" + viewer.getName() + " &fснят, так как цель &#ffff00" + snap.targetName() + " &fслишком близко...");
                        }
                    });
                    toRemove.add(viewerId);
                    continue;
                }
                if (configManager.isBossBarMaxDistanceEnabled() && distance >= configManager.getBossBarMaxDistance()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Player viewer = Bukkit.getPlayer(viewerId);
                        if (viewer != null) {
                            configManager.executeActions(viewer, "bossbar.gone-far", Map.of("player", snap.targetName()));
                            plugin.log("Боссбар для &#ffff00" + viewer.getName() + " &fснят, так как цель &#ffff00" + snap.targetName() + " &fслишком далеко...");
                        }
                    });
                    toRemove.add(viewerId);
                    continue;
                }
            }

            long elapsed = (System.currentTimeMillis() - snap.startTime()) / 1000;
            if (!snap.notime() && configManager.isBossBarTimeoutEnabled() && elapsed >= configManager.getBossBarTimeout()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player viewer = Bukkit.getPlayer(viewerId);
                    if (viewer != null) {
                        configManager.executeActions(viewer, "bossbar.timeout", Map.of("player", snap.targetName()));
                        plugin.log("Боссбар для &#ffff00" + viewer.getName() + " &fснят по таймауту времени...");
                    }
                });
                toRemove.add(viewerId);
                continue;
            }

            Component newText = buildBossBarText(snap.baseText(), snap.targetName(), distance, direction);
            snap.bossBar().name(newText);

            if (configManager.isBossBarProgressEnabled()) {
                float progress = calculateProgress(distance, elapsed, snap.nodist(), snap.notime(), isTimeMode);
                snap.bossBar().progress(Math.max(0.0f, Math.min(1.0f, progress)));
            }
        }

        if (!toRemove.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (UUID uuid : toRemove) {
                    BossBarData data = activeBossBars.remove(uuid);
                    if (data != null) {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null && player.isOnline()) {
                            player.hideBossBar(data.bossBar());
                        }
                    }
                }
            });
        }
    }

    public void activateBossBar(Player viewer, Player target, boolean nodist, boolean notime) {
        if (!configManager.isBossBarEnabled() || viewer == null || target == null) return;

        if (viewer.getUniqueId().equals(target.getUniqueId())) {
            configManager.executeActions(viewer, "bossbar.self-target", Map.of("player", target.getName()));
            return;
        }

        if (activeBossBars.containsKey(viewer.getUniqueId())) {
            configManager.executeActions(viewer, "bossbar.already-active", Map.of("player", target.getName()));
            return;
        }

        Component initialText = HexColors.translateToComponent(configManager.getBossBarText()
                .replace("{player}", target.getName())
                .replace("{distance}", "???")
                .replace("{direction}", "⬆")
                .replace("{blocks-word-format}", configManager.getBlockDeclension(1)));

        BossBar bossBar = BossBar.bossBar(
                initialText,
                1.0f,
                configManager.getBossBarColorEnum(),
                configManager.getBossBarOverlayEnum()
        );

        viewer.showBossBar(bossBar);

        BossBarData data = new BossBarData(bossBar, target.getUniqueId(), System.currentTimeMillis(), nodist, notime);
        activeBossBars.put(viewer.getUniqueId(), data);

        configManager.executeActions(viewer, "bossbar.activated", Map.of("player", target.getName()));
        plugin.log("Активирован боссбар для игрока &#ffff00" + viewer.getName() + " на цель &#ffff00" + target.getName());
    }

    public void removeBossBar(Player viewer) {
        if (viewer == null) return;
        BossBarData data = activeBossBars.remove(viewer.getUniqueId());
        if (data != null && viewer.isOnline()) {
            viewer.hideBossBar(data.bossBar());
            plugin.log("Боссбар для игрока &#ffff00" + viewer.getName() + " принудительно удален!");
        }
    }

    private Component buildBossBarText(String baseText, String targetName, double distance, String direction) {
        String text = baseText
                .replace("{player}", targetName)
                .replace("{distance}", configManager.formatNumber((int) distance))
                .replace("{direction}", direction)
                .replace("{blocks-word-format}", configManager.getBlockDeclension((int) distance));

        return HexColors.translateToComponent(text);
    }

    public void activateBossBarFromConsole(Player target, boolean nodist, boolean notime) {
        if (!configManager.isBossBarEnabled() || target == null) return;

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.hasPermission("nearmanager.bossbar")) {
                activateBossBar(viewer, target, nodist, notime);
                break;
            }
        }
    }

    public boolean hasActiveBossBar(Player player) {
        return activeBossBars.containsKey(player.getUniqueId());
    }

    public void removeAll() {
        for (Map.Entry<UUID, BossBarData> entry : activeBossBars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                player.hideBossBar(entry.getValue().bossBar());
            }
        }
        activeBossBars.clear();
    }

    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        removeAll();
    }

    public void restart() {
        shutdown();
        if (configManager.isBossBarEnabled()) {
            startGlobalUpdateTask();
        }
    }

    public UUID getTargetId(Player viewer) {
        if (viewer == null) return null;
        BossBarData data = activeBossBars.get(viewer.getUniqueId());
        return data != null ? data.targetId() : null;
    }

    private float calculateProgress(double distance, long elapsed, boolean nodist, boolean notime, boolean isTimeMode) {
        if (nodist && !isTimeMode) return 1.0f;
        if (notime && isTimeMode) return 1.0f;

        if (isTimeMode) {
            int timeout = configManager.getBossBarTimeout();
            if (timeout <= 0) return 1.0f;
            return Math.max(0f, 1.0f - ((float) elapsed / timeout));
        }

        int minDist = configManager.getBossBarMinDistance();
        int maxDist = configManager.getBossBarMaxDistance();
        if (maxDist <= minDist) return 1.0f;

        if (configManager.useMinDistanceAsZero()) {
            if (distance <= minDist) return (float) configManager.getBossBarMaxProgress() / 100f;
            if (distance >= maxDist) return (float) configManager.getBossBarMinProgress() / 100f;
            double range = maxDist - minDist;
            float minProg = (float) configManager.getBossBarMinProgress() / 100f;
            float maxProg = (float) configManager.getBossBarMaxProgress() / 100f;
            return minProg + (maxProg - minProg) * (1.0f - (float) ((distance - minDist) / range));
        }
        return (float) (configManager.getBossBarMaxProgress() / 100f * (1 - distance / maxDist));
    }
}