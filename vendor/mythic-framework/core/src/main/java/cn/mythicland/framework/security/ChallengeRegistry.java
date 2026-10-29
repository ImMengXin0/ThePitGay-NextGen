package cn.mythicland.framework.security;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChallengeRegistry<K, V> {
    private final Map<K, Challenge<V>> challenges = new ConcurrentHashMap<>();
    private final Clock clock;

    public ChallengeRegistry() {
        this(Clock.systemUTC());
    }

    public ChallengeRegistry(Clock clock) {
        this.clock = clock;
    }

    public UUID issue(K key, V value, Duration lifetime) {
        return issue(key, UUID.randomUUID(), value, lifetime);
    }

    public UUID issue(K key, UUID nonce, V value, Duration lifetime) {
        if (lifetime.isNegative() || lifetime.isZero()) throw new IllegalArgumentException("Challenge lifetime must be positive");
        challenges.put(key, new Challenge<>(nonce, value, clock.millis() + lifetime.toMillis()));
        return nonce;
    }

    public Optional<V> consume(K key, UUID nonce) {
        Challenge<V> challenge = challenges.get(key);
        if (challenge == null || !challenge.nonce().equals(nonce)) return Optional.empty();
        if (!challenges.remove(key, challenge) || clock.millis() >= challenge.expiresAt()) return Optional.empty();
        return Optional.of(challenge.value());
    }

    public Optional<V> remove(K key) {
        Challenge<V> challenge = challenges.remove(key);
        return challenge == null ? Optional.empty() : Optional.of(challenge.value());
    }

    public boolean contains(K key) {
        Challenge<V> challenge = challenges.get(key);
        return challenge != null && clock.millis() < challenge.expiresAt();
    }

    private record Challenge<V>(UUID nonce, V value, long expiresAt) {
    }
}
