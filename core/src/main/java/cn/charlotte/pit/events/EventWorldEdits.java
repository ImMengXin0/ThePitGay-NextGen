package cn.charlotte.pit.events;

import cn.charlotte.pit.ThePit;
import org.bukkit.Bukkit;
import com.sk89q.worldedit.EditSession;
import com.boydti.fawe.util.SetQueue;

import java.util.concurrent.*;
import java.util.logging.Level;

/** Preserve build/undo ordering across activity replacement without blocking gameplay. */
public final class EventWorldEdits {
    private static final java.util.Set<EditSession> FLUSHING = ConcurrentHashMap.newKeySet();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(action -> {
        Thread thread = new Thread(action, "Pit-Event-WorldEdit");
        thread.setDaemon(true);
        return thread;
    });

    private EventWorldEdits() { }

    public static void build(AbstractEvent event, Runnable action) {
        long generation = event.getGeneration();
        submit(() -> {
            if (event.isActive() && event.getGeneration() == generation) {
                try {
                    action.run();
                } catch (Throwable failure) {
                    if (ThePit.getInstance().isEnabled()) event.runEventTask(() -> {
                        if (event.getGeneration() == generation)
                            ThePit.getInstance().getEventFactory().abort(event, failure);
                    });
                    throw failure;
                }
            }
        });
    }

    public static void undo(Runnable action) { submit(action); }

    public static void flush(EditSession session) {
        FLUSHING.add(session);
        try { session.flushQueue(); }
        finally { FLUSHING.remove(session); }
    }

    public static void restore(EditSession session) {
        FLUSHING.add(session);
        try {
            // FAWE undo itself calls flushQueue, so it must also be visible to shutdown.
            session.undo(session);
            session.flushQueue();
        } finally { FLUSHING.remove(session); }
    }

    private static void submit(Runnable action) {
        WORKER.execute(() -> {
            try {
                action.run();
            } catch (Throwable failure) {
                ThePit.getInstance().getLogger().log(Level.SEVERE, "Activity world edit failed", failure);
            }
        });
    }

    public static void shutdown() {
        WORKER.shutdown();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!WORKER.awaitTermination(10, TimeUnit.MILLISECONDS) && System.nanoTime() < deadline) {
                // FAWE's asynchronous flush waits for its main-thread queue. During disable
                // Bukkit no longer ticks that queue, so drain our own pending edits here.
                if (Bukkit.isPrimaryThread()) {
                    for (EditSession session : FLUSHING) {
                        if (session.getQueue() != null
                                && session.getQueue().getStage() == SetQueue.QueueStage.ACTIVE)
                            SetQueue.IMP.flush(session.getQueue());
                    }
                }
            }
            if (!WORKER.isTerminated()) {
                ThePit.getInstance().getLogger().warning("Activity world edits did not finish within 10 seconds.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Throwable failure) {
            ThePit.getInstance().getLogger().log(Level.SEVERE, "Could not drain activity edits during shutdown", failure);
        }
    }
}
