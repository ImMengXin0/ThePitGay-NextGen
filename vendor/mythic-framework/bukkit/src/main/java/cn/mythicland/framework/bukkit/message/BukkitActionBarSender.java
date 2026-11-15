package cn.mythicland.framework.bukkit.message;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

public final class BukkitActionBarSender {
    public void send(Player player, String message) {
        if (player == null || message == null) return;
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                org.bukkit.ChatColor.translateAlternateColorCodes('&', message)));
    }
}
