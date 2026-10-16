package cn.charlotte.pit.events;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.events.trigger.type.*;
import cn.charlotte.pit.events.trigger.type.addon.IPreparative;
import net.mizukilab.pit.OnlineServerTestContext;
import net.mizukilab.pit.config.PitGlobalConfig;
import net.mizukilab.pit.util.cooldown.Cooldown;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.*;
import org.junit.*;
import org.mockito.MockedStatic;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EventLifecycleTest {
    private ThePit plugin;
    private MockedStatic<ThePit> pit;
    private OnlineServerTestContext server;
    private BukkitScheduler scheduler;
    private EventFactory factory;
    private final List<Runnable> callbacks = new ArrayList<>();
    private final List<BukkitTask> tasks = new ArrayList<>();

    @Before public void setup() throws Exception {
        plugin = mock(ThePit.class, RETURNS_DEEP_STUBS);
        pit = mockStatic(ThePit.class);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        scheduler = mock(BukkitScheduler.class);
        server = new OnlineServerTestContext(scheduler, mock(PluginManager.class));
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> record(call.getArgument(1)));
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenAnswer(call -> record(call.getArgument(1)));
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> record(call.getArgument(1)));
        factory = new EventFactory();
        when(plugin.getEventFactory()).thenReturn(factory);
        PitGlobalConfig config = mock(PitGlobalConfig.class);
        config.animationForEpicEvent = List.of("&5Event ");
        config.periodForEpicEvent = 0;
        when(plugin.getGlobalConfig()).thenReturn(config);
    }
    private BukkitTask record(Runnable callback) {
        BukkitTask task = mock(BukkitTask.class);
        when(task.getTaskId()).thenReturn(tasks.size() + 1);
        callbacks.add(callback); tasks.add(task);
        return task;
    }
    @After public void close() throws Exception { server.close(); pit.close(); }

    private class Minor extends AbstractEvent implements INormalEvent {
        int starts, stops; Runnable action = () -> {};
        @Override public String getEventInternalName() { return "test"; }
        @Override public String getEventName() { return "test"; }
        @Override public int requireOnline() { return 5; }
        @Override public void onActive() { starts++; assertSame(this, factory.getActiveNormalEvent()); assertTrue(isActive()); action.run(); }
        @Override public void onInactive() { stops++; }
        void track(BukkitTask task) { trackTask(task); }
    }
    private class Major extends AbstractEvent implements IEpicEvent, IPreparative {
        int prepares, starts, stops;
        @Override public String getEventInternalName() { return "test_major"; }
        @Override public String getEventName() { return "test"; }
        @Override public int requireOnline() { return 0; }
        @Override public void onPreActive() { prepares++; }
        @Override public void onActive() { starts++; }
        @Override public void onInactive() { stops++; }
    }

    @Test public void oneShotCanEndItselfDuringActivation() {
        Minor event = new Minor(); event.action = () -> factory.inactiveEvent(event);
        factory.activeEvent(event);
        assertNull(factory.getActiveNormalEvent()); assertFalse(event.isActive());
        assertEquals(1, event.stops); assertTrue(tasks.isEmpty());
    }
    @Test public void failedActivationCleansUpAndAllowsNextEvent() {
        Minor failed = new Minor();
        BukkitTask owned = mock(BukkitTask.class);
        failed.action = () -> { failed.track(owned); throw new IllegalStateException("expected"); };
        factory.activeEvent(failed);
        verify(owned).cancel(); assertTrue(failed.isAborting()); assertEquals(1, failed.stops);
        Minor next = new Minor(); factory.activeEvent(next); assertSame(next, factory.getActiveNormalEvent());
    }
    @Test public void repeatedPushCannotDuplicateCallbacksOrReplaceActiveEvent() {
        Minor first = new Minor(), second = new Minor();
        factory.activeEvent(first); factory.activeEvent(first); factory.activeEvent(second);
        assertEquals(1, first.starts); assertEquals(0, second.starts); assertEquals(1, tasks.size());
        factory.inactiveEvent(first); factory.inactiveEvent(first);
        assertEquals(1, first.stops); verify(tasks.get(0)).cancel();
    }
    @Test public void forceMinorActuallyBypassesMinimumPlayers() {
        Minor event = new Minor();
        factory.pushEvent(event); assertEquals(0, event.starts);
        factory.pushEvent(event, true); assertEquals(1, event.starts);
    }
    @Test public void asynchronousEntryDefersUntilServerThread() throws Exception {
        server.primary(false);
        Minor event = new Minor(); factory.pushEvent(event, true);
        assertEquals(0, event.starts);
        server.primary(true); callbacks.get(0).run();
        assertEquals(1, event.starts);
        verify(scheduler, never()).runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong());
    }
    @Test public void preparationStopsMinorAndCannotBeDuplicated() {
        Minor minor = new Minor(); Major major = new Major();
        factory.activeEvent(minor); factory.readyEpicEvent(major); factory.readyEpicEvent(major);
        assertEquals(1, minor.stops); assertEquals(1, major.prepares);
        factory.getNextEpicEventTimer().fastExpired();
        callbacks.get(1).run(); callbacks.get(1).run();
        assertEquals(1, major.starts); assertSame(major, factory.getActiveEpicEvent());
        verify(scheduler).runTaskTimer(eq(plugin), any(Runnable.class), eq(10L), eq(1L));
    }
    @Test public void staleDelayedWorkDoesNotAffectNextRunOfSameObject() {
        Minor event = new Minor(); int[] executed = {0};
        factory.activeEvent(event); event.runEventTaskLater(() -> executed[0]++, 10);
        Runnable old = callbacks.get(1);
        factory.inactiveEvent(event); factory.activeEvent(event);
        old.run(); assertEquals(0, executed[0]);
        event.runEventTask(() -> executed[0]++); callbacks.get(callbacks.size()-1).run();
        assertEquals(1, executed[0]);
    }
    @Test public void timerEndsExpiredMinorEvenWhileStartCooldownIsActive() {
        Minor event = new Minor(); factory.activeEvent(event);
        factory.getNormalEnd().fastExpired();
        EventTimer timer = new EventTimer(); timer.setCooldown(new Cooldown(1, TimeUnit.HOURS));
        timer.run(); assertEquals(1, event.stops); assertNull(factory.getActiveNormalEvent());
    }
    @Test public void epicWatchdogEndsActivityIfItsOwnTimerFails() {
        Major event = new Major(); factory.activeEvent(event);
        factory.getEpicEnd().fastExpired();
        EventTimer timer = new EventTimer(); timer.setCooldown(new Cooldown(1, TimeUnit.HOURS));
        timer.run(); assertEquals(1, event.stops); assertNull(factory.getActiveEpicEvent());
    }
    @Test public void shutdownCancelsPreparationAndRejectsFurtherStarts() {
        Major event = new Major(); factory.readyEpicEvent(event);
        factory.shutdown(); callbacks.get(0).run(); factory.activeEvent(event);
        verify(tasks.get(0)).cancel(); assertEquals(0, event.starts); assertNull(factory.getNextEpicEvent());
    }
    @Test public void everyStopCancelsAllOwnedRepeatingTasks() {
        Minor event = new Minor(); BukkitTask task = mock(BukkitTask.class);
        factory.activeEvent(event); event.track(task); factory.shutdown();
        verify(task).cancel(); assertEquals(1, event.stops); assertFalse(event.isActive());
    }
    @Test public void emptySelectedQueueReturnsSentinelEvenIfOtherQueueHasEntries() {
        var handler = EventsHandler.INSTANCE;
        List<String> oldEpic = new ArrayList<>(handler.getEpicQueue());
        List<String> oldNormal = new ArrayList<>(handler.getNormalQueue());
        try {
            handler.getEpicQueue().clear(); handler.getNormalQueue().clear();
            handler.getNormalQueue().add("test");
            assertEquals("NULL", handler.nextEvent(true));
        } finally {
            handler.getEpicQueue().clear(); handler.getEpicQueue().addAll(oldEpic);
            handler.getNormalQueue().clear(); handler.getNormalQueue().addAll(oldNormal);
        }
    }
}
