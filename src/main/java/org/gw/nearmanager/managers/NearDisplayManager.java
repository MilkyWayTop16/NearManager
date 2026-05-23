package org.gw.nearmanager.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.gw.nearmanager.utils.DirectionUtils;
import org.gw.nearmanager.utils.HexColors;

import java.util.ArrayList;
import java.util.List;

public final class NearDisplayManager {

    private final ConfigManager configManager;
    private final List<ButtonDefinition> buttonOrder = new ArrayList<>();

    public NearDisplayManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    private void buildButtonOrder() {
        String template = configManager.getConfig().getString("actions.near.near-players", "");
        record ButtonPos(int index, String key) {}
        List<ButtonPos> positions = new ArrayList<>();

        int invIdx = template.indexOf("{open-inventory-button}");
        if (invIdx != -1) positions.add(new ButtonPos(invIdx, "inv"));

        int tpIdx = template.indexOf("{teleport-button}");
        if (tpIdx != -1) positions.add(new ButtonPos(tpIdx, "tp"));

        int bbIdx = template.indexOf("{bossbar-button}");
        if (bbIdx != -1) positions.add(new ButtonPos(bbIdx, "bb"));

        positions.sort((a, b) -> Integer.compare(a.index, b.index));

        buttonOrder.clear();
        for (ButtonPos pos : positions) {
            buttonOrder.add(new ButtonDefinition(pos.key));
        }
    }

    public void sendNearList(Player viewer, List<NearPlayerManager.PlayerDistance> nearby) {
        buildButtonOrder();
        for (int i = 0; i < nearby.size(); i++) {
            NearPlayerManager.PlayerDistance pd = nearby.get(i);
            Player target = pd.getPlayer();
            if (target == null) continue;

            int dist = (int) pd.distance();
            String direction = configManager.getDirection(DirectionUtils.getDirectionKey(
                    viewer.getLocation().getX(),
                    viewer.getLocation().getZ(),
                    viewer.getLocation().getYaw(),
                    target.getLocation().getX(),
                    target.getLocation().getZ()
            ));

            String filled = configManager.getConfig().getString("actions.near.near-players",
                            "&#FFFF00◆ &f{number}. &#FFFF00{player} &f- в &#FFFF00{blocks} &f{blocks-word-format} &#FFFF00({direction})")
                    .replace("{number}", String.valueOf(i + 1))
                    .replace("{player}", pd.name())
                    .replace("{blocks}", configManager.formatNumber(dist))
                    .replace("{direction}", direction)
                    .replace("{blocks-word-format}", configManager.getBlockDeclension(dist))
                    .replace("{open-inventory-button}", "")
                    .replace("{teleport-button}", "")
                    .replace("{bossbar-button}", "")
                    .replaceAll(" {2,}", " ");

            Component line = HexColors.translateToComponent("  " + filled);

            for (ButtonDefinition def : buttonOrder) {
                Component button = createButton(def.key, viewer, target);
                if (button != null) {
                    line = line.append(Component.text(" ")).append(button);
                }
            }

            viewer.sendMessage(line);
        }
    }

    private Component createButton(String key, Player viewer, Player target) {
        ConfigurationSection sec = switch (key) {
            case "inv" -> configManager.getConfig().getConfigurationSection("actions.near.open-inventory-button");
            case "tp" -> configManager.getConfig().getConfigurationSection("actions.near.teleport-button");
            case "bb" -> configManager.getConfig().getConfigurationSection("actions.near.bossbar-button");
            default -> null;
        };

        if (sec == null) return null;

        String permission = sec.getString("permission");
        if (permission != null && !viewer.hasPermission(permission)) return null;

        String text = sec.getString("text", "");
        String hover = sec.getString("hover-text", "");
        String cmd = sec.getString("click-command", "").replace("{player}", target.getName());

        return HexColors.translateToComponent(text)
                .hoverEvent(HoverEvent.showText(HexColors.translateToComponent(hover)))
                .clickEvent(ClickEvent.runCommand(cmd));
    }

    private record ButtonDefinition(String key) {}
}