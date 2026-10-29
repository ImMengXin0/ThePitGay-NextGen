package cn.mythicland.framework.bukkit.store;

import org.bukkit.entity.Player;

import java.util.UUID;

public interface StoredPlayerSaveService<P, I> {
    void start(StoredPlayerCache<P, I> cache);

    void stop();

    StoredPlayerData<P, I> reactivate(Player player, StoredPlayerCache<P, I> cache);

    void saveAndRemove(Player player, StoredPlayerCache<P, I> cache);

    void captureOnlineInventories(StoredPlayerCache<P, I> cache);

    void flushAllNow(StoredPlayerCache<P, I> cache);

    void flushAllNow(StoredPlayerCache<P, I> cache, boolean announcePlayers);

    void shutdownAndFlush(StoredPlayerCache<P, I> cache, boolean announcePlayers);

    boolean flushNow(UUID playerUniqueId, StoredPlayerCache<P, I> cache);

    void flushAsync(UUID playerUniqueId, StoredPlayerCache<P, I> cache);

    void clear();
}
