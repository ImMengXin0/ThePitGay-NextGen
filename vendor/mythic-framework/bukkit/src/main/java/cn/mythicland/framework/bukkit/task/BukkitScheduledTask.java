package cn.mythicland.framework.bukkit.task;

import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

public final class BukkitScheduledTask implements ScheduledTaskHandle {
    private final BukkitTask delegate;

    public BukkitScheduledTask(BukkitTask delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void cancel() {
        this.delegate.cancel();
    }
}
