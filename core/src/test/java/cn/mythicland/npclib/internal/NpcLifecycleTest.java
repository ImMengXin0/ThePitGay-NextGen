package cn.mythicland.npclib.internal;

import cn.charlotte.pit.ThePit;
import cn.mythicland.npclib.NPCLib;
import net.mizukilab.pit.platform.paper.FancyNpcService;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.*;
import org.mockito.MockedStatic;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Verifies the game-facing lifecycle while Fancy owns rendering and packets. */
public class NpcLifecycleTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private NPCLib library;
    private World world;
    private Player player;
    private NPCBase npc;
    private FancyNpcService.NpcHandle handle;
    private final Set<UUID> visible = new HashSet<>();

    @Before public void setup() {
        bukkit = net.mizukilab.pit.PaperTestBootstrap.mockBukkit();
        pit = mockStatic(ThePit.class);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        ThePit plugin = mock(ThePit.class);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        FancyNpcService service = mock(FancyNpcService.class);
        when(plugin.getFancyNpcs()).thenReturn(service);
        handle = mock(FancyNpcService.NpcHandle.class);
        when(handle.entityId()).thenReturn(123);
        when(service.create(anyString(), any(), any(), any())).thenReturn(handle);
        doAnswer(call -> { visible.add(((Player) call.getArgument(0)).getUniqueId()); return null; }).when(handle).show(any());
        doAnswer(call -> { visible.remove(((Player) call.getArgument(0)).getUniqueId()); return null; }).when(handle).hide(any());
        when(handle.isShown(any())).thenAnswer(call -> visible.contains(((Player) call.getArgument(0)).getUniqueId()));
        world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        player = viewer();
        library = mock(NPCLib.class);
        when(library.getAutoHideDistance()).thenReturn(50.0);
        npc = new NPCBase(library, List.of("default")) {};
        npc.setLocation(new Location(world, 0, 64, 0));
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
        if (pit != null) pit.close();
        if (bukkit != null) bukkit.close();
    }
    @Test public void privateTextIsCopiedAndClearingRestoresDefault() {
        List<String> input = new ArrayList<>(List.of("private"));
        npc.setText(player, input); input.set(0, "mutated");
        assertEquals(List.of("private"), npc.getText(player));
        npc.removeText(player);
        verify(handle).setText(player, List.of("default"));
        npc.setText((List<String>) null);
        assertEquals(List.of(), npc.getText(player));
    }
    @Test public void repeatedShowAndManualHideRetainViewerIntent() {
        npc.show(player); npc.show(player);
        verify(handle, times(1)).show(player);
        npc.hide(player, true);
        assertFalse(npc.isShown(player));
        assertTrue(npc.getShown().contains(player.getUniqueId()));
        npc.hide(player); npc.show(player, true);
        assertFalse(npc.getShown().contains(player.getUniqueId()));
        npc.show(player);
        assertTrue(npc.isShown(player));
    }
    @Test public void outOfRangeShowRemembersUserUntilTheyReturn() {
        when(player.getLocation()).thenReturn(new Location(world, 500, 64, 0));
        npc.show(player);
        verify(handle, never()).show(player);
        assertTrue(npc.getAutoHidden().contains(player.getUniqueId()));
        when(player.getLocation()).thenReturn(new Location(world, 1, 64, 0));
        npc.show(player, true);
        assertTrue(npc.isShown(player));
    }
    @Test public void logoutDropsPersonalTextAndVisibility() {
        npc.setText(player, List.of("old")); npc.show(player); npc.onLogout(player);
        assertFalse(npc.isShown(player));
        assertEquals(List.of("default"), npc.getText(player));
        verify(handle).setText(player, null);
    }
    @Test public void destructionClosesFancyAndClearsRegistryOnlyOnce() {
        npc.show(player); npc.show(viewer()); npc.destroy(); npc.destroy();
        assertFalse(npc.isCreated()); assertTrue(npc.getShown().isEmpty());
        assertNull(NPCManager.getByEntityId(npc.getEntityId()));
        verify(handle).close(); verify(library).forgetNPC(npc);
    }
    @Test public void teleportCopiesInputAndLetsFancyUpdateWorldVisibility() {
        World next = mock(World.class);
        Location destination = new Location(next, 9, 70, 5);
        npc.teleport(destination); destination.setX(100);
        assertEquals(9, npc.getLocation().getX(), 0);
        verify(handle).teleport(new Location(next, 9, 70, 5));
    }
}
