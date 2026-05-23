package org.gw.nearmanager.managers;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NearCacheManager {

    private final Map<String, CachedNearResult> cache;

    private static final long CACHE_TTL = 1500L;
    private static final int MAX_CACHE_SIZE = 200;

    public NearCacheManager() {
        this.cache = new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedNearResult> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        };
    }

    public List<NearPlayerManager.PlayerDistance> getCached(Player viewer, int radius) {
        if (viewer == null) return null;

        String key = buildKey(viewer, radius);
        CachedNearResult cached = cache.get(key);

        if (cached != null) {
            if (System.currentTimeMillis() - cached.timestamp() < CACHE_TTL) {
                return cached.players();
            }
            cache.remove(key);
        }
        return null;
    }

    public void put(Player viewer, int radius, List<NearPlayerManager.PlayerDistance> players) {
        if (viewer == null || players == null || players.isEmpty()) return;

        String key = buildKey(viewer, radius);
        cache.put(key, new CachedNearResult(new ArrayList<>(players), System.currentTimeMillis()));
    }

    public void invalidate(UUID viewerId) {
        if (viewerId == null) return;

        String prefix = viewerId.toString() + ":";
        cache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public void clear() {
        cache.clear();
    }

    private String buildKey(Player viewer, int radius) {
        return viewer.getUniqueId() + ":" + radius + ":" + viewer.getWorld().getUID();
    }

    private record CachedNearResult(List<NearPlayerManager.PlayerDistance> players, long timestamp) {}
}