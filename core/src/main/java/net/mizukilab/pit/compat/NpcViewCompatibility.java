package net.mizukilab.pit.compat;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

/** Optional packet cleanup for NPCView; the core itself requires no NPC plugin. */
public final class NpcViewCompatibility implements Listener {
    private final Plugin plugin;
    private Runnable stopBridge;

    public NpcViewCompatibility(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        enableIfAvailable();
    }

    private void enableIfAvailable() {
        if (stopBridge != null || !Bukkit.getPluginManager().isPluginEnabled("NPCView")) return;
        NpcDeathAnimationListener bridge = new NpcDeathAnimationListener(plugin);
        bridge.start();
        stopBridge = bridge::close;
        plugin.getLogger().info("NPCView compatibility: NPC death animation filtering enabled.");
    }

    @EventHandler
    public void onEnable(PluginEnableEvent event) {
        if (isDependency(event.getPlugin())) enableIfAvailable();
    }

    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        if (isDependency(event.getPlugin())) stopBridge();
    }

    private boolean isDependency(Plugin dependency) {
        return dependency.getName().equals("NPCView");
    }

    private void stopBridge() {
        if (stopBridge == null) return;
        stopBridge.run();
        stopBridge = null;
    }

    public void close() {
        stopBridge();
        HandlerList.unregisterAll(this);
    }
}
