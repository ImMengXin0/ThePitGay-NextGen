package cn.mythicland.framework.bukkit.command;

import cn.mythicland.framework.text.TextUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CommandFeedback {
    private CommandFeedback() {
    }

    public static void send(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) return;
        sender.sendMessage(TextUtil.translate(message));
    }

    public static void send(Player player, String message) {
        send((CommandSender) player, message);
    }
}
