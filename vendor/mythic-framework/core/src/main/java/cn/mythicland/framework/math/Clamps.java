package cn.mythicland.framework.math;

/** Java 17 equivalents of the Math.clamp calls used by the vendored framework. */
public final class Clamps {
    private Clamps() { }
    public static int clamp(int value, int min, int max) {
        if (min > max) throw new IllegalArgumentException("min > max");
        return Math.min(max, Math.max(min, value));
    }
    public static double clamp(double value, double min, double max) {
        if (Double.isNaN(min) || Double.isNaN(max) || min > max) throw new IllegalArgumentException("Invalid bounds");
        return Math.min(max, Math.max(min, value));
    }
    public static float clamp(float value, float min, float max) {
        if (Float.isNaN(min) || Float.isNaN(max) || min > max) throw new IllegalArgumentException("Invalid bounds");
        return Math.min(max, Math.max(min, value));
    }
}
