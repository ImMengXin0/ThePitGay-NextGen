package cn.mythicland.framework.bukkit.task;

public interface TaskScheduler {
    void executeSync(Runnable task);

    void executeSyncIfNeeded(Runnable task);

    ScheduledTaskHandle scheduleSyncRepeating(
            Runnable task,
            long delayTicks,
            long periodTicks
    );

    void executeSyncLater(Runnable task, long delayTicks);

    void executeAsync(Runnable task);
}
