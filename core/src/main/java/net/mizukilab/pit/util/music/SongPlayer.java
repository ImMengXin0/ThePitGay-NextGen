package net.mizukilab.pit.util.music;

import cn.charlotte.pit.ThePit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class SongPlayer {

    protected Song song;
    protected volatile boolean playing = false;
    protected short tick = -1;
    protected ArrayList<String> playerList = new ArrayList<String>();
    protected boolean loop;
    protected boolean autoDestroy = false;
    protected volatile boolean destroyed = false;
    protected BukkitTask playerThread;
    private long playbackGeneration;
    private double ticksUntilNextFrame;

    protected byte fadeTarget = 100;
    protected byte volume = 100;
    protected byte fadeStart = volume;
    protected int fadeDuration = 60;
    protected int fadeDone = 0;
    protected FadeType fadeType = FadeType.FADE_LINEAR;

    public SongPlayer(Song song) {
        this.song = song;
        createThread();
    }

    public FadeType getFadeType() {
        return fadeType;
    }

    public void setFadeType(FadeType fadeType) {
        this.fadeType = fadeType;
    }

    public byte getFadeTarget() {
        return fadeTarget;
    }

    public void setFadeTarget(byte fadeTarget) {
        this.fadeTarget = fadeTarget;
    }

    public byte getFadeStart() {
        return fadeStart;
    }

    public void setFadeStart(byte fadeStart) {
        this.fadeStart = fadeStart;
    }

    public int getFadeDuration() {
        return fadeDuration;
    }

    public void setFadeDuration(int fadeDuration) {
        this.fadeDuration = fadeDuration;
    }

    public int getFadeDone() {
        return fadeDone;
    }

    public void setFadeDone(int fadeDone) {
        this.fadeDone = fadeDone;
    }

    protected void calculateFade() {
        if (fadeDone == fadeDuration) {
            return; // no fade today
        }
        double targetVolume = Interpolator.interpLinear(new double[]{0, fadeStart, fadeDuration, fadeTarget}, fadeDone);
        setVolume((byte) targetVolume);
        fadeDone++;
    }

    /** Keep the legacy hook, but never reserve a worker thread for a paused song. */
    protected synchronized void createThread() {
        if (!playing || destroyed || playerThread != null) return;
        final long generation = ++playbackGeneration;
        playerThread = Bukkit.getScheduler().runTaskTimer(ThePit.getInstance(),
                () -> advancePlayback(generation), 0L, 1L);
    }

    private synchronized void advancePlayback(long generation) {
        if (generation != playbackGeneration || !playing || destroyed) return;
        if (ticksUntilNextFrame > 0) ticksUntilNextFrame -= 1;
        // NBS tempo can be fractional. Carry the remainder rather than rounding each frame.
        double delay = song.getDelay();
        if (!Double.isFinite(delay) || delay <= 0) delay = 1;
        delay = Math.max(1.0 / 32.0, delay);
        try {
            while (ticksUntilNextFrame <= 0 && playing && !destroyed && generation == playbackGeneration) {
                calculateFade();
                if (tick >= song.getLength()) {
                    if (loop) tick = 0;
                    else {
                        playing = false;
                        tick = -1;
                        cancelPlayback();
                        Bukkit.getPluginManager().callEvent(new SongEndEvent(this));
                        if (autoDestroy && !playing) destroy();
                        return;
                    }
                } else tick++;
                ticksUntilNextFrame += delay;
                playFrame(tick);
            }
        } catch (RuntimeException | LinkageError failure) {
            playing = false;
            cancelPlayback();
            throw failure;
        }
    }

    private void cancelPlayback() {
        ++playbackGeneration;
        if (playerThread != null) {
            playerThread.cancel();
            playerThread = null;
        }
    }

    private void stoppedEvent() {
        Runnable fire = () -> Bukkit.getPluginManager().callEvent(new SongStoppedEvent(this));
        if (Bukkit.isPrimaryThread()) fire.run();
        else Bukkit.getScheduler().runTask(ThePit.getInstance(), fire);
    }

    public List<String> getPlayerList() {
        return Collections.unmodifiableList(playerList);
    }

    public void addPlayer(Player p) {
        synchronized (this) {
            if (!playerList.contains(p.getName())) {
                playerList.add(p.getName());
                ArrayList<SongPlayer> songs = NoteBlockPlayerMain.plugin.playingSongs
                        .get(p.getName());
                if (songs == null) {
                    songs = new ArrayList<>();
                }
                songs.add(this);
                NoteBlockPlayerMain.plugin.playingSongs.put(p.getName(), songs);
            }
        }
    }

    public boolean isLoop() {
        synchronized (this) {
            return this.loop;
        }
    }

    public void setLoop(boolean loop) {
        synchronized (this) {
            this.loop = loop;
        }
    }

    public boolean getAutoDestroy() {
        synchronized (this) {
            return autoDestroy;
        }
    }

    public void setAutoDestroy(boolean value) {
        synchronized (this) {
            autoDestroy = value;
        }
    }

    public abstract void playTick(Player p, int tick);

    protected void playFrame(int tick) {
        for (Player player : Bukkit.getOnlinePlayers()) playTick(player, tick);
    }

    public void destroy() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(ThePit.getInstance(), this::destroy);
            return;
        }
        synchronized (this) {
            if (destroyed) return;
            SongDestroyingEvent event = new SongDestroyingEvent(this);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return;
            destroyed = true;
            playing = false;
            cancelPlayback();
            setTick((short) -1);
            for (String name : new ArrayList<>(playerList)) {
                ArrayList<SongPlayer> songs = NoteBlockPlayerMain.plugin.playingSongs.get(name);
                if (songs != null) {
                    songs.remove(this);
                    if (songs.isEmpty()) NoteBlockPlayerMain.plugin.playingSongs.remove(name);
                }
            }
            playerList.clear();
        }
    }

    public boolean isPlaying() {
        return playing;
    }

    public synchronized void setPlaying(boolean playing) {
        if (destroyed) return;
        this.playing = playing;
        if (playing) createThread();
        else {
            cancelPlayback();
            stoppedEvent();
        }
    }

    public short getTick() {
        return tick;
    }

    public void setTick(short tick) {
        this.tick = tick;
    }

    public void removePlayer(Player p) {
        synchronized (this) {
            playerList.remove(p.getName());
            if (NoteBlockPlayerMain.plugin.playingSongs.get(p.getName()) == null) {
                return;
            }
            ArrayList<SongPlayer> songs = new ArrayList<SongPlayer>(
                    NoteBlockPlayerMain.plugin.playingSongs.get(p.getName()));
            songs.remove(this);
            NoteBlockPlayerMain.plugin.playingSongs.put(p.getName(), songs);
            if (playerList.isEmpty() && autoDestroy) {
                SongEndEvent event = new SongEndEvent(this);
                Bukkit.getPluginManager().callEvent(event);
                destroy();
            }
        }
    }

    public byte getVolume() {
        return volume;
    }

    public void setVolume(byte volume) {
        this.volume = volume;
    }

    public Song getSong() {
        return song;
    }
}
