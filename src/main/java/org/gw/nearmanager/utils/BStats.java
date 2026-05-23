package org.gw.nearmanager.utils;

import org.gw.nearmanager.NearManager;
import org.bstats.bukkit.Metrics;

public class BStats {

    public BStats(NearManager plugin) {
        if (plugin == null) return;
        if (!plugin.getConfigManager().isBStatsEnabled()) return;

        try {
            new Metrics(plugin, 31103);
        } catch (Exception e) {
            plugin.error("Ошибка инициализации метрик bStats: " + e.getMessage());
        }
    }
}