package cn.mythicland.framework.task;

public final class RoundRobinBudget {
    private final double phase;
    private double remainder;
    private int cursor;

    public RoundRobinBudget(int seed) {
        this.phase = Math.floorMod(seed * 0x9E3779B9, 1024) / 1024.0;
        this.remainder = this.phase;
    }

    public int begin(int size, double multiplier) {
        if (size < 0) throw new IllegalArgumentException("size must be nonnegative");
        if (!Double.isFinite(multiplier) || multiplier <= 0) {
            throw new IllegalArgumentException("movement multiplier must be finite and positive");
        }
        if (size == 0) {
            reset();
            return 0;
        }
        if (multiplier >= 1) return size;
        this.remainder += size * multiplier;
        int updates = Math.min(size, (int) Math.floor(this.remainder + 1e-9));
        this.remainder = Math.max(0, this.remainder - updates);
        return updates;
    }

    public int next(int size) {
        if (size <= 0) throw new IllegalArgumentException("size must be positive");
        this.cursor %= size;
        int index = this.cursor;
        this.cursor = (this.cursor + 1) % size;
        return index;
    }

    public void reset() {
        this.remainder = this.phase;
    }
}
