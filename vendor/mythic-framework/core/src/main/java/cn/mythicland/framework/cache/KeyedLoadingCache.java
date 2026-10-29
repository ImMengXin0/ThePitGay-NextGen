package cn.mythicland.framework.cache;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class KeyedLoadingCache<K, V> {
    private final Map<K, V> values = new ConcurrentHashMap<>();
    private final Map<K, LoadingTask<V>> loadingTasks = new ConcurrentHashMap<>();

    public boolean contains(K key) {
        return this.values.containsKey(key);
    }

    public V get(K key) {
        return this.values.get(key);
    }

    public Collection<V> values() {
        return Collections.unmodifiableCollection(this.values.values());
    }

    public void put(K key, V value) {
        this.values.put(key, value);
    }

    public V remove(K key) {
        return this.values.remove(key);
    }

    public boolean isLoading(K key) {
        return this.loadingTasks.containsKey(key);
    }

    public CompletableFuture<V> getOrStartLoading(
            K key,
            Supplier<CompletableFuture<V>> taskSupplier
    ) {
        return this.getOrStartLoading(key, null, taskSupplier);
    }

    public CompletableFuture<V> getOrStartLoading(
            K key,
            Object owner,
            Supplier<CompletableFuture<V>> taskSupplier
    ) {
        LoadingTask<V> existing = this.loadingTasks.get(key);
        if (existing != null) return existing.future();

        CompletableFuture<V> placeholder = new CompletableFuture<>();
        LoadingTask<V> loadingTask = new LoadingTask<>(owner, placeholder);
        LoadingTask<V> racing = this.loadingTasks.putIfAbsent(key, loadingTask);
        if (racing != null) return racing.future();

        try {
            CompletableFuture<V> started = taskSupplier.get();
            started.whenComplete((result, throwable) -> {
                this.loadingTasks.remove(key, loadingTask);
                if (throwable != null) {
                    placeholder.completeExceptionally(throwable);
                    return;
                }
                placeholder.complete(result);
            });
        } catch (RuntimeException exception) {
            this.loadingTasks.remove(key, loadingTask);
            placeholder.completeExceptionally(exception);
        }

        return placeholder;
    }

    public void finishLoading(K key) {
        this.loadingTasks.remove(key);
    }

    public void finishLoading(K key, Object owner) {
        this.removeLoading(key, owner);
    }

    public void cancelLoading(K key, Object owner) {
        this.removeLoading(key, owner);
    }

    public boolean isCurrentLoading(K key, Object owner) {
        LoadingTask<V> loadingTask = this.loadingTasks.get(key);
        return loadingTask != null && loadingTask.owner() == owner;
    }

    public void clear() {
        this.values.clear();
        this.loadingTasks.clear();
    }

    private void removeLoading(K key, Object owner) {
        LoadingTask<V> loadingTask = this.loadingTasks.get(key);
        if (loadingTask != null && loadingTask.owner() == owner) {
            this.loadingTasks.remove(key, loadingTask);
        }
    }

    private record LoadingTask<V>(Object owner, CompletableFuture<V> future) {
    }
}
