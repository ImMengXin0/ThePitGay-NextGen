package net.mizukilab.pit.util;

import cn.charlotte.pit.ThePit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/** Capture Bukkit state before submitting work; completion always runs on the server thread. */
public final class DatabaseTasks {
    private DatabaseTasks() {}

    public static <T> void submit(Player player, Supplier<T> query, Consumer<T> completion) {
        ThePit plugin = ThePit.getInstance();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                T result = query.get();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) completion.accept(result);
                });
            } catch (Exception failure) {
                plugin.getLogger().log(Level.SEVERE, "Saved item database operation failed", failure);
                if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) player.sendMessage("§c数据库操作失败，请稍后重试。");
                });
            }
        });
    }
}
