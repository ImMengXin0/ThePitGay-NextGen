package cn.mythicland.framework.bukkit;

import java.util.ArrayList;
import java.util.List;

public final class BukkitRegistration implements AutoCloseable {
    private final List<Runnable> undoActions;
    private boolean closed;

    public BukkitRegistration() {
        this.undoActions = new ArrayList<>();
    }

    public void addUndoAction(Runnable undoAction) {
        if (this.closed) throw new IllegalStateException("Bukkit registration is already closed");
        this.undoActions.add(undoAction);
    }

    public void combine(BukkitRegistration registration) {
        if (registration == null) return;
        this.addUndoAction(registration::close);
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        Throwable failure = null;
        for (int index = this.undoActions.size() - 1; index >= 0; index--) {
            try {
                this.undoActions.get(index).run();
            } catch (RuntimeException | Error exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        this.undoActions.clear();
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException exception) throw exception;
    }

    public boolean isClosed() {
        return this.closed;
    }
}
