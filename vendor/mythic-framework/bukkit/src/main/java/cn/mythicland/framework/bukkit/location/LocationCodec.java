package cn.mythicland.framework.bukkit.location;

import cn.mythicland.framework.bukkit.config.ConfigException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;

public final class LocationCodec {
    private static final String DELIMITER_REGEX = "\\|";
    private static final String DEFAULT_CONFIG_PATH = "location";

    private LocationCodec() {
    }

    public static Location parse(String serialized) {
        return parse(serialized, DEFAULT_CONFIG_PATH);
    }

    public static Location parse(String serialized, String configPath) {
        if (serialized == null || serialized.trim().isEmpty())
            throw new ConfigException(configPath + " must not be empty");
        String[] split = serialized.split(DELIMITER_REGEX);
        if (split.length != 6) throw new ConfigException(configPath + " format must be world|x|y|z|yaw|pitch");

        String worldName = split[0].trim();
        World world = Bukkit.getWorld(worldName);
        if (world == null) throw new ConfigException(configPath + " world does not exist: " + worldName);

        try {
            double x = Double.parseDouble(split[1]);
            double y = Double.parseDouble(split[2]);
            double z = Double.parseDouble(split[3]);
            float yaw = Float.parseFloat(split[4]);
            float pitch = Float.parseFloat(split[5]);
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException exception) {
            throw new ConfigException(configPath + " numeric value is invalid", exception);
        }
    }

    public static String serialize(Location location) {
        if (location == null) throw new IllegalArgumentException("location must not be null");
        if (location.getWorld() == null) throw new IllegalArgumentException("location world must not be null");

        return String.format(
                Locale.ROOT,
                "%s|%.1f|%.1f|%.1f|%.1f|%.1f",
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                (double) location.getYaw(),
                (double) location.getPitch()
        );
    }
}
