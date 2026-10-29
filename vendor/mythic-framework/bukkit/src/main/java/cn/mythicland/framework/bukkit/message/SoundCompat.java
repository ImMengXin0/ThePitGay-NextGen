package cn.mythicland.framework.bukkit.message;

import org.bukkit.Sound;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class SoundCompat {
    private static final Map<String, String> SOUND_ALIASES = createSoundAliases();

    private SoundCompat() {
    }

    public static Sound matchSound(String rawName) {
        String soundName = trimToEmpty(rawName);
        if (soundName.isEmpty()) {
            return null;
        }

        Sound sound = tryValueOf(soundName);
        if (sound != null) {
            return sound;
        }

        String alias = SOUND_ALIASES.get(soundName.toUpperCase(Locale.ROOT));
        return alias == null ? null : tryValueOf(alias);
    }

    private static Sound tryValueOf(String soundName) {
        try {
            return Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Map<String, String> createSoundAliases() {
        Map<String, String> aliases = new LinkedHashMap<String, String>();
        aliases.put("NOTE_PLING", "BLOCK_NOTE_BLOCK_PLING");
        aliases.put("BLOCK_NOTE_BLOCK_PLING", "NOTE_PLING");
        aliases.put("NOTE_BASS", "BLOCK_NOTE_BLOCK_BASS");
        aliases.put("BLOCK_NOTE_BLOCK_BASS", "NOTE_BASS");
        aliases.put("NOTE_BASS_DRUM", "BLOCK_NOTE_BLOCK_BASEDRUM");
        aliases.put("BLOCK_NOTE_BLOCK_BASEDRUM", "NOTE_BASS_DRUM");
        aliases.put("NOTE_SNARE_DRUM", "BLOCK_NOTE_BLOCK_SNARE");
        aliases.put("BLOCK_NOTE_BLOCK_SNARE", "NOTE_SNARE_DRUM");
        aliases.put("NOTE_STICKS", "BLOCK_NOTE_BLOCK_HAT");
        aliases.put("BLOCK_NOTE_BLOCK_HAT", "NOTE_STICKS");
        aliases.put("NOTE_BASS_GUITAR", "BLOCK_NOTE_BLOCK_GUITAR");
        aliases.put("BLOCK_NOTE_BLOCK_GUITAR", "NOTE_BASS_GUITAR");
        return aliases;
    }

    private static String trimToEmpty(String input) {
        return input == null ? "" : input.trim();
    }
}
