package cn.mythicland.framework.text;

import java.util.Objects;
import java.util.regex.Pattern;

public record PlayerNameStyle(String prefix) {
    private static final Pattern FORMATTING = Pattern.compile("(?i)§[0-9A-FK-OR]");

    public PlayerNameStyle {
        Objects.requireNonNull(prefix, "player name prefix");
    }

    public String nameColor() {
        for (int index = 0; index + 1 < this.prefix.length(); index++) {
            char marker = this.prefix.charAt(index);
            if (marker != '&' && marker != '§') continue;
            char code = Character.toLowerCase(this.prefix.charAt(index + 1));
            if ("0123456789abcdef".indexOf(code) >= 0) return "&" + code;
        }
        return "&7";
    }

    public String plainName(String playerName) {
        return playerName;
    }

    public String coloredName(String playerName) {
        return this.nameColor() + playerName;
    }

    public String prefixedName(String playerName) {
        if (this.prefix.isEmpty()) return this.coloredName(playerName);
        return this.prefix.stripTrailing() + " " + this.coloredName(playerName);
    }

    public String plainPrefixedName(String playerName) {
        return FORMATTING.matcher(TextUtil.translate(this.prefixedName(playerName))).replaceAll("");
    }
}
