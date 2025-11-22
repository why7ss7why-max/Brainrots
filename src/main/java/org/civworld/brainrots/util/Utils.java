package org.civworld.brainrots.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class Utils {
    public static Component parse(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        String msg = text.replace("<prefix>", "<gray>[<blue>Бреинрот<gray>] <white>");
        return MiniMessage.miniMessage().deserialize("<!i>" + msg);
    }
}