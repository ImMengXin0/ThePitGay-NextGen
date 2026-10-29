package cn.mythicland.framework.path;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public final class PathLoadBalancer {
    private final List<Point> route;
    private final double[] starts, lengths;
    private final double perimeter;
    private final boolean enclosed;
    private final Long2ObjectLinkedOpenHashMap<Member> members = new Long2ObjectLinkedOpenHashMap<>();
    private Member[] snapshot = new Member[0];
    private int[] occupancy = new int[0];
    private double[] phases = new double[0];
    private boolean balanced;
    private boolean membershipChanged, occupancyChanged;
    private long tick, anchorTick, anchor = -1;
    private final int[] candidates = new int[8];
    private double origin, routeSpeed;
    private int crossings;

    public PathLoadBalancer(List<Point> route) {
        if (route.isEmpty()) throw new IllegalArgumentException("load balancing requires a nonempty route");
        this.route = List.copyOf(route);
        starts = new double[route.size()];
        lengths = new double[route.size()];
        double total = 0, area = 0;
        boolean level = true;
        for (int index = 0; index < route.size(); index++) {
            Point a = route.get(index), b = route.get((index + 1) % route.size());
            starts[index] = total;
            lengths[index] = Math.hypot(b.x() - a.x(), b.z() - a.z());
            total += lengths[index];
            area += a.x() * b.z() - b.x() * a.z();
            level &= Math.abs(a.y() - route.get(0).y()) < .01;
        }
        perimeter = total;
        enclosed = level && route.size() >= 3 && Math.abs(area) > 1;
    }

    public int initialTarget(long ordinal) {
        return (int) Math.floorMod(ordinal, route.size());
    }

    public void begin(long tick) {
        this.tick = tick;
    }

    public void observe(long id, double x, double y, double z) {
        observe(id, x, y, z, tick);
    }

    private void observe(long id, double x, double y, double z, long now) {
        Member member = members.get(id);
        if (member == null) {
            member = new Member(id);
            members.put(id, member);
            membershipChanged = true;
        }
        double phase = project(x, z);
        Point projected = point(phase);
        double distance = Math.hypot(x - projected.x(), z - projected.z());
        long elapsed = now - member.observedAt;
        double advance = signedDistance(member.phase, phase);
        member.speed = elapsed > 0 && member.observedAt > 0 && member.target == null
                && distance < .75 && member.distance < .75 && advance > 0
                ? advance / elapsed : 0;
        member.phase = phase;
        member.x = projected.x();
        member.z = projected.z();
        member.distance = Math.abs(y - projected.y()) <= .6 ? distance : Double.POSITIVE_INFINITY;
        member.observedAt = now;
        occupancyChanged = true;
    }

    public void end(double maximumSpeed) {
        if (!Double.isFinite(maximumSpeed) || maximumSpeed <= 0)
            throw new IllegalArgumentException("maximum movement speed must be finite and positive");
        refreshMembership();
        double sum = 0;
        int moving = 0;
        for (Member member : snapshot) {
            if (member.speed <= 0 || member.speed > maximumSpeed * 1.5) continue;
            sum += member.speed;
            moving++;
        }
        routeSpeed = moving == 0 ? maximumSpeed : Math.min(maximumSpeed, sum / moving);
        Member reference = members.get(anchor);
        if (reference == null || reference.target != null || reference.distance > .75) {
            reference = null;
            for (Member member : snapshot) {
                if (member.target == null && member.distance <= .75) {
                    reference = member;
                    break;
                }
            }
            if (reference == null) return;
            double previous = origin + routeSpeed * Math.max(0, tick - anchorTick);
            origin = reference.phase;
            for (Member member : snapshot) {
                if (member.target != null) member.target.offset = wrap(previous + member.target.offset - origin);
            }
            anchor = reference.id;
        } else origin = reference.phase;
        anchorTick = tick;
        balanced = evenlySpaced();
        occupancyChanged = true;
    }

    public Target crossing(long id, double ratio, Predicate<Point> passable) {
        refreshMembership();
        Member source = members.get(id);
        if (!enclosed || balanced || source == null || source.target != null || source.distance > .75) return null;
        if (anchor < 0 || id == anchor || snapshot.length < 3 || crossings >= Math.ceil(snapshot.length * ratio))
            return null;
        rebuildOccupancy();
        int slot = slot(source.phase);
        boolean crowded = occupancy[slot] > 1 || !aligned(source.phase, slot);
        Point position = point(source.phase), opposite = point(source.phase + perimeter * .5);
        double crossingDistance = Math.hypot(opposite.x() - position.x(), opposite.z() - position.z());
        double preferred = source.phase + perimeter * .5 - crossingDistance;
        Arrays.fill(candidates, -1);
        for (int attempt = 0; attempt < candidates.length; attempt++) {
            int candidate = -1;
            double nearest = Double.POSITIVE_INFINITY;
            for (int index = 0; index < occupancy.length; index++) {
                if (occupancy[index] != 0) continue;
                boolean checked = false;
                for (int previous = 0; previous < attempt; previous++) checked |= candidates[previous] == index;
                if (checked) continue;
                double distance = Math.abs(signedDistance(preferred, origin + index * spacing()));
                if (distance >= nearest) continue;
                candidate = index;
                nearest = distance;
            }
            if (candidate < 0) return null;
            candidates[attempt] = candidate;
            double phase = interceptionPhase(origin + candidate * spacing(), position.x(), position.z(), routeSpeed);
            if (Math.abs(signedDistance(source.phase, phase)) < perimeter * .25) continue;
            if (!crowded && hasDirectSource(candidate)) continue;
            Point destination = point(phase);
            if (!passable.test(destination)) continue;
            Target target = new Target(candidate * spacing());
            target.arrivalPhase = phase;
            source.target = target;
            crossings++;
            occupancyChanged = true;
            return target;
        }
        return null;
    }

    public Point destination(Target target, double x, double z, long now) {
        return destination(target, x, z, now, routeSpeed);
    }

    public Point destination(Target target, double x, double z, long now, double travelSpeed) {
        if (!Double.isFinite(travelSpeed) || travelSpeed <= 0)
            throw new IllegalArgumentException("travel speed must be finite and positive");
        double phase = wrap(origin + target.offset + routeSpeed * Math.max(0, now - tick));
        target.arrivalPhase = interceptionPhase(phase, x, z, travelSpeed);
        return point(target.arrivalPhase);
    }

    private double interceptionPhase(double phase, double x, double z, double travelSpeed) {
        double low = 1, high = Math.max(1, perimeter / travelSpeed);
        for (int iteration = 0; iteration < 14; iteration++) {
            double time = (low + high) * .5;
            double distanceSquared = distanceSquared(phase + routeSpeed * time, x, z);
            if (distanceSquared > travelSpeed * travelSpeed * time * time) low = time;
            else high = time;
        }
        return wrap(phase + routeSpeed * high);
    }

    public boolean hasArrived(Target target, double x, double z, long now, double margin) {
        double phase = origin + target.offset + routeSpeed * Math.max(0, now - tick);
        return distanceSquared(phase, x, z) < margin * margin;
    }

    public int nextWaypoint(Target target) {
        int segment = segment(target.arrivalPhase);
        return (segment + 1) % route.size();
    }

    public void finish(long id, double x, double y, double z, long now) {
        Member member = members.get(id);
        if (member == null || member.target == null) return;
        member.target = null;
        crossings--;
        observe(id, x, y, z, now);
    }

    public void remove(long id) {
        Member member = members.remove(id);
        if (member == null) return;
        if (member.target != null) crossings--;
        membershipChanged = occupancyChanged = true;
    }

    private boolean hasDirectSource(int emptySlot) {
        double destination = origin + emptySlot * spacing();
        for (Member member : snapshot) {
            if (member.id == anchor || member.target != null || member.distance > .75) continue;
            int slot = slot(member.phase);
            if (occupancy[slot] <= 1 && aligned(member.phase, slot)) continue;
            double arrival = interceptionPhase(destination, member.x, member.z, routeSpeed);
            if (Math.abs(signedDistance(member.phase, arrival)) >= perimeter * .25) return true;
        }
        return false;
    }

    private void refreshMembership() {
        if (!membershipChanged) return;
        snapshot = members.values().toArray(Member[]::new);
        occupancy = new int[snapshot.length];
        phases = new double[snapshot.length];
        balanced = false;
        membershipChanged = false;
    }

    private void rebuildOccupancy() {
        refreshMembership();
        if (!occupancyChanged) return;
        Arrays.fill(occupancy, 0);
        for (Member member : snapshot) {
            double phase = member.target == null ? member.phase : origin + member.target.offset;
            int slot = slot(phase);
            if (member.target != null || aligned(phase, slot)) occupancy[slot]++;
        }
        occupancyChanged = false;
    }

    private boolean evenlySpaced() {
        if (snapshot.length < 3) return true;
        for (int index = 0; index < snapshot.length; index++) {
            Member member = snapshot[index];
            phases[index] = wrap(member.target == null ? member.phase : origin + member.target.offset);
        }
        Arrays.sort(phases);
        double spacing = spacing();
        for (int index = 0; index < phases.length; index++) {
            double next = index + 1 < phases.length ? phases[index + 1] : phases[0] + perimeter;
            double gap = next - phases[index];
            if (gap < spacing * .6 || gap > spacing * 1.4) return false;
        }
        return true;
    }

    private boolean aligned(double phase, int slot) {
        return Math.abs(signedDistance(phase, origin + slot * spacing())) <= spacing() * .2;
    }

    private int slot(double phase) {
        return Math.floorMod((int) Math.round(wrap(phase - origin) / spacing()), snapshot.length);
    }

    private double spacing() {
        return perimeter / snapshot.length;
    }

    private double project(double x, double z) {
        double nearest = Double.POSITIVE_INFINITY, phase = 0;
        for (int index = 0; index < route.size(); index++) {
            if (lengths[index] == 0) continue;
            Point a = route.get(index), b = route.get((index + 1) % route.size());
            double dx = b.x() - a.x(), dz = b.z() - a.z();
            double fraction = cn.mythicland.framework.math.Clamps.clamp(((x - a.x()) * dx + (z - a.z()) * dz)
                    / (lengths[index] * lengths[index]), 0, 1);
            double distance = Math.pow(x - a.x() - dx * fraction, 2) + Math.pow(z - a.z() - dz * fraction, 2);
            if (distance >= nearest) continue;
            nearest = distance;
            phase = starts[index] + lengths[index] * fraction;
        }
        return wrap(phase);
    }

    private double distanceSquared(double phase, double x, double z) {
        phase = wrap(phase);
        int index = segment(phase);
        Point a = route.get(index), b = route.get((index + 1) % route.size());
        double fraction = lengths[index] == 0 ? 0 : (phase - starts[index]) / lengths[index];
        double dx = a.x() + (b.x() - a.x()) * fraction - x;
        double dz = a.z() + (b.z() - a.z()) * fraction - z;
        return dx * dx + dz * dz;
    }

    private Point point(double phase) {
        phase = wrap(phase);
        int index = segment(phase);
        Point a = route.get(index), b = route.get((index + 1) % route.size());
        double fraction = lengths[index] == 0 ? 0 : (phase - starts[index]) / lengths[index];
        return new Point(a.x() + (b.x() - a.x()) * fraction, a.y(), a.z() + (b.z() - a.z()) * fraction);
    }

    private int segment(double phase) {
        int index = Arrays.binarySearch(starts, wrap(phase));
        return index >= 0 ? index : Math.max(0, -index - 2);
    }

    private double signedDistance(double from, double to) {
        return wrap(to - from + perimeter * .5) - perimeter * .5;
    }

    private double wrap(double phase) {
        return perimeter == 0 ? 0 : phase - Math.floor(phase / perimeter) * perimeter;
    }

    public static final class Target {
        private double offset, arrivalPhase;

        private Target(double offset) {
            this.offset = offset;
        }
    }

    private static final class Member {
        private final long id;
        private double phase, x, z, distance = Double.POSITIVE_INFINITY, speed;
        private long observedAt;
        private Target target;

        private Member(long id) {
            this.id = id;
        }
    }

    public record Point(double x, double y, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("path point coordinates must be finite");
        }
    }
}
