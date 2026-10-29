package cn.mythicland.framework.world;

public final class SquareSpiral {
    private SquareSpiral() {
    }

    public static Coordinate at(long index) {
        if (index < 0) throw new IllegalArgumentException("Grid index must be nonnegative: " + index);
        if (index == 0) return new Coordinate(0, 0);
        long radius = (long) Math.ceil((Math.sqrt(index + 1) - 1) / 2);
        long side = 2 * radius;
        long offset = (2 * radius + 1) * (2 * radius + 1) - 1 - index;
        if (offset < side) return new Coordinate(Math.toIntExact(radius - offset), Math.toIntExact(-radius));
        if (offset < 2 * side) return new Coordinate(Math.toIntExact(-radius), Math.toIntExact(-radius + offset - side));
        if (offset < 3 * side) return new Coordinate(Math.toIntExact(-radius + offset - 2 * side), Math.toIntExact(radius));
        return new Coordinate(Math.toIntExact(radius), Math.toIntExact(radius - offset + 3 * side));
    }

    public record Coordinate(int x, int z) {
    }
}
