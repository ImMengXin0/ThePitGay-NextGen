package net.mizukilab.pit.util.sign;

import com.comphenix.protocol.*;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.reflect.StructureModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.*;
import org.junit.*;
import org.mockito.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

public class SignGuiLifecycleTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ProtocolLibrary> protocols;
    private ProtocolManager protocol;
    private PacketListener receiver;
    private JavaPlugin plugin;
    private CraftPlayer player;
    private World world;
    private SignGui gui;
    private SignGui.SignGUIListener response;
    private final List<Runnable> pending = new ArrayList<>(), timeouts = new ArrayList<>();
    private final List<BukkitTask> timeoutTasks = new ArrayList<>();
    @Before public void setup() {
        com.comphenix.protocol.utility.MinecraftVersion.setCurrentVersion(new com.comphenix.protocol.utility.MinecraftVersion(1, 21, 11));
        bukkit = net.mizukilab.pit.PaperTestBootstrap.mockBukkit(); protocols = mockStatic(ProtocolLibrary.class);
        protocol = mock(ProtocolManager.class); protocols.when(ProtocolLibrary::getProtocolManager).thenReturn(protocol);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        BukkitScheduler scheduler = mock(BukkitScheduler.class); bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        plugin = mock(JavaPlugin.class); world = mock(World.class); player = mock(CraftPlayer.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID()); when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world); when(player.getLocation()).thenReturn(new Location(world, 1, 64, 1));
        Block block = mock(Block.class); when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        when(block.getLocation()).thenAnswer(call -> new Location(world, 1, 64, 1));
        when(block.getBlockData()).thenReturn(mock(BlockData.class));
        ServerPlayer handle = mock(ServerPlayer.class); handle.connection = mock(ServerGamePacketListenerImpl.class);
        when(player.getHandle()).thenReturn(handle);
        bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            pending.add(call.getArgument(1)); return mock(BukkitTask.class);
        });
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), eq(1200L))).thenAnswer(call -> {
            timeouts.add(call.getArgument(1)); BukkitTask task = mock(BukkitTask.class); timeoutTasks.add(task); return task;
        });
        response = mock(SignGui.SignGUIListener.class); gui = new SignGui(plugin);
        ArgumentCaptor<PacketListener> captor = ArgumentCaptor.forClass(PacketListener.class);
        verify(protocol).addPacketListener(captor.capture()); receiver = captor.getValue();
    }
    private void open() { gui.open(player, new String[]{"a", "b", "c", "d"}, response); }
    private PacketEvent submit(int y) {
        var packet = new ServerboundSignUpdatePacket(new BlockPos(1, y, 1), true, "one", "two", "three", "four");
        var container = new PacketContainer(PacketType.Play.Client.UPDATE_SIGN, packet,
                new StructureModifier<>(packet.getClass()).withTarget(packet));
        PacketEvent event = PacketEvent.fromClient(this, container, player); receiver.onPacketReceiving(event); return event;
    }
    @After public void close() { bukkit.when(Bukkit::isPrimaryThread).thenReturn(true); gui.close(); protocols.close(); bukkit.close(); }
    @Test public void duplicatePacketsScheduleOneMainThreadResponseAndCancelExpiry() {
        open(); bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
        assertTrue(submit(61).isCancelled()); submit(61); assertEquals(1, pending.size()); verifyNoInteractions(response);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true); pending.getFirst().run();
        verify(response).onSignDone(eq(player), argThat(lines -> Arrays.equals(lines, new String[]{"one", "two", "three", "four"})));
        verify(timeoutTasks.getFirst()).cancel(); timeouts.getFirst().run(); verifyNoMoreInteractions(response);
    }
    @Test public void wrongPositionCannotConsumeTheSession() {
        open(); assertFalse(submit(60).isCancelled()); verifyNoInteractions(response);
        assertTrue(submit(61).isCancelled()); verify(response).onSignDone(eq(player), any(String[].class));
    }
    @Test public void replacementAndWorldChangeDiscardAlreadyQueuedResponses() {
        open(); bukkit.when(Bukkit::isPrimaryThread).thenReturn(false); submit(61);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true); open(); pending.getFirst().run(); verifyNoInteractions(response);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false); submit(61);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true); when(player.getWorld()).thenReturn(mock(World.class));
        gui.onWorldChange(new PlayerChangedWorldEvent(player, world)); pending.get(1).run(); verifyNoInteractions(response);
        timeoutTasks.forEach(task -> verify(task).cancel());
    }
    @Test public void timeoutAndPluginCloseRestoreTheClientBlockAndReleaseListeners() {
        open(); timeouts.getFirst().run(); verifyNoInteractions(response);
        open(); gui.close(); gui.close(); timeoutTasks.forEach(task -> verify(task).cancel());
        verify(protocol).removePacketListener(receiver);
        verify(player, times(4)).sendBlockChange(any(Location.class), nullable(BlockData.class));
        assertThrows(IllegalStateException.class, this::open);
    }
}
