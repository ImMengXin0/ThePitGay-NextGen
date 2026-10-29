package cn.mythicland.framework.bukkit.task;

import cn.mythicland.framework.component.DefaultImplementation;
import cn.mythicland.framework.component.InjectComponent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

@InjectComponent
@DefaultImplementation
public final class BukkitTaskScheduler implements TaskScheduler {
    private final JavaPlugin plugin;

    public BukkitTaskScheduler(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void executeSync(Runnable task) {
        Bukkit.getScheduler().runTask(this.plugin, Objects.requireNonNull(task, "task"));
    }

    @Override
    public void executeSyncIfNeeded(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        this.executeSync(task);
    }

    @Override
    public ScheduledTaskHandle scheduleSyncRepeating(
            Runnable task,
            long delayTicks,
            long periodTicks
    ) {
        if (delayTicks < 0L || periodTicks <= 0L) {
            throw new IllegalArgumentException(
                    "Task ticks must be delay >= 0 and period > 0: " + delayTicks + "/" + periodTicks
            );
        }
        return new BukkitScheduledTask(
                Bukkit.getScheduler().runTaskTimer(
                        this.plugin,
                        Objects.requireNonNull(task, "task"),
                        delayTicks,
                        periodTicks
                )
        );
    }

    @Override
    public void executeSyncLater(Runnable task, long delayTicks) {
        if (delayTicks < 0L) throw new IllegalArgumentException("Delay ticks cannot be negative: " + delayTicks);
        Bukkit.getScheduler().runTaskLater(this.plugin, Objects.requireNonNull(task, "task"), delayTicks);
    }

    @Override
    public void executeAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, Objects.requireNonNull(task, "task"));
    }
}
