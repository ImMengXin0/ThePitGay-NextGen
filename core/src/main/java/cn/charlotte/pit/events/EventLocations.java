package cn.charlotte.pit.events;

import cn.charlotte.pit.ThePit;
import net.mizukilab.pit.config.PitWorldConfig;
import org.bukkit.Location;
import java.util.List;

/** Validate required map data before mutating player inventories or building arenas. */
final class EventLocations {
    private EventLocations() { }
    static void validate(AbstractEvent event) {
        String name = event.getEventInternalName();
        PitWorldConfig config = ThePit.getInstance().getPitConfig();
        switch (name) {
            case "spire" -> {
                locations(config.getSpawnLocations(), 1);
                locations(config.getSpireFloorLoc(), 10);
                location(config.getSpireLoc());
                location(config.getRagePitMiddle());
                location(config.getPortalPosA());
                location(config.getPortalPosB());
                for (Location floor : config.getSpireFloorLoc()) sameWorld(config.getSpireLoc(), floor);
                sameWorld(config.getSpireLoc(), config.getRagePitMiddle());
                sameWorld(config.getSpireLoc(), config.getPortalPosA());
                sameWorld(config.getSpireLoc(), config.getPortalPosB());
            }
            case "rage_pit" -> {
                locations(config.getSpawnLocations(), 1);
                location(config.getRagePitMiddle());
            }
            case "ham" -> {
                locations(config.getSpawnLocations(), 1);
                location(config.getHamburgerOfferNpcLocA());
                locations(config.getHamburgerNpcLocA(), 1);
            }
            case "squads" -> locations(config.getSquadsLocations(), 1);
            case "block_head" -> locations(config.getBlockHeadLocations(), 1);
            default -> { }
        }
    }
    private static void location(Location location) {
        if (location == null || location.getWorld() == null
                || !Double.isFinite(location.getX()) || !Double.isFinite(location.getY()) || !Double.isFinite(location.getZ()))
            throw new IllegalStateException("Activity map has a missing or invalid location/world");
    }
    private static void locations(List<Location> locations, int minimum) {
        if (locations == null || locations.size() < minimum)
            throw new IllegalStateException("Activity map requires at least " + minimum + " locations");
        locations.forEach(EventLocations::location);
    }
    private static void sameWorld(Location first, Location second) {
        if (!first.getWorld().equals(second.getWorld())) throw new IllegalStateException("Activity arena locations must share a world");
    }
}
