package cn.mythicland.framework.bukkit.task;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class TickWorkQueue implements Executor {
    private final TaskScheduler scheduler;
    private final int maxTasksPerTick;
    private long budgetNanos;
    private final Queue<Runnable> pending = new ArrayDeque<>();
    private final Queue<Runnable> lowPriority = new ArrayDeque<>();
    private boolean scheduled;
    private boolean draining;
    private long startedNanos;

    public TickWorkQueue(TaskScheduler scheduler, int maxTasksPerTick, long budgetNanos) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        if (maxTasksPerTick <= 0) throw new IllegalArgumentException("maxTasksPerTick must be positive");
        if (budgetNanos <= 0) throw new IllegalArgumentException("budgetNanos must be positive");
        this.maxTasksPerTick = maxTasksPerTick;
        this.budgetNanos = budgetNanos;
    }

    public <T> CompletableFuture<T> submit(Supplier<T> work) {
        Objects.requireNonNull(work, "work");
        CompletableFuture<T> result = new CompletableFuture<>();
        this.execute(() -> {
            try {
                result.complete(work.get());
            } catch (Exception exception) {
                result.completeExceptionally(exception);
            }
        });
        return result;
    }

    @Override
    public void execute(Runnable work) {
        this.execute(work, this.pending);
    }

    public void executeLowPriority(Runnable work) {
        this.execute(work, this.lowPriority);
    }

    private void execute(Runnable work, Queue<Runnable> queue) {
        Objects.requireNonNull(work, "work");
        synchronized (this.pending) {
            queue.add(work);
            if (!this.scheduled) {
                this.scheduled = true;
                this.scheduler.executeSyncLater(this::drain, 1L);
            }
        }
    }

    public boolean hasBudget() {
        return this.draining && System.nanoTime() - this.startedNanos < this.budgetNanos;
    }

    public void setBudgetNanos(long budgetNanos) {
        if (budgetNanos <= 0) throw new IllegalArgumentException("budgetNanos must be positive");
        this.budgetNanos = budgetNanos;
    }

    private void drain() {
        this.startedNanos = System.nanoTime();
        this.draining = true;
        int normalCount;
        int lowCount;
        synchronized (this.pending) {
            normalCount = Math.min(this.maxTasksPerTick, this.pending.size());
            lowCount = Math.min(this.maxTasksPerTick - normalCount, this.lowPriority.size());
        }
        try {
            for (int processed = 0; processed < normalCount + lowCount; processed++) {
                Runnable work;
                synchronized (this.pending) {
                    work = processed < normalCount ? this.pending.poll() : this.lowPriority.poll();
                }
                if (work == null) break;
                work.run();
                if (!this.hasBudget()) break;
            }
        } finally {
            this.draining = false;
            synchronized (this.pending) {
                if (this.pending.isEmpty() && this.lowPriority.isEmpty()) this.scheduled = false;
                else this.scheduler.executeSyncLater(this::drain, 1L);
            }
        }
    }
}
