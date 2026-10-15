package net.mizukilab.pit.util;

import com.google.common.annotations.Beta;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.mizukilab.pit.item.IMythicItem;
import nya.Skip;

import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.LongSupplier;
@Skip
public class ItemGlobalReference extends Long2ObjectLinkedOpenHashMap<IMythicItem> {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final LongSupplier limit;
    public ItemGlobalReference(LongSupplier limit) {
        this.limit = limit;
    }

    public IMythicItem getValue(long key) {
        return getAndMoveToFirst(key);
    }

    public IMythicItem getValue(UUID key) {
        return getValue(key.hashCode());
    }

    @Beta
    public void putValue(long key, IMythicItem value) {
        putAndMoveToFirst(key, value);
    }

    public void executeLRU() {
        long capacity = Math.max(0, limit.getAsLong());
        lock.writeLock().lock();
        try {
            while (super.size() > capacity) super.removeLast();
        } finally { lock.writeLock().unlock(); }
    }

    @Override
    public IMythicItem putAndMoveToFirst(long string, IMythicItem mythicItem) {
        lock.writeLock().lock();
        try { return super.putAndMoveToFirst(string, mythicItem); }
        finally { lock.writeLock().unlock(); }

    }

    @Override
    public IMythicItem put(long uuid, IMythicItem mythicItem) {
        try {
            lock.writeLock().lock();
            return super.put(uuid, mythicItem);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void putValue(UUID uuid, IMythicItem item) {

        putValue(uuid.hashCode(), item);
    }

    @Override
    public IMythicItem removeLast() {
        try {
            lock.writeLock().lock();
            return super.removeLast();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public IMythicItem remove(UUID key) {
        try {
            lock.writeLock().lock();
            IMythicItem item = super.get(key.hashCode());
            return item != null && key.equals(item.uuid) ? super.remove(key.hashCode()) : null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public IMythicItem get(long key) {
        lock.readLock().lock();
        try { return super.get(key); }
        finally { lock.readLock().unlock(); }
    }

    @Override
    public IMythicItem getAndMoveToFirst(long key) {
        lock.writeLock().lock();
        try { return super.getAndMoveToFirst(key); }
        finally { lock.writeLock().unlock(); }
    }

    @Override
    public IMythicItem remove(long key) {
        lock.writeLock().lock();
        try { return super.remove(key); }
        finally { lock.writeLock().unlock(); }
    }

    @Override
    public int size() {
        lock.readLock().lock();
        try { return super.size(); }
        finally { lock.readLock().unlock(); }
    }

    @Override
    public boolean containsKey(long key) {
        lock.readLock().lock();
        try { return super.containsKey(key); }
        finally { lock.readLock().unlock(); }
    }

    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            super.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
