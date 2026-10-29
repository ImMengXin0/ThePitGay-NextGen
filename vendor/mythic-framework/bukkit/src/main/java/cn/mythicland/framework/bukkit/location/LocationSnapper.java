package cn.mythicland.framework.bukkit.location;

import org.bukkit.Location;

public final class LocationSnapper {
    private static final float VIEW_STEP = 45.0F;
    private static final float MIN_PITCH = -90.0F;
    private static final float MAX_PITCH = 90.0F;

    private LocationSnapper() {
    }

    public static Location snapBlockAndView(Location location) {
        if (location == null) throw new IllegalArgumentException("location must not be null");
        if (location.getWorld() == null) throw new IllegalArgumentException("location world must not be null");

        Location result = snapBlockAndYaw(location);
        result.setPitch(clampPitch(snapToStep(location.getPitch())));
        return result;
    }

    public static Location snapBlockAndYaw(Location location) {
        Location result = location.clone();
        result.setX(Math.floor(location.getX()) + 0.5D);
        result.setZ(Math.floor(location.getZ()) + 0.5D);
        result.setYaw(normalizeYaw(snapToStep(location.getYaw())));
        result.setPitch(0.0F);
        return result;
    }

    private static float snapToStep(float angle) {
        return Math.round(angle / VIEW_STEP) * VIEW_STEP;
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0F;
        if (normalized <= -180.0F) {
            normalized += 360.0F;
        } else if (normalized > 180.0F) {
            normalized -= 360.0F;
        }
        return normalized;
    }

    private static float clampPitch(float pitch) {
        return cn.mythicland.framework.math.Clamps.clamp(pitch, MIN_PITCH, MAX_PITCH);
    }
}
