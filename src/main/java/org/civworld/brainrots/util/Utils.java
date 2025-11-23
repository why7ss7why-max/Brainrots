package org.civworld.brainrots.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.civworld.brainrots.type.Rarity;

public final class Utils {
    public static Component parse(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        String msg = text.replace("<prefix>", "<gray>[<blue>Бреинрот<gray>] <white>");
        return MiniMessage.miniMessage().deserialize("<!i>" + msg);
    }

    public static String capitalizeFirst(String text) {
        if (text == null || text.isEmpty()) return text;

        if(text.equals("BRAINROT_GOD")) return "Brainrot God";

        String[] words = text.toLowerCase().split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }

        return result.toString().trim();
    }

    public static String formatNumber(long number) {
        if (number < 1000) return String.valueOf(number);
        final String[] units = {"K", "M", "B", "T"};
        double num = number;
        int unitIndex = -1;
        while (num >= 1000 && unitIndex < units.length - 1) {
            num /= 1000.0;
            unitIndex++;
        }
        if (num % 1 == 0) {
            return String.format("%.0f%s", num, units[unitIndex]);
        } else {
            return String.format("%.1f%s", num, units[unitIndex]);
        }
    }

    public static String colorFromRarity(Rarity rarity){
        return switch(rarity){
            case COMMON -> "&2";
            case RARE -> "&b";
            case EPIC -> "&5";
            case LEGENDARY -> "&e";
            case MYTHIC -> "&#FF0000";
            case BRAINROT_GOD -> "&6";
            case SECRET -> "&0";
            case LIMITED -> "&d";
        };
    }
}