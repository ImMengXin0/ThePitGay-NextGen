package net.mizukilab.pit.listener;

import cn.charlotte.pit.ThePit;
import com.destroystokyo.paper.event.server.ServerExceptionEvent;
import com.destroystokyo.paper.exception.ServerSchedulerException;
import org.bukkit.craftbukkit.scheduler.CraftTask;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Adds the missing runnable context when the JVM omits a frequently thrown stack trace. */
public final class TaskFailureListener implements Listener {
    private final Map<Class<?>, Long> lastWarnings = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerException(ServerExceptionEvent event) {
        if (!(event.getException() instanceof ServerSchedulerException failure)) return;
        BukkitTask task = failure.getTask();
        ThePit plugin = ThePit.getInstance();
        if (task.getOwner() != plugin || failure.getCause() == null
                || failure.getCause().getStackTrace().length != 0) return;
        Object runnable = task;
        if (task instanceof CraftTask craft) {
            if (craft.rTask != null) runnable = craft.rTask;
            else if (craft.cTask != null) runnable = craft.cTask;
        }
        Class<?> type = runnable.getClass();
        long now = System.nanoTime();
        lastWarnings.compute(type, (key, previous) -> {
            if (previous != null && now - previous < TimeUnit.SECONDS.toNanos(30)) return previous;
            plugin.getLogger().warning("任务 #" + task.getTaskId() + " (" + type.getName()
                    + ") 抛出 " + failure.getCause().getClass().getName()
                    + "，异常没有堆栈；启动 JVM 时添加 -XX:-OmitStackTraceInFastThrow 后复现以获取调用位置。");
            return now;
        });
    }
}
