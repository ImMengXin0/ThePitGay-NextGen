package cn.mythicland.framework.math;

public final class Angles {
    private Angles() {
    }

    public static double angle(double degrees) {
        double angle = degrees % 360;
        return angle < -180 ? angle + 360 : angle > 180 ? angle - 360 : angle;
    }

    public static float turn(float current, float desired, double maximum) {
        double delta = angle(desired - current);
        return (float) angle(current + cn.mythicland.framework.math.Clamps.clamp(delta, -maximum, maximum));
    }
}
