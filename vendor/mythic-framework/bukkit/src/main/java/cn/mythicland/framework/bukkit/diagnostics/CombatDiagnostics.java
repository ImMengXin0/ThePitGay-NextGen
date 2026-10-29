package cn.mythicland.framework.bukkit.diagnostics;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import cn.mythicland.framework.diagnostics.DiagnosticIds;
import cn.mythicland.framework.diagnostics.DiagnosticRecorder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class CombatDiagnostics implements AutoCloseable {
    public record Status(
            boolean recording,
            boolean stopping,
            boolean buffering,
            Path directory,
            long records,
            long bytes,
            String reason,
            String error
    ) {
    }

    private final JavaPlugin plugin;
    private final Path root;
    private final DiagnosticRecorder.Limits limits;
    private Session current;
    private DiagnosticRecorder.Summary last;
    private boolean closed;

    public CombatDiagnostics(JavaPlugin plugin, Path root, DiagnosticRecorder.Limits limits) {
        this.plugin = plugin;
        this.root = root;
        this.limits = limits;
    }

    public Path start(Collection<? extends Player> players) throws IOException, ReflectiveOperationException {
        return this.start(players, players, DiagnosticOptions.defaults((int) this.limits.duration().toSeconds()));
    }

    public Path start(
            Collection<? extends Player> players,
            Collection<? extends Player> observers,
            DiagnosticOptions options
    ) throws IOException, ReflectiveOperationException {
        return this.start(players, observers, options, null);
    }

    public Path watch(
            Collection<? extends Player> players,
            Collection<? extends Player> observers,
            DiagnosticOptions options,
            DiagnosticRecorder.History history
    ) throws IOException, ReflectiveOperationException {
        return this.start(players, observers, options, history);
    }

    private Path start(
            Collection<? extends Player> players,
            Collection<? extends Player> observers,
            DiagnosticOptions options,
            DiagnosticRecorder.History history
    ) throws IOException, ReflectiveOperationException {
        if (this.closed) throw new IllegalStateException("Diagnostics are closed");
        if (this.current != null) throw new IllegalStateException("A diagnostic session is active or stopping");
        if (players.isEmpty() || players.size() > 8)
            throw new IllegalArgumentException("Select between 1 and 8 online players");
        if (observers.isEmpty() || observers.size() > 16)
            throw new IllegalArgumentException("Select between 1 and 16 online observers");
        if (!Bukkit.getServer().getClass().getPackage().getName().endsWith("v1_8_R3")) {
            throw new IllegalStateException("Combat diagnostics require a Minecraft 1.8.8 v1_8_R3 server");
        }
        Set<UUID> watched = players.stream()
                .map(Player::getUniqueId)
                .collect(Collectors.toUnmodifiableSet());
        Set<UUID> receivers = observers.stream().map(Player::getUniqueId).collect(Collectors.toUnmodifiableSet());
        DiagnosticRecorder.Limits sessionLimits = new DiagnosticRecorder.Limits(
                Duration.ofSeconds(Math.min(options.durationSeconds(), this.limits.duration().toSeconds())),
                this.limits.segmentBytes(), this.limits.sessionBytes(), this.limits.directoryBytes(),
                this.limits.minimumFreeBytes(), this.limits.queueCapacity());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("server", Bukkit.getVersion());
        metadata.put("bukkit", Bukkit.getBukkitVersion());
        metadata.put("java", System.getProperty("java.version"));
        metadata.put("vm", System.getProperty("java.vm.name"));
        metadata.put("os", System.getProperty("os.name"));
        metadata.put("players", players.stream()
                .map(player -> Map.of("name", player.getName(), "uuid", player.getUniqueId().toString()))
                .toList());
        metadata.put("observers", observers.stream()
                .map(player -> Map.of("name", player.getName(), "uuid", player.getUniqueId().toString())).toList());
        metadata.put("packets", options.packets());
        metadata.put("details", options.details());
        metadata.put("sampleTicks", options.sampleTicks());
        if (history != null) {
            metadata.put("historySeconds", history.window().toSeconds());
            metadata.put("historyBytes", history.maxBytes());
        }
        metadata.put("durationSeconds", sessionLimits.duration().toSeconds());
        metadata.put("segmentBytes", this.limits.segmentBytes());
        metadata.put("sessionBytes", this.limits.sessionBytes());
        metadata.put("directoryBytes", this.limits.directoryBytes());
        metadata.put("minimumFreeBytes", this.limits.minimumFreeBytes());
        metadata.put("correlation", "frameId links synchronous decode only; packetId is diagnostic object identity, not a protocol sequence number");
        DiagnosticRecorder recorder = new DiagnosticRecorder(this.root, sessionLimits, metadata, history);
        Session session = new Session(recorder, watched, receivers, options, history != null);
        this.current = session;
        try {
            if (options.includes(DiagnosticOptions.Detail.CORE)) session.core = new DiagnosticCoreBridge(recorder, session.ids, watched);
            session.packets = new DiagnosticPackets(this.plugin, recorder, session.ids, players, receivers, options);
            if (options.includes(DiagnosticOptions.Detail.EVENTS) || options.includes(DiagnosticOptions.Detail.LISTENERS)) {
                session.events = new DiagnosticEvents(
                        this.plugin, recorder, session.ids, watched,
                        () -> session.core == null ? 0 : session.core.packetId(),
                        () -> session.core == null ? 0 : session.core.damageId(), options);
            }
            this.captureSnapshots(session, "initial", true);
            session.task = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> this.tick(session), 1, 1);
            this.fingerprint(session);
            recorder.completion().whenComplete((summary, failure) -> {
                if (!this.closed && this.plugin.isEnabled())
                    Bukkit.getScheduler().runTask(this.plugin, () -> this.finish(session));
            });
            return recorder.directory();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            recorder.fail("session.install", failure);
            this.finish(session);
            throw failure;
        }
    }

    public void mark(String actor, String label) {
        Session session = this.current;
        if (session == null || !session.recorder.isRecording())
            throw new IllegalStateException("No active diagnostic session");
        if (label.isBlank() || label.length() > 512)
            throw new IllegalArgumentException("Diagnostic marker must contain 1 to 512 characters");
        session.recorder.trigger();
        session.recorder.record("marker", Map.of("actor", actor, "label", label, "tick", DiagnosticSnapshot.tick()));
        this.captureSnapshots(session, "marker", true);
    }

    public void stop() {
        if (this.current == null) throw new IllegalStateException("No active diagnostic session");
        this.current.recorder.stop("command");
        this.finish(this.current);
    }

    public Status status() {
        if (this.current != null) {
            DiagnosticRecorder recorder = this.current.recorder;
            return new Status(
                    recorder.isRecording(),
                    !recorder.isRecording(),
                    recorder.isBuffering(),
                    recorder.directory(),
                    recorder.records(),
                    recorder.bytes(),
                    "",
                    ""
            );
        }
        if (this.last != null)
            return new Status(
                    false,
                    false,
                    false,
                    this.last.directory(),
                    this.last.records(),
                    this.last.uncompressedBytes(),
                    this.last.reason(),
                    this.last.error()
            );
        return new Status(false, false, false, this.root, 0, 0, "not_started", "");
    }

    private void tick(Session session) {
        if (!session.recorder.isRecording()) {
            this.finish(session);
            return;
        }
        try {
            long now = System.nanoTime();
            if (session.options.captures(DiagnosticOptions.PacketGroup.COMBAT)
                    || session.options.captures(DiagnosticOptions.PacketGroup.MOVEMENT)) {
                session.recorder.record("tick", Map.of("tick", DiagnosticSnapshot.tick(), "gapNanos", now - session.lastTick));
            }
            session.lastTick = now;
            int tick = session.ticks++;
            if (tick % 20 == 0 && session.events != null) session.events.refresh();
            for (UUID uuid : session.connections) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) session.packets.connect(player);
            }
            boolean checkpoint = session.history && tick % 20 == 0;
            if (checkpoint || tick % session.options.sampleTicks() == 0) this.captureSnapshots(session, "tick", checkpoint);
            if (tick % 20 == 0 && session.options.includes(DiagnosticOptions.Detail.RUNTIME)) {
                session.recorder.record("runtime", Map.of(
                        "heapUsed", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(),
                        "gc", ManagementFactory.getGarbageCollectorMXBeans()
                                .stream()
                                .map(gc -> Map.of("name", gc.getName(),
                                        "count", gc.getCollectionCount(), "millis", gc.getCollectionTime()))
                                .toList()));
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            session.recorder.fail("tick", failure);
            this.finish(session);
        }
    }

    private void captureSnapshots(Session session, String phase, boolean force) {
        List<Player> targets = new ArrayList<>();
        for (UUID uuid : session.watched) {
            Player player = Bukkit.getPlayer(uuid);
            Map<String, Object> snapshot;
            if (player == null) snapshot = Map.of("uuid", uuid.toString(), "online", false);
            else {
                targets.add(player);
                session.packets.connect(player);
                snapshot = DiagnosticSnapshot.player(player, session.options);
            }
            Map<String, Object> previous = session.playerStates.put(uuid, snapshot);
            if (force || !snapshot.equals(previous) || session.options.captures(DiagnosticOptions.PacketGroup.COMBAT))
                session.recorder.record(phase.equals("marker") ? "marker.player" : "player." + phase, snapshot);
        }
        for (UUID uuid : session.observers) {
            Player viewer = Bukkit.getPlayer(uuid);
            if (viewer == null) continue;
            session.packets.connect(viewer);
            Map<String, Object> snapshot = DiagnosticSnapshot.viewer(viewer, targets, session.options);
            Map<String, Object> previous = session.viewerStates.put(uuid, snapshot);
            if (snapshot.size() > 1 && (force || !snapshot.equals(previous)))
                session.recorder.record(phase.equals("marker") ? "marker.viewer" : "viewer." + phase, snapshot);
        }
    }

    private void fingerprint(Session session) {
        List<Map<String, Object>> artifacts = new ArrayList<>();
        for (Plugin installed : Bukkit.getPluginManager().getPlugins()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("plugin", installed.getName());
            entry.put("version", installed.getDescription().getVersion());
            entry.put("enabled", installed.isEnabled());
            var source = installed.getClass().getProtectionDomain().getCodeSource();
            if (source != null) entry.put("source", source.getLocation().toString());
            artifacts.add(entry);
        }
        var serverSource = Bukkit.getServer().getClass().getProtectionDomain().getCodeSource();
        if (serverSource != null)
            artifacts.add(new LinkedHashMap<>(Map.of("server", true, "source", serverSource.getLocation().toString())));
        session.hashes.execute(() -> {
            try {
                for (Map<String, Object> artifact : artifacts) {
                    if (!session.recorder.isRecording() || Thread.currentThread().isInterrupted()) return;
                    if (artifact.containsKey("source")) {
                        Path path = Path.of(URI.create((String) artifact.get("source")));
                        if (Files.isRegularFile(path)) {
                            MessageDigest hash = MessageDigest.getInstance("SHA-256");
                            try (var input = Files.newInputStream(path)) {
                                byte[] buffer = new byte[65536];
                                int read;
                                while ((read = input.read(buffer)) != -1) {
                                    if (Thread.currentThread().isInterrupted()) return;
                                    hash.update(buffer, 0, read);
                                }
                            }
                            artifact.put("sha256", HexFormat.of().formatHex(hash.digest()));
                            artifact.put("bytes", Files.size(path));
                        } else artifact.put("hashUnavailable", "not_a_regular_file");
                    }
                    session.recorder.record("artifact", artifact);
                }
            } catch (IOException | NoSuchAlgorithmException | RuntimeException failure) {
                session.recorder.fail("artifact", failure);
            }
        });
    }

    private void finish(Session session) {
        if (session.cleaning) return;
        session.cleaning = true;
        session.recorder.stop("stopped");
        if (session.task != null) session.task.cancel();
        session.hashes.shutdownNow();
        try {
            if (session.events != null) session.events.close();
        } catch (ReflectiveOperationException | RuntimeException failure) {
            session.recorder.fail("event.uninstall", failure);
            this.plugin.getLogger().severe("Failed to remove diagnostic event probes: " + failure);
        }
        try {
            if (session.core != null) session.core.close();
        } catch (ReflectiveOperationException | RuntimeException failure) {
            session.recorder.fail("core.uninstall", failure);
            this.plugin.getLogger().severe("Failed to remove core diagnostic probes: " + failure);
        }
        session.detached = session.packets == null ? CompletableFuture.completedFuture(null) : session.packets.detach();
        CompletableFuture.allOf(session.detached, session.recorder.completion()).whenComplete((unused, failure) -> {
            if (failure != null)
                this.plugin.getLogger().severe("Failed to remove diagnostic channel probes: " + failure);
            if (!this.closed && this.plugin.isEnabled()) Bukkit.getScheduler().runTask(this.plugin, () -> {
                this.last = session.recorder.completion().join();
                if (this.current == session) this.current = null;
                this.plugin.getLogger().info("Diagnostics stopped: reason=" + this.last.reason() + ", records=" + this.last.records()
                        + ", dropped=" + this.last.dropped() + ", error=" + this.last.error() + ", directory=" + this.last.directory());
            });
        });
    }

    @Override
    public void close() {
        this.closed = true;
        Session session = this.current;
        if (session == null) return;
        this.finish(session);
        session.recorder.close();
        try {
            session.detached.get(5, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException("Diagnostic channel cleanup did not complete", failure);
        }
        this.last = session.recorder.completion().join();
        this.current = null;
    }

    private static final class Session {
        final DiagnosticRecorder recorder;
        final Set<UUID> watched;
        final Set<UUID> observers;
        final Set<UUID> connections;
        final DiagnosticOptions options;
        final boolean history;
        final Map<UUID, Map<String, Object>> playerStates = new HashMap<>();
        final Map<UUID, Map<String, Object>> viewerStates = new HashMap<>();
        final DiagnosticIds ids;
        final ExecutorService hashes = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "diagnostic-artifact-hashes");
            thread.setDaemon(true);
            return thread;
        });
        DiagnosticCoreBridge core;
        DiagnosticPackets packets;
        DiagnosticEvents events;
        BukkitTask task;
        CompletableFuture<Void> detached;
        long lastTick = System.nanoTime();
        int ticks;
        boolean cleaning;

        Session(DiagnosticRecorder recorder, Set<UUID> watched, Set<UUID> observers, DiagnosticOptions options, boolean history) {
            this.recorder = recorder;
            this.watched = watched;
            this.observers = observers;
            this.connections = new HashSet<>(watched);
            this.connections.addAll(observers);
            this.options = options;
            this.history = history;
            this.ids = new DiagnosticIds(recorder::nextId);
        }
    }
}
