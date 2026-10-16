package cn.charlotte.pit.events;

import lombok.Getter;
import lombok.Setter;
import cn.charlotte.pit.ThePit;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/9 13:11
 */
public abstract class AbstractEvent {

    @Setter @Getter
    private volatile boolean isActive = false;
    private volatile long generation;
    @Getter private boolean aborting;
    private final List<BukkitTask> tasks = new ArrayList<>();

    public final long getGeneration() { return generation; }

    final void beginLifecycle() {
        cancelTasks();
        generation++;
        isActive = true;
        aborting = false;
    }

    final void markAborting() { aborting = true; }

    final void endLifecycle() {
        isActive = false;
        generation++;
        cancelTasks();
    }

    private void cancelTasks() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
    }

    protected final BukkitTask trackTask(BukkitTask task) {
        tasks.add(task);
        return task;
    }

    public final void runEventTask(Runnable action) {
        runEventTaskLater(action, 0);
    }

    public final void runEventTaskLater(Runnable action, long delay) {
        long current = generation;
        if (!isActive) return;
        // The callback may come from a world-edit worker; only the server thread owns tasks.
        Bukkit.getScheduler().runTaskLater(ThePit.getInstance(), () -> {
            if (isActive && generation == current) action.run();
        }, delay);
    }

    public abstract String getEventInternalName();

    public abstract String getEventName();

    public abstract int requireOnline();

    public abstract void onActive();

    public abstract void onInactive();

}
