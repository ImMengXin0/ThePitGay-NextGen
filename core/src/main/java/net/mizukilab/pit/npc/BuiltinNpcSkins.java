package net.mizukilab.pit.npc;

import net.jitse.npclib.api.skin.Skin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Signed, bundled player skins. Loading never performs a network request. */
public final class BuiltinNpcSkins {
    private record Entry(Skin skin, List<String> legacyValues) { }
    private static final Map<String, Entry> SKINS = load();

    private BuiltinNpcSkins() { }

    private static Map<String, Entry> load() {
        try (InputStream stream = BuiltinNpcSkins.class.getResourceAsStream("/npc-starrail-skins.yml")) {
            if (stream == null) throw new IllegalStateException("Missing bundled NPC skins");
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            ConfigurationSection skins = yaml.getConfigurationSection("skins");
            if (skins == null) throw new IllegalStateException("Missing NPC skin entries");
            Map<String, Entry> result = new LinkedHashMap<>();
            for (String id : skins.getKeys(false)) {
                ConfigurationSection section = skins.getConfigurationSection(id);
                String value = section.getString("value"), signature = section.getString("signature");
                if (value == null || value.isBlank() || signature == null || signature.isBlank()) {
                    throw new IllegalStateException("Incomplete bundled skin: " + id);
                }
                result.put(id, new Entry(new Skin(value, signature), List.copyOf(section.getStringList("legacy-values"))));
            }
            return Map.copyOf(result);
        } catch (Exception failure) {
            throw new IllegalStateException("Could not load bundled NPC skins", failure);
        }
    }

    public static Skin get(String id) {
        Entry entry = SKINS.get(id);
        if (entry == null) throw new IllegalArgumentException("Unknown built-in NPC: " + id);
        return entry.skin();
    }

    /** Replace only absent/old shipped defaults; keep administrator-defined skins intact. */
    public static boolean migrateDefaults(ConfigurationSection config) {
        boolean changed = false;
        for (var mapping : SKINS.entrySet()) {
            String valueKey = mapping.getKey() + "-npc-skin";
            String signatureKey = "not-netease-skins." + mapping.getKey() + "-signature";
            Entry entry = mapping.getValue();
            String current = config.getString(valueKey);
            if (current == null || current.isBlank() || entry.legacyValues().contains(current)) {
                config.set(valueKey, entry.skin().getValue());
                config.set(signatureKey, entry.skin().getSignature());
                changed = true;
            } else if (current.equals(entry.skin().getValue())
                    && !entry.skin().getSignature().equals(config.getString(signatureKey))) {
                // A copied default value must retain its matching Mojang signature.
                config.set(signatureKey, entry.skin().getSignature());
                changed = true;
            }
        }
        return changed;
    }
}
