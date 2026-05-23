package org.gw.nearmanager.managers;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.gw.nearmanager.NearManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NearPlayerManager {

    private static final Set<String> VANISH_METADATA_KEYS = Set.of(
            "vanished", "cmi_vanished", "pv_vanished",
            "advancedvanish_vanished", "supervanish_vanished",
            "vanish", "isVanished", "hidden", "isHidden"
    );

    private final NearManager plugin = NearManager.getPlugin(NearManager.class);
    private final ConfigManager configManager;
    private final RadiusManager radiusManager;
    private final NearCacheManager nearCacheManager;

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public NearPlayerManager(ConfigManager configManager, RadiusManager radiusManager) {
        this.configManager = configManager;
        this.radiusManager = radiusManager;
        this.nearCacheManager = new NearCacheManager();
    }

    public List<PlayerDistance> getNearbyPlayers(Player viewer, int radius) {
        if (viewer == null) return List.of();

        List<PlayerDistance> cached = nearCacheManager.getCached(viewer, radius);
        if (cached != null) return cached;

        List<PlayerDistance> result = new ArrayList<>();

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.getUniqueId().equals(viewer.getUniqueId())) continue;
            if (!target.getWorld().equals(viewer.getWorld())) continue;
            if (target.getGameMode() == GameMode.SPECTATOR) continue;
            if (target.hasMetadata("NPC")) continue;

            if (configManager.isHideVanished() && !viewer.hasPermission("nearmanager.near.bypass-vanish")) {
                if (isVanished(target)) continue;
            }

            if (configManager.isIgnoredPlayersEnabled() &&
                    configManager.getIgnoredPlayers().stream()
                            .anyMatch(name -> name.equalsIgnoreCase(target.getName()))) {
                continue;
            }

            double distSq = viewer.getLocation().distanceSquared(target.getLocation());
            if (distSq <= (long) radius * radius) {
                result.add(new PlayerDistance(target.getUniqueId(), target.getName(), Math.sqrt(distSq)));
            }
        }

        result.sort(Comparator.comparingDouble(PlayerDistance::distance));
        nearCacheManager.put(viewer, radius, result);

        return result;
    }

    public void invalidateCache(UUID uuid) {
        if (uuid != null) nearCacheManager.invalidate(uuid);
    }

    public void invalidateAllCache() {
        nearCacheManager.clear();
        plugin.log("Кэш результатов сканирования ближних игроков &#FFFF00успешно &fочищен!");
    }

    public void clearCooldowns() {
        cooldowns.clear();
        plugin.log("База данных активных задержек команд &#FFFF00успешно &fочищена!");
    }

    private boolean isVanished(Player player) {
        if (player == null) return false;

        if (player.isInvisible()) {
            return true;
        }

        for (String key : VANISH_METADATA_KEYS) {
            for (MetadataValue meta : player.getMetadata(key)) {
                if (meta.asBoolean()) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isOnCooldown(Player player) {
        if (!configManager.isNearCooldownEnabled() || player.hasPermission("nearmanager.near.bypass-cooldown")) return false;

        Long last = cooldowns.get(player.getUniqueId());
        return last != null && (System.currentTimeMillis() - last) < (configManager.getNearCooldownTime() * 1000L);
    }

    public long getRemainingCooldown(Player player) {
        Long last = cooldowns.get(player.getUniqueId());
        if (last == null) return 0L;
        return Math.max(0L, ((configManager.getNearCooldownTime() * 1000L) - (System.currentTimeMillis() - last)) / 1000L);
    }

    public void setCooldown(Player player) {
        if (configManager.isNearCooldownEnabled() && !player.hasPermission("nearmanager.near.bypass-cooldown")) {
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    public void clearCooldown(UUID uuid) {
        if (uuid != null) {
            cooldowns.remove(uuid);
            nearCacheManager.invalidate(uuid);
        }
    }

    public record PlayerDistance(UUID uuid, String name, double distance) {
        public Player getPlayer() {
            return Bukkit.getPlayer(uuid);
        }
    }
}