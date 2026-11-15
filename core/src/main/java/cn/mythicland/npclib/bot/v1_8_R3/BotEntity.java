package cn.mythicland.npclib.bot.v1_8_R3;

import cn.mythicland.npclib.api.skin.Skin;
import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.Stat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

/** A real server player for combat; visibility is owned by BotVisibility. */
public class BotEntity extends ServerPlayer {
    private final boolean walking;
    private final String sourceName;
    private final BiConsumer<BotEntity, DamageSource> deathHandler;
    private final BiConsumer<BotEntity, Runnable> stepHandler;
    private final EmbeddedChannel botChannel;

    public record Options(String name, String displayName, Skin skin, boolean walking,
                          BiConsumer<BotEntity, DamageSource> deathHandler,
                          BiConsumer<BotEntity, Runnable> stepHandler) {
        public Options {
            Objects.requireNonNull(name); Objects.requireNonNull(displayName);
            Objects.requireNonNull(deathHandler); Objects.requireNonNull(stepHandler);
        }
    }

    public static BotEntity create(Location location, Options options) {
        BotEntity entity = new BotEntity(location, profile(options.skin()), options);
        entity.spawn();
        return entity;
    }

    private static GameProfile profile(Skin skin) {
        UUID id = UUID.randomUUID();
        PropertyMap properties = skin == null ? PropertyMap.EMPTY : new PropertyMap(
                ImmutableMultimap.of("textures", new Property("textures", skin.value(), skin.signature())));
        return new GameProfile(id, "b" + id.toString().replace("-", "").substring(0, 15), properties);
    }

    private BotEntity(Location location, GameProfile profile, Options options) {
        super(MinecraftServer.getServer(), ((CraftWorld) location.getWorld()).getHandle(),
                profile, ClientInformation.createDefault());
        walking = options.walking(); sourceName = options.name();
        deathHandler = options.deathHandler(); stepHandler = options.stepHandler();
        botChannel = new EmbeddedChannel();
        net.minecraft.network.Connection network = new net.minecraft.network.Connection(PacketFlow.SERVERBOUND);
        network.channel = botChannel;
        network.address = new InetSocketAddress("127.0.0.1", 0);
        connection = new ServerGamePacketListenerImpl(MinecraftServer.getServer(), network, this,
                CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(Packet<?> packet) { }
            @Override public void send(Packet<?> packet, ChannelFutureListener listener) { }
        };
        gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        invulnerableTime = 0;
        suppressTrackerForLogin = true;
        getEntityData().set(Avatar.DATA_PLAYER_MODE_CUSTOMISATION, (byte) 127);
        setSprinting(!walking);
        setPos(location.getX(), location.getY(), location.getZ());
        setRot(location.getYaw(), location.getPitch());
        getBukkitEntity().setDisplayName(options.displayName());
        getBukkitEntity().setPersistent(false);
    }

    protected final void spawn() {
        ((ServerLevel) level()).addNewPlayer(this);
        ((ServerLevel) level()).players().remove(this);
        if (!valid) throw new IllegalStateException("Cannot add bot at " + getBukkitEntity().getLocation());
    }

    public UUID getUniqueID() { return getUUID(); }
    @Override public Component getTabListDisplayName() { return Component.empty(); }
    @Override public void tick() { }
    @Override public void doTick() { }
    @Override public void awardStat(Stat<?> stat, int amount) { }

    public void tickState() {
        ++tickCount;
        super.baseTick();
        if (invulnerableTime > 0) --invulnerableTime;
        ++attackStrengthTicker;
    }

    public void step(float strafe, float forward, double speed, boolean jump) {
        if (!isAlive() || isRemoved()) return;
        updateSprinting();
        var movement = getAttribute(Attributes.MOVEMENT_SPEED);
        movement.setBaseValue(.1 * speed * (getBukkitEntity().getWalkSpeed() / .2));
        setSpeed((float) movement.getValue());
        if (jump && onGround()) jumpFromGround();
        travel(new Vec3(strafe, 0, forward));
        setYBodyRot(getYRot());
        setYHeadRot(getYRot());
    }

    public void hit(org.bukkit.entity.Entity target) {
        swing(InteractionHand.MAIN_HAND);
        attack(((CraftEntity) target).getHandle());
        updateSprinting();
    }

    private void updateSprinting() {
        if (isSprinting() == walking) setSprinting(!walking);
    }

    @Override protected void checkFallDamage(double distance, boolean ground, BlockState block, BlockPos pos) {
        fallDistance = 0;
    }
    @Override public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        fallDistance = 0; return false;
    }
    @Override public void die(DamageSource source) { deathHandler.accept(this, source); }
    @Override public String getPlainTextName() {
        return sourceName == null ? super.getPlainTextName() : sourceName;
    }
    @Override protected void playStepSound(BlockPos pos, BlockState block) {
        if (stepHandler != null) stepHandler.accept(this, () -> super.playStepSound(pos, block));
    }

    public void transfer(Location location) {
        ((ServerLevel) level()).removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
        ServerLevel target = ((CraftWorld) location.getWorld()).getHandle();
        unsetRemoved();
        setServerLevel(target);
        gameMode.setLevel(target);
        setDeltaMovement(Vec3.ZERO);
        setPos(location.getX(), location.getY(), location.getZ());
        setRot(location.getYaw(), location.getPitch());
        spawn();
    }

    public void destroy() {
        if (!isRemoved()) ((ServerLevel) level()).removePlayerImmediately(this, Entity.RemovalReason.DISCARDED);
        botChannel.finishAndReleaseAll();
    }
}
