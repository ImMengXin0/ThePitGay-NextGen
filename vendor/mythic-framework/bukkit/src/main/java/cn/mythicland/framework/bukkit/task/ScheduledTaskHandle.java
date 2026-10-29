package cn.mythicland.framework.bukkit.task;

@FunctionalInterface
public interface ScheduledTaskHandle {
    void cancel();
}
