package cn.mythicland.npclib.bot.v1_8_R3;

import cn.mythicland.framework.task.StaggeredDeadline;
import cn.charlotte.pit.ThePit;
import net.mizukilab.pit.platform.paper.FancyTextService.TextHandle;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.*;

public class BotVisibility {
    private static final int PROFILE_REMOVE_DELAY_TICKS = 2;
    private static final int MAX_RELATIVE_MOVE_TICKS = 400;
    private static final int MAX_REVEALS_PER_TICK = 16;
    private final Map<UUID, BotVisibilitySettings> configurations = new HashMap<>();
    private final Map<UUID, Viewer> viewers = new LinkedHashMap<>();
    private final Map<UUID, Activity> activity = new HashMap<>();
    private final Map<UUID, TextHandle> nameplates = new HashMap<>();
    private final Set<UUID> online = new HashSet<>();
    private long currentTick;
    private long revealTick = Long.MIN_VALUE;
    private int reveals, revealCursor;

    private static double angle(double degrees) {
        return ((degrees + 180) % 360 + 360) % 360 - 180;
    }

    protected final boolean hasNameplate(UUID bot) {
        return nameplates.containsKey(bot);
    }

    public void configure(UUID bot, BotVisibilitySettings config) {
        BotVisibilitySettings previous = configurations.put(bot, Objects.requireNonNull(config, "config"));
        if (config.equals(previous)) return;
        for (Viewer viewer : viewers.values()) {
            Display display = viewer.entities.get(bot);
            if (display != null) {
                display.checkedAt = Long.MIN_VALUE / 2;
            }
        }
    }

    public void activity(Collection<? extends Player> players, long tick) {
        currentTick = tick;
        online.clear();
        for (Player player : players) {
            UUID id = player.getUniqueId();
            online.add(id);
            Viewer viewer = viewers.get(id);
            if (viewer != null) {
                viewer.pending.clear();
                viewer.retiredTeams.entrySet().removeIf(entry -> {
                    if (tick < entry.getValue()) return false;
                    send(player, ClientboundSetPlayerTeamPacket.createRemovePacket(new PlayerTeam(new Scoreboard(), entry.getKey())));
                    return true;
                });
            }
            Location location = player.getLocation();
            Activity previous = activity.get(id);
            boolean moved = previous == null || !previous.location().getWorld().equals(location.getWorld())
                    || previous.location().distanceSquared(location) > 0.03 * 0.03
                    || Math.abs(angle(previous.location().getYaw() - location.getYaw())) > 0.5
                    || Math.abs(previous.location().getPitch() - location.getPitch()) > 0.5;
            if (moved) {
                activity.put(id, new Activity(location, tick));
            }
        }
        activity.keySet().retainAll(online);
        viewers.keySet().retainAll(online);
    }

    public void update(
            ServerPlayer bot,
            Collection<? extends Player> players,
            long tick,
            UUID opponent,
            Set<UUID> combatViewers,
            BotVisibilitySettings config,
            String name,
            boolean hasEquipment
    ) {
        configure(bot.getUUID(), config);
        TextHandle nameplate = nameplates.get(bot.getUUID());
        if (name != null) {
            if (nameplate == null) {
                nameplate = ThePit.getInstance().getFancyTexts().create(
                        bot.getBukkitEntity().getLocation().add(0, 2.1, 0), List.of(name));
                nameplates.put(bot.getUUID(), nameplate);
            }
            nameplate.setText(List.of(name));
            nameplate.setLocation(bot.getBukkitEntity().getLocation().add(0, 2.1, 0));
        } else if (nameplate != null) {
            nameplate.close(); nameplates.remove(bot.getUUID()); nameplate = null;
        }
        Update update = new Update(bot, nameplate, tick, config, hasEquipment);
        for (Player player : players) {
            Viewer viewer = viewers.computeIfAbsent(player.getUniqueId(), id -> new Viewer());
            boolean fighting = player.getUniqueId().equals(opponent) || combatViewers.contains(player.getUniqueId());
            Display display = viewer.entities.get(bot.getUUID());
            boolean shouldCheckVisibility = fighting || display == null || !display.spawned
                    || tick - display.checkedAt >= config.visibilityCheckTicks();
            boolean visible;
            if (shouldCheckVisibility) {
                visible = visible(player, bot, tick, fighting, config);
            } else {
                visible = display.visible;
            }
            if (display != null && tick - display.checkedAt >= config.visibilityCheckTicks()) {
                display.checkedAt = tick;
                display.visible = visible;
            }
            if (!visible) {
                if (display != null) {
                    hide(player, bot, viewer);
                }
                continue;
            }
            if (display == null) {
                display = new Display(tick);
                viewer.entities.put(bot.getUUID(), display);
            }
            if (!display.spawned && tick - display.revealedAt < config.spawnDelayTicks()) continue;
            if (!display.spawned && !fighting) {
                Display pending = display;
                double distance = player.getEyeLocation().distanceSquared(bot.getBukkitEntity().getLocation());
                viewer.pending.put(bot.getUUID(), new Reveal(bot.getUUID(), display, tick,
                        config.maxRevealsPerTick(), distance, () -> update.sync(player, viewer, pending)));
                continue;
            }
            viewer.pending.remove(bot.getUUID());
            update.sync(player, viewer, display);
        }
    }

    public void flush(long tick) {
        if (revealTick != tick) {
            revealTick = tick;
            reveals = 0;
        }
        List<Viewer> ordered = new ArrayList<>(viewers.values());
        List<Deque<Reveal>> queues = new ArrayList<>();
        for (Viewer viewer : ordered) {
            if (viewer.revealTick != tick) {
                viewer.revealTick = tick;
                viewer.reveals = 0;
            }
            List<Reveal> pending = new ArrayList<>(viewer.pending.values());
            pending.sort(Comparator.comparingLong((Reveal reveal) -> reveal.display.revealedAt)
                    .thenComparingDouble(Reveal::distance));
            queues.add(new ArrayDeque<>(pending));
            viewer.pending.clear();
        }
        int skipped = 0;
        while (reveals < MAX_REVEALS_PER_TICK && skipped < ordered.size()) {
            int index = Math.floorMod(revealCursor++, ordered.size());
            Viewer viewer = ordered.get(index);
            Reveal reveal = queues.get(index).pollFirst();
            if (reveal == null || viewer.reveals >= reveal.maxPerTick) {
                skipped++;
                continue;
            }
            if (reveal.tick != tick || viewer.entities.get(reveal.bot) != reveal.display || reveal.display.spawned) {
                continue;
            }
            reveal.send.run();
            viewer.reveals++;
            reveals++;
            skipped = 0;
        }
    }

    private boolean visible(
            Player player,
            ServerPlayer bot,
            long tick,
            boolean fighting,
            BotVisibilitySettings config
    ) {
        if (!player.getWorld().equals(bot.getBukkitEntity().getWorld())) return false;
        if (!player.getWorld().isChunkLoaded((int) Math.floor(bot.getX()) >> 4, (int) Math.floor(bot.getZ()) >> 4)) {
            return false;
        }
        if (!player.canSee(bot.getBukkitEntity())) return false;
        Location eye = player.getEyeLocation();
        double dx = bot.getX() - eye.getX();
        double dy = bot.getY() + bot.getEyeHeight() - eye.getY();
        double dz = bot.getZ() - eye.getZ();
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared > config.viewDistance() * config.viewDistance()) return false;
        if (!((net.minecraft.server.level.ServerLevel) ((CraftPlayer) player).getHandle().level()).getChunkSource().chunkMap.isChunkTracked(
                ((CraftPlayer) player).getHandle(), (int) Math.floor(bot.getX()) >> 4, (int) Math.floor(bot.getZ()) >> 4)) return false;
        if (fighting) return true;
        Activity current = activity.get(player.getUniqueId());
        if (eye.getY() > config.maxViewerY()) return false;
        if (current != null && tick - current.tick() >= config.idleSeconds() * 20L) {
            this.onIdleHidden(player);
            return false;
        }
        if (distanceSquared <= config.nearbyDistance() * config.nearbyDistance()) return true;
        double yaw = Math.toRadians(eye.getYaw());
        double pitch = Math.toRadians(eye.getPitch());
        double horizontal = Math.cos(pitch);
        double dot = (-Math.sin(yaw) * horizontal * dx - Math.sin(pitch) * dy
                + Math.cos(yaw) * horizontal * dz) / Math.sqrt(distanceSquared);
        if (dot < Math.cos(Math.toRadians(config.viewAngle()))) return false;
        return player.hasLineOfSight(bot.getBukkitEntity());
    }

    public void equipment(Player player, ServerPlayer bot) {
        for (int slot = 0; slot < 5; slot++)
            send(player, equipmentPacket(bot, slot, equipmentItem(bot, slot)));
    }

    public boolean muteCombatSounds(UUID player, UUID bot, long tick) {
        BotVisibilitySettings config = configurations.get(bot);
        if (config == null) return false;
        Activity current = activity.get(player);
        if (current == null || tick - current.tick() < config.idleSeconds() * 20) return false;
        Viewer viewer = viewers.get(player);
        Display display = viewer == null ? null : viewer.entities.get(bot);
        return display == null || !display.spawned;
    }

    public boolean isVisible(UUID player, UUID bot) {
        Viewer viewer = viewers.get(player);
        Display display = viewer == null ? null : viewer.entities.get(bot);
        return display != null && display.spawned;
    }

    protected void onIdleHidden(Player player) {
    }

    public void restoreIdle(Player player, long tick, long idleTicks) {
        activity.put(player.getUniqueId(), new Activity(player.getLocation(), tick - idleTicks));
    }

    public void action(ServerPlayer bot, int action) {
        for (Map.Entry<UUID, Viewer> entry : viewers.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && isVisible(entry.getKey(), bot.getUUID())) {
                send(player, new ClientboundAnimatePacket(bot, action));
            }
        }
    }

    public void destroy(ServerPlayer bot) {
        for (Map.Entry<UUID, Viewer> entry : viewers.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && entry.getValue().entities.containsKey(bot.getUUID())) {
                hide(player, bot, entry.getValue());
            }
        }
        TextHandle plate = nameplates.remove(bot.getUUID());
        if (plate != null) plate.close();
        configurations.remove(bot.getUUID());
    }

    private void hide(Player player, ServerPlayer bot, Viewer viewer) {
        Display display = viewer.entities.remove(bot.getUUID());
        viewer.pending.remove(bot.getUUID());
        if (!display.spawned) return;
        TextHandle nameplate = nameplates.get(bot.getUUID());
        send(player, new ClientboundRemoveEntitiesPacket(bot.getId()));
        if (nameplate != null) nameplate.hide(player);
        if (display.profileRemoveAt > 0) {
            send(player, new ClientboundPlayerInfoRemovePacket(List.of(bot.getUUID())));
        }
        if (display.team != null) {
            viewer.retiredTeams.put(display.team, currentTick + 2);
        }
    }

    private void send(Player player, Packet<?> packet) {
        ((CraftPlayer) player).getHandle().connection.send(packet);
    }

    public void clear() {
        for (Map.Entry<UUID, Viewer> entry : viewers.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;
            for (String team : entry.getValue().retiredTeams.keySet()) {
                send(player, ClientboundSetPlayerTeamPacket.createRemovePacket(new PlayerTeam(new Scoreboard(), team)));
            }
        }
        configurations.clear();
        viewers.clear();
        activity.clear();
        online.clear();
        nameplates.values().forEach(TextHandle::close);
        nameplates.clear();
        revealTick = Long.MIN_VALUE;
        reveals = revealCursor = 0;
    }

    private static final EquipmentSlot[] SLOTS = { EquipmentSlot.MAINHAND, EquipmentSlot.FEET,
            EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD };

    private static ItemStack equipmentItem(ServerPlayer bot, int slot) { return bot.getItemBySlot(SLOTS[slot]); }
    private static ClientboundSetEquipmentPacket equipmentPacket(ServerPlayer bot, int slot, ItemStack item) {
        return new ClientboundSetEquipmentPacket(bot.getId(), List.of(Pair.of(SLOTS[slot], item)));
    }

    private record Reveal(
            UUID bot,
            Display display,
            long tick,
            int maxPerTick,
            double distance,
            Runnable send
    ) {
    }

    private static final class Viewer {
        private final Map<UUID, Display> entities = new HashMap<>();
        private final Map<UUID, Reveal> pending = new HashMap<>();
        private final Map<String, Long> retiredTeams = new HashMap<>();
        private long revealTick;
        private int reveals;
    }

    private static final class Display {
        private final long revealedAt;
        private long checkedAt;
        private boolean visible = true, spawned;
        private String team;
        private Movement location;
        private long lastTeleportTick;
        private ItemStack[] equipment;
        private int hurtTicks;
        private long nextEquipmentCheck;
        private long profileRemoveAt;

        private Display(long tick) {
            revealedAt = tick;
            checkedAt = tick;
        }
    }

    private static final class Movement {
        private final int entityId;
        private final long x, y, z;
        private final PositionMoveRotation position;
        private final byte yaw, pitch;
        private final boolean onGround;
        private Packet<?> teleport, look, relative;
        private long relativeX, relativeY, relativeZ;
        private boolean relativeLook;

        private Movement(Entity entity) {
            entityId = entity.getId();
            position = PositionMoveRotation.of(entity);
            x = (long) Math.floor(entity.getX() * 4096.0D);
            y = (long) Math.floor(entity.getY() * 4096.0D);
            z = (long) Math.floor(entity.getZ() * 4096.0D);
            yaw = (byte) (int) (entity.getYRot() * 256.0F / 360.0F);
            pitch = (byte) (int) (entity.getXRot() * 256.0F / 360.0F);
            onGround = entity.onGround();
        }

        private Packet<?> packet(Movement previous, boolean absolute) {
            if (previous == null) return teleport();
            long dx = (long) x - previous.x, dy = (long) y - previous.y, dz = (long) z - previous.z;
            boolean moved = dx != 0 || dy != 0 || dz != 0;
            boolean turned = yaw != previous.yaw || pitch != previous.pitch;
            if (!moved && !turned && onGround == previous.onGround) return null;
            if (absolute || onGround != previous.onGround || dx < Short.MIN_VALUE || dx > Short.MAX_VALUE
                    || dy < Short.MIN_VALUE || dy > Short.MAX_VALUE || dz < Short.MIN_VALUE || dz > Short.MAX_VALUE) return teleport();
            if (!moved) {
                if (look == null) {
                    look = new ClientboundMoveEntityPacket.Rot(entityId, yaw, pitch, onGround);
                }
                return look;
            }
            if (relative == null || relativeX != previous.x || relativeY != previous.y
                    || relativeZ != previous.z || relativeLook != turned) {
                relativeX = previous.x;
                relativeY = previous.y;
                relativeZ = previous.z;
                relativeLook = turned;
                relative = turned
                        ? new ClientboundMoveEntityPacket.PosRot(
                        entityId,
                        (short) dx,
                        (short) dy,
                        (short) dz,
                        yaw,
                        pitch,
                        onGround
                )
                        : new ClientboundMoveEntityPacket.Pos(
                        entityId,
                        (short) dx,
                        (short) dy,
                        (short) dz,
                        onGround
                );
            }
            return relative;
        }

        private Packet<?> teleport() {
            if (teleport == null) {
                teleport = ClientboundTeleportEntityPacket.teleport(entityId, position, Set.of(), onGround);
            }
            return teleport;
        }
    }

    private record Activity(Location location, long tick) {
    }

    private final class Update {
        private final ServerPlayer bot;
        private final TextHandle nameplate;
        private final long tick;
        private final BotVisibilitySettings config;
        private final boolean hasEquipment;
        private final Movement movement;
        private final ClientboundSetEntityDataPacket metadata;
        private ClientboundRotateHeadPacket rotation;
        private ClientboundHurtAnimationPacket hurt;
        private ItemStack[] equipment;
        private ClientboundSetEquipmentPacket[] equipmentPackets;

        private Update(
                ServerPlayer bot,
                TextHandle nameplate,
                long tick,
                BotVisibilitySettings config,
                boolean hasEquipment
        ) {
            this.bot = bot;
            this.nameplate = nameplate;
            this.tick = tick;
            this.config = config;
            this.hasEquipment = hasEquipment;
            movement = new Movement(bot);
            var dirty = bot.getEntityData().packDirty();
            metadata = dirty == null || dirty.isEmpty() ? null : new ClientboundSetEntityDataPacket(bot.getId(), dirty);
        }

        private void sync(Player player, Viewer viewer, Display display) {
            boolean initial = !display.spawned;
            if (!display.spawned) {
                send(player, ClientboundPlayerInfoUpdatePacket.createSinglePlayerInitializing(bot, false));
                display.team = "b" + bot.getUUID().toString().replace("-", "").substring(0, 15);
                viewer.retiredTeams.remove(display.team);
                Scoreboard scoreboard = new Scoreboard();
                PlayerTeam team = scoreboard.addPlayerTeam(display.team);
                team.setNameTagVisibility(Team.Visibility.NEVER);
                scoreboard.addPlayerToTeam(bot.getGameProfile().name(), team);
                send(player, ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
                send(player, new ClientboundAddEntityPacket(bot.getId(), bot.getUUID(), bot.getX(), bot.getY(), bot.getZ(),
                        bot.getXRot(), bot.getYRot(), bot.getType(), 0, bot.getDeltaMovement(), bot.getYHeadRot()));
                var fullData = bot.getEntityData().getNonDefaultValues();
                if (fullData != null && !fullData.isEmpty()) send(player, new ClientboundSetEntityDataPacket(bot.getId(), fullData));
                if (nameplate != null) {
                    nameplate.show(player);
                }
                display.spawned = true;
                display.profileRemoveAt = tick + PROFILE_REMOVE_DELAY_TICKS;
                display.location = movement;
                display.lastTeleportTick = tick;
                if (hasEquipment) {
                    ItemStack[] snapshot = equipment();
                    for (int slot = 0; slot < 5; slot++) {
                        if (snapshot[slot] != null) {
                            send(player, equipmentPacket(slot));
                        }
                    }
                    display.equipment = snapshot;
                    display.nextEquipmentCheck =
                            StaggeredDeadline.after(tick, -bot.getId(), config.equipmentCheckTicks());
                }
            }
            if (nameplate != null) nameplate.show(player);
            if (display.profileRemoveAt > 0 && tick >= display.profileRemoveAt) {
                send(player,
                        new ClientboundPlayerInfoRemovePacket(List.of(bot.getUUID())));
                display.profileRemoveAt = 0;
            }
            if (metadata != null && !initial) {
                send(player, metadata);
            }
            boolean absolute = tick - display.lastTeleportTick >= MAX_RELATIVE_MOVE_TICKS;
            Packet<?> packet = movement.packet(display.location, absolute);
            if (packet != null) {
                send(player, packet);
                if (packet instanceof ClientboundTeleportEntityPacket) {
                    display.lastTeleportTick = tick;
                }
            }
            if (initial || display.location.yaw != movement.yaw) {
                if (rotation == null) {
                    rotation = new ClientboundRotateHeadPacket(bot, movement.yaw);
                }
                send(player, rotation);
            }
            if (packet != null) {
                display.location = movement;
            }
            if (hasEquipment && tick >= display.nextEquipmentCheck) {
                display.nextEquipmentCheck = StaggeredDeadline.after(tick, -bot.getId(), config.equipmentCheckTicks());
                boolean changed = false;
                for (int slot = 0; slot < 5; slot++) {
                    ItemStack current = equipment == null ? equipmentItem(bot, slot) : equipment[slot];
                    if (display.equipment != null && ItemStack.matches(display.equipment[slot], current)) {
                        continue;
                    }
                    send(player, equipmentPacket(slot));
                    changed = true;
                }
                if (changed) {
                    display.equipment = equipment();
                }
            }
            if (bot.hurtTime > display.hurtTicks) {
                if (hurt == null) {
                    hurt = new ClientboundHurtAnimationPacket(bot.getId(), 0f);
                }
                send(player, hurt);
            }
            display.hurtTicks = bot.hurtTime;
        }

        private ItemStack[] equipment() {
            if (equipment != null) return equipment;
            equipment = new ItemStack[5];
            for (int slot = 0; slot < equipment.length; slot++) {
                ItemStack item = equipmentItem(bot, slot);
                equipment[slot] = item == null ? null : item.copy();
            }
            return equipment;
        }

        private ClientboundSetEquipmentPacket equipmentPacket(int slot) {
            if (equipmentPackets == null) {
                equipmentPackets = new ClientboundSetEquipmentPacket[5];
            }
            if (equipmentPackets[slot] == null) {
                equipmentPackets[slot] = BotVisibility.equipmentPacket(bot, slot, equipment()[slot]);
            }
            return equipmentPackets[slot];
        }
    }
}
