package org.gw.nearmanager.managers;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.gw.nearmanager.NearManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RadiusManager {

    private final NearManager plugin = NearManager.getPlugin(NearManager.class);
    private final ConfigManager configManager;
    private final LuckPerms luckPerms;
    private final Map<UUID, Integer> radiusCache = new ConcurrentHashMap<>();

    public RadiusManager(ConfigManager configManager) {
        this.configManager = configManager;
        LuckPerms lp = null;
        try {
            lp = Bukkit.getServicesManager().load(LuckPerms.class);
            if (lp != null) {
                plugin.log("Api плагина LuckPerms успешно подключена!");
            }
        } catch (Exception e) {
            plugin.log("Api плагина LuckPerms не найдена, расширенные группы радиусов отключены...");
        }
        this.luckPerms = lp;
    }

    public int getRadius(Player player) {
        if (player == null) {
            return configManager.getDefaultRadius();
        }

        UUID uuid = player.getUniqueId();
        Integer cached = radiusCache.get(uuid);
        if (cached != null) {
            return cached;
        }

        int radius = getMaxRadiusFromPermissions(player);
        if (radius > 0) {
            radiusCache.put(uuid, radius);
            return radius;
        }

        if (configManager.isNearRadiusGroupsEnabled() && luckPerms != null) {
            radius = getMaxRadiusFromLuckPerms(player);
            if (radius > 0) {
                radiusCache.put(uuid, radius);
                return radius;
            }
        }

        radius = configManager.getDefaultRadius();
        radiusCache.put(uuid, radius);
        return radius;
    }

    private int getMaxRadiusFromPermissions(Player player) {
        int max = -1;
        for (PermissionAttachmentInfo pai : player.getEffectivePermissions()) {
            if (pai.getValue() && pai.getPermission().startsWith("nearmanager.near-radius.")) {
                try {
                    int value = Integer.parseInt(pai.getPermission().substring("nearmanager.near-radius.".length()));
                    if (value > max) {
                        max = value;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return max;
    }

    private int getMaxRadiusFromLuckPerms(Player player) {
        if (luckPerms == null) return -1;

        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) return -1;

            Map<String, Integer> groupRadii = configManager.getNearRadiusGroups();
            int max = -1;

            for (var group : user.getInheritedGroups(QueryOptions.defaultContextualOptions())) {
                Integer radius = groupRadii.get(group.getName());
                if (radius != null && radius > max) {
                    max = radius;
                }
            }
            return max;
        } catch (Exception e) {
            plugin.error("Ошибка получения групп из &#fb8808LuckPerms &fдля игрока &#fb8808" + player.getName() + ": " + e.getMessage());
            return -1;
        }
    }

    public void clearAllCache() {
        radiusCache.clear();
        plugin.log("Кэш вычислителя радиусов игроков &#FFFF00успешно &fочищен!");
    }

    public void invalidate(UUID uuid) {
        if (uuid != null) {
            radiusCache.remove(uuid);
        }
    }
}