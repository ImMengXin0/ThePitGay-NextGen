package cn.mythicland.framework.path;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PathLoadBalancerTest {
    private static final List<PathLoadBalancer.Point> ROUTE = circle();

    @Test
    void distributesInitialTargets() {
        PathLoadBalancer balancing = new PathLoadBalancer(circle());
        for (int population : new int[]{30, 73, 300}) {
            int[] starts = new int[8];
            for (int slot = 0; slot < population; slot++) starts[balancing.initialTarget(slot)]++;
            for (int count : starts) assertTrue(count == population / 8 || count == population / 8 + 1);
        }
    }

    @Test
    void predictsTheMovingGapAndContinuesFromTheFarSide() {
        Simulation simulation = new Simulation(8, false);
        simulation.sample(10);
        PathLoadBalancer.Target crossing = simulation.balancing.crossing(1, .35, point -> true);
        assertNotNull(crossing);
        PathLoadBalancer.Point destination = simulation.balancing.destination(crossing, 7, 0, 10, Simulation.SPEED);
        double arrival = phase(destination);
        double travelTicks = Math.hypot(destination.x() - 7, destination.z()) / Simulation.SPEED;
        double gap = wrap(arrival - Simulation.SPEED * travelTicks);
        double spacing = perimeter() / 8;
        assertEquals(Math.round(gap / spacing) * spacing, gap, .01);
        assertTrue(arrival >= perimeter() * .25 && arrival <= perimeter() * .75);
        assertTrue(simulation.balancing.nextWaypoint(crossing) >= 3);
        PathLoadBalancer.Point later = simulation.balancing.destination(crossing, 7, 0, 20, Simulation.SPEED);
        assertNotEquals(destination, later);
        simulation.balancing.finish(1, later.x(), later.y(), later.z(), 20);
        simulation.balancing.remove(1);
    }

    @Test
    void stopsCrossingWhenUniformGapsMoveTogether() {
        Simulation simulation = new Simulation(30, true);
        simulation.run(600);
        assertEquals(0, simulation.started);
        assertEquals(perimeter() / 30, simulation.minimumGap(), .001);
    }

    @Test
    void acceptsUniformGapsWithoutRequiringExactSlotAlignment() {
        Simulation simulation = new Simulation(30, true);
        for (int id = 0; id < simulation.phases.length; id++) {
            simulation.phases[id] = wrap(simulation.phases[id] + Math.sin(id * Math.PI * 2 / 30)
                    * perimeter() / 30 * .35);
            simulation.positions[id] = point(simulation.phases[id]);
        }
        simulation.run(600);
        assertEquals(0, simulation.started);
    }

    @Test
    void convergesAtFixedSpeedAndStopsCrossingForLargeGroups() {
        for (int population : new int[]{30, 73, 300}) {
            Simulation simulation = new Simulation(population, false);
            simulation.run(2400);
            assertTrue(simulation.started > 0);
            assertEquals(0, simulation.active(), "Every crossing should finish for population " + population);
            double spacing = perimeter() / population;
            assertTrue(simulation.minimumGap() >= spacing * .55,
                    "Bots should spread around the moving ring: " + population + ", gap " + simulation.minimumGap());
            assertTrue(simulation.maximumGap() <= spacing * 1.45,
                    "Large moving gaps should be filled: " + population + ", gap " + simulation.maximumGap());
            int started = simulation.started;
            simulation.run(600);
            assertEquals(started, simulation.started, "Uniform groups should stop crossing");
        }
    }

    @Test
    void followsMovingGapsAtReducedUpdateRates() {
        for (double multiplier : new double[]{1, .8, .5, .1}) {
            Simulation simulation = new Simulation(30, false, Simulation.SPEED * multiplier);
            simulation.run((int) Math.ceil(2400 / multiplier));
            assertEquals(0, simulation.active());
            assertTrue(simulation.minimumGap() >= perimeter() / 30 * .55);
            assertTrue(simulation.maximumGap() <= perimeter() / 30 * 1.45);
            int started = simulation.started;
            simulation.run(100);
            assertEquals(started, simulation.started);
        }
    }

    @Test
    void respectsTheCrossingLimitAndRejectsBlockedOrUnsupportedRoutes() {
        Simulation simulation = new Simulation(8, false);
        simulation.sample(10);
        assertNull(simulation.balancing.crossing(1, 0, point -> true));
        int[] checks = {0};
        assertNull(simulation.balancing.crossing(1, .35, point -> {
            checks[0]++;
            return false;
        }));
        assertTrue(checks[0] > 0 && checks[0] <= 8);
        int selected = 0;
        for (int id = 1; id < 8; id++) {
            if (simulation.balancing.crossing(id, .35, point -> true) != null) selected++;
        }
        assertEquals(3, selected);
        List<PathLoadBalancer.Point> elevated = new ArrayList<>(circle());
        elevated.set(0, new PathLoadBalancer.Point(7, 65, 0));
        for (List<PathLoadBalancer.Point> route : List.of(elevated, circle().subList(0, 2))) {
            PathLoadBalancer balancing = new PathLoadBalancer(route);
            balancing.begin(10);
            for (int id = 0; id < 8; id++) balancing.observe(id, 7, 64, 0);
            balancing.end(Simulation.SPEED);
            assertNull(balancing.crossing(1, .35, point -> true));
        }
    }

    @Test
    void releasesDeathsAndRebalancesAfterMembershipChanges() {
        Simulation simulation = new Simulation(30, false);
        simulation.run(40);
        for (int id = 1; id < 30; id++) {
            simulation.balancing.remove(id);
            simulation.targets[id] = null;
            simulation.phases[id] = simulation.phases[0];
            simulation.positions[id] = simulation.positions[0];
        }
        simulation.tick = 50;
        simulation.sample(50);
        simulation.run(2400);
        assertEquals(0, simulation.active());
        assertTrue(simulation.minimumGap() >= perimeter() / 30 * .55);
    }

    private static List<PathLoadBalancer.Point> circle() {
        List<PathLoadBalancer.Point> route = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            double angle = index * Math.PI / 4;
            route.add(new PathLoadBalancer.Point(Math.cos(angle) * 7, 64, Math.sin(angle) * 7));
        }
        return route;
    }

    private static double perimeter() {
        return 8 * 14 * Math.sin(Math.PI / 8);
    }

    private static double wrap(double phase) {
        return phase - Math.floor(phase / perimeter()) * perimeter();
    }

    private static PathLoadBalancer.Point point(double phase) {
        double segment = wrap(phase) / (perimeter() / 8);
        int index = (int) segment;
        PathLoadBalancer.Point a = ROUTE.get(index), b = ROUTE.get((index + 1) % 8);
        return new PathLoadBalancer.Point(a.x() + (b.x() - a.x()) * (segment - index), 64,
                a.z() + (b.z() - a.z()) * (segment - index));
    }

    private static double phase(PathLoadBalancer.Point point) {
        double nearest = Double.POSITIVE_INFINITY, phase = 0;
        List<PathLoadBalancer.Point> route = ROUTE;
        for (int index = 0; index < 8; index++) {
            PathLoadBalancer.Point a = route.get(index), b = route.get((index + 1) % 8);
            double dx = b.x() - a.x(), dz = b.z() - a.z();
            double fraction = cn.mythicland.framework.math.Clamps.clamp(((point.x() - a.x()) * dx + (point.z() - a.z()) * dz)
                    / (dx * dx + dz * dz), 0, 1);
            double distance = Math.hypot(point.x() - a.x() - dx * fraction, point.z() - a.z() - dz * fraction);
            if (distance >= nearest) continue;
            nearest = distance;
            phase = (index + fraction) * perimeter() / 8;
        }
        return wrap(phase);
    }

    private static final class Simulation {
        private static final double SPEED = .16;
        private final double speed;
        private final PathLoadBalancer balancing = new PathLoadBalancer(circle());
        private final double[] phases;
        private final PathLoadBalancer.Point[] positions;
        private final PathLoadBalancer.Target[] targets;
        private long tick;
        private int started;

        private Simulation(int population, boolean uniform) {
            this(population, uniform, SPEED);
        }

        private Simulation(int population, boolean uniform, double speed) {
            this.speed = speed;
            phases = new double[population];
            positions = new PathLoadBalancer.Point[population];
            targets = new PathLoadBalancer.Target[population];
            for (int id = 0; id < population; id++) {
                phases[id] = uniform ? perimeter() * id / population : 0;
                positions[id] = point(phases[id]);
            }
        }

        private void sample(long tick) {
            balancing.begin(tick);
            for (int id = 0; id < phases.length; id++) {
                PathLoadBalancer.Point position = positions[id];
                balancing.observe(id, position.x(), position.y(), position.z());
            }
            balancing.end(speed);
        }

        private void run(int ticks) {
            for (int step = 0; step < ticks; step++) {
                tick++;
                if (tick % 10 == 0) sample(tick);
                for (int id = 0; id < phases.length; id++) {
                    if (targets[id] == null && tick % 10 == 0) {
                        targets[id] = balancing.crossing(id, .35, destination -> true);
                        if (targets[id] != null) started++;
                    }
                    if (targets[id] == null) {
                        phases[id] = wrap(phases[id] + speed);
                        positions[id] = point(phases[id]);
                        continue;
                    }
                    PathLoadBalancer.Point source = positions[id];
                    PathLoadBalancer.Point destination =
                            balancing.destination(targets[id], source.x(), source.z(), tick, speed);
                    double dx = destination.x() - source.x(), dz = destination.z() - source.z();
                    double distance = Math.hypot(dx, dz);
                    if (distance <= speed) {
                        balancing.finish(id, destination.x(), destination.y(), destination.z(), tick);
                        targets[id] = null;
                        positions[id] = destination;
                        phases[id] = phase(destination);
                    } else positions[id] = new PathLoadBalancer.Point(source.x() + dx * speed / distance, 64,
                            source.z() + dz * speed / distance);
                    assertTrue(active() <= Math.ceil(phases.length * .35));
                }
            }
        }

        private int active() {
            int active = 0;
            for (PathLoadBalancer.Target target : targets) if (target != null) active++;
            return active;
        }

        private double[] gaps() {
            double[] sorted = phases.clone();
            Arrays.sort(sorted);
            double[] gaps = new double[sorted.length];
            for (int index = 0; index < sorted.length; index++)
                gaps[index] = wrap(sorted[(index + 1) % sorted.length] - sorted[index]);
            return gaps;
        }

        private double minimumGap() {
            return Arrays.stream(gaps()).min().orElseThrow();
        }

        private double maximumGap() {
            return Arrays.stream(gaps()).max().orElseThrow();
        }
    }
}
