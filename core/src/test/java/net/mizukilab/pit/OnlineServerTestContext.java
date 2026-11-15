package net.mizukilab.pit;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import java.lang.reflect.Field;
import java.util.*;
import static org.mockito.Mockito.*;

/** Replaces online-player and scheduling services; retains Paper registry services. */
public final class OnlineServerTestContext implements AutoCloseable {
    private final Field serverField;
    private final Server previous;
    private final Server server;
    private List<Player> players = List.of();
    private boolean primary = true;
    public OnlineServerTestContext(BukkitScheduler scheduler, PluginManager manager) throws Exception {
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previous = (Server) serverField.get(null);
        server = mock(Server.class, call -> call.getMethod().invoke(previous, call.getArguments()));
        doAnswer(call -> players).when(server).getOnlinePlayers();
        doAnswer(call -> primary).when(server).isPrimaryThread();
        doReturn(scheduler).when(server).getScheduler();
        doReturn(manager).when(server).getPluginManager();
        doAnswer(call -> players.stream().filter(p -> p.getUniqueId().equals(call.getArgument(0)))
                .findFirst().orElse(null)).when(server).getPlayer(any(UUID.class));
        serverField.set(null, server);
    }
    public void players(List<Player> players) { this.players = List.copyOf(players); }
    public void primary(boolean primary) { this.primary = primary; }
    @Override public void close() throws Exception { serverField.set(null, previous); }
}
