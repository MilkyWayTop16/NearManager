package org.gw.nearmanager.managers;

import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.gw.nearmanager.NearManager;

public class SoundsManager {
    private final NearManager plugin;
    private final FileConfiguration soundsConfig;

    public SoundsManager(NearManager plugin) {
        this.plugin = plugin;
        this.soundsConfig = plugin.getConfigManager().getSoundsConfig();
    }

    public void playSound(Player player, String soundPath) {
        if (player == null) return;
        if (soundsConfig.getBoolean("sounds." + soundPath + ".enabled", false)) {
            String soundName = soundsConfig.getString("sounds." + soundPath + ".sound", "");
            float volume = (float) soundsConfig.getDouble("sounds." + soundPath + ".volume", 1.0);
            float pitch = (float) soundsConfig.getDouble("sounds." + soundPath + ".pitch", 1.0);
            try {
                Sound sound = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid sound name in sounds.yml: " + soundName);
            }
        }
    }
}