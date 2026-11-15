package net.mizukilab.pit.platform.paper;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Desired viewers are retained when temporarily outside the world or render range. */
public final class ViewerPolicy {
    private final Set<UUID> included = new HashSet<>();
    private final Set<UUID> excluded = new HashSet<>();
    private boolean publicVisible;

    public record Snapshot(boolean publicVisible, Set<UUID> included, Set<UUID> excluded) {}

    public synchronized Snapshot snapshot() {
        return new Snapshot(publicVisible, Set.copyOf(included), Set.copyOf(excluded));
    }

    public synchronized void restore(Snapshot snapshot) {
        publicVisible = snapshot.publicVisible();
        included.clear();
        included.addAll(snapshot.included());
        excluded.clear();
        excluded.addAll(snapshot.excluded());
    }

    public synchronized void showAll() {
        publicVisible = true;
        included.clear();
        excluded.clear();
    }

    public synchronized void showOnly(Collection<UUID> viewers) {
        publicVisible = false;
        included.clear();
        included.addAll(viewers);
        excluded.clear();
    }

    public synchronized void show(UUID viewer) {
        included.add(viewer);
        excluded.remove(viewer);
    }

    public synchronized void hide(UUID viewer) {
        included.remove(viewer);
        excluded.add(viewer);
    }

    public synchronized boolean accepts(UUID viewer) {
        return !excluded.contains(viewer) && (publicVisible || included.contains(viewer));
    }

    public synchronized boolean isPublic() { return publicVisible; }

    public synchronized Set<UUID> privateCandidates(Collection<UUID> shown) {
        Set<UUID> candidates = new HashSet<>(included);
        candidates.addAll(shown);
        return candidates;
    }

    public synchronized void forget(UUID viewer) {
        included.remove(viewer);
        excluded.remove(viewer);
    }

    public synchronized void clear() {
        publicVisible = false;
        included.clear();
        excluded.clear();
    }
}
