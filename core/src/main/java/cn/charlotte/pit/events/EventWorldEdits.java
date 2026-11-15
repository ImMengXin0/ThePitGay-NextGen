package cn.charlotte.pit.events;

import cn.charlotte.pit.ThePit;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Bukkit;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.logging.Level;

/** Serializes event edits and undo on the server thread, including during disable. */
public final class EventWorldEdits {
    @FunctionalInterface
    public interface EditAction { void run() throws Exception; }

    private static final Queue<Runnable> PENDING = new ArrayDeque<>();
    private static boolean scheduled;
    private EventWorldEdits() { }

    public static void build(AbstractEvent event, EditAction action) {
        long generation = event.getGeneration();
        submit(() -> {
            if (!event.isActive() || event.getGeneration() != generation) return;
            try {
                action.run();
            } catch (Exception failure) {
                ThePit.getInstance().getEventFactory().abort(event, failure);
                throw new IllegalStateException("Could not build event map", failure);
            }
        });
    }

    public static void undo(Runnable action) { submit(action); }
    public static void flush(EditSession session) { session.flushSession(); }

    public static void restore(EditSession session) {
        try (EditSession target = WorldEdit.getInstance().newEditSession(session.getWorld())) {
            session.undo(target);
        } finally { session.close(); }
    }

    public static void pasteLegacy(EditSession session, InputStream input, BlockVector3 origin) throws Exception {
        try (var reader = BuiltInClipboardFormat.MCEDIT_SCHEMATIC.getReader(input)) {
            var clipboard = reader.read();
            var holder = new ClipboardHolder(clipboard);
            Operations.complete(holder.createPaste(session).to(origin).ignoreAirBlocks(false).build());
        }
    }

    private static synchronized void submit(Runnable action) {
        PENDING.add(action);
        if (!scheduled) {
            scheduled = true;
            Bukkit.getScheduler().runTask(ThePit.getInstance(), EventWorldEdits::drain);
        }
    }

    private static void drain() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("World edits require the server thread");
        while (true) {
            Runnable action;
            synchronized (EventWorldEdits.class) {
                action = PENDING.poll();
                if (action == null) { scheduled = false; return; }
            }
            try { action.run(); }
            catch (Throwable failure) {
                ThePit.getInstance().getLogger().log(Level.SEVERE, "Activity world edit failed", failure);
            }
        }
    }

    public static void shutdown() { drain(); }
}
