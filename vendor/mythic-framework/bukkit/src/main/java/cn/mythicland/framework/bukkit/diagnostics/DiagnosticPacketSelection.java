package cn.mythicland.framework.bukkit.diagnostics;

import net.minecraft.network.protocol.game.*;
import org.bukkit.entity.Player;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

final class DiagnosticPacketSelection {
    private final DiagnosticOptions options;
    private final Map<UUID, Integer> entityIds = new ConcurrentHashMap<>();
    private final Set<String> names;
    private final Map<UUID, Set<String>> teams = new ConcurrentHashMap<>();
    DiagnosticPacketSelection(DiagnosticOptions options, Collection<? extends Player> targets) {
        this.options = options;
        names = targets.stream().map(Player::getName).collect(Collectors.toUnmodifiableSet());
        targets.forEach(this::track);
    }
    private DiagnosticPacketSelection(DiagnosticPacketSelection source) {
        options = source.options; names = source.names; entityIds.putAll(source.entityIds);
        source.teams.forEach((id, values) -> { Set<String> copy = ConcurrentHashMap.newKeySet(); copy.addAll(values); teams.put(id, copy); });
    }
    DiagnosticPacketSelection copy() { return new DiagnosticPacketSelection(this); }
    void track(Player target) { entityIds.put(target.getUniqueId(), target.getEntityId()); }
    void observe(Player viewer) {
        Set<String> known = ConcurrentHashMap.newKeySet();
        for (String name : names) {
            var team = viewer.getScoreboard().getEntryTeam(name);
            if (team != null) known.add(team.getName());
        }
        teams.put(viewer.getUniqueId(), known);
    }
    boolean relevant(UUID player, Object packet, boolean outbound) {
        String type = packet.getClass().getSimpleName();
        if (!outbound) return entityIds.containsKey(player) && options.inbound(type);
        if (!options.outbound(type)) return false;
        if (packet instanceof ClientboundAddEntityPacket spawn) {
            if (!entityIds.containsKey(spawn.getUUID())) return false;
            entityIds.put(spawn.getUUID(), spawn.getId()); return true;
        }
        if (packet instanceof ClientboundRemoveEntitiesPacket remove)
            return remove.getEntityIds().intStream().anyMatch(entityIds::containsValue);
        if (packet instanceof ClientboundPlayerInfoUpdatePacket info)
            return info.entries().stream().anyMatch(entry -> entityIds.containsKey(entry.profileId()));
        if (packet instanceof ClientboundPlayerInfoRemovePacket info)
            return info.profileIds().stream().anyMatch(entityIds::containsKey);
        if (packet instanceof ClientboundSetScorePacket score) return names.contains(score.owner());
        if (packet instanceof ClientboundSetPlayerTeamPacket team)
            return team.getPlayers().stream().anyMatch(names::contains) || teams.getOrDefault(player, Set.of()).contains(team.getName());
        return switch (type) {
            case "ClientboundSetObjectivePacket", "ClientboundSetDisplayObjectivePacket", "ClientboundResetScorePacket",
                 "ClientboundKeepAlivePacket", "ClientboundPingPacket", "ClientboundRespawnPacket" -> true;
            case "ClientboundSetHealthPacket", "ClientboundPlayerPositionPacket", "ClientboundExplodePacket",
                 "ClientboundSetHeldSlotPacket" -> entityIds.containsKey(player);
            default -> entityIds.containsValue(entityId(packet));
        };
    }
    Object snapshot(Object packet) { return DiagnosticSnapshot.packet(packet, options.includes(DiagnosticOptions.Detail.NBT)); }
    void sent(UUID viewer, Object packet) {
        if (!(packet instanceof ClientboundSetPlayerTeamPacket team)) return;
        Set<String> known = teams.computeIfAbsent(viewer, ignored -> ConcurrentHashMap.newKeySet());
        if (team.getTeamAction() == ClientboundSetPlayerTeamPacket.Action.REMOVE) known.remove(team.getName());
        else if (team.getPlayers().stream().anyMatch(names::contains)) known.add(team.getName());
    }
    private static Integer entityId(Object packet) {
        for (Class<?> owner = packet.getClass(); owner != null; owner = owner.getSuperclass()) {
            for (String name : List.of("entityId", "id", "entity")) {
                try {
                    Field field = owner.getDeclaredField(name);
                    if (field.getType() == int.class && field.trySetAccessible()) return field.getInt(packet);
                } catch (NoSuchFieldException ignored) {
                } catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
            }
        }
        return null;
    }
}
