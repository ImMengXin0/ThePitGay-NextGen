package cn.mythicland.framework.bukkit.store;

import cn.mythicland.framework.cache.KeyedLoadingCache;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class StoredPlayerCache<P, I> {
    private final KeyedLoadingCache<UUID, StoredPlayerSession<P, I>> sessions = new KeyedLoadingCache<>();
    private final KeyedLoadingCache<UUID, StoredPlayerData<P, I>> loadingTasks = new KeyedLoadingCache<>();

    public boolean isLoaded(UUID playerUniqueId) {
        return this.sessions.contains(playerUniqueId);
    }

    public boolean isLoading(UUID playerUniqueId) {
        return this.loadingTasks.isLoading(playerUniqueId);
    }

    public StoredPlayerData<P, I> findCached(UUID playerUniqueId) {
        StoredPlayerSession<P, I> session = this.sessions.get(playerUniqueId);
        return session == null ? null : session.snapshot();
    }

    public StoredPlayerSession<P, I> findSession(UUID playerUniqueId) {
        return this.sessions.get(playerUniqueId);
    }

    public void putSession(UUID playerUniqueId, StoredPlayerSession<P, I> session) {
        this.sessions.put(playerUniqueId, session);
    }

    public StoredPlayerSession<P, I> removeSession(UUID playerUniqueId) {
        return this.sessions.remove(playerUniqueId);
    }

    public Collection<StoredPlayerSession<P, I>> sessions() {
        return this.sessions.values();
    }

    public CompletableFuture<StoredPlayerData<P, I>> getOrStartLoading(
            UUID playerUniqueId,
            Supplier<CompletableFuture<StoredPlayerData<P, I>>> taskSupplier
    ) {
        return this.getOrStartLoading(playerUniqueId, null, taskSupplier);
    }

    public CompletableFuture<StoredPlayerData<P, I>> getOrStartLoading(
            UUID playerUniqueId,
            Object owner,
            Supplier<CompletableFuture<StoredPlayerData<P, I>>> taskSupplier
    ) {
        return this.loadingTasks.getOrStartLoading(playerUniqueId, owner, taskSupplier);
    }

    public void finishLoading(UUID playerUniqueId) {
        this.loadingTasks.finishLoading(playerUniqueId);
    }

    public void finishLoading(UUID playerUniqueId, Object owner) {
        this.loadingTasks.finishLoading(playerUniqueId, owner);
    }

    public void cancelLoading(UUID playerUniqueId, Object owner) {
        this.loadingTasks.cancelLoading(playerUniqueId, owner);
    }

    public boolean isCurrentLoading(UUID playerUniqueId, Object owner) {
        return this.loadingTasks.isCurrentLoading(playerUniqueId, owner);
    }

    public void clear() {
        this.sessions.clear();
        this.loadingTasks.clear();
    }
}
