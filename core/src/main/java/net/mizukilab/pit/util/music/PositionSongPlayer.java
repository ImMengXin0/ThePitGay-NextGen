package net.mizukilab.pit.util.music;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;

public class PositionSongPlayer extends SongPlayer {

    private volatile Location targetLocation;
    private record FrameNote(Sound sound, float volume, float pitch) { }
    private List<FrameNote> frameNotes;

    public PositionSongPlayer(Song song) {
        super(song);
    }

    public Location getTargetLocation() {
        return targetLocation;
    }

    public void setTargetLocation(Location targetLocation) {
        this.targetLocation = targetLocation;
    }

    @Override
    public void playTick(Player p, int tick) {
        Location location = targetLocation;
        if (location == null || location.getWorld() == null || !p.getWorld().equals(location.getWorld())) {
            // not in same world
            return;
        }
        byte playerVolume = NoteBlockPlayerMain.getPlayerVolume(p);

        List<FrameNote> notes = frameNotes == null ? notesAt(tick) : frameNotes;
        for (FrameNote note : notes) {
            p.playSound(location, note.sound(), note.volume() * volume * playerVolume / 1000000f, note.pitch());
        }
    }

    private List<FrameNote> notesAt(int tick) {
        List<FrameNote> notes = new ArrayList<>();
        for (Layer layer : song.getLayerHashMap().values()) {
            Note note = layer.getNote(tick);
            if (note != null) notes.add(new FrameNote(Instrument.getInstrument(note.getInstrument()),
                    layer.getVolume(), NotePitch.getPitch(note.getKey() - 33)));
        }
        return notes;
    }

    @Override
    protected void playFrame(int tick) {
        // Resolve sparse NBS layers once per frame while retaining virtual playTick dispatch.
        frameNotes = notesAt(tick);
        try { super.playFrame(tick); }
        finally { frameNotes = null; }
    }
}
