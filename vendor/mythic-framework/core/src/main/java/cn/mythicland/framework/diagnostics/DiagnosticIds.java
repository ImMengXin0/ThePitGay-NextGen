package cn.mythicland.framework.diagnostics;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

public final class DiagnosticIds {
    private final Map<Key, Long> ids = new HashMap<>();
    private final ReferenceQueue<Object> collected = new ReferenceQueue<>();
    private final LongSupplier sequence;

    public DiagnosticIds(LongSupplier sequence) {
        this.sequence = sequence;
    }

    public synchronized long id(Object object) {
        if (object == null) return 0;
        Key old;
        while ((old = (Key) this.collected.poll()) != null) this.ids.remove(old);
        Key key = new Key(object, this.collected);
        Long existing = this.ids.get(key);
        if (existing != null) return existing;
        if (this.ids.size() >= 65536) throw new IllegalStateException("Diagnostic identity capacity reached");
        long id = this.sequence.getAsLong();
        this.ids.put(key, id);
        return id;
    }

    private static final class Key extends WeakReference<Object> {
        private final int hash;

        Key(Object object, ReferenceQueue<Object> queue) {
            super(object, queue);
            this.hash = System.identityHashCode(object);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof Key key && this.get() != null && this.get() == key.get();
        }
    }
}
