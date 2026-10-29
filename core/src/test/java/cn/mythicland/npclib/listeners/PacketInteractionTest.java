package cn.mythicland.npclib.listeners;

import cn.mythicland.npclib.NPCLib;
import cn.mythicland.npclib.api.events.NPCInteractEvent;
import cn.mythicland.npclib.internal.NPCBase;
import cn.mythicland.npclib.internal.NPCManager;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.*;
import org.junit.*;
import org.mockito.MockedStatic;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

public class PacketInteractionTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ProtocolLibrary> protocols;
    private PluginManager plugins;
    private BukkitScheduler scheduler;
    private NPCLib lib;
    private NPCBase npc;
    private Player player;
    private PacketListener listener;
    private final List<Runnable> pending = new ArrayList<>();

    @Before public void setup() throws Exception {
        com.comphenix.protocol.utility.MinecraftVersion.setCurrentVersion(
                new com.comphenix.protocol.utility.MinecraftVersion(1, 8, 8));
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getServer).thenReturn(mock(org.bukkit.Server.class, RETURNS_DEEP_STUBS));
        var packages = com.comphenix.protocol.utility.MinecraftReflection.class.getDeclaredMethod(
                "setMinecraftPackage", String.class, String.class);
        packages.setAccessible(true);
        packages.invoke(null, "net.minecraft.server.v1_8_R3", "org.bukkit.craftbukkit.v1_8_R3");
        protocols = mockStatic(ProtocolLibrary.class);
        protocols.when(ProtocolLibrary::getProtocolManager).thenReturn(mock(ProtocolManager.class));
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        plugins = mock(PluginManager.class);
        scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTask(any(), any(Runnable.class))).thenAnswer(call -> {
            pending.add(call.getArgument(1)); return mock(BukkitTask.class);
        });
        lib = mock(NPCLib.class);
        when(lib.getPlugin()).thenReturn(mock(JavaPlugin.class));
        player = mock(Player.class);
        World world = mock(World.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 1, 64, 0));
        npc = mock(NPCBase.class);
        when(npc.getEntityId()).thenReturn(2100000000);
        when(npc.getInstance()).thenReturn(lib);
        when(npc.getWorld()).thenReturn(world);
        when(npc.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(npc.isCreated()).thenReturn(true);
        when(npc.isShown(player)).thenReturn(true);
        NPCManager.add(npc);
        listener = new PacketListener(); listener.start(lib);
    }

    @After public void close() {
        if (listener != null) listener.stop();
        if (npc != null) NPCManager.remove(npc);
        if (protocols != null) protocols.close();
        if (bukkit != null) bukkit.close();
    }

    @Test public void interactAndInteractAtOpenAMenuOnlyOnce() {
        listener.handleInteract(player, npc.getEntityId(), false);
        listener.handleInteract(player, npc.getEntityId(), false);
        verify(plugins).callEvent(any(NPCInteractEvent.class));
    }

    @Test public void eachLibraryDispatchesOnlyItsOwnNpcs() {
        NPCLib other = mock(NPCLib.class);
        JavaPlugin plugin = lib.getPlugin();
        when(other.getPlugin()).thenReturn(plugin);
        PacketListener foreign = new PacketListener(); foreign.start(other);
        try {
            foreign.handleInteract(player, npc.getEntityId(), true);
            listener.handleInteract(player, npc.getEntityId(), true);
            verify(plugins).callEvent(any(NPCInteractEvent.class));
        } finally { foreign.stop(); }
    }

    @Test public void offThreadPacketsCoalesceAndRecheckVisibilityOnTheServerThread() {
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
        listener.handleInteract(player, npc.getEntityId(), false);
        listener.handleInteract(player, npc.getEntityId(), false);
        assertEquals(1, pending.size());
        verify(npc, never()).isShown(any());
        when(npc.isShown(player)).thenReturn(false);
        pending.get(0).run();
        verify(plugins, never()).callEvent(any());
    }

    @Test public void stoppingTheLibraryMakesQueuedClicksHarmless() {
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
        listener.handleInteract(player, npc.getEntityId(), true);
        listener.stop();
        pending.get(0).run();
        verify(plugins, never()).callEvent(any());
    }

    @Test public void destroyedOrDistantNpcsCannotOpenMenus() {
        World world = player.getWorld();
        when(player.getLocation()).thenReturn(new Location(world, 100, 64, 0));
        listener.handleInteract(player, npc.getEntityId(), true);
        NPCManager.remove(npc);
        listener.handleInteract(player, npc.getEntityId(), true);
        verify(plugins, never()).callEvent(any());
    }
}
