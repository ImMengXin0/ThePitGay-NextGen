package net.mizukilab.pit.platform.paper;

import com.google.common.collect.ForwardingMap;
import de.oliver.fancynpcs.api.NpcAttribute;
import de.oliver.fancynpcs.api.NpcData;
import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;

/** Keeps absent optional Fancy attributes readable without changing their concurrent storage. */
final class FancyNpcData extends NpcData {
    private final Map<NpcAttribute, String> attributeView;

    FancyNpcData(String name, UUID creator, Location location) {
        super(name, creator, location);
        Map<NpcAttribute, String> backing = super.getAttributes();
        attributeView = new ForwardingMap<>() {
            @Override protected Map<NpcAttribute, String> delegate() { return backing; }
            // FancyNpcs 2.10.1 queries custom_model even when its provider is absent.
            @Override public boolean containsKey(Object key) { return key != null && super.containsKey(key); }
            @Override public String get(Object key) { return key == null ? null : super.get(key); }
        };
    }

    @Override public Map<NpcAttribute, String> getAttributes() { return attributeView; }
}
