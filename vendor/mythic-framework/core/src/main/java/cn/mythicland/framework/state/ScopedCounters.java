package cn.mythicland.framework.state;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public final class ScopedCounters<O, K> {
    private final Map<O, Object2IntOpenHashMap<CounterKey<K, O>>> counters = new Object2ObjectOpenHashMap<>();

    public int preview(O owner, K key, O counterpart) {
        var owned = this.counters.get(owner);
        int count = owned == null ? 0 : owned.getInt(new CounterKey<>(key, counterpart));
        return this.nextCount(key, count);
    }

    public int record(O owner, K key, O counterpart) {
        var owned = this.counters.computeIfAbsent(owner, ignored -> new Object2IntOpenHashMap<>());
        var counterKey = new CounterKey<>(key, counterpart);
        int count = this.nextCount(key, owned.getInt(counterKey));
        owned.put(counterKey, count);
        return count;
    }

    public void set(O owner, K key, O counterpart, int count) {
        this.counters.computeIfAbsent(owner, ignored -> new Object2IntOpenHashMap<>())
                .put(new CounterKey<>(key, counterpart), count);
    }

    public boolean consume(O owner, K key, O counterpart, int requiredCount) {
        if (requiredCount <= 0) throw new IllegalArgumentException("requiredCount must be positive: " + requiredCount);
        var owned = this.counters.get(owner);
        if (owned == null) return false;
        var counterKey = new CounterKey<>(key, counterpart);
        if (owned.getInt(counterKey) < requiredCount) return false;
        owned.removeInt(counterKey);
        if (owned.isEmpty()) this.counters.remove(owner);
        return true;
    }

    public void clear(O owner, K key, O counterpart) {
        var owned = this.counters.get(owner);
        if (owned == null) return;
        owned.removeInt(new CounterKey<>(key, counterpart));
        if (owned.isEmpty()) this.counters.remove(owner);
    }

    public void clear(O owner, Predicate<K> keys) {
        var owned = this.counters.get(owner);
        if (owned == null) return;
        owned.keySet().removeIf(key -> keys.test(key.key()));
        if (owned.isEmpty()) this.counters.remove(owner);
    }

    public void clearMatching(BiPredicate<K, O> predicate) {
        var iterator = this.counters.values().iterator();
        while (iterator.hasNext()) {
            var owned = iterator.next();
            owned.keySet().removeIf(key -> predicate.test(key.key(), key.counterpart()));
            if (owned.isEmpty()) iterator.remove();
        }
    }

    public void clearOwner(O owner) {
        this.counters.remove(owner);
    }

    public void clear() {
        this.counters.clear();
    }

    public void forEach(O owner, CounterConsumer<K, O> consumer) {
        var owned = this.counters.get(owner);
        if (owned == null) return;
        for (var entry : owned.object2IntEntrySet()) {
            var key = entry.getKey();
            consumer.accept(key.key(), key.counterpart(), entry.getIntValue());
        }
    }

    private int nextCount(K key, int count) {
        try {
            return Math.addExact(count, 1);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Counter overflow for runtime key: " + key, exception);
        }
    }

    @FunctionalInterface
    public interface CounterConsumer<K, O> {
        void accept(K key, O counterpart, int count);
    }

    private record CounterKey<K, O>(K key, O counterpart) {
    }
}
