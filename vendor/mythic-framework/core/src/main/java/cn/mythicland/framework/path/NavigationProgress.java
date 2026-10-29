package cn.mythicland.framework.path;

public final class NavigationProgress {
    private final int checkInterval;
    private final double minimumDistanceSquared;
    private int updates;
    private boolean initialized;
    private double x, y, z;

    public NavigationProgress(int checkInterval, double minimumDistanceSquared) {
        if (checkInterval <= 0) throw new IllegalArgumentException("check interval must be positive");
        if (!Double.isFinite(minimumDistanceSquared) || minimumDistanceSquared <= 0)
            throw new IllegalArgumentException("minimum distance squared must be finite and positive");
        this.checkInterval = checkInterval;
        this.minimumDistanceSquared = minimumDistanceSquared;
    }

    public boolean isStuck(double x, double y, double z) {
        if (!this.initialized) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.initialized = true;
            return false;
        }
        if (++this.updates <= this.checkInterval) return false;
        double dx = x - this.x, dy = y - this.y, dz = z - this.z;
        boolean stuck = dx * dx + dy * dy + dz * dz < this.minimumDistanceSquared;
        this.x = x;
        this.y = y;
        this.z = z;
        this.updates = 0;
        return stuck;
    }

    public void reset() {
        this.updates = 0;
        this.initialized = false;
    }
}
