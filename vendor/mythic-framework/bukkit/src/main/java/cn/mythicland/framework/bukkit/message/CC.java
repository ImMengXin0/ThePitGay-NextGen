package cn.mythicland.framework.bukkit.message;


import cn.mythicland.framework.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CC {
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("(?i)(?:&#([0-9a-f]{6})|<#([0-9a-f]{6})>)");
    private static final LegacyColor[] LEGACY_COLORS = {
            new LegacyColor(ChatColor.BLACK, 0x000000),
            new LegacyColor(ChatColor.DARK_BLUE, 0x0000AA),
            new LegacyColor(ChatColor.DARK_GREEN, 0x00AA00),
            new LegacyColor(ChatColor.DARK_AQUA, 0x00AAAA),
            new LegacyColor(ChatColor.DARK_RED, 0xAA0000),
            new LegacyColor(ChatColor.DARK_PURPLE, 0xAA00AA),
            new LegacyColor(ChatColor.GOLD, 0xFFAA00),
            new LegacyColor(ChatColor.GRAY, 0xAAAAAA),
            new LegacyColor(ChatColor.DARK_GRAY, 0x555555),
            new LegacyColor(ChatColor.BLUE, 0x5555FF),
            new LegacyColor(ChatColor.GREEN, 0x55FF55),
            new LegacyColor(ChatColor.AQUA, 0x55FFFF),
            new LegacyColor(ChatColor.RED, 0xFF5555),
            new LegacyColor(ChatColor.LIGHT_PURPLE, 0xFF55FF),
            new LegacyColor(ChatColor.YELLOW, 0xFFFF55),
            new LegacyColor(ChatColor.WHITE, 0xFFFFFF)
    };

    private CC() {
    }

    public static String translate(String msg) {
        if (msg == null || msg.isEmpty()) {
            return "";
        }

        String resolvedColors = replaceHexColors(msg);
        return TextUtil.translate(resolvedColors);
    }

    private static String replaceHexColors(String input) {
        Matcher matcher = HEX_COLOR_PATTERN.matcher(input);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(toColorCode(hex)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String toColorCode(String hex) {
        return supportsHexColors() ? toModernHexColor(hex) : nearestLegacyColor(hex).toString();
    }

    private static boolean supportsHexColors() {
        try {
            String version = Bukkit.getBukkitVersion();
            int releaseSeparator = version.indexOf('-');
            if (releaseSeparator >= 0) {
                version = version.substring(0, releaseSeparator);
            }
            String[] parts = version.split("\\.");
            if (parts.length < 2) {
                return true;
            }

            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            return major > 1 || minor >= 16;
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private static String toModernHexColor(String hex) {
        StringBuilder builder = new StringBuilder(14);
        builder.append(ChatColor.COLOR_CHAR).append('x');
        for (char character : hex.toCharArray()) {
            builder.append(ChatColor.COLOR_CHAR).append(character);
        }
        return builder.toString();
    }

    private static ChatColor nearestLegacyColor(String hex) {
        int rgb = Integer.parseInt(hex, 16);
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;

        LegacyColor bestMatch = LEGACY_COLORS[0];
        int bestDistance = Integer.MAX_VALUE;
        for (LegacyColor candidate : LEGACY_COLORS) {
            int candidateRed = (candidate.rgb() >> 16) & 0xFF;
            int candidateGreen = (candidate.rgb() >> 8) & 0xFF;
            int candidateBlue = candidate.rgb() & 0xFF;

            int redDelta = red - candidateRed;
            int greenDelta = green - candidateGreen;
            int blueDelta = blue - candidateBlue;
            int distance = redDelta * redDelta + greenDelta * greenDelta + blueDelta * blueDelta;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestMatch = candidate;
            }
        }
        return bestMatch.chatColor();
    }

    private record LegacyColor(ChatColor chatColor, int rgb) {
    }
}
