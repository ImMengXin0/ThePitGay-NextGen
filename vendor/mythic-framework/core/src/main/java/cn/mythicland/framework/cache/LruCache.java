package cn.mythicland.framework.cache;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LruCache<K, V> {
    private final int maxSize;
    private final Map<K, V> values;

    public LruCache(int maxSize) {
        this.maxSize = Math.max(1, maxSize);
        this.values = new LinkedHashMap<>(
                this.maxSize,
                0.75F,
                true
        ) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return this.size() > LruCache.this.maxSize;
            }
        };
    }

    public synchronized V get(K key) {
        if (key == null) return null;
        return this.values.get(key);
    }

    public synchronized void put(K key, V value) {
        if (key == null || value == null) return;
        this.values.put(key, value);
    }

    public synchronized V remove(K key) {
        if (key == null) return null;
        return this.values.remove(key);
    }

    public synchronized void clear() {
        this.values.clear();
    }
}
