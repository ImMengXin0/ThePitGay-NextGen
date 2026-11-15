package cn.mythicland.framework.bukkit.message;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class BukkitTitleSender {
    public void send(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player == null) return;
        player.sendTitle(title == null ? null : ChatColor.translateAlternateColorCodes('&', title),
                subtitle == null ? null : ChatColor.translateAlternateColorCodes('&', subtitle), fadeIn, stay, fadeOut);
    }
}
