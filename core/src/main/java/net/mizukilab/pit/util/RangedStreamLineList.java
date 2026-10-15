package net.mizukilab.pit.util;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.Objects;
import java.util.function.Predicate;

public class RangedStreamLineList<T> extends ConcurrentLinkedDeque<T> {
    private final Predicate<T> predicate;
    private final int maxElement;

    public RangedStreamLineList(int maxElement, Predicate<T> t) {
        super();
        if (maxElement < 0) throw new IllegalArgumentException("Negative capacity");
        this.maxElement = maxElement;
        this.predicate = Objects.requireNonNull(t, "predicate");
    }

    public RangedStreamLineList(int maxElement, Predicate<T> t, Collection<T> t2) {
        super(t2);
        if (maxElement < 0) throw new IllegalArgumentException("Negative capacity");
        this.maxElement = maxElement;
        this.predicate = Objects.requireNonNull(t, "predicate");
    }

    @Override
    public synchronized boolean offerFirst(T t) {
        boolean b = super.offerFirst(t);
        recycle();
        return b;
    }

    @Override
    public boolean offer(T t) {
        return offerFirst(t);
    }

    @Override
    public boolean offerLast(T t) {
        throw new UnsupportedOperationException("Only can offer at first");
    }

    @Override
    public void addFirst(T t) {
        this.offerFirst(t);
    }

    @Override
    public void addLast(T t) {
        this.offerLast(t);
    }

    @Override
    public boolean add(T t) {
        return this.offerFirst(t);
    }

    @Override
    public boolean addAll(Collection<? extends T> entries) {
        if (entries == this) throw new IllegalArgumentException("Cannot add collection to itself");
        boolean changed = false;
        for (T entry : entries) changed |= add(entry);
        return changed;
    }
    public void onRecycle(T t){

    }
    public synchronized void recycle() {
        // Expiration times need not follow insertion order.
        for (T entry : this) {
            if (predicate.test(entry) && super.removeFirstOccurrence(entry)) {
                onRecycle(entry);
            }
        }
        // Zero means expiration only, as used by the dropped-entity tracker.
        if (maxElement > 0) {
            int excess = size() - maxElement;
            while (excess-- > 0) {
                T entry = super.pollLast();
                if (entry == null) break;
                onRecycle(entry);
            }
        }
    }

}
