package cn.mythicland.framework.bukkit.diagnostics;

import cn.mythicland.framework.diagnostics.DiagnosticIds;
import cn.mythicland.framework.diagnostics.DiagnosticRecorder;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.LongSupplier;

final class DiagnosticEvents implements AutoCloseable {
    private final DiagnosticRecorder recorder;
    private final DiagnosticOptions options;
    private final DiagnosticIds ids;
    private final Set<UUID> watched;
    private final LongSupplier packetId;
    private final LongSupplier damageId;
    private final Field executor;
    private final Listener boundary = new Listener() {
    };
    private final Map<RegisteredListener, Binding> bindings = new IdentityHashMap<>();
    private final Map<Event, State> states = new WeakHashMap<>();
    private final ThreadLocal<Long> parent = ThreadLocal.withInitial(() -> 0L);
    private boolean closed;

    DiagnosticEvents(
            JavaPlugin owner,
            DiagnosticRecorder recorder,
            DiagnosticIds ids,
            Set<UUID> watched,
            LongSupplier packetId,
            LongSupplier damageId,
            DiagnosticOptions options
    ) throws ReflectiveOperationException {
        this.recorder = recorder;
        this.options = options;
        this.ids = ids;
        this.watched = watched;
        this.packetId = packetId;
        this.damageId = damageId;
        this.executor = RegisteredListener.class.getDeclaredField("executor");
        this.executor.setAccessible(true);
        try {
            for (String name : new String[]{
                    "org.bukkit.event.entity.EntityDamageEvent", "org.bukkit.event.player.PlayerVelocityEvent",
                    "org.bukkit.event.player.PlayerMoveEvent", "org.bukkit.event.player.PlayerAnimationEvent",
                    "org.bukkit.event.player.PlayerItemHeldEvent", "org.bukkit.event.player.PlayerInteractEvent",
                    "org.bukkit.event.player.PlayerToggleSprintEvent", "org.bukkit.event.player.PlayerToggleSneakEvent",
                    "org.bukkit.event.player.PlayerJoinEvent", "org.bukkit.event.player.PlayerQuitEvent",
                    "org.bukkit.event.entity.EntityShootBowEvent", "org.bukkit.event.entity.ProjectileLaunchEvent",
                    "org.bukkit.event.entity.ProjectileHitEvent", "org.bukkit.event.entity.EntityDeathEvent",
                    "org.bukkit.event.player.PlayerRespawnEvent", "org.bukkit.event.entity.EntityRegainHealthEvent",
                    "org.bukkit.event.inventory.InventoryClickEvent", "org.bukkit.event.inventory.InventoryDragEvent",
                    "org.bukkit.event.player.PlayerItemBreakEvent", "org.bukkit.event.player.PlayerChangedWorldEvent",
                    "io.papermc.paper.event.player.PrePlayerAttackEntityEvent"
            }) {
                Class<? extends Event> type;
                try {
                    type = Class.forName(name).asSubclass(Event.class);
                } catch (ClassNotFoundException unavailable) {
                    if (!name.startsWith("io.papermc.")) throw unavailable;
                    this.recorder.record("capability", Map.of("event", name, "available", false));
                    continue;
                }
                if (!options.event(type.getSimpleName())) continue;
                for (EventPriority priority : new EventPriority[]{EventPriority.LOWEST, EventPriority.MONITOR}) {
                    Bukkit.getPluginManager().registerEvent(type, this.boundary, priority,
                            (listener, event) -> this.captureBoundary(event, priority), owner, false);
                }
            }
            this.refresh();
        } catch (ReflectiveOperationException | RuntimeException failure) {
            try {
                this.close();
            } catch (IllegalAccessException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    void refresh() throws IllegalAccessException {
        if (this.closed || !this.options.includes(DiagnosticOptions.Detail.LISTENERS)) return;
        for (HandlerList handlers : HandlerList.getHandlerLists()) {
            for (RegisteredListener registration : handlers.getRegisteredListeners()) {
                Binding old = this.bindings.get(registration);
                if (old != null) {
                    if (this.executor.get(registration) != old.wrapper()) {
                        throw new IllegalStateException("Diagnostic listener executor replaced: " + registration.getPlugin().getName());
                    }
                    continue;
                }
                if (this.bindings.size() >= 16384)
                    throw new IllegalStateException("Diagnostic listener capacity reached");
                EventExecutor original = (EventExecutor) this.executor.get(registration);
                EventExecutor wrapper = (listener, event) -> this.execute(registration, original, listener, event);
                this.executor.set(registration, wrapper);
                this.bindings.put(registration, new Binding(original, wrapper));
            }
        }
    }

    private void execute(RegisteredListener registration, EventExecutor original, Listener listener, Event event) throws EventException {
        if (this.closed || !this.recorder.isRecording() || event.isAsynchronous() || !this.options.event(event.getClass().getSimpleName())) {
            original.execute(listener, event);
            return;
        }
        State state;
        Map<String, Object> before;
        long invocation;
        long previousParent = this.parent.get();
        boolean selected;
        try {
            selected = previousParent != 0 || this.states.containsKey(event)
                    || DiagnosticSnapshot.containsPlayer(event, this.watched);
        } catch (RuntimeException failure) {
            this.recorder.fail("event.select", failure);
            original.execute(listener, event);
            return;
        }
        if (!selected) {
            original.execute(listener, event);
            return;
        }
        try {
            state = this.states.get(event);
            if (state == null) {
                state = new State(this.ids.id(event));
                this.states.put(event, state);
            }
            before = DiagnosticSnapshot.event(event, this.options.includes(DiagnosticOptions.Detail.NBT));
            if (!before.equals(state.last)) {
                this.recorder.record("event.state", Map.of("eventId", state.id, "phase", "before", "state", before,
                        "packetId", this.packetId.getAsLong(), "parentId", previousParent));
                state.last = before;
            }
            invocation = this.recorder.nextId();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("eventId", state.id);
            entry.put("invocationId", invocation);
            entry.put("parentId", previousParent);
            entry.put("packetId", this.packetId.getAsLong());
            entry.put("damageId", this.damageId.getAsLong());
            entry.put("plugin", registration.getPlugin().getName());
            entry.put("listener", listener.getClass().getName());
            entry.put("registrationId", this.ids.id(registration));
            entry.put("priority", registration.getPriority().name());
            entry.put("ignoresCancelled", registration.isIgnoringCancelled());
            this.recorder.record("listener.enter", entry);
        } catch (RuntimeException failure) {
            this.recorder.fail("event.before", failure);
            original.execute(listener, event);
            return;
        }
        this.parent.set(invocation);
        long started = System.nanoTime();
        Throwable thrown = null;
        try {
            original.execute(listener, event);
        } catch (EventException | RuntimeException | Error failure) {
            thrown = failure;
            throw failure;
        } finally {
            try {
                Map<String, Object> after = DiagnosticSnapshot.event(event, this.options.includes(DiagnosticOptions.Detail.NBT));
                if (!after.equals(before)) {
                    this.recorder.record("event.state", Map.of("eventId", state.id, "phase", "after", "state", after,
                            "invocationId", invocation, "packetId", this.packetId.getAsLong()));
                }
                state.last = after;
                Map<String, Object> exit = new LinkedHashMap<>();
                exit.put("eventId", state.id);
                exit.put("invocationId", invocation);
                exit.put("durationNanos", System.nanoTime() - started);
                exit.put("changed", !after.equals(before));
                exit.put("exception", thrown == null ? null : thrown.toString());
                this.recorder.record("listener.exit", exit);
            } catch (RuntimeException failure) {
                this.recorder.fail("event.after", failure);
            } finally {
                if (previousParent == 0) this.parent.remove();
                else this.parent.set(previousParent);
            }
        }
    }

    private void captureBoundary(Event event, EventPriority priority) {
        if (this.options.includes(DiagnosticOptions.Detail.LISTENERS) || this.closed || !this.recorder.isRecording()
                || event.isAsynchronous() || !this.options.event(event.getClass().getSimpleName())) return;
        try {
            if (!DiagnosticSnapshot.containsPlayer(event, this.watched)) return;
            this.recorder.record("event.state", Map.of(
                    "eventId", this.ids.id(event), "phase", priority.name(),
                    "state", DiagnosticSnapshot.event(event, this.options.includes(DiagnosticOptions.Detail.NBT)),
                    "packetId", this.packetId.getAsLong(), "damageId", this.damageId.getAsLong()));
        } catch (RuntimeException failure) {
            this.recorder.fail("event.boundary", failure);
        }
    }

    @Override
    public void close() throws IllegalAccessException {
        this.closed = true;
        for (var entry : this.bindings.entrySet()) {
            if (this.executor.get(entry.getKey()) == entry.getValue().wrapper()) {
                this.executor.set(entry.getKey(), entry.getValue().original());
            }
        }
        HandlerList.unregisterAll(this.boundary);
        this.bindings.clear();
        this.states.clear();
        this.parent.remove();
    }

    private record Binding(EventExecutor original, EventExecutor wrapper) {
    }

    private static final class State {
        final long id;
        Map<String, Object> last;

        State(long id) {
            this.id = id;
        }
    }
}
