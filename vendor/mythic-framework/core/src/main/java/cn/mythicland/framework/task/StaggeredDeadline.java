package cn.mythicland.framework.task;

public final class StaggeredDeadline {
    private StaggeredDeadline() {
    }

    public static long after(long tick, int phaseOffset, int interval) {
        if (interval <= 0) throw new IllegalArgumentException("check interval must be positive");
        return tick + interval - Math.floorMod(tick + phaseOffset, interval);
    }
}
