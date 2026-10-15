package net.mizukilab.pit.util.music;

import cn.charlotte.pit.ThePit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.*;
import org.mockito.MockedStatic;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SongPlaybackPerformanceTest {
    private net.mizukilab.pit.OnlineServerTestContext bukkit;
    private MockedStatic<ThePit> pit;
    private BukkitScheduler scheduler;
    private PluginManager manager;
    private final List<Runnable> callbacks = new ArrayList<>();
    private final List<BukkitTask> tasks = new ArrayList<>();

    @Before public void setup() throws Exception {
        pit = mockStatic(ThePit.class);
        pit.when(ThePit::getInstance).thenReturn(mock(ThePit.class));
        scheduler = mock(BukkitScheduler.class); manager = mock(PluginManager.class);
        bukkit = new net.mizukilab.pit.OnlineServerTestContext(scheduler, manager);
        when(scheduler.runTaskTimer(any(), any(Runnable.class), eq(0L), eq(1L))).thenAnswer(call -> {
            callbacks.add(call.getArgument(1));
            BukkitTask task = mock(BukkitTask.class); tasks.add(task); return task;
        });
    }
    @After public void cleanup() throws Exception {
        NoteBlockPlayerMain.plugin.playingSongs.clear();
        pit.close(); bukkit.close();
    }
    private SongPlayer song(float speed, short length) {
        return new SongPlayer(new Song(speed, new HashMap<>(), (short) 1, length, "test", "", "", null)) {
            @Override public void playTick(Player player, int tick) { }
        };
    }
    @Test public void pausedSongsDoNotReserveTasksAndRepeatedWearCyclesCancelEveryTask() {
        for (int i=0;i<100;i++) {
            SongPlayer song=song(10, (short) 100);
            assertEquals(i, tasks.size());
            song.setPlaying(true); song.setPlaying(true);
            assertEquals(i+1, tasks.size());
            song.setPlaying(false);
            verify(tasks.get(i)).cancel();
        }
        verify(scheduler, never()).runTaskAsynchronously(any(), any(Runnable.class));
        assertEquals(100, tasks.size());
    }
    @Test public void pauseResumeKeepsProgressAndRejectsOldScheduledCallback() {
        SongPlayer song=song(10, (short) 100); song.setPlaying(true);
        Runnable first=callbacks.get(0);
        first.run(); assertEquals(0,song.getTick());
        first.run(); assertEquals(0,song.getTick());
        first.run(); assertEquals(1,song.getTick());
        song.setPlaying(false); song.setPlaying(true);
        first.run(); assertEquals(1,song.getTick());
        callbacks.get(1).run(); callbacks.get(1).run(); assertEquals(2,song.getTick());
        verify(tasks.get(0)).cancel();
    }
    @Test public void fractionalTempoRetainsRemainderAndSupportsMoreThanTwentyFramesPerSecond() {
        SongPlayer slow=song(8,(short) 1000); slow.setPlaying(true);
        for(int i=0;i<101;i++) callbacks.get(0).run();
        assertEquals(40,slow.getTick());
        SongPlayer fast=song(40,(short)1000); fast.setPlaying(true);
        for(int i=0;i<101;i++) callbacks.get(1).run();
        assertEquals(200,fast.getTick());
    }
    @Test public void endCancelsTaskAndAutoDestroyRemovesSongReferences() {
        SongPlayer song=song(20,(short) 1);
        Player listener=mock(Player.class); when(listener.getName()).thenReturn("listener");
        song.addPlayer(listener); song.setAutoDestroy(true); song.setPlaying(true);
        Runnable frame=callbacks.get(0); frame.run(); frame.run(); frame.run();
        assertFalse(song.isPlaying()); assertEquals(-1,song.getTick());
        assertTrue(song.getPlayerList().isEmpty()); assertTrue(NoteBlockPlayerMain.plugin.playingSongs.isEmpty());
        verify(tasks.get(0)).cancel(); verify(manager).callEvent(isA(SongEndEvent.class));
        song.setPlaying(true); assertEquals(1,tasks.size());
    }
    @Test public void loopingPlaysTickZeroAgainAndDoesNotCancelTask() {
        SongPlayer song=song(20,(short)1); song.setLoop(true); song.setPlaying(true);
        Runnable frame=callbacks.get(0);
        frame.run(); assertEquals(0,song.getTick()); frame.run(); assertEquals(1,song.getTick());
        frame.run(); assertEquals(0,song.getTick()); frame.run(); assertEquals(1,song.getTick());
        verify(tasks.get(0),never()).cancel();
    }
    @Test public void cancelledDestroyKeepsSongAliveAndSuccessfulDestroyCannotRunStaleCallbacks() {
        SongPlayer song=song(20,(short)100); song.setPlaying(true);
        doAnswer(call -> { if(call.getArgument(0) instanceof SongDestroyingEvent e)e.setCancelled(true); return null; })
                .when(manager).callEvent(any());
        song.destroy(); assertTrue(song.isPlaying()); verify(tasks.get(0),never()).cancel();
        reset(manager); song.destroy(); callbacks.get(0).run();
        assertFalse(song.isPlaying()); assertEquals(-1,song.getTick()); verify(tasks.get(0)).cancel();
    }
    @Test public void asynchronousStopSchedulesOnlyEventOnMainThread() throws Exception {
        SongPlayer song=song(20,(short)100); song.setPlaying(true);
        bukkit.primary(false);
        song.setPlaying(false); verify(tasks.get(0)).cancel(); verifyNoInteractions(manager);
        verify(scheduler).runTask(any(), any(Runnable.class));
    }
    @Test public void invalidTempoIsBoundedAndMaximumLengthDoesNotOverflowShort() {
        SongPlayer invalid=song(Float.POSITIVE_INFINITY,(short)100); invalid.setPlaying(true);
        callbacks.get(0).run(); assertEquals(0,invalid.getTick());
        SongPlayer maximum=song(20,Short.MAX_VALUE); maximum.setTick(Short.MAX_VALUE);
        maximum.setLoop(true); maximum.setPlaying(true); callbacks.get(1).run();
        assertEquals(0,maximum.getTick());
    }

    @Test public void positionSongResolvesLayersOnceForOneHundredListenersAndKeepsIndividualVolume() throws Exception {
        World world=mock(World.class); Location origin=new Location(world,0,64,0);
        List<Player> listeners=new ArrayList<>();
        for(int i=0;i<100;i++) {
            Player player=mock(Player.class); when(player.getWorld()).thenReturn(world);
            when(player.getName()).thenReturn("listener"+i); listeners.add(player);
            NoteBlockPlayerMain.setPlayerVolume(player,(byte)(i==0?50:100));
        }
        bukkit.players(listeners);
        Layer layer=spy(new Layer()); layer.setNote(0,new Note((byte)0,(byte)45));
        HashMap<Integer,Layer> layers=new HashMap<>(); layers.put(0,layer);
        PositionSongPlayer song=new PositionSongPlayer(new Song(20,layers,(short)1,(short)10,"test","","",null));
        song.setTargetLocation(origin); song.setVolume((byte)100); song.playFrame(0);
        verify(layer,times(1)).getNote(0);
        verify(listeners.get(0)).playSound(eq(origin),any(Sound.class),eq(0.5f),anyFloat());
        verify(listeners.get(99)).playSound(eq(origin),any(Sound.class),eq(1.0f),anyFloat());
        // Direct legacy calls do not reuse a previous frame's notes.
        song.playTick(listeners.get(0),1); verify(layer).getNote(1);
        song.setTargetLocation(null); song.playTick(listeners.get(0),2);
        NoteBlockPlayerMain.plugin.playerVolume.clear();
    }
}
