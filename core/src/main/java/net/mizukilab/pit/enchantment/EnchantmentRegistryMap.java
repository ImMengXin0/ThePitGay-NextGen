package net.mizukilab.pit.enchantment;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.AbstractMap;
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Keeps the legacy mutable Map API while tracking changes by instance, not enchantment equality. */
final class EnchantmentRegistryMap extends AbstractMap<String, AbstractEnchantment> {
    private final Map<String, AbstractEnchantment> entries = new Object2ObjectOpenHashMap<>();
    private final Runnable changed;
    private final Collection<AbstractEnchantment> values = new AbstractCollection<>() {
        @Override public int size() { return entries.size(); }
        @Override public void clear() { EnchantmentRegistryMap.this.clear(); }
        @Override public void forEach(Consumer<? super AbstractEnchantment> action) { entries.values().forEach(action); }
        @Override public Iterator<AbstractEnchantment> iterator() {
            Iterator<AbstractEnchantment> iterator = entries.values().iterator();
            return new Iterator<>() {
                @Override public boolean hasNext() { return iterator.hasNext(); }
                @Override public AbstractEnchantment next() { return iterator.next(); }
                @Override public void remove() { iterator.remove(); changed.run(); }
            };
        }
    };
    private final Set<Entry<String, AbstractEnchantment>> view = new AbstractSet<>() {
        @Override public int size() { return entries.size(); }
        @Override public void clear() { EnchantmentRegistryMap.this.clear(); }
        @Override public Iterator<Entry<String, AbstractEnchantment>> iterator() {
            Iterator<Entry<String, AbstractEnchantment>> iterator = entries.entrySet().iterator();
            return new Iterator<>() {
                @Override public boolean hasNext() { return iterator.hasNext(); }
                @Override public Entry<String, AbstractEnchantment> next() {
                    Entry<String, AbstractEnchantment> entry = iterator.next();
                    return new SimpleEntry<>(entry) {
                        @Override public AbstractEnchantment setValue(AbstractEnchantment value) {
                            AbstractEnchantment previous = entry.setValue(value);
                            super.setValue(value);
                            if (previous != value) changed.run();
                            return previous;
                        }
                    };
                }
                @Override public void remove() {
                    iterator.remove();
                    changed.run();
                }
            };
        }
    };

    EnchantmentRegistryMap(Runnable changed) { this.changed = changed; }
    @Override public int size() { return entries.size(); }
    @Override public boolean containsKey(Object key) { return entries.containsKey(key); }
    @Override public AbstractEnchantment get(Object key) { return entries.get(key); }
    @Override public Set<Entry<String, AbstractEnchantment>> entrySet() { return view; }
    @Override public Collection<AbstractEnchantment> values() { return values; }
    @Override public void forEach(BiConsumer<? super String, ? super AbstractEnchantment> action) { entries.forEach(action); }

    @Override public AbstractEnchantment put(String key, AbstractEnchantment value) {
        boolean existed = entries.containsKey(key);
        AbstractEnchantment previous = entries.put(key, value);
        if (!existed || previous != value) changed.run();
        return previous;
    }

    @Override public AbstractEnchantment remove(Object key) {
        if (!entries.containsKey(key)) return null;
        AbstractEnchantment previous = entries.remove(key);
        changed.run();
        return previous;
    }

    @Override public void clear() {
        if (entries.isEmpty()) return;
        entries.clear();
        changed.run();
    }
}
