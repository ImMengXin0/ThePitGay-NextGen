package cn.mythicland.framework.bukkit;

import cn.mythicland.framework.bukkit.task.TaskScheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChatTabCompleteEvent;

import java.util.Map;
import java.util.List;
import java.util.function.Function;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class ChatInputSessions implements Listener, AutoCloseable {
    private final TaskScheduler scheduler;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public ChatInputSessions(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void begin(Player player, Object owner, Predicate<String> consumes, Input input, Runnable cleanup) {
        this.begin(player, owner, consumes, input, cleanup, null);
    }

    public void begin(
            Player player,
            Object owner,
            Predicate<String> consumes,
            Input input,
            Runnable cleanup,
            Function<String, List<String>> completions
    ) {
        Session session = new Session(player, owner, consumes, input, cleanup, completions);
        Session previous = this.sessions.remove(player.getUniqueId());
        if (previous != null) previous.cleanup.run();
        this.sessions.put(player.getUniqueId(), session);
    }

    public void begin(Player player, Object owner, Input input, Runnable cleanup) {
        begin(player, owner, text -> true, input, cleanup);
    }

    public boolean contains(UUID player, Object owner) {
        Session session = this.sessions.get(player);
        return session != null && session.owner == owner;
    }

    public void clear(UUID player, Object owner) {
        Session session = this.sessions.get(player);
        if (session != null && session.owner == owner && this.sessions.remove(player, session)) session.cleanup.run();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Session session = this.sessions.get(player.getUniqueId());
        if (session == null || session.player != player) return;
        String text = event.getMessage().trim();
        boolean cancel = text.equalsIgnoreCase("cancel");
        if (!cancel && !session.consumes.test(text)) return;
        event.setCancelled(true);
        this.scheduler.executeSync(() -> {
            if (this.sessions.get(player.getUniqueId()) != session || !player.isOnline()) return;
            if (session.input.accept(player, text, cancel) == Result.FINISH
                    && this.sessions.remove(player.getUniqueId(), session)) session.cleanup.run();
        });
    }

    @EventHandler
    public void onTabComplete(PlayerChatTabCompleteEvent event) {
        Session session = this.sessions.get(event.getPlayer().getUniqueId());
        if (session == null || session.player != event.getPlayer() || session.completions == null) return;
        event.getTabCompletions().clear();
        event.getTabCompletions().addAll(session.completions.apply(event.getChatMessage()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Session session = this.sessions.remove(event.getPlayer().getUniqueId());
        if (session != null) session.cleanup.run();
    }

    @Override
    public void close() {
        for (Session session : this.sessions.values()) clear(session.player.getUniqueId(), session.owner);
    }

    public enum Result { RETRY, FINISH }

    @FunctionalInterface
    public interface Input {
        Result accept(Player player, String text, boolean cancelled);
    }

    private record Session(Player player, Object owner, Predicate<String> consumes, Input input, Runnable cleanup,
                           Function<String, List<String>> completions) {
    }
}
