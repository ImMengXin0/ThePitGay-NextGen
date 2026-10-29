package cn.mythicland.framework.math;

public final class IntervalProgress {
    private static final double ROUNDING_TOLERANCE = 1.0E-9D;

    private IntervalProgress() {
    }

    public static int completedCount(double progress, double interval) {
        if (!Double.isFinite(progress)) throw new IllegalArgumentException("Progress must be finite: " + progress);
        if (!Double.isFinite(interval) || interval <= 0.0D) {
            throw new IllegalArgumentException("Interval must be finite and positive: " + interval);
        }
        return Math.max(0, (int) Math.floor(progress / interval + ROUNDING_TOLERANCE));
    }

    public static int crossedCount(double previousProgress, double currentProgress, double interval) {
        return completedCount(currentProgress, interval) - completedCount(previousProgress, interval);
    }
}
