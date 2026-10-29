package cn.mythicland.framework.path;

public final class CyclicPathCursor {
    private int index;
    private boolean reachedSinglePoint;

    public int target(int size) {
        if (size < 0) throw new IllegalArgumentException("path size must be nonnegative");
        if (size == 0 || size == 1 && this.reachedSinglePoint) return -1;
        this.index %= size;
        return this.index;
    }

    public void advance(int size) {
        if (size <= 0) throw new IllegalArgumentException("path size must be positive");
        if (size == 1) this.reachedSinglePoint = true;
        else this.index = (this.index + 1) % size;
    }

    public void reset() {
        reset(0);
    }

    public void reset(int index) {
        if (index < 0) throw new IllegalArgumentException("path index must be nonnegative");
        this.index = index;
        this.reachedSinglePoint = false;
    }
}
