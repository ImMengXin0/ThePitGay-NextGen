package cn.mythicland.framework.cache;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentityLruCacheTest {
    @Test
    void getShouldDistinguishEqualObjectsAndEvictLeastRecentlyUsedEntry() {
        IdentityLruCache<String, Integer> cache = new IdentityLruCache<>(2);
        String first = new String("equal");
        String second = new String("equal");
        String third = "third";
        cache.put(first, 1);
        cache.put(second, 2);
        assertEquals(1, cache.get(first));

        cache.put(third, 3);

        assertNull(cache.get(second));
        assertEquals(1, cache.get(first));
        assertEquals(3, cache.get(third));
    }

    @Test
    void computeIfAbsentShouldReuseResultsButNotCacheNullsOrFailures() {
        IdentityLruCache<Object, Integer> cache = new IdentityLruCache<>(2);
        Object key = new Object();
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> cache.computeIfAbsent(key, ignored -> {
            throw new IllegalStateException("cannot load");
        }));
        assertNull(cache.computeIfAbsent(key, ignored -> null));
        assertEquals(1, cache.computeIfAbsent(key, ignored -> calls.incrementAndGet()));
        assertEquals(1, cache.computeIfAbsent(key, ignored -> calls.incrementAndGet()));
        assertEquals(1, calls.get());

        cache.clear();
        assertEquals(2, cache.computeIfAbsent(key, ignored -> calls.incrementAndGet()));
    }

    @Test
    void putShouldIgnoreNullsAndKeepAtLeastOneEntry() {
        IdentityLruCache<Object, Integer> cache = new IdentityLruCache<>(0);
        Object first = new Object();
        Object second = new Object();
        cache.put(first, 1);
        cache.put(null, 2);
        cache.put(first, null);
        assertEquals(1, cache.get(first));
        assertNull(cache.get(null));
        assertNull(cache.computeIfAbsent(null, ignored -> {
            throw new AssertionError("null keys must not invoke the loader");
        }));

        cache.put(second, 2);
        assertNull(cache.get(first));
        assertEquals(2, cache.get(second));
    }
}
