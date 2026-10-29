package net.mizukilab.pit.npc;

import cn.charlotte.pit.data.PlayerProfile;
import net.mizukilab.pit.listener.CombatListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Settles native bots which remove themselves without firing PlayerDeathEvent. */
public final class MythicBotCombat implements Listener, AutoCloseable {
    private static final long COMBAT_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final Map<UUID, State> states = new ConcurrentHashMap<>();
    private final Plugin plugin;
    private final Map<Player, List<EntityDamageEvent>> pending = new IdentityHashMap<>();
    private BukkitTask flushTask, cleanupTask;
    private boolean active;

    public MythicBotCombat(Plugin plugin) { this.plugin = plugin; }

    public void start() {
        active = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, MythicBotCombat::cleanup, 100L, 100L);
    }

    /** Passive lookup only; native profiles never enter the database operator. */
    public static PlayerProfile profile(UUID id) {
        State state = states.get(id);
        return state == null ? null : state.profile;
    }

    public static PlayerProfile profileFor(Player player) {
        return NpcIdentity.isMythicBot(player) ? state(player).profile : null;
    }

    private static State state(Player player) {
        return states.compute(player.getUniqueId(), (id, old) -> old != null && old.player.get() == player
                ? old : new State(player));
    }

    /** Shares the guard with both Bukkit death events and explicit addon settlements. */
    public static boolean claimDeath(Player player) {
        if (!NpcIdentity.isMythicBot(player)) return true;
        State state = state(player);
        if (state.settled) return false;
        state.settled = true;
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void prepareDamage(EntityDamageEvent event) {
        if (!active) return;
        if (event.getEntity() instanceof Player player) profileFor(player);
        if (event instanceof EntityDamageByEntityEvent hit) {
            Player source = playerSource(hit.getDamager());
            if (source != null) profileFor(source);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void afterDamage(EntityDamageEvent event) {
        if (!active || !(event.getEntity() instanceof Player player) || !NpcIdentity.isMythicBot(player)) return;
        state(player);
        pending.computeIfAbsent(player, ignored -> new ArrayList<>()).add(event);
        // One callback for all affected bots, after NMS has applied the final damage.
        if (flushTask == null) flushTask = Bukkit.getScheduler().runTask(plugin, this::flush);
    }

    private void flush() {
        flushTask = null;
        Map<Player, List<EntityDamageEvent>> hits = new IdentityHashMap<>(pending);
        pending.clear();
        if (!active) return;
        for (var entry : hits.entrySet()) {
            Player victim = entry.getKey();
            State state = state(victim);
            if (state.settled) continue;
            long now = System.nanoTime();
            for (EntityDamageEvent event : entry.getValue()) {
                if (event.isCancelled() || !Double.isFinite(event.getFinalDamage()) || event.getFinalDamage() <= 0) continue;
                if (event instanceof EntityDamageByEntityEvent hit) {
                    Player source = playerSource(hit.getDamager());
                    // A bot's final blow must not award an earlier human attacker.
                    if (source != null) {
                        state.attacker = NpcIdentity.isNpc(source) ? null : source.getUniqueId();
                        state.hitAt = now;
                    }
                }
            }
            // Removing/reloading a healthy bot is not a kill. Do not predict a
            // lethal hit from event damage: another listener can cancel or heal it.
            if (victim.getHealth() > 0) continue;
            Player killer = state.attacker != null && now - state.hitAt < COMBAT_NANOS
                    ? Bukkit.getPlayer(state.attacker) : null;
            if (killer != null && (!killer.isOnline() || NpcIdentity.isNpc(killer)
                    || !PlayerProfile.getPlayerProfileByUuid(killer.getUniqueId()).isLoaded())) killer = null;
            CombatListener combat = CombatListener.INSTANCE;
            if (combat != null) combat.handlePlayerDeath(victim, killer, false);
        }
    }

    private static Player playerSource(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        if (entity instanceof TNTPrimed) {
            var shooter = entity.getMetadata("shooter");
            if (!shooter.isEmpty()) {
                try { return Bukkit.getPlayer(UUID.fromString(shooter.get(0).asString())); }
                catch (IllegalArgumentException ignored) { }
            }
        }
        return null;
    }

    private static void cleanup() {
        long now = System.nanoTime();
        states.entrySet().removeIf(entry -> {
            State state = entry.getValue();
            Player player = state.player.get();
            if (player == null) return true;
            if (player.isValid()) { state.invalidAt = 0; return false; }
            if (state.invalidAt == 0) state.invalidAt = now;
            return now - state.invalidAt >= COMBAT_NANOS;
        });
    }

    @Override public void close() {
        active = false;
        if (flushTask != null) flushTask.cancel();
        if (cleanupTask != null) cleanupTask.cancel();
        flushTask = cleanupTask = null;
        pending.clear();
        states.clear();
        HandlerList.unregisterAll(this);
    }

    private static final class State {
        final WeakReference<Player> player;
        final PlayerProfile profile;
        UUID attacker;
        long hitAt, invalidAt;
        boolean settled;
        State(Player player) {
            this.player = new WeakReference<>(player);
            profile = new BotProfile(player);
        }
    }

    private static final class BotProfile extends PlayerProfile {
        BotProfile(Player player) {
            super(player.getUniqueId(), player.getName());
            prestige = 0;
            setExperience(0);
            setCoins(0);
            setBot(true);
            setInArena(true);
            // Remains unloaded: no persistence, player-only rewards or login hooks.
        }
        @Override public String getFormattedName() { return "§7" + getPlayerName(); }
        @Override public String getFormattedNameWithRoman() { return getFormattedName(); }
    }
}
