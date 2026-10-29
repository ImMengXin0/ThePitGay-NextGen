package cn.mythicland.framework.bukkit.bossbar;

import cn.mythicland.framework.bukkit.ListenerComponent;
import cn.mythicland.framework.component.InjectComponent;
import cn.mythicland.framework.component.ManagedLifecycle;
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@InjectComponent
@ListenerComponent
public final class BukkitWitherBossBarService implements BossBarService, Listener, ManagedLifecycle {
    private static final double BOSS_BAR_DISTANCE = 48.0D;
    private static final double WALL_SEARCH_START = 6.0D;
    private static final double WALL_SEARCH_STEP = 0.5D;
    private static final double WALL_OFFSET = 1.0D;
    private static final double DOWNWARD_WALL_SEARCH_STEP = 0.25D;
    private static final double WITHER_SMOKE_HIDE_DEPTH = 3.5D;
    private static final float DOWNWARD_VIEW_PITCH_THRESHOLD = 10.0F;
    private static final float VIEW_YAW_THRESHOLD = 1.5F;
    private static final float VIEW_PITCH_THRESHOLD = 1.5F;

    private final JavaPlugin plugin;
    private final Map<UUID, PlayerBossBar> bossBars = new HashMap<>();

    public BukkitWitherBossBarService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void show(Player player, String title, double progress) {
        if (!this.isCraftPlayer(player)) return;
        PlayerBossBar bossBar = this.bossBars.get(player.getUniqueId());
        if (bossBar != null && !bossBar.isFor(player)) {
            bossBar.destroy();
            bossBar = null;
        }
        if (bossBar == null) {
            bossBar = new PlayerBossBar(player);
            this.bossBars.put(player.getUniqueId(), bossBar);
        }
        bossBar.update(title, progress);
    }

    @Override
    public void remove(Player player) {
        PlayerBossBar bossBar = this.bossBars.remove(player.getUniqueId());
        if (bossBar != null && player.isOnline()) bossBar.destroy();
    }

    @Override
    public void clear() {
        for (Map.Entry<UUID, PlayerBossBar> entry : this.bossBars.entrySet()) {
            Player player = this.plugin.getServer().getPlayer(entry.getKey());
            if (player != null) entry.getValue().destroy();
        }
        this.bossBars.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        this.remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        this.remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        this.respawn(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        this.respawn(event.getPlayer());
    }

    @Override
    public void reload() {
        this.clear();
    }

    @Override
    public void unload() {
        this.clear();
    }

    private boolean isCraftPlayer(Player player) {
        for (Class<?> type = player.getClass(); type != null; type = type.getSuperclass()) {
            if ("org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer".equals(type.getName())) return true;
        }
        return false;
    }

    private void respawn(Player player) {
        PlayerBossBar bossBar = this.bossBars.get(player.getUniqueId());
        if (bossBar != null && bossBar.isFor(player)) bossBar.respawn();
    }

    private enum Relocation {
        NONE,
        TELEPORTED,
        RESPAWNED
    }

    private static final class PlayerBossBar {
        private final Player player;
        private EntityWither wither;
        private Location lastPlayerLocation;
        private String lastTitle;
        private float lastHealth = 1.0F;

        private PlayerBossBar(Player player) {
            this.player = player;
            this.wither = this.createWither();
            this.spawn();
        }

        private boolean isFor(Player player) {
            return this.player == player;
        }

        private void update(String title, double progress) {
            Relocation relocation = this.relocateIfNeeded();
            float health = (float) cn.mythicland.framework.math.Clamps.clamp(300.0D * progress, 0.0D, 300.0D);
            String translatedTitle = ChatColor.translateAlternateColorCodes('&', title);
            if (relocation == Relocation.NONE
                    && translatedTitle.equals(this.lastTitle)
                    && Float.compare(health, this.lastHealth) == 0) return;
            this.lastTitle = translatedTitle;
            this.lastHealth = health;
            this.wither.setHealth(Math.max(1.0F, health));
            this.wither.setCustomName(translatedTitle);
            if (relocation == Relocation.TELEPORTED) this.send(new PacketPlayOutEntityTeleport(this.wither));
            this.send(new PacketPlayOutEntityMetadata(this.wither.getId(), this.wither.getDataWatcher(), true));
        }

        private void destroy() {
            if (this.player.isOnline()) this.send(new PacketPlayOutEntityDestroy(this.wither.getId()));
        }

        private void respawn() {
            this.destroy();
            this.wither = this.createWither();
            this.spawn();
        }

        private Relocation relocateIfNeeded() {
            Location playerLocation = this.player.getLocation();
            if (this.lastPlayerLocation == null || this.lastPlayerLocation.getWorld() != playerLocation.getWorld()) {
                this.respawn();
                return Relocation.RESPAWNED;
            }
            if (this.lastPlayerLocation.distanceSquared(playerLocation) > 0.0D
                    || this.angleDifference(this.lastPlayerLocation.getYaw(), playerLocation.getYaw())
                    >= VIEW_YAW_THRESHOLD
                    || Math.abs(this.lastPlayerLocation.getPitch() - playerLocation.getPitch())
                    >= VIEW_PITCH_THRESHOLD) {
                this.teleportToHiddenLocation();
                return Relocation.TELEPORTED;
            }
            return Relocation.NONE;
        }

        private void spawn() {
            this.teleportToHiddenLocation();
            this.wither.setInvisible(true);
            this.wither.setCustomNameVisible(true);
            this.wither.setHealth(Math.max(1.0F, this.lastHealth));
            if (this.lastTitle != null) this.wither.setCustomName(this.lastTitle);
            this.send(new PacketPlayOutSpawnEntityLiving(this.wither));
            this.send(new PacketPlayOutEntityMetadata(this.wither.getId(), this.wither.getDataWatcher(), true));
        }

        private EntityWither createWither() {
            CraftPlayer craftPlayer = (CraftPlayer) this.player;
            return new EntityWither(craftPlayer.getHandle().getWorld());
        }

        private void teleportToHiddenLocation() {
            Location playerLocation = this.player.getLocation();
            Location hiddenLocation = this.findHiddenLocation(this.player.getEyeLocation());
            this.lastPlayerLocation = playerLocation.clone();
            this.wither.setPosition(hiddenLocation.getX(), hiddenLocation.getY(), hiddenLocation.getZ());
        }

        private Location findHiddenLocation(Location eyeLocation) {
            Vector direction = eyeLocation.getDirection().normalize();
            if (eyeLocation.getPitch() >= DOWNWARD_VIEW_PITCH_THRESHOLD) {
                return this.findLocationBehindWall(eyeLocation, direction);
            }
            double farthestWallDistance = -1.0D;
            for (double distance = WALL_SEARCH_START; distance <= BOSS_BAR_DISTANCE; distance += WALL_SEARCH_STEP) {
                Location sample = eyeLocation.clone().add(direction.clone().multiply(distance));
                if (this.isOpaqueWall(sample.getBlock())) farthestWallDistance = distance;
            }
            if (farthestWallDistance >= 0.0D) {
                return eyeLocation.clone().add(direction.multiply(farthestWallDistance + WALL_OFFSET));
            }
            return eyeLocation.clone().add(direction.multiply(BOSS_BAR_DISTANCE));
        }

        private Location findLocationBehindWall(Location eyeLocation, Vector direction) {
            for (double distance = DOWNWARD_WALL_SEARCH_STEP;
                 distance <= BOSS_BAR_DISTANCE;
                 distance += DOWNWARD_WALL_SEARCH_STEP) {
                Location sample = eyeLocation.clone().add(direction.clone().multiply(distance));
                if (this.isOpaqueWall(sample.getBlock())) {
                    double hiddenDistance = Math.min(
                            BOSS_BAR_DISTANCE,
                            distance + (WITHER_SMOKE_HIDE_DEPTH / Math.abs(direction.getY()))
                    );
                    return eyeLocation.clone().add(direction.clone().multiply(hiddenDistance));
                }
            }
            return eyeLocation.clone().add(direction.multiply(BOSS_BAR_DISTANCE));
        }

        private boolean isOpaqueWall(Block block) {
            Material material = block.getType();
            return material.isSolid() && !material.isTransparent() && material != Material.BARRIER;
        }

        private float angleDifference(float first, float second) {
            float difference = Math.abs(first - second) % 360.0F;
            return difference > 180.0F ? 360.0F - difference : difference;
        }

        private void send(Packet<?> packet) {
            ((CraftPlayer) this.player).getHandle().playerConnection.sendPacket(packet);
        }
    }
}
