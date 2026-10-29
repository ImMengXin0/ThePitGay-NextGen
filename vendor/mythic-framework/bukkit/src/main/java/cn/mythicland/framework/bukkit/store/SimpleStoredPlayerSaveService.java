package cn.mythicland.framework.bukkit.store;

import cn.mythicland.framework.bukkit.task.ScheduledTaskHandle;
import cn.mythicland.framework.bukkit.task.TaskScheduler;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.UnaryOperator;
import java.util.function.Consumer;

public final class SimpleStoredPlayerSaveService<P, I> implements StoredPlayerSaveService<P, I> {
    private final Logger logger;
    private final Executor ioExecutor;
    private final StoredPlayerRepository<P, I> repository;
    private final StoredPlayerInventory<I> inventoryAdapter;
    private final TaskScheduler taskScheduler;
    private final Map<UUID, StoredPlayerSession<P, I>> pendingSessions;
    private final long flushIntervalTicks;
    private final Consumer<UUID> shutdownAnnouncement;
    private final AtomicBoolean flushWorkerRunning;
    private final AtomicBoolean flushRequested;
    private final AtomicBoolean flushAllRequested;
    private final Queue<UUID> requestedSessionIds;
    private final Object flushWorkerMonitor;

    private ScheduledTaskHandle flushTask;
    private List<Player> capturePlayers = List.of();
    private int captureIndex;
    private int captureBatchSize;
    private long ticksUntilCapture;
    private volatile StoredPlayerCache<P, I> activeCache;
    private volatile boolean acceptingAsyncFlushes;

    public SimpleStoredPlayerSaveService(
            Logger logger,
            Executor ioExecutor,
            StoredPlayerRepository<P, I> repository,
            StoredPlayerInventory<I> inventoryAdapter,
            TaskScheduler taskScheduler,
            long flushIntervalTicks,
            Consumer<UUID> shutdownAnnouncement
    ) {
        this.logger = logger;
        this.ioExecutor = ioExecutor;
        this.repository = repository;
        this.inventoryAdapter = inventoryAdapter;
        this.taskScheduler = taskScheduler;
        this.flushIntervalTicks = flushIntervalTicks;
        this.shutdownAnnouncement = shutdownAnnouncement;
        this.pendingSessions = new ConcurrentHashMap<>();
        this.flushWorkerRunning = new AtomicBoolean(false);
        this.flushRequested = new AtomicBoolean(false);
        this.flushAllRequested = new AtomicBoolean(false);
        this.requestedSessionIds = new ConcurrentLinkedQueue<>();
        this.flushWorkerMonitor = new Object();
        this.acceptingAsyncFlushes = true;
    }

    @Override
    public void start(StoredPlayerCache<P, I> cache) {
        if (this.flushTask != null) return;
        this.acceptingAsyncFlushes = true;
        this.activeCache = cache;
        this.ticksUntilCapture = 0;
        this.flushTask = this.taskScheduler.scheduleSyncRepeating(
                () -> this.captureTick(cache),
                this.flushIntervalTicks,
                1L
        );
    }

    private void captureTick(StoredPlayerCache<P, I> cache) {
        if (this.ticksUntilCapture-- <= 0) {
            this.capturePlayers = List.copyOf(Bukkit.getOnlinePlayers());
            this.captureIndex = 0;
            this.captureBatchSize =
                    Math.max(1, (int) Math.ceil(this.capturePlayers.size() / (double) this.flushIntervalTicks));
            this.ticksUntilCapture = this.flushIntervalTicks - 1;
            if (this.capturePlayers.isEmpty()) this.flushAllAsync(cache);
        }
        if (this.capturePlayers.isEmpty()) return;
        int endIndex = Math.min(this.capturePlayers.size(), this.captureIndex + this.captureBatchSize);
        while (this.captureIndex < endIndex) {
            Player player = this.capturePlayers.get(this.captureIndex++);
            if (Bukkit.getPlayer(player.getUniqueId()) == player) this.captureOnlineInventory(player, cache);
        }
        if (this.captureIndex == this.capturePlayers.size()) {
            this.capturePlayers = List.of();
            this.flushAllAsync(cache);
        }
    }

    @Override
    public void stop() {
        if (this.flushTask != null) {
            this.flushTask.cancel();
            this.flushTask = null;
        }
        this.capturePlayers = List.of();
        this.activeCache = null;
    }

    public StoredPlayerSession<P, I> retainedSession(UUID playerUniqueId) {
        return this.pendingSessions.get(playerUniqueId);
    }

    public void releaseRetainedSession(UUID playerUniqueId, StoredPlayerSession<P, I> session) {
        this.pendingSessions.remove(playerUniqueId, session);
    }

    @Override
    public StoredPlayerData<P, I> reactivate(Player player, StoredPlayerCache<P, I> cache) {
        UUID playerUniqueId = player.getUniqueId();
        StoredPlayerSession<P, I> session = this.pendingSessions.remove(playerUniqueId);
        if (session == null) return null;
        cache.putSession(playerUniqueId, session);
        StoredPlayerData<P, I> playerState = session.snapshot();
        this.inventoryAdapter.apply(player, playerState.inventoryData());
        return playerState;
    }

    @Override
    public void saveAndRemove(Player player, StoredPlayerCache<P, I> cache) {
        this.saveAndRemove(player, cache, ignored -> {});
    }

    public void saveAndRemove(Player player, StoredPlayerCache<P, I> cache, Consumer<StoredPlayerSession<P, I>> beforeFlush) {
        UUID playerUniqueId = player.getUniqueId();
        cache.cancelLoading(playerUniqueId, player);
        StoredPlayerSession<P, I> session = cache.removeSession(playerUniqueId);
        if (session == null) return;
        session.captureInventory(
                this.inventoryAdapter.mergeCaptured(this.inventoryAdapter.capture(player),
                        session.snapshot().inventoryData())
        );
        beforeFlush.accept(session);
        this.pendingSessions.put(playerUniqueId, session);
        this.flushSessionsAsync(List.of(session));
    }

    @Override
    public void captureOnlineInventories(StoredPlayerCache<P, I> cache) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.captureOnlineInventory(player, cache);
        }
    }

    public void captureOnlineInventory(Player player, StoredPlayerCache<P, I> cache) {
        if (player == null) return;
        StoredPlayerSession<P, I> session = cache.findSession(player.getUniqueId());
        if (session == null) return;
        session.captureInventory(
                this.inventoryAdapter.mergeCaptured(this.inventoryAdapter.capture(player),
                        session.snapshot().inventoryData())
        );
    }

    @Override
    public void flushAllNow(StoredPlayerCache<P, I> cache) {
        this.flushAllNow(cache, false);
    }

    @Override
    public void flushAllNow(StoredPlayerCache<P, I> cache, boolean announcePlayers) {
        Collection<StoredPlayerSession<P, I>> sessions = this.collectAllSessions(cache);
        List<StoredPlayerSession<P, I>> lockedSessions = this.lockSessions(sessions);
        try {
            List<FlushContext<P, I>> contexts = this.collectFlushContexts(sessions);
            if (contexts.isEmpty()) return;
            if (announcePlayers) {
                this.logShutdownFlushTargets(contexts);
            }
            this.flushContexts(contexts, true);
        } finally {
            this.unlockSessions(lockedSessions);
        }
    }

    @Override
    public void shutdownAndFlush(StoredPlayerCache<P, I> cache, boolean announcePlayers) {
        this.acceptingAsyncFlushes = false;
        this.stop();
        this.awaitFlushWorker();
        this.flushAllNow(cache, announcePlayers);
        this.flushRequested.set(false);
        this.flushAllRequested.set(false);
    }

    @Override
    public boolean flushNow(UUID playerUniqueId, StoredPlayerCache<P, I> cache) {
        StoredPlayerSession<P, I> session = this.findFlushTarget(playerUniqueId, cache);
        if (session == null) return false;
        session.flushLock().lock();
        try {
            List<FlushContext<P, I>> contexts = this.collectFlushContexts(List.of(session));
            if (contexts.isEmpty()) return false;
            this.flushContexts(contexts, true);
            return true;
        } finally {
            session.flushLock().unlock();
        }
    }

    @Override
    public void flushAsync(UUID playerUniqueId, StoredPlayerCache<P, I> cache) {
        if (!this.acceptingAsyncFlushes) return;
        this.activeCache = cache;
        StoredPlayerSession<P, I> session = this.findFlushTarget(playerUniqueId, cache);
        if (session == null) return;
        this.flushSessionsAsync(List.of(session));
    }

    public boolean updateRetainedProfile(
            UUID playerUniqueId,
            StoredPlayerCache<P, I> cache,
            UnaryOperator<P> change
    ) {
        AtomicBoolean found = new AtomicBoolean();
        this.pendingSessions.computeIfPresent(playerUniqueId, (id, session) -> {
            session.updateProfile(change);
            found.set(true);
            return session;
        });
        if (!found.get()) {
            StoredPlayerSession<P, I> session = cache.findSession(playerUniqueId);
            if (session == null) return false;
            session.updateProfile(change);
        }
        flushAsync(playerUniqueId, cache);
        return true;
    }

    public void transact(
            Player player,
            StoredPlayerCache<P, I> cache,
            UnaryOperator<P> profileChange,
            Runnable inventoryChange
    ) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Player transaction requires primary thread");
        StoredPlayerSession<P, I> session = cache.findSession(player.getUniqueId());
        if (session == null) throw new IllegalStateException("Player data not loaded: " + player.getUniqueId());
        session.flushLock().lock();
        try {
            P previousProfile = session.profileData();
            I previousInventory = this.inventoryAdapter.mergeCaptured(this.inventoryAdapter.capture(player),
                    session.snapshot().inventoryData());
            try {
                inventoryChange.run();
                session.updateProfile(profileChange);
                session.captureInventory(
                        this.inventoryAdapter.mergeCaptured(this.inventoryAdapter.capture(player), previousInventory));
                this.flushContexts(this.collectFlushContexts(List.of(session)), true);
            } catch (RuntimeException exception) {
                this.inventoryAdapter.apply(player, previousInventory);
                session.updateProfile(ignored -> previousProfile);
                session.captureInventory(previousInventory);
                throw exception;
            }
        } finally {
            session.flushLock().unlock();
        }
    }

    @Override
    public void clear() {
        this.pendingSessions.clear();
        this.flushRequested.set(false);
        this.flushAllRequested.set(false);
        this.requestedSessionIds.clear();
    }

    public void transactTogether(
            Map<Player, UnaryOperator<P>> changes,
            StoredPlayerCache<P, I> cache,
            Runnable inventoryChange,
            Consumer<List<StoredPlayerFlush<P, I>>> commit
    ) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Player transaction requires primary thread");
        Map<Player, StoredPlayerSession<P, I>> sessions = new LinkedHashMap<>();
        for (Player player : changes.keySet()) {
            StoredPlayerSession<P, I> session = cache.findSession(player.getUniqueId());
            if (session == null) throw new IllegalStateException("Player data not loaded: " + player.getUniqueId());
            sessions.put(player, session);
        }
        List<StoredPlayerSession<P, I>> lockedSessions = this.lockSessions(sessions.values());
        try {
            Map<Player, P> profiles = new LinkedHashMap<>();
            Map<Player, I> inventories = new LinkedHashMap<>();
            for (Player player : changes.keySet()) {
                StoredPlayerSession<P, I> session = sessions.get(player);
                profiles.put(player, session.profileData());
                inventories.put(player, this.inventoryAdapter.mergeCaptured(
                        this.inventoryAdapter.capture(player), session.snapshot().inventoryData()
                ));
            }
            List<FlushContext<P, I>> contexts = List.of();
            try {
                inventoryChange.run();
                for (Player player : changes.keySet()) {
                    StoredPlayerSession<P, I> session = sessions.get(player);
                    session.updateProfile(changes.get(player));
                    session.captureInventory(this.inventoryAdapter.mergeCaptured(
                            this.inventoryAdapter.capture(player), inventories.get(player)
                    ));
                }
                contexts = this.collectFlushContexts(sessions.values());
                List<StoredPlayerFlush<P, I>> requests = contexts.stream().map(FlushContext::request).toList();
                commit.accept(requests);
            } catch (RuntimeException exception) {
                for (FlushContext<P, I> context : contexts) context.session().flushFailed(context.request());
                for (Player player : changes.keySet()) {
                    this.inventoryAdapter.apply(player, inventories.get(player));
                    sessions.get(player).updateProfile(ignored -> profiles.get(player));
                    sessions.get(player).captureInventory(inventories.get(player));
                }
                throw exception;
            }
            for (FlushContext<P, I> context : contexts) {
                context.session().flushSucceeded(context.request());
            }
        } finally {
            this.unlockSessions(lockedSessions);
        }
    }

    private StoredPlayerSession<P, I> findFlushTarget(UUID playerUniqueId, StoredPlayerCache<P, I> cache) {
        StoredPlayerSession<P, I> pendingSession = this.pendingSessions.get(playerUniqueId);
        if (pendingSession != null) return pendingSession;
        return cache.findSession(playerUniqueId);
    }

    private void flushAllAsync(StoredPlayerCache<P, I> cache) {
        this.activeCache = cache;
        if (!this.acceptingAsyncFlushes) return;
        this.flushAllRequested.set(true);
        this.flushRequested.set(true);
        this.startFlushWorker();
    }

    private Collection<StoredPlayerSession<P, I>> collectAllSessions(StoredPlayerCache<P, I> cache) {
        Map<UUID, StoredPlayerSession<P, I>> combined = new Object2ObjectLinkedOpenHashMap<>();
        for (StoredPlayerSession<P, I> session : cache.sessions()) {
            combined.put(session.uniqueId(), session);
        }
        combined.putAll(this.pendingSessions);
        return combined.values();
    }

    private void flushSessionsAsync(Collection<StoredPlayerSession<P, I>> sessions) {
        if (!this.acceptingAsyncFlushes) return;
        if (sessions.isEmpty()) return;
        for (StoredPlayerSession<P, I> session : sessions) {
            this.requestedSessionIds.add(session.uniqueId());
        }
        this.flushRequested.set(true);
        this.startFlushWorker();
    }

    private void startFlushWorker() {
        if (!this.flushWorkerRunning.compareAndSet(false, true)) return;
        CompletableFuture.runAsync(this::runFlushWorker, this.ioExecutor);
    }

    private void runFlushWorker() {
        try {
            while (this.flushRequested.getAndSet(false)) {
                for (StoredPlayerSession<P, I> session : this.collectRequestedSessionsSnapshot()) {
                    session.flushLock().lock();
                    try {
                        this.flushContexts(this.collectFlushContexts(List.of(session)), false);
                    } finally {
                        session.flushLock().unlock();
                    }
                }
            }
        } finally {
            this.flushWorkerRunning.set(false);
            synchronized (this.flushWorkerMonitor) {
                this.flushWorkerMonitor.notifyAll();
            }
            if (this.acceptingAsyncFlushes && this.flushRequested.get()) this.startFlushWorker();
        }
    }

    private List<StoredPlayerSession<P, I>> lockSessions(Collection<StoredPlayerSession<P, I>> sessions) {
        List<StoredPlayerSession<P, I>> ordered = sessions.stream()
                .sorted(Comparator.comparing(StoredPlayerSession::uniqueId))
                .toList();
        for (StoredPlayerSession<P, I> session : ordered) session.flushLock().lock();
        return ordered;
    }

    private void unlockSessions(List<StoredPlayerSession<P, I>> sessions) {
        for (int index = sessions.size() - 1; index >= 0; index--) sessions.get(index).flushLock().unlock();
    }

    private void awaitFlushWorker() {
        synchronized (this.flushWorkerMonitor) {
            while (this.flushWorkerRunning.get()) {
                try {
                    this.flushWorkerMonitor.wait(100L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private Collection<StoredPlayerSession<P, I>> collectRequestedSessionsSnapshot() {
        Set<UUID> requestedPlayerIds = new LinkedHashSet<>();
        UUID requestedPlayerId;
        while ((requestedPlayerId = this.requestedSessionIds.poll()) != null) {
            requestedPlayerIds.add(requestedPlayerId);
        }
        if (this.flushAllRequested.getAndSet(false)) return this.collectAllSessionsSnapshot();

        StoredPlayerCache<P, I> cache = this.activeCache;
        List<StoredPlayerSession<P, I>> sessions = new ObjectArrayList<>(requestedPlayerIds.size());
        for (UUID playerUniqueId : requestedPlayerIds) {
            StoredPlayerSession<P, I> pendingSession = this.pendingSessions.get(playerUniqueId);
            if (pendingSession != null) {
                sessions.add(pendingSession);
                continue;
            }
            if (cache == null) continue;
            StoredPlayerSession<P, I> activeSession = cache.findSession(playerUniqueId);
            if (activeSession != null) sessions.add(activeSession);
        }
        return sessions;
    }

    private List<FlushContext<P, I>> collectFlushContexts(Collection<StoredPlayerSession<P, I>> sessions) {
        List<FlushContext<P, I>> contexts = new ObjectArrayList<>();
        for (StoredPlayerSession<P, I> session : sessions) {
            StoredPlayerFlush<P, I> request = session.prepareFlush();
            if (request != null) {
                contexts.add(
                        new FlushContext<>(session, request)
                );
            }
        }
        return contexts;
    }

    private void flushContexts(List<FlushContext<P, I>> contexts, boolean propagateFailure) {
        if (contexts.isEmpty()) return;
        try {
            List<StoredPlayerFlush<P, I>> requests = new ObjectArrayList<>(contexts.size());
            for (FlushContext<P, I> context : contexts) {
                requests.add(context.request());
            }
            this.repository.flush(requests);
            for (FlushContext<P, I> context : contexts) {
                context.session().flushSucceeded(context.request());
                if (context.session().hasPendingChanges()) {
                    this.flushSessionsAsync(List.of(context.session()));
                } else {
                    this.pendingSessions.computeIfPresent(context.session().uniqueId(), (id, session) ->
                            session != context.session() || session.hasPendingChanges() ? session : null);
                }
            }
        } catch (Exception exception) {
            for (FlushContext<P, I> context : contexts) {
                context.session().flushFailed(context.request());
            }
            this.logger.log(Level.SEVERE, "Failed to flush player data batch", exception);
            if (propagateFailure) {
                throw new IllegalStateException("Failed to flush player data batch", exception);
            }
        }
    }

    private void logShutdownFlushTargets(List<FlushContext<P, I>> contexts) {
        for (FlushContext<P, I> context : contexts) this.shutdownAnnouncement.accept(context.session().uniqueId());
    }

    private Collection<StoredPlayerSession<P, I>> collectAllSessionsSnapshot() {
        StoredPlayerCache<P, I> cache = this.activeCache;
        Map<UUID, StoredPlayerSession<P, I>> combined = new Object2ObjectLinkedOpenHashMap<>(this.pendingSessions);
        if (cache != null) {
            for (StoredPlayerSession<P, I> session : cache.sessions()) {
                combined.put(session.uniqueId(), session);
            }
        }
        combined.putAll(this.pendingSessions);
        return combined.values();
    }

    private record FlushContext<P, I>(StoredPlayerSession<P, I> session, StoredPlayerFlush<P, I> request) {
    }
}
