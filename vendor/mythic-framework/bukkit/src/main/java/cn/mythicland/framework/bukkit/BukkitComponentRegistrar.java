package cn.mythicland.framework.bukkit;

import cn.mythicland.framework.command.BukkitCommandRegistrar;
import cn.mythicland.framework.component.ComponentResolver;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class BukkitComponentRegistrar {
    private BukkitComponentRegistrar() {
    }

    public static BukkitRegistration register(JavaPlugin plugin, ComponentResolver components) {
        BukkitRegistration registration = registerListeners(plugin, components);
        try {
            registration.combine(BukkitCommandRegistrar.register(plugin, components));
            return registration;
        } catch (RuntimeException | Error exception) {
            try {
                registration.close();
            } catch (RuntimeException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw exception;
        }
    }

    public static BukkitRegistration registerListeners(JavaPlugin plugin, ComponentResolver components) {
        BukkitRegistration registration = new BukkitRegistration();
        try {
            for (Listener listener : components.resolveAll(ListenerComponent.class, Listener.class)) {
                Bukkit.getPluginManager().registerEvents(listener, plugin);
                registration.addUndoAction(() -> HandlerList.unregisterAll(listener));
            }
            return registration;
        } catch (RuntimeException | Error exception) {
            try {
                registration.close();
            } catch (RuntimeException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw exception;
        }
    }
}
