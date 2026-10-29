package cn.mythicland.framework.cooldown;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public final class Cooldowns<K> {
    private final Map<K, Long> cooldownUntilMillis = new HashMap<>();

    public void start(K key, long durationMillis, long nowMillis) {
        if (key == null || durationMillis <= 0L) return;
        this.cooldownUntilMillis.put(key, nowMillis + durationMillis);
    }

    public boolean tryStart(K key, long durationMillis, long nowMillis) {
        if (key == null || durationMillis <= 0L) return false;
        if (this.remainingMillis(key, nowMillis) > 0L) return false;
        this.start(key, durationMillis, nowMillis);
        return true;
    }

    public long remainingMillis(K key, long nowMillis) {
        if (key == null) return 0L;
        long cooldownUntil = this.cooldownUntilMillis.getOrDefault(key, 0L);
        if (cooldownUntil <= nowMillis) {
            this.cooldownUntilMillis.remove(key);
            return 0L;
        }
        return cooldownUntil - nowMillis;
    }

    public void reduce(K key, long reductionMillis, long nowMillis) {
        if (key == null || reductionMillis <= 0L) return;
        long cooldownUntil = this.cooldownUntilMillis.getOrDefault(key, 0L);
        if (cooldownUntil <= nowMillis) {
            this.cooldownUntilMillis.remove(key);
            return;
        }
        this.cooldownUntilMillis.put(key, Math.max(nowMillis, cooldownUntil - reductionMillis));
    }

    public void clear(Predicate<K> predicate) {
        this.cooldownUntilMillis.keySet().removeIf(predicate);
    }

    public int size() {
        return this.cooldownUntilMillis.size();
    }

    public Map<K, Long> remaining(Predicate<K> predicate, long nowMillis) {
        Map<K, Long> remaining = new HashMap<>();
        this.cooldownUntilMillis.forEach((key, expiresAt) -> {
            if (predicate.test(key) && expiresAt > nowMillis) remaining.put(key, expiresAt - nowMillis);
        });
        return remaining;
    }
}
