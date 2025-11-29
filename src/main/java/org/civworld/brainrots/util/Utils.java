package org.civworld.brainrots.util;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.civworld.brainrots.model.House;
import org.civworld.brainrots.model.Lobby;
import org.civworld.brainrots.type.Modificator;
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

    public static String formatNumber(double number) {
        if (number < 1000) {
            return (number % 1 == 0)
                    ? String.valueOf((long) number)
                    : String.valueOf(number);
        }

        final String[] units = {"K", "M", "B", "T"};
        double num = number;
        int unitIndex = -1;

        while (num >= 1000 && unitIndex < units.length - 1) {
            num /= 1000.0;
            unitIndex++;
        }

        String formatted = (num % 1 == 0)
                ? String.format("%.0f", num)
                : String.format("%.1f", num);

        return formatted + units[unitIndex];
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

    public static String colorFromModificator(Modificator modificator){
        return switch(modificator){
            case BRONZE -> "";
            case GOLD -> "&e";
            case DIAMOND -> "&b";
            case RAINBOW -> null;
            case LAVA -> "&6";
            case BLOODROT -> "&4";
            case CELESTIAL -> "&c";
            case CANDY -> "&a";
            case GALAXY -> "&0";
            case YIN_YANG -> "&7";
            case RADIOACTIVE -> "&2";
        };
    }

    public static void deleteHologram(Lobby lobby, House house, String need){
        DHAPI.removeHologram(lobby.getNum() + "_" + house.getId() + "_" + need);
    }

    public static Hologram createHologram(Location loc, String name){
        if(DHAPI.getHologram(name) != null) DHAPI.removeHologram(name);
        return DHAPI.createHologram(name, loc, false);
    }
}