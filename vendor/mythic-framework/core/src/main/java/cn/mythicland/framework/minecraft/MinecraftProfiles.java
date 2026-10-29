package cn.mythicland.framework.minecraft;

import cn.mythicland.framework.cache.KeyedLoadingCache;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class MinecraftProfiles {
    private final HttpClient client;
    private final URI endpoint;
    private final URI fallbackEndpoint;
    private final Clock clock;
    private final KeyedLoadingCache<String, CachedProfile> cache = new KeyedLoadingCache<>();

    public MinecraftProfiles() {
        this(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                URI.create("https://api.mojang.com/users/profiles/minecraft/"),
                URI.create("https://eclipse.skinsrestorer.net/mojang/uuid/"),
                Clock.systemUTC()
        );
    }

    public MinecraftProfiles(HttpClient client, URI endpoint) {
        this(client, endpoint, Clock.systemUTC());
    }

    MinecraftProfiles(HttpClient client, URI endpoint, Clock clock) {
        this(client, endpoint, null, clock);
    }

    MinecraftProfiles(
            HttpClient client,
            URI endpoint,
            URI fallbackEndpoint,
            Clock clock
    ) {
        this.client = client;
        this.endpoint = endpoint;
        this.fallbackEndpoint = fallbackEndpoint;
        this.clock = clock;
    }

    public Optional<Profile> findByName(String name) {
        if (!name.matches("[A-Za-z0-9_]{1,16}")) throw new IllegalArgumentException("Invalid Minecraft name");
        String key = name.toLowerCase(Locale.ROOT);
        try {
            CachedProfile cached = cache.getOrStartLoading(
                    key,
                    () -> CompletableFuture.completedFuture(loadProfile(name, key))
            ).join();
            return Optional.ofNullable(cached.profile());
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException failure) throw failure;
            throw new IllegalStateException("Minecraft profile lookup failed for " + name, exception.getCause());
        }
    }

    private CachedProfile loadProfile(String name, String key) {
        CachedProfile cached = cache.get(key);
        if (cached != null && clock.millis() < cached.expiresAtMillis()) return cached;
        cache.values().stream()
                .filter(entry -> clock.millis() >= entry.expiresAtMillis())
                .forEach(entry -> cache.remove(entry.profile().name().toLowerCase(Locale.ROOT)));
        Profile profile = lookup(name).orElse(null);
        CachedProfile result = new CachedProfile(profile, clock.millis() + Duration.ofMinutes(1).toMillis());
        if (profile != null) cache.put(key, result);
        return result;
    }

    private Optional<Profile> lookup(String name) {
        HttpRequest request = HttpRequest.newBuilder(endpoint.resolve(name))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 204 || response.statusCode() == 404) return Optional.empty();
            if (fallbackEndpoint != null && (response.statusCode() == 429 || response.statusCode() >= 500)) {
                return lookupFallback(name);
            }
            if (response.statusCode() != 200) throw new IllegalStateException("Minecraft profile lookup failed: HTTP " + response.statusCode());
            JsonObject object = JsonParser.parseString(response.body()).getAsJsonObject();
            String id = object.get("id").getAsString();
            String canonicalName = object.get("name").getAsString();
            if (!id.matches("[0-9a-fA-F]{32}") || !canonicalName.equalsIgnoreCase(name)) {
                throw new IllegalStateException("Invalid Minecraft profile response");
            }
            UUID uuid = UUID.fromString(id.substring(0, 8) + "-" + id.substring(8, 12) + "-"
                    + id.substring(12, 16) + "-" + id.substring(16, 20) + "-" + id.substring(20));
            return Optional.of(new Profile(uuid, canonicalName));
        } catch (IOException exception) {
            if (fallbackEndpoint != null) return lookupFallback(name);
            throw new IllegalStateException("Minecraft profile lookup unavailable", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Minecraft profile lookup interrupted", exception);
        }
    }

    private Optional<Profile> lookupFallback(String name) {
        HttpRequest request = HttpRequest.newBuilder(fallbackEndpoint.resolve(name))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Minecraft profile fallback lookup failed: HTTP " + response.statusCode());
            }
            JsonObject object = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonElement exists = object.get("exists");
            JsonElement id = object.get("uuid");
            if (exists == null || !exists.isJsonPrimitive() || !exists.getAsJsonPrimitive().isBoolean() || id == null) {
                throw new IllegalStateException("Invalid Minecraft profile fallback response");
            }
            if (!exists.getAsBoolean()) {
                if (!id.isJsonNull()) throw new IllegalStateException("Invalid Minecraft profile fallback response");
                return Optional.empty();
            }
            if (!id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString()) {
                throw new IllegalStateException("Invalid Minecraft profile fallback response");
            }
            UUID uuid = UUID.fromString(id.getAsString());
            if (!uuid.toString().equalsIgnoreCase(id.getAsString())) {
                throw new IllegalStateException("Invalid Minecraft profile fallback response");
            }
            return Optional.of(new Profile(uuid, name));
        } catch (IOException exception) {
            throw new IllegalStateException("Minecraft profile fallback lookup unavailable", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Minecraft profile fallback lookup interrupted", exception);
        }
    }

    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    public record Profile(UUID uuid, String name) {
    }

    private record CachedProfile(Profile profile, long expiresAtMillis) {
    }
}
