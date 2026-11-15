package cn.mythicland.framework.bukkit.bossbar;

import cn.mythicland.framework.bukkit.ListenerComponent;
import cn.mythicland.framework.component.InjectComponent;
import cn.mythicland.framework.component.ManagedLifecycle;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Kept under the original service name for extensions; Paper renders native boss bars. */
@InjectComponent
@ListenerComponent
public final class BukkitWitherBossBarService implements BossBarService, Listener, ManagedLifecycle {
    private final JavaPlugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    public BukkitWitherBossBarService(JavaPlugin plugin) { this.plugin = plugin; }
    private void onMain(Runnable action) {
        if (Bukkit.isPrimaryThread()) action.run();
        else if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, action);
    }
    @Override public void show(Player player, String title, double progress) {
        if (!Double.isFinite(progress)) throw new IllegalArgumentException("Nonfinite boss bar progress");
        onMain(() -> {
            if (!player.isOnline()) return;
            BossBar bar = bars.computeIfAbsent(player.getUniqueId(), id ->
                    Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SOLID));
            bar.setTitle(ChatColor.translateAlternateColorCodes('&', title));
            bar.setProgress(Math.clamp(progress, 0.0, 1.0));
            bar.addPlayer(player);
            bar.setVisible(true);
        });
    }
    @Override public void remove(Player player) {
        onMain(() -> { BossBar bar = bars.remove(player.getUniqueId()); if (bar != null) bar.removeAll(); });
    }
    @Override public void clear() { onMain(() -> { bars.values().forEach(BossBar::removeAll); bars.clear(); }); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { remove(event.getPlayer()); }
    @Override public void reload() { clear(); }
    @Override public void unload() { clear(); }
}
