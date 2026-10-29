package cn.mythicland.npclib.internal;

import cn.mythicland.npclib.NPCLib;
import cn.mythicland.npclib.api.skin.Skin;
import cn.mythicland.npclib.api.state.*;
import cn.mythicland.npclib.hologram.Hologram;
import net.minecraft.server.v1_8_R3.DispenserRegistry;
import org.bukkit.*;
import org.bukkit.craftbukkit.v1_8_R3.CraftServer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.*;
import org.mockito.MockedStatic;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Exercises viewer/cache lifecycle independently of a live Minecraft connection. */
public class NpcLifecycleTest {
    private MockedStatic<Bukkit> bukkit;
    private NPCLib library;
    private World world;
    private Player player;
    private RecordingNpc npc;

    @Before public void setup() {
        DispenserRegistry.c();
        bukkit = mockStatic(Bukkit.class);
        CraftServer server = mock(CraftServer.class);
        PluginManager plugins = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(plugins);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        bukkit.when(Bukkit::getViewDistance).thenReturn(10);
        world = mock(World.class);
        player = viewer();
        library = mock(NPCLib.class);
        when(library.getAutoHideDistance()).thenReturn(50.0);
        npc = new RecordingNpc(library, List.of("default"));
        npc.setLocation(new Location(world, 0, 64, 0));
        npc.disableFOV();
        npc.create();
    }

    private Player viewer() {
        Player result = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(result.getUniqueId()).thenReturn(id);
        when(result.getWorld()).thenReturn(world);
        when(result.getLocation()).thenReturn(new Location(world, 1, 64, 0));
        when(result.isOnline()).thenReturn(true);
        bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(result);
        return result;
    }

    @After public void cleanup() {
        if (npc != null) npc.destroy();
        if (bukkit != null) bukkit.close();
    }

    @Test public void clearingPersonalTextRestoresDefaultAndRebuildsDifferentLineCounts() {
        npc.show(player);
        Hologram first = npc.playerHologram.get(player.getUniqueId());
        npc.setText(player, List.of("one", "two"));
        verify(first).hide(player);
        npc.removeText(player);
        assertEquals(List.of("default"), npc.getText(player));
        npc.setText((List<String>) null);
        assertEquals(List.of(), npc.getText(player));
    }

    @Test public void unchangedTextDoesNotBuildPacketsAndInputListsAreCopied() {
        List<String> input = new ArrayList<>(List.of("custom"));
        npc.setText(player, input);
        input.set(0, "mutated");
        npc.show(player);
        Hologram hologram = npc.playerHologram.get(player.getUniqueId());
        npc.setText(player, List.of("custom"));
        verify(hologram, never()).getUpdatePackets(any());
        assertEquals(List.of("custom"), npc.getText(player));
    }

    @Test public void hiddenTextUpdatesCacheWithoutSendingAndDoesNotResubscribeAfterManualHide() {
        npc.show(player);
        npc.hide(player, true);
        Hologram hologram = npc.playerHologram.get(player.getUniqueId());
        npc.setText(player, List.of("latest"));
        verify(hologram).getUpdatePackets(List.of("latest"));
        verify(hologram, never()).update(any(), any());
        npc.hide(player);
        npc.show(player, true);
        assertFalse(npc.getShown().contains(player.getUniqueId()));
        npc.show(player);
        assertTrue(npc.isShown(player));
        assertEquals(2, npc.shows);
    }

    @Test public void destroyWithSeveralViewersClearsRegistryAndCachesAndIsIdempotent() {
        Player other = viewer();
        npc.show(player); npc.show(other);
        npc.setText(player, List.of("private"));
        npc.destroy(); npc.destroy();
        assertFalse(npc.isCreated());
        assertEquals(2, npc.hides);
        assertNull(NPCManager.getByEntityId(npc.getEntityId()));
        assertTrue(npc.getShown().isEmpty());
        assertTrue(npc.playerText.isEmpty());
        assertTrue(npc.playerHologram.isEmpty());
        verify(library).forgetNPC(npc);
    }

    @Test public void logoutRemovesTextHologramAndVisibilityBeforeReconnect() {
        npc.setText(player, List.of("old session")); npc.show(player);
        npc.onLogout(player);
        assertTrue(npc.playerText.isEmpty());
        assertTrue(npc.playerHologram.isEmpty());
        assertFalse(npc.isShown(player));
        npc.show(player);
        assertEquals(List.of("default"), npc.getText(player));
    }

    @Test public void repeatedShowIsIdempotentAndManualHideStillDestroysAnOutOfRangeEntity() {
        npc.show(player); npc.show(player);
        assertEquals(1, npc.shows);
        when(player.getLocation()).thenReturn(new Location(world, 500, 64, 0));
        npc.hide(player);
        assertEquals(1, npc.hides);
        assertTrue(npc.getAutoHidden().isEmpty());
    }

    @Test public void changingWorldRespawnsTheSameNpcOnlyForViewersInItsNewWorld() {
        npc.show(player);
        World next = mock(World.class);
        Location destination = new Location(next, 9, 70, 5);
        npc.setLocation(destination);
        destination.setX(1234);
        assertEquals(9, npc.getLocation().getX(), 0);
        assertEquals(1, npc.hides);
        assertFalse(npc.isShown(player));
        when(player.getWorld()).thenReturn(next);
        when(player.getLocation()).thenReturn(new Location(next, 10, 70, 5));
        npc.show(player);
        assertTrue(npc.isShown(player));
        assertEquals(2, npc.shows);
    }

    static final class RecordingNpc extends NPCBase {
        int shows, hides;
        RecordingNpc(NPCLib instance, List<String> text) { super(instance, text); }
        @Override protected Hologram getHologram(Player player) {
            return playerHologram.computeIfAbsent(player.getUniqueId(), ignored -> {
                Hologram hologram = mock(Hologram.class);
                when(hologram.getUpdatePackets(any())).thenReturn(List.of());
                return hologram;
            });
        }
        @Override public boolean isLocationChunkLoaded() { return true; }
        @Override public void createPackets() { }
        @Override public void createPackets(Player player) { }
        @Override public void sendShowPackets(Player player) { shows++; getHologram(player).show(player); }
        @Override public void sendHidePackets(Player player, boolean auto) { hides++; }
        @Override public void sendMetadataPacket(Player player) { }
        @Override public void sendEquipmentPacket(Player player, NPCSlot slot, boolean auto) { }
        @Override public void sendAnimationPacket(Player player, NPCAnimation animation) { }
        @Override public void sendHeadRotationPackets(Location location) { }
        @Override public void updateSkin(Skin skin) { setSkin(skin); }
        @Override public void teleport(Location location) { setLocation(location); }
    }
}
