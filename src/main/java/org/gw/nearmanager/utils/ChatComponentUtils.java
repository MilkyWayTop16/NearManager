package org.gw.nearmanager.utils;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatComponentUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("(&[0-9a-fk-or])|(&#[A-Fa-f0-9]{6})");

    public static TextComponent createClickableComponent(String text, String hoverText, String clickCommand) {
        // Создаем компонент для текста кнопки
        TextComponent component = parseColoredText(text);

        // Устанавливаем hover-текст, если он не пустой
        if (hoverText != null && !hoverText.isEmpty()) {
            TextComponent hoverComponent = parseColoredText(hoverText);
            BaseComponent[] hoverComponents = hoverComponent.getExtra() != null && !hoverComponent.getExtra().isEmpty()
                    ? hoverComponent.getExtra().toArray(new BaseComponent[0])
                    : new BaseComponent[]{hoverComponent};
            component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new Text(hoverComponents)));
        }

        // Устанавливаем команду для клика, если она не пустая
        if (clickCommand != null && !clickCommand.isEmpty()) {
            component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, clickCommand));
        }

        return component;
    }

    public static TextComponent parseColoredText(String message) {
        if (message == null || message.isEmpty()) {
            return new TextComponent("");
        }

        // Заменяем \n на перенос строки
        message = message.replace("\\n", "\n");

        // Разбиваем строку на сегменты по цветовым кодам
        List<TextComponent> components = new ArrayList<>();
        Matcher matcher = COLOR_CODE_PATTERN.matcher(message);
        int lastEnd = 0;
        ChatColor currentColor = null;

        while (matcher.find()) {
            int start = matcher.start();
            String group = matcher.group();

            // Добавляем текст до цветового кода
            if (start > lastEnd) {
                TextComponent component = new TextComponent(message.substring(lastEnd, start));
                if (currentColor != null) {
                    component.setColor(currentColor);
                }
                components.add(component);
            }

            // Обрабатываем цветовой код
            if (group.startsWith("&#")) {
                String hexCode = group.substring(2);
                currentColor = ChatColor.of("#" + hexCode);
            } else if (group.startsWith("&")) {
                char code = group.charAt(1);
                currentColor = ChatColor.getByChar(code);
            }

            lastEnd = matcher.end();
        }

        // Добавляем оставшийся текст
        if (lastEnd < message.length()) {
            TextComponent component = new TextComponent(message.substring(lastEnd));
            if (currentColor != null) {
                component.setColor(currentColor);
            }
            components.add(component);
        }

        // Объединяем компоненты
        TextComponent result = new TextComponent();
        for (TextComponent component : components) {
            result.addExtra(component);
        }

        return result;
    }
}