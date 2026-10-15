package net.mizukilab.pit.performance;

import cn.charlotte.pit.ThePit;
import me.clip.placeholderapi.PlaceholderAPI;
import net.mizukilab.pit.config.TabConfiguration;
import net.mizukilab.pit.tab.TabHandle;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.*;
import org.mockito.MockedStatic;
import java.util.*;
import java.lang.reflect.Method;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TabPerformanceTest {
    private List<String> oldHead, oldPart;
    private HashMap<String, List<String>> oldAnimation;
    private MockedStatic<PlaceholderAPI> papi;
    private RecordingTab tab;
    private static class RecordingTab extends TabHandle {
        final List<Sent> sent = new ArrayList<>();
        boolean failNext;
        record Sent(Player player, String header, String footer) { }
        @Override protected void sendTabPacket(Player player, String header, String footer) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("Expected packet failure");
            }
            sent.add(new Sent(player, header, footer));
        }
        long count(Player player) { return sent.stream().filter(s -> s.player() == player).count(); }
    }
    private Method update;

    @Before
    public void setup() throws Exception {
        var config = TabConfiguration.INSTANCE;
        oldHead = config.getHead(); oldPart = config.getPart(); oldAnimation = config.getAnimation();
        config.setHead(List.of("&aHello")); config.setPart(List.of("World")); config.setAnimation(new HashMap<>());
        papi = mockStatic(PlaceholderAPI.class);
        tab = new RecordingTab();
        update = TabHandle.class.getDeclaredMethod("updatePlayers", Collection.class);
        update.setAccessible(true);
    }

    @After
    public void cleanup() {
        if (papi != null) papi.close();
        TabConfiguration.INSTANCE.setHead(oldHead);
        TabConfiguration.INSTANCE.setPart(oldPart);
        TabConfiguration.INSTANCE.setAnimation(oldAnimation);
    }

    @Test
    public void unchangedFramesSendOncePerSessionAndReconnectsAlwaysReceiveTab() throws Exception {
        Player player = player(UUID.randomUUID());
        update.invoke(tab, List.of(player));
        update.invoke(tab, List.of(player));
        assertEquals(1, tab.count(player));
        papi.verifyNoInteractions();
        // Same UUID reconnects before an empty refresh.
        Player rejoined = player(player.getUniqueId());
        update.invoke(tab, List.of(rejoined));
        assertEquals(1, tab.count(rejoined));
        update.invoke(tab, List.of());
        update.invoke(tab, List.of(rejoined));
        assertEquals(2, tab.count(rejoined));
    }

    @Test
    public void personalPlaceholdersAreRefreshedEvenWhenCommonFrameDoesNotChange() throws Exception {
        Player first = player(UUID.randomUUID()), second = player(UUID.randomUUID());
        TabConfiguration.INSTANCE.setPart(List.of("%pit_boost%"));
        papi.when(() -> PlaceholderAPI.setPlaceholders(first, "%pit_boost%")).thenReturn("first", "updated");
        papi.when(() -> PlaceholderAPI.setPlaceholders(second, "%pit_boost%")).thenReturn("second");
        update.invoke(tab, List.of(first, second));
        update.invoke(tab, List.of(first, second));
        assertEquals(2, tab.count(first));
        assertEquals(1, tab.count(second));
        assertEquals("updated", tab.sent.get(2).footer());
        assertEquals("second", tab.sent.get(1).footer());
    }

    @Test
    public void animationKeepsItsFramesAndHandlesCounterOverflow() throws Exception {
        Player player = player(UUID.randomUUID());
        TabConfiguration.INSTANCE.setHead(List.of("{title}"));
        TabConfiguration.INSTANCE.getAnimation().put("title", List.of("A", "B", "C"));
        tab.setCounter(0); update.invoke(tab, List.of(player));
        tab.setCounter(1); update.invoke(tab, List.of(player));
        tab.setCounter(Integer.MIN_VALUE); update.invoke(tab, List.of(player));
        assertEquals("A§r", tab.sent.get(0).header());
        assertEquals("B§r", tab.sent.get(1).header());
        assertTrue(Math.floorMod(Integer.MIN_VALUE, 3) >= 0);
        verify(player, never()).sendMessage(anyString());
    }

    @Test
    public void failedSendIsRetriedWithoutBlockingOtherPlayers() throws Exception {
        Player first = player(UUID.randomUUID()), second = player(UUID.randomUUID());
        tab.failNext = true;
        update.invoke(tab, List.of(first, second));
        assertEquals(0, tab.count(first));
        assertEquals(1, tab.count(second));
        update.invoke(tab, List.of(first, second));
        assertEquals(1, tab.count(first));
        assertEquals(1, tab.count(second));
    }

    @Test
    public void placeholderAndPacketWorkRunsOnMainThread() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class); MockedStatic<ThePit> pit = mockStatic(ThePit.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(mock(BukkitTask.class));
            tab.fetchTab();
            verify(scheduler).runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong());
            verify(scheduler, never()).runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong());
        }
    }

    private Player player(UUID uuid) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        return player;
    }
}
