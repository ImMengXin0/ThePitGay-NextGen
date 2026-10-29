package cn.mythicland.framework.bukkit.diagnostics;

import net.minecraft.server.v1_8_R3.PacketPlayOutPlayerInfo;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

final class DiagnosticPacketSelection {
    private final DiagnosticOptions options;
    private final Map<UUID, Integer> entityIds;
    private final Set<String> names;
    private final Map<UUID, Map<String, Set<String>>> teams = new ConcurrentHashMap<>();

    DiagnosticPacketSelection(DiagnosticOptions options, Collection<? extends Player> targets) {
        this.options = options;
        this.entityIds = new ConcurrentHashMap<>();
        this.names = targets.stream().map(Player::getName).collect(Collectors.toUnmodifiableSet());
        for (Player target : targets) this.track(target);
    }

    void track(Player target) {
        this.entityIds.put(target.getUniqueId(), target.getEntityId());
    }

    private DiagnosticPacketSelection(DiagnosticPacketSelection source) {
        this.options = source.options;
        this.names = source.names;
        this.entityIds = source.entityIds;
        source.teams.forEach((viewer, teams) -> {
            Map<String, Set<String>> copied = new ConcurrentHashMap<>();
            teams.forEach((name, members) -> {
                Set<String> entries = ConcurrentHashMap.newKeySet();
                entries.addAll(members);
                copied.put(name, entries);
            });
            this.teams.put(viewer, copied);
        });
    }

    DiagnosticPacketSelection copy() {
        return new DiagnosticPacketSelection(this);
    }

    void observe(Player viewer) {
        Map<String, Set<String>> known = new ConcurrentHashMap<>();
        for (String name : this.names) {
            var team = viewer.getScoreboard().getEntryTeam(name);
            if (team != null) known.computeIfAbsent(team.getName(), ignored -> ConcurrentHashMap.newKeySet()).add(name);
        }
        this.teams.put(viewer.getUniqueId(), known);
    }

    boolean relevant(UUID player, Object packet, boolean outbound) {
        String type = packet.getClass().getSimpleName();
        if (!outbound) return this.entityIds.containsKey(player) && this.options.inbound(type);
        if (!this.options.outbound(type)) return false;
        return switch (type) {
            case "PacketPlayOutNamedEntitySpawn" -> {
                UUID target = (UUID) field(packet, "b");
                if (!this.entityIds.containsKey(target)) yield false;
                this.entityIds.put(target, (Integer) field(packet, "a"));
                yield true;
            }
            case "PacketPlayOutEntityDestroy" -> this.destroyed(packet).length != 0;
            case "PacketPlayOutPlayerInfo" -> !this.profiles(packet).isEmpty();
            case "PacketPlayOutScoreboardScore" -> this.names.contains(field(packet, "a"));
            case "PacketPlayOutScoreboardTeam" -> this.team(player, packet);
            case "PacketPlayOutScoreboardObjective", "PacketPlayOutScoreboardDisplayObjective",
                 "PacketPlayOutKeepAlive", "PacketPlayOutTransaction", "PacketPlayOutRespawn" -> true;
            case "PacketPlayOutUpdateHealth", "PacketPlayOutPosition", "PacketPlayOutExplosion",
                 "PacketPlayOutHeldItemSlot" -> this.entityIds.containsKey(player);
            default -> this.entityIds.containsValue((Integer) field(packet, "a"));
        };
    }

    Object snapshot(Object packet) {
        String type = packet.getClass().getSimpleName();
        return switch (type) {
            case "PacketPlayOutEntityEquipment" -> {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("entityId", field(packet, "a"));
                out.put("slot", field(packet, "b"));
                out.put("item", DiagnosticSnapshot.item(
                        (net.minecraft.server.v1_8_R3.ItemStack) field(packet, "c"),
                        this.options.includes(DiagnosticOptions.Detail.NBT)
                ));
                yield out;
            }
            case "PacketPlayOutEntityDestroy" -> Map.of("entityIds", this.destroyed(packet));
            case "PacketPlayOutScoreboardScore" -> Map.of(
                    "entry", field(packet, "a"), "objective", field(packet, "b"),
                    "score", field(packet, "c"), "action", field(packet, "d").toString());
            case "PacketPlayOutScoreboardObjective" -> Map.of(
                    "objective", field(packet, "a"), "displayName", field(packet, "b"),
                    "renderType", String.valueOf(field(packet, "c")), "mode", field(packet, "d"));
            case "PacketPlayOutScoreboardDisplayObjective" -> Map.of(
                    "slot", field(packet, "a"), "objective", field(packet, "b"));
            case "PacketPlayOutPlayerInfo" -> Map.of(
                    "action", field(packet, "a").toString(),
                    "players", this.profiles(packet).stream().map(entry -> {
                        Map<String, Object> out = new LinkedHashMap<>();
                        out.put("uuid", entry.a().getId().toString());
                        out.put("name", entry.a().getName());
                        out.put("ping", entry.b());
                        out.put("gameMode", String.valueOf(entry.c()));
                        return out;
                    }).toList());
            case "PacketPlayOutScoreboardTeam" -> {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("team", field(packet, "a"));
                out.put("mode", field(packet, "h"));
                out.put("displayName", field(packet, "b"));
                out.put("prefix", field(packet, "c"));
                out.put("suffix", field(packet, "d"));
                out.put("visibility", field(packet, "e"));
                out.put("color", field(packet, "f"));
                out.put("options", field(packet, "i"));
                out.put("entries", this.entries(packet).stream().filter(this.names::contains).toList());
                yield out;
            }
            default -> DiagnosticSnapshot.packet(packet, this.options.includes(DiagnosticOptions.Detail.NBT));
        };
    }

    private boolean team(UUID viewer, Object packet) {
        Map<String, Set<String>> known = this.teams.get(viewer);
        return this.entries(packet).stream().anyMatch(this.names::contains)
                || known != null && known.containsKey(field(packet, "a"));
    }

    void sent(UUID viewer, Object packet) {
        if (!packet.getClass().getSimpleName().equals("PacketPlayOutScoreboardTeam")) return;
        Map<String, Set<String>> known = this.teams.computeIfAbsent(viewer, ignored -> new ConcurrentHashMap<>());
        String name = (String) field(packet, "a");
        int mode = (Integer) field(packet, "h");
        if (mode == 0 || mode == 1) known.remove(name);
        if (mode == 0 || mode == 3) {
            for (String entry : this.entries(packet)) {
                if (this.names.contains(entry)) known.computeIfAbsent(name, ignored -> ConcurrentHashMap.newKeySet()).add(entry);
            }
        } else if (mode == 4) {
            Set<String> members = known.get(name);
            if (members != null) {
                members.removeAll(this.entries(packet));
                if (members.isEmpty()) known.remove(name);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Collection<String> entries(Object packet) {
        return (Collection<String>) field(packet, "g");
    }

    private int[] destroyed(Object packet) {
        return java.util.Arrays.stream((int[]) field(packet, "a"))
                .filter(this.entityIds::containsValue)
                .toArray();
    }

    @SuppressWarnings("unchecked")
    private List<PacketPlayOutPlayerInfo.PlayerInfoData> profiles(Object packet) {
        return ((List<PacketPlayOutPlayerInfo.PlayerInfoData>) field(packet, "b")).stream()
                .filter(entry -> this.entityIds.containsKey(entry.a().getId()))
                .toList();
    }

    private static Object field(Object packet, String name) {
        for (Class<?> owner = packet.getClass(); owner != null; owner = owner.getSuperclass()) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(packet);
            } catch (NoSuchFieldException ignored) {
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Cannot read diagnostic packet field " + name, failure);
            }
        }
        throw new IllegalStateException("Missing diagnostic packet field " + packet.getClass().getName() + "." + name);
    }
}
