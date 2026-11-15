package net.mizukilab.pit.util.chat;

import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/1 13:26
 */
public class TitleUtil {


    public static void sendTitle(Player player, String title, String sub, int fadeIn, int fadeOut, int duration) {
        player.sendTitle(title == null ? null : CC.translate(title), sub == null ? null : CC.translate(sub), fadeIn, duration, fadeOut);
    }
}
