package com.thebetterfolia;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class MessageUtil {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private MessageUtil() {}

    public static Component parse(String template, String... replacements) {
        String text = template;
        for (int i = 0; i < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        if (text.contains("\u00A7")) {
            return LEGACY.deserialize(text);
        }
        return MINI.deserialize(text);
    }
}
