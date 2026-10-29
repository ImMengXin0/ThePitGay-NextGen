package cn.mythicland.framework.bukkit.store;

import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

public interface StoredPlayerLoadService<P, I> {
    CompletableFuture<StoredPlayerData<P, I>> load(Player player, StoredPlayerCache<P, I> cache);
}
