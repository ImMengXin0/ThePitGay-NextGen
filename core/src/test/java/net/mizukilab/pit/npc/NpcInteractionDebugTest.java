package net.mizukilab.pit.npc;

import cn.charlotte.pit.ThePit;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.*;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NpcInteractionDebugTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private MockedStatic<ProtocolLibrary> protocol;
    private ProtocolManager manager;
    private Player player;
    private final List<Runnable> expiry = new ArrayList<>();

    @Before public void setup() {
        bukkit = net.mizukilab.pit.PaperTestBootstrap.mockBukkit();
        pit = mockStatic(ThePit.class);
        protocol = mockStatic(ProtocolLibrary.class);
        ThePit owner = mock(ThePit.class);
        pit.when(ThePit::getInstance).thenReturn(owner);
        when(owner.getLogger()).thenReturn(mock(Logger.class));
        when(owner.getDescription()).thenReturn(new PluginDescriptionFile("ThePitUltimate", "test", ThePit.class.getName()));
        NpcFactory factory = mock(NpcFactory.class);
        when(owner.getNpcFactory()).thenReturn(factory);
        when(factory.getPitNpc()).thenReturn(List.of());
        manager = mock(ProtocolManager.class);
        protocol.when(ProtocolLibrary::getProtocolManager).thenReturn(manager);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTaskLater(eq(owner), any(Runnable.class), eq(1200L))).thenAnswer(call -> {
            expiry.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Tester");
    }

    @After public void cleanup() {
        NpcInteractionDebug.close();
        protocol.close(); pit.close(); bukkit.close();
    }

    @Test public void disabledTracingDoesNotEvaluateDetailsOrTouchProtocolLib() {
        NpcInteractionDebug.trace(player, "unused", () -> { fail("Disabled trace evaluated"); return ""; });
        verifyNoInteractions(manager);
    }

    @Test public void repeatedRequestsKeepOneObserverAndOldTimeoutCannotEndANewSession() {
        NpcInteractionDebug.start(player);
        NpcInteractionDebug.start(player);
        verify(manager).addPacketListener(any(PacketAdapter.class));
        expiry.getFirst().run();
        verify(manager, never()).removePacketListener(any());
        expiry.getLast().run();
        verify(manager).removePacketListener(any(PacketAdapter.class));
        NpcInteractionDebug.trace(player, "expired", () -> { fail("Expired trace evaluated"); return ""; });
    }

    @Test public void quitAndShutdownDetachObserverAndTraceOutputIsBounded() {
        NpcInteractionDebug.start(player);
        AtomicInteger details = new AtomicInteger();
        for (int i = 0; i < 1000; i++) NpcInteractionDebug.trace(player, "spam", () -> {
            details.incrementAndGet(); return "";
        });
        assertTrue(details.get() <= 120);
        NpcInteractionDebug.forget(player);
        verify(manager).removePacketListener(any(PacketAdapter.class));
        NpcInteractionDebug.start(player);
        NpcInteractionDebug.close();
        verify(manager, times(2)).removePacketListener(any(PacketAdapter.class));
    }
}
