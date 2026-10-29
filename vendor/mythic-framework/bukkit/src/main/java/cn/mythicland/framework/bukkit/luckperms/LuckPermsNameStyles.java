package cn.mythicland.framework.bukkit.luckperms;

import cn.mythicland.framework.text.PlayerNameStyle;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class LuckPermsNameStyles {
    private final LuckPerms luckPerms;

    public LuckPermsNameStyles(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    public PlayerNameStyle resolve(Player player, String group, Supplier<PlayerNameStyle> memberStyle) {
        User user = this.luckPerms.getPlayerAdapter(Player.class).getUser(player);
        QueryOptions options = this.luckPerms.getContextManager().getQueryOptions(player);
        return this.groupStyle(user, options, group).orElseGet(memberStyle);
    }

    public CompletableFuture<PlayerNameStyle> lookup(
            UUID playerId,
            String group,
            Supplier<CompletableFuture<PlayerNameStyle>> memberStyle
    ) {
        User cached = this.luckPerms.getUserManager().getUser(playerId);
        CompletableFuture<User> loaded = cached == null ? this.luckPerms.getUserManager().loadUser(playerId)
                : CompletableFuture.completedFuture(cached);
        return loaded.thenCompose(user ->
                this.groupStyle(user, user.getQueryOptions(), group)
                        .map(CompletableFuture::completedFuture)
                        .orElseGet(memberStyle));
    }

    public Optional<PlayerNameStyle> groupStyle(User user, QueryOptions options, String group) {
        boolean inheritsGroup = user.getInheritedGroups(options).stream()
                .anyMatch(inherited -> inherited.getName().equals(group));
        if (!inheritsGroup) return Optional.empty();
        String prefix = user.getCachedData().getMetaData(options).getPrefix();
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalStateException("Missing LuckPerms prefix for user " + user.getUniqueId() + " in group " + group);
        }
        return Optional.of(new PlayerNameStyle(prefix));
    }
}
