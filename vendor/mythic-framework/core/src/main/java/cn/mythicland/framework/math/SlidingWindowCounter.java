package cn.mythicland.framework.math;

import java.util.ArrayDeque;

public final class SlidingWindowCounter {
    private final long windowMillis;
    private final int maxCount;
    private final ArrayDeque<Long> timestamps = new ArrayDeque<>();

    public SlidingWindowCounter(long windowMillis, int maxCount) {
        if (windowMillis <= 0L) throw new IllegalArgumentException("Window must be positive: " + windowMillis);
        if (maxCount <= 0) throw new IllegalArgumentException("Maximum count must be positive: " + maxCount);
        this.windowMillis = windowMillis;
        this.maxCount = maxCount;
    }

    public void record(long nowMillis) {
        this.expire(nowMillis);
        if (this.timestamps.size() == this.maxCount) this.timestamps.removeFirst();
        this.timestamps.addLast(nowMillis);
    }

    public int count(long nowMillis) {
        this.expire(nowMillis);
        return this.timestamps.size();
    }

    private void expire(long nowMillis) {
        while (!this.timestamps.isEmpty() && nowMillis - this.timestamps.getFirst() >= this.windowMillis) {
            this.timestamps.removeFirst();
        }
    }
}
