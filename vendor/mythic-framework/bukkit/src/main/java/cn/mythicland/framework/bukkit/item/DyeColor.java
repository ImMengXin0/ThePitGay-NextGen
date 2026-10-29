package cn.mythicland.framework.bukkit.item;


import org.bukkit.ChatColor;
import org.bukkit.Color;

public enum DyeColor {

    WHITE(ChatColor.WHITE, Color.WHITE),
    FUCHSIA(ChatColor.LIGHT_PURPLE, Color.FUCHSIA),
    LIME(ChatColor.GREEN, Color.LIME),
    MAROON(ChatColor.DARK_RED, Color.MAROON),
    NAVY(ChatColor.DARK_BLUE, Color.NAVY),
    TEAL(ChatColor.DARK_AQUA, Color.TEAL),
    OLIVE(ChatColor.GREEN, Color.OLIVE),
    SILVER(ChatColor.WHITE, Color.SILVER),
    GRAY(ChatColor.GRAY, Color.GRAY),
    PURPLE(ChatColor.WHITE, Color.PURPLE),
    AQUA(ChatColor.AQUA, Color.AQUA),
    BLUE(ChatColor.BLUE, Color.BLUE),
    GREEN(ChatColor.DARK_GREEN, Color.GREEN),
    YELLOW(ChatColor.YELLOW, Color.YELLOW),
    ORANGE(ChatColor.GOLD, Color.ORANGE),
    RED(ChatColor.RED, Color.RED);

    private final ChatColor chatColor;
    private final Color color;

    DyeColor(ChatColor chatColor, Color color) {

        this.chatColor = chatColor;
        this.color = color;
    }

    public ChatColor getChatColor() {
        return this.chatColor;
    }

    public Color getColor() {
        return this.color;
    }
}
