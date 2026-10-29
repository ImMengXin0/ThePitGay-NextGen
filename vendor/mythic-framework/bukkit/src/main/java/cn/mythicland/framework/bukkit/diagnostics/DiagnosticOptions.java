package cn.mythicland.framework.bukkit.diagnostics;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public record DiagnosticOptions(Set<PacketGroup> packets, Set<Detail> details, int durationSeconds, int sampleTicks) {
    public enum PacketGroup {
        COMBAT, MOVEMENT, EQUIPMENT, HEALTH, VISIBILITY, LATENCY
    }

    public enum Detail {
        PROTOCOL, FRAMES, EVENTS, LISTENERS, CORE, NBT, RUNTIME
    }

    public DiagnosticOptions {
        packets = Set.copyOf(packets);
        details = Set.copyOf(details);
        if (packets.isEmpty()) throw new IllegalArgumentException("Select at least one packet group");
        if (durationSeconds < 1 || durationSeconds > 1800)
            throw new IllegalArgumentException("duration must be between 1 and 1800 seconds");
        if (sampleTicks < 1 || sampleTicks > 1200)
            throw new IllegalArgumentException("sample-ticks must be between 1 and 1200");
        if (details.contains(Detail.CORE) && !packets.contains(PacketGroup.COMBAT))
            throw new IllegalArgumentException("core requires the combat packet group");
        if (details.contains(Detail.FRAMES) && !packets.contains(PacketGroup.COMBAT)
                && !packets.contains(PacketGroup.MOVEMENT) && !packets.contains(PacketGroup.LATENCY)
                && !packets.contains(PacketGroup.EQUIPMENT))
            throw new IllegalArgumentException("frames requires combat, movement, equipment or latency");
    }

    public static DiagnosticOptions defaults(int durationSeconds) {
        return new DiagnosticOptions(Set.of(PacketGroup.COMBAT), Set.of(), durationSeconds, 1);
    }

    public boolean captures(PacketGroup group) {
        return this.packets.contains(group);
    }

    public boolean includes(Detail detail) {
        return this.details.contains(detail);
    }

    public static Set<PacketGroup> parsePackets(String text) {
        return parse(text, PacketGroup.class);
    }

    public static Set<Detail> parseDetails(String text) {
        return text.equalsIgnoreCase("none") ? Set.of() : parse(text, Detail.class);
    }

    private static <E extends Enum<E>> Set<E> parse(String text, Class<E> type) {
        Set<E> values = EnumSet.noneOf(type);
        for (String part : text.split(",", -1)) {
            try {
                values.add(Enum.valueOf(type, part.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Unknown " + type.getSimpleName() + ": " + part);
            }
        }
        return values;
    }

    boolean inbound(String type) {
        return switch (type) {
            case "PacketPlayInUseEntity", "PacketPlayInArmAnimation", "PacketPlayInBlockDig",
                 "PacketPlayInBlockPlace" -> this.captures(PacketGroup.COMBAT);
            case "PacketPlayInHeldItemSlot" -> this.captures(PacketGroup.COMBAT) || this.captures(PacketGroup.EQUIPMENT);
            case "PacketPlayInFlying", "PacketPlayInPosition", "PacketPlayInPositionLook", "PacketPlayInLook" ->
                    this.captures(PacketGroup.MOVEMENT);
            case "PacketPlayInEntityAction" -> this.captures(PacketGroup.COMBAT) || this.captures(PacketGroup.MOVEMENT);
            case "PacketPlayInKeepAlive", "PacketPlayInTransaction" -> this.captures(PacketGroup.LATENCY);
            default -> false;
        };
    }

    boolean outbound(String type) {
        return switch (type) {
            case "PacketPlayOutEntityVelocity", "PacketPlayOutEntityStatus", "PacketPlayOutAnimation",
                 "PacketPlayOutExplosion" -> this.captures(PacketGroup.COMBAT);
            case "PacketPlayOutPosition", "PacketPlayOutEntityTeleport", "PacketPlayOutRelEntityMove",
                 "PacketPlayOutRelEntityMoveLook", "PacketPlayOutEntityLook", "PacketPlayOutEntityHeadRotation" ->
                    this.captures(PacketGroup.MOVEMENT);
            case "PacketPlayOutEntityMetadata" -> this.captures(PacketGroup.HEALTH)
                    || this.captures(PacketGroup.VISIBILITY) || this.captures(PacketGroup.COMBAT);
            case "PacketPlayOutUpdateAttributes", "PacketPlayOutEntityEffect", "PacketPlayOutRemoveEntityEffect" ->
                    this.captures(PacketGroup.COMBAT) || this.captures(PacketGroup.HEALTH);
            case "PacketPlayOutHeldItemSlot", "PacketPlayOutEntityEquipment" ->
                    this.captures(PacketGroup.EQUIPMENT) || this.captures(PacketGroup.COMBAT);
            case "PacketPlayOutUpdateHealth", "PacketPlayOutScoreboardScore", "PacketPlayOutScoreboardObjective",
                 "PacketPlayOutScoreboardDisplayObjective" -> this.captures(PacketGroup.HEALTH);
            case "PacketPlayOutNamedEntitySpawn", "PacketPlayOutEntityDestroy", "PacketPlayOutRespawn" ->
                    this.captures(PacketGroup.VISIBILITY) || this.captures(PacketGroup.EQUIPMENT)
                            || this.captures(PacketGroup.HEALTH);
            case "PacketPlayOutPlayerInfo", "PacketPlayOutScoreboardTeam" -> this.captures(PacketGroup.VISIBILITY);
            case "PacketPlayOutKeepAlive", "PacketPlayOutTransaction" -> this.captures(PacketGroup.LATENCY);
            default -> false;
        };
    }

    boolean frame(int protocolId) {
        return switch (protocolId) {
            case 0, 15 -> this.captures(PacketGroup.LATENCY);
            case 2, 7, 8, 10 -> this.captures(PacketGroup.COMBAT);
            case 9 -> this.captures(PacketGroup.COMBAT) || this.captures(PacketGroup.EQUIPMENT);
            case 3, 4, 5, 6 -> this.captures(PacketGroup.MOVEMENT);
            case 11 -> this.captures(PacketGroup.COMBAT) || this.captures(PacketGroup.MOVEMENT);
            default -> false;
        };
    }

    boolean event(String name) {
        if (name.equals("PlayerJoinEvent") || name.equals("PlayerQuitEvent") || name.equals("PlayerRespawnEvent")) return true;
        if (this.captures(PacketGroup.MOVEMENT) && (name.contains("Move") || name.contains("Teleport")
                || name.contains("Sprint") || name.contains("Sneak") || name.contains("Velocity"))) return true;
        if (this.captures(PacketGroup.EQUIPMENT) && (name.contains("Inventory") || name.contains("Equipment")
                || name.contains("ItemHeld") || name.contains("ItemBreak"))) return true;
        if (this.captures(PacketGroup.HEALTH) && (name.contains("Damage") || name.contains("Heal")
                || name.contains("Death") || name.contains("Respawn"))) return true;
        if (this.captures(PacketGroup.VISIBILITY) && (name.contains("Teleport") || name.contains("ChangedWorld"))) return true;
        return this.captures(PacketGroup.COMBAT) && (name.contains("Damage") || name.contains("Attack")
                || name.contains("Combat") || name.contains("Velocity") || name.contains("Projectile")
                || name.contains("Bow") || name.contains("Death") || name.contains("Resurrect")
                || name.contains("Heal") || name.contains("Hit") || name.contains("Melee") || name.contains("Strike")
                || name.equals("PlayerAnimationEvent") || name.equals("PlayerItemHeldEvent")
                || name.equals("PlayerInteractEvent") || name.contains("Sprint") || name.contains("Sneak"));
    }
}
