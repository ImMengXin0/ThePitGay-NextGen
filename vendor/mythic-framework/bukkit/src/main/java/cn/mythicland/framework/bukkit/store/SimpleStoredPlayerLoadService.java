package cn.mythicland.framework.bukkit.store;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SimpleStoredPlayerLoadService<P, I> implements StoredPlayerLoadService<P, I> {
    private final Logger logger;
    private final Executor ioExecutor;
    private final StoredPlayerRepository<P, I> repository;
    private final StoredPlayerInventory<I> inventoryAdapter;
    private final StoredPlayerProfile<P> profileAdapter;
    private final Consumer<Player> loadFailureHandler;
    private final BiConsumer<Player, Runnable> mainThreadExecutor;

    public SimpleStoredPlayerLoadService(
            Logger logger,
            Executor ioExecutor,
            StoredPlayerRepository<P, I> repository,
            StoredPlayerInventory<I> inventoryAdapter,
            StoredPlayerProfile<P> profileAdapter,
            Consumer<Player> loadFailureHandler,
            Consumer<Runnable> mainThreadExecutor
    ) {
        this(logger, ioExecutor, repository, inventoryAdapter, profileAdapter, loadFailureHandler,
                (player, work) -> mainThreadExecutor.accept(work));
    }

    public SimpleStoredPlayerLoadService(
            Logger logger,
            Executor ioExecutor,
            StoredPlayerRepository<P, I> repository,
            StoredPlayerInventory<I> inventoryAdapter,
            StoredPlayerProfile<P> profileAdapter,
            Consumer<Player> loadFailureHandler,
            BiConsumer<Player, Runnable> mainThreadExecutor
    ) {
        this.logger = logger;
        this.ioExecutor = ioExecutor;
        this.repository = repository;
        this.inventoryAdapter = inventoryAdapter;
        this.profileAdapter = profileAdapter;
        this.loadFailureHandler = loadFailureHandler;
        this.mainThreadExecutor = mainThreadExecutor;
    }

    @Override
    public CompletableFuture<StoredPlayerData<P, I>> load(Player player, StoredPlayerCache<P, I> cache) {
        return this.loadRetained(player, cache, null);
    }

    public CompletableFuture<StoredPlayerData<P, I>> loadRetained(
            Player player,
            StoredPlayerCache<P, I> cache,
            StoredPlayerSession<P, I> retained
    ) {
        UUID playerUniqueId = player.getUniqueId();
        StoredPlayerSession<P, I> existing = cache.findSession(playerUniqueId);
        if (existing != null) return CompletableFuture.completedFuture(existing.snapshot());

        return cache.getOrStartLoading(
                playerUniqueId,
                player,
                () -> this.loadAsync(playerUniqueId, player, cache, retained)
        );
    }

    private CompletableFuture<StoredPlayerData<P, I>> loadAsync(
            UUID playerUniqueId,
            Player player,
            StoredPlayerCache<P, I> cache,
            StoredPlayerSession<P, I> retained
    ) {
        return CompletableFuture.supplyAsync(
                        () -> this.loadSnapshot(playerUniqueId, retained),
                        this.ioExecutor
                )
                .handle((loadedData, throwable) -> {
                    if (throwable != null || loadedData == null) {
                        Throwable cause = throwable == null
                                ? new IllegalStateException("Loaded player state is null for " + playerUniqueId)
                                : throwable;
                        this.logger.log(
                                Level.SEVERE,
                                "Failed to load player data for " + player.getName(),
                                cause
                        );
                        return this.disconnectOnMain(playerUniqueId, player, cache, cause);
                    }
                    return this.applyLoadedDataOnMain(playerUniqueId, player, loadedData.data(), loadedData.inventory(), cache, retained);
                })
                .thenCompose(completion -> completion);
    }

    private PreparedLoad<P, I> loadSnapshot(UUID playerUniqueId, StoredPlayerSession<P, I> retained) {
        try {
            StoredPlayerData<P, I> data = retained == null ? this.repository.load(playerUniqueId) : retained.snapshot();
            return data == null ? null : new PreparedLoad<>(data, this.inventoryAdapter.prepare(data.inventoryData()));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load player data for " + playerUniqueId, exception);
        }
    }

    private CompletableFuture<StoredPlayerData<P, I>> applyLoadedDataOnMain(
            UUID playerUniqueId,
            Player fallbackPlayer,
            StoredPlayerData<P, I> loadedData,
            StoredPlayerInventory.Prepared inventory,
            StoredPlayerCache<P, I> cache,
            StoredPlayerSession<P, I> retained
    ) {
        CompletableFuture<StoredPlayerData<P, I>> completion = new CompletableFuture<>();
        try {
            this.mainThreadExecutor.accept(fallbackPlayer, new Runnable() {
                @Override
                public void run() {
                    if (!cache.isCurrentLoading(playerUniqueId, fallbackPlayer)) {
                        completion.complete(loadedData);
                        return;
                    }
                    boolean rescheduled = false;
                    StoredPlayerSession<P, I> installed = null;
                    try {
                        Player player = SimpleStoredPlayerLoadService.this.resolveOnlinePlayer(playerUniqueId, fallbackPlayer);
                        if (player == null || !player.isOnline()) {
                            completion.complete(loadedData);
                            return;
                        }
                        if (!inventory.advance()) {
                            SimpleStoredPlayerLoadService.this.mainThreadExecutor.accept(fallbackPlayer, this);
                            rescheduled = true;
                            return;
                        }
                        installed = retained != null ? retained
                                : new StoredPlayerSession<>(loadedData, SimpleStoredPlayerLoadService.this.profileAdapter);
                        cache.putSession(playerUniqueId, installed);
                        inventory.apply(player);
                        completion.complete(loadedData);
                    } catch (Throwable throwable) {
                        if (installed != null && cache.findSession(playerUniqueId) == installed)
                            cache.removeSession(playerUniqueId);
                        completion.completeExceptionally(throwable);
                    } finally {
                        if (!rescheduled) cache.finishLoading(playerUniqueId, fallbackPlayer);
                    }
                }
            });
        } catch (Throwable throwable) {
            cache.finishLoading(playerUniqueId, fallbackPlayer);
            completion.completeExceptionally(throwable);
        }
        return completion;
    }

    private CompletableFuture<StoredPlayerData<P, I>> disconnectOnMain(
            UUID playerUniqueId,
            Player fallbackPlayer,
            StoredPlayerCache<P, I> cache,
            Throwable cause
    ) {
        CompletableFuture<StoredPlayerData<P, I>> completion = new CompletableFuture<>();
        try {
            this.mainThreadExecutor.accept(fallbackPlayer, () -> {
                if (!cache.isCurrentLoading(playerUniqueId, fallbackPlayer)) {
                    completion.completeExceptionally(cause);
                    return;
                }
                try {
                    Player player = this.resolveOnlinePlayer(playerUniqueId, fallbackPlayer);
                    if (player != null && player.isOnline()) {
                        this.loadFailureHandler.accept(player);
                    }
                    completion.completeExceptionally(cause);
                } catch (Throwable throwable) {
                    cause.addSuppressed(throwable);
                    completion.completeExceptionally(cause);
                } finally {
                    cache.finishLoading(playerUniqueId, fallbackPlayer);
                }
            });
        } catch (Throwable throwable) {
            cache.finishLoading(playerUniqueId, fallbackPlayer);
            completion.completeExceptionally(throwable);
        }
        return completion;
    }

    private Player resolveOnlinePlayer(UUID playerUniqueId, Player fallbackPlayer) {
        Player player = Bukkit.getPlayer(playerUniqueId);
        if (player != null) return player == fallbackPlayer ? player : null;
        if (fallbackPlayer != null && fallbackPlayer.isOnline()
                && playerUniqueId.equals(fallbackPlayer.getUniqueId())) {
            return fallbackPlayer;
        }
        return null;
    }

    private record PreparedLoad<P, I>(StoredPlayerData<P, I> data, StoredPlayerInventory.Prepared inventory) {
    }

}
