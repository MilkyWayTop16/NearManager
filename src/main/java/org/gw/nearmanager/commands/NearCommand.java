package org.gw.nearmanager.commands;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.gw.nearmanager.NearManager;
import org.gw.nearmanager.managers.ConfigManager;
import org.gw.nearmanager.managers.SoundsManager;
import org.gw.nearmanager.utils.ChatComponentUtils;

import java.util.*;
import java.util.stream.Collectors;

public class NearCommand implements CommandExecutor {
    private static final String PERM_NEAR = "nearmanager.near";
    private static final String PERM_BYPASS_COOLDOWN = "nearmanager.near.bypass-cooldown";
    private static final String PERM_BYPASS_VANISH = "nearmanager.near.bypass-vanish";
    private static final String PERM_CUSTOM_RADIUS = "nearmanager.near.custom-radius";
    private static final String PERM_RADIUS_PREFIX = "nearmanager.near-radius.";

    private final NearManager plugin;
    private final ConfigManager configManager;
    private final SoundsManager soundsManager;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public NearCommand(NearManager plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.soundsManager = plugin.getSoundsManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sendMessage(sender, "plugin-messages.no-console", "", null);
            return true;
        }

        Player player = (Player) sender;
        if (!checkPermissions(player, label)) {
            return true;
        }

        if (!handleCooldown(player)) {
            return true;
        }

        double radius = getRadius(player, args);
        if (radius < 0) {
            return true;
        }

        List<PlayerDistance> nearbyPlayers = calculateNearbyPlayers(player, radius);
        if (nearbyPlayers.isEmpty()) {
            sendMessage(player, "near-messages.no-players", "", null);
            return true;
        }

        if (!checkMaxPlayers(player, nearbyPlayers)) {
            return true;
        }

        sendNearMessages(player, nearbyPlayers, radius);
        return true;
    }

    private boolean checkPermissions(Player player, String label) {
        LuckPerms luckPerms = LuckPermsProvider.get();
        User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
        boolean hasNearPermission = user.getCachedData().getPermissionData().checkPermission(PERM_NEAR).asBoolean() || player.isOp();
        if (!hasNearPermission) {
            plugin.getLogger().warning("Player " + player.getName() + " attempted /" + label + " without nearmanager.near permission or OP status.");
            sendMessage(player, "plugin-messages.no-permission", "", null);
            return false;
        }
        return true;
    }

    private boolean handleCooldown(Player player) {
        boolean cooldownEnabled = configManager.getBoolean("near-cooldown.enabled", true);
        if (cooldownEnabled && !player.hasPermission(PERM_BYPASS_COOLDOWN)) {
            long cooldownTime = configManager.getInt("near-cooldown.time", 5) * 1000L;
            long currentTime = System.currentTimeMillis();
            Long lastUsed = cooldowns.get(player.getUniqueId());

            if (lastUsed != null && currentTime - lastUsed < cooldownTime) {
                long timeLeft = (cooldownTime - (currentTime - lastUsed)) / 1000;
                sendMessage(player, "near-messages.cooldown", "", Collections.singletonMap("{time}", String.valueOf(timeLeft)));
                return false;
            }
            cooldowns.put(player.getUniqueId(), currentTime);
        }
        return true;
    }

    private double getRadius(Player player, String[] args) {
        double maxRadius = getMaxRadius(player);
        boolean customRadiusEnabled = configManager.getBoolean("custom-radius-selection.enabled", false);
        double minRadius = configManager.getDouble("custom-radius-selection.min-radius", 0.0);
        double configMaxRadius = configManager.getDouble("custom-radius-selection.max-radius", 100000.0);

        if (customRadiusEnabled && args.length > 0 && (player.hasPermission(PERM_CUSTOM_RADIUS) || player.isOp())) {
            try {
                double specifiedRadius = Double.parseDouble(args[0]);
                if (specifiedRadius < minRadius) {
                    sendMessage(player, "near-messages.invalid-min-radius", "", Collections.singletonMap("{min-radius}", String.valueOf((int) minRadius)));
                    return -1;
                }
                if (player.isOp() || player.hasPermission(PERM_CUSTOM_RADIUS)) {
                    if (specifiedRadius > configMaxRadius) {
                        sendMessage(player, "near-messages.invalid-max-radius", "", Collections.singletonMap("{max-radius}", String.valueOf((int) configMaxRadius)));
                        return -1;
                    }
                } else if (specifiedRadius > maxRadius) {
                    sendMessage(player, "near-messages.invalid-max-radius", "", Collections.singletonMap("{max-radius}", String.valueOf((int) maxRadius)));
                    return -1;
                }
                return specifiedRadius;
            } catch (NumberFormatException e) {
                sendMessage(player, "near-messages.invalid-radius", "", null);
                return -1;
            }
        }
        return maxRadius;
    }

    private double getMaxRadius(Player player) {
        double cachedRadius = plugin.getPlayerRadius(player.getUniqueId());
        if (cachedRadius >= 0) {
            return cachedRadius;
        }

        if (configManager.isNearRadiusGroupsEnabled()) {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
            Map<String, Object> groupRadiuses = configManager.getNearRadiusGroups();
            String primaryGroup = user.getPrimaryGroup();
            if (groupRadiuses.containsKey(primaryGroup)) {
                try {
                    return Double.parseDouble(groupRadiuses.get(primaryGroup).toString());
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return configManager.getDouble("default-radius-without-permission.radius", 100.0);
    }

    private List<PlayerDistance> calculateNearbyPlayers(Player player, double radius) {
        boolean hideVanished = configManager.getBoolean("hide-vanished-players.enabled", true);
        boolean ignorePlayersEnabled = configManager.getBoolean("ignored-players.enabled", true);
        List<String> ignoredPlayers = configManager.getStringList("ignored-players.players");

        return player.getWorld().getPlayers().stream()
                .filter(p -> p != player && player.getLocation().distance(p.getLocation()) <= radius)
                .filter(p -> !hideVanished || !isVanished(p, player))
                .filter(p -> !ignorePlayersEnabled || !ignoredPlayers.contains(p.getName()))
                .map(p -> new PlayerDistance(p, (int) Math.round(player.getLocation().distance(p.getLocation())), getDirection(player, p)))
                .collect(Collectors.toList());
    }

    private boolean isVanished(Player target, Player player) {
        return (!player.canSee(target) || target.hasPotionEffect(PotionEffectType.INVISIBILITY)) && !player.hasPermission(PERM_BYPASS_VANISH);
    }

    private boolean checkMaxPlayers(Player player, List<PlayerDistance> nearbyPlayers) {
        boolean maxPlayersEnabled = configManager.getBoolean("max-players-in-radius.enabled", true);
        if (maxPlayersEnabled) {
            int maxPlayers = configManager.getInt("max-players-in-radius.max", 10);
            if (nearbyPlayers.size() > maxPlayers) {
                sendMessage(player, "near-messages.too-many-players", "", Collections.singletonMap("{max-players}", String.valueOf(maxPlayers)));
                return false;
            }
        }
        return true;
    }

    private void sendNearMessages(Player player, List<PlayerDistance> nearbyPlayers, double radius) {
        List<String> nearMessages = configManager.getMessageList("near-messages.near");
        if (nearMessages.isEmpty()) {
            nearMessages.add(configManager.getMessage("near-messages.near", ""));
        }

        List<TextComponent> playerMessages = buildPlayerMessages(player, nearbyPlayers);
        String prefix = nearMessages.stream()
                .filter(line -> line.contains("{near-players}"))
                .findFirst()
                .map(line -> line.indexOf("{near-players}") > 0 && line.charAt(line.indexOf("{near-players}") - 1) == ' ' ? " " : "")
                .orElse("");

        for (String line : nearMessages) {
            if (line.contains("{near-players}")) {
                for (TextComponent playerMessage : playerMessages) {
                    TextComponent prefixedMessage = new TextComponent(prefix);
                    prefixedMessage.addExtra(playerMessage);
                    player.spigot().sendMessage(prefixedMessage);
                    soundsManager.playSound(player, "near-messages.near-players");
                }
            } else {
                player.spigot().sendMessage(ChatComponentUtils.parseColoredText(line.replace("{radius}", String.valueOf((int) radius))));
                soundsManager.playSound(player, "near-messages.near");
            }
        }
    }

    private List<TextComponent> buildPlayerMessages(Player player, List<PlayerDistance> nearbyPlayers) {
        String nearPlayerFormat = configManager.getMessage("near-messages.near-players", "");
        Map<String, ButtonConfig> buttons = new HashMap<>();
        buttons.put("{open-inventory-button}", new ButtonConfig(
                "nearmanager.near-buttons.open-inventory",
                configManager.getMessage("near-messages.open-inventory-button.text", "&f| &#FFFF00[Инв]"),
                configManager.getMessage("near-messages.open-inventory-button.hover-text", "&#FFFF00◆ &fНажмите, чтобы &#FFFF00открыть &fинвентарь игрока"),
                "/invsee {player}"
        ));
        buttons.put("{teleport-button}", new ButtonConfig(
                "nearmanager.near-buttons.teleport",
                configManager.getMessage("near-messages.teleport-button.text", "&#FFFF00[Тп]"),
                configManager.getMessage("near-messages.teleport-button.hover-text", "&#FFFF00◆ &fНажмите, чтобы &#FFFF00телепортироваться &fк игроку"),
                "/tp {player}"
        ));
        buttons.put("{bossbar-button}", new ButtonConfig(
                "nearmanager.near-buttons.bossbar",
                configManager.getMessage("near-messages.bossbar-button.text", "&#FFFF00[Боссбар]"),
                configManager.getMessage("near-messages.bossbar-button.hover-text", "&#FFFF00◆ &fНажмите, чтобы &#FFFF00переключить &fбоссбар"),
                "/nm bossbar {player}"
        ));

        List<TextComponent> messages = new ArrayList<>();
        int number = 1;
        for (PlayerDistance pd : nearbyPlayers) {
            String message = nearPlayerFormat
                    .replace("{number}", String.valueOf(number))
                    .replace("{player}", pd.player.getName())
                    .replace("{blocks}", String.valueOf(pd.distance))
                    .replace("{direction}", pd.direction);

            TextComponent component = buildMessageWithButtons(player, message, buttons, pd.player.getName());
            messages.add(component);
            number++;
        }
        return messages;
    }

    private TextComponent buildMessageWithButtons(Player player, String message, Map<String, ButtonConfig> buttons, String targetName) {
        String[] parts = message.split("\n", -1);
        TextComponent fullComponent = new TextComponent();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            TextComponent partComponent = new TextComponent();
            StringBuilder textBuilder = new StringBuilder();
            int lastIndex = 0;

            while (lastIndex < part.length()) {
                int nextIndex = part.length();
                String placeholder = null;
                ButtonConfig button = null;

                for (Map.Entry<String, ButtonConfig> entry : buttons.entrySet()) {
                    int index = part.indexOf(entry.getKey(), lastIndex);
                    if (index != -1 && index < nextIndex) {
                        nextIndex = index;
                        placeholder = entry.getKey();
                        button = entry.getValue();
                    }
                }

                if (nextIndex == part.length()) {
                    textBuilder.append(part.substring(lastIndex));
                    break;
                }

                textBuilder.append(part.substring(lastIndex, nextIndex));
                lastIndex = nextIndex + placeholder.length();

                if (textBuilder.length() > 0) {
                    partComponent.addExtra(ChatComponentUtils.parseColoredText(textBuilder.toString()));
                    textBuilder.setLength(0);
                }

                if (button != null && player.hasPermission(button.permission)) {
                    String command = button.command.replace("{player}", targetName);
                    TextComponent buttonComponent = ChatComponentUtils.createClickableComponent(button.text, button.hoverText, command);
                    partComponent.addExtra(buttonComponent);
                }
            }

            if (textBuilder.length() > 0) {
                partComponent.addExtra(ChatComponentUtils.parseColoredText(textBuilder.toString()));
            }

            fullComponent.addExtra(partComponent);
            if (i < parts.length - 1) {
                fullComponent.addExtra(new TextComponent("\n"));
            }
        }

        return fullComponent;
    }

    private void sendMessage(CommandSender sender, String path, String def, Map<String, String> placeholders) {
        String message = configManager.getMessage(path, def);
        if (!message.isEmpty()) {
            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    message = message.replace(entry.getKey(), entry.getValue());
                }
            }
            sender.sendMessage(ChatComponentUtils.parseColoredText(message));
            soundsManager.playSound(sender instanceof Player ? (Player) sender : null, path);
        }
    }

    private String getDirection(Player player, Player target) {
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

    private static class PlayerDistance {
        final Player player;
        final int distance;
        final String direction;

        PlayerDistance(Player player, int distance, String direction) {
            this.player = player;
            this.distance = distance;
            this.direction = direction;
        }
    }

    private static class ButtonConfig {
        final String permission;
        final String text;
        final String hoverText;
        final String command;

        ButtonConfig(String permission, String text, String hoverText, String command) {
            this.permission = permission;
            this.text = text;
            this.hoverText = hoverText;
            this.command = command;
        }
    }
}