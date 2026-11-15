package net.mizukilab.pit.util.chat;

import cn.charlotte.pit.ThePit;
import net.mizukilab.pit.actionbar.IActionBarManager;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/1 12:37
 */
public class ActionBarUtil {

    @Deprecated
    public static void sendActionBar(Player player, String message) {
        if (sendActionBar1(player, "default", message, 1)) {
            sendActionBar0(player, message);
        }
    }

    public static boolean sendActionBar1(Player player, String channel, String message, int repeat) {
        IActionBarManager actionBarManager = ThePit.getInstance().getActionBarManager();
        if (actionBarManager != null) {
            actionBarManager.addActionBarOnQueue(player, channel, message, repeat);
            return false;
        }
        return true;
    }

    public static void sendActionBar0(Player player, String message) {
        player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .deserialize(CC.translate(message)));
    }


}
