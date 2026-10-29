package cn.mythicland.framework.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class IdentityLruCache<K, V> {
    private final int maxSize;
    private final Map<IdentityKey<K>, V> values;

    public IdentityLruCache(int maxSize) {
        this.maxSize = Math.max(1, maxSize);
        this.values = new LinkedHashMap<>(
                this.maxSize,
                0.75F,
                true
        ) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<IdentityKey<K>, V> eldest) {
                return this.size() > IdentityLruCache.this.maxSize;
            }
        };
    }

    public synchronized V get(K key) {
        if (key == null) return null;
        return this.values.get(new IdentityKey<>(key));
    }

    public synchronized V computeIfAbsent(K key, Function<K, V> mappingFunction) {
        if (key == null) return null;
        IdentityKey<K> identityKey = new IdentityKey<>(key);
        V existing = this.values.get(identityKey);
        if (existing != null) return existing;
        V computed = mappingFunction.apply(key);
        if (computed != null) {
            this.values.put(identityKey, computed);
        }
        return computed;
    }

    public synchronized void put(K key, V value) {
        if (key == null || value == null) return;
        this.values.put(new IdentityKey<>(key), value);
    }

    public synchronized void clear() {
        this.values.clear();
    }

    private record IdentityKey<K>(K value) {
        @Override
        public boolean equals(Object other) {
            return other instanceof IdentityKey<?> key && this.value == key.value;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this.value);
        }
    }
}
