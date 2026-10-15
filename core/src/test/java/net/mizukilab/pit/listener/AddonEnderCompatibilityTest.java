package net.mizukilab.pit.listener;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.event.PitPlayerSpawnEvent;
import cn.charlotte.pit.api.PitInternalHook;
import cn.charlotte.pit.data.PlayerProfile;
import com.google.common.util.concurrent.AtomicDouble;
import net.mizukilab.pit.BukkitDataTestSupport;
import net.mizukilab.pit.compat.enchant.*;
import net.mizukilab.pit.enchantment.AbstractEnchantment;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import net.mizukilab.pit.enchantment.IActionDisplayEnchant;
import net.mizukilab.pit.item.DyeColor;
import net.mizukilab.pit.item.type.mythic.MythicLeggingsItem;
import net.mizukilab.pit.parm.listener.*;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.Utils;
import net.mizukilab.pit.util.cooldown.Cooldown;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.*;
import org.mockito.MockedStatic;

import java.lang.reflect.*;
import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Actual Addon sources pass through the core registry and Bukkit priority/duplicate handling. */
public class AddonEnderCompatibilityTest extends BukkitDataTestSupport {
    private interface TestArrow extends Arrow { @Override org.bukkit.projectiles.ProjectileSource getShooter(); }
    private interface TestPearl extends EnderPearl { @Override org.bukkit.projectiles.ProjectileSource getShooter(); }
    private URLClassLoader addon;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private MockedStatic<PlayerProfile> profiles;
    private MockedStatic<PlayerUtil> players;
    private MockedStatic<Utils> utils;
    private ThePit plugin;
    private Plugin addonPlugin;
    private PluginManager manager;
    private EnchantmentFactor factor;
    private PitInternalHook api;
    private Player attacker, victim;
    private World world;
    private ItemStack held;
    private final Map<ItemStack, Map<String, Integer>> levels = new IdentityHashMap<>();
    private final List<TestPearl> pearls = new ArrayList<>();
    private final List<Scheduled> scheduled = new ArrayList<>();
    private final Set<HandlerList> handlers = new HashSet<>();
    private record Scheduled(Runnable action, BukkitTask task, long delay) { }

    @Before public void setup() throws Exception {
        addon = new URLClassLoader(new URL[]{Path.of(System.getProperty("pitAddonJar")).toUri().toURL(),
                Path.of(System.getProperty("pitAddonLoaderJar")).toUri().toURL(),
                Path.of("build/libs/ThePitGay-V.jar").toUri().toURL()}, getClass().getClassLoader());
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class, RETURNS_DEEP_STUBS));
        manager = mock(PluginManager.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        pit = mockStatic(ThePit.class);
        plugin = mock(ThePit.class);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        addonPlugin = mock(Plugin.class); when(addonPlugin.isEnabled()).thenReturn(true);
        when(plugin.isEnabled()).thenReturn(true);
        factor = new EnchantmentFactor(); when(plugin.getEnchantmentFactor()).thenReturn(factor);
        api = mock(PitInternalHook.class); pit.when(ThePit::getApi).thenReturn(api);
        when(api.getItemEnchantLevel(any(), anyString())).thenAnswer(call ->
                levels.getOrDefault(call.getArgument(0), Map.of()).getOrDefault(call.getArgument(1), -1));
        profiles = mockStatic(PlayerProfile.class);
        players = mockStatic(PlayerUtil.class);
        utils = mockStatic(Utils.class);
        world = mock(World.class);
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());
        attacker = player(); victim = player();
        equip(Map.of());
        when(attacker.launchProjectile(EnderPearl.class)).thenAnswer(call -> {
            TestPearl pearl = mock(TestPearl.class);
            when(pearl.getShooter()).thenReturn(attacker); when(pearl.isValid()).thenReturn(true);
            when(pearl.getLocation()).thenReturn(new Location(world, 4, 70, 4));
            fire(new ProjectileLaunchEvent(pearl));
            pearls.add(pearl);
            return pearl;
        });
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenAnswer(call -> {
            BukkitTask task = mock(BukkitTask.class);
            scheduled.add(new Scheduled(call.getArgument(1), task, call.getArgument(2)));
            return task;
        });
        doAnswer(call -> { register(call.getArgument(0), call.getArgument(1)); return null; })
                .when(manager).registerEvents(any(Listener.class), any(Plugin.class));
    }

    @After public void teardown() throws Exception {
        for (AbstractEnchantment enchant : new ArrayList<>(factor.getEnchantments())) factor.removeEnchantment(enchant);
        for (HandlerList list : handlers) { list.unregister(plugin); list.unregister(addonPlugin); }
        utils.close(); players.close(); profiles.close(); pit.close(); bukkit.close(); addon.close();
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        when(player.getWorld()).thenReturn(world); when(player.getLocation()).thenReturn(new Location(world, 4, 70, 4));
        when(player.isOnline()).thenReturn(true); when(player.isValid()).thenReturn(true);
        when(player.getName()).thenReturn("compat-player"); when(player.getActivePotionEffects()).thenReturn(List.of());
        return player;
    }

    private AbstractEnchantment source(String name) throws Exception {
        return (AbstractEnchantment) addon.loadClass("cn.apnf.mistral.enchantments." + name).getConstructor().newInstance();
    }
    private AbstractEnchantment install(String name) throws Exception {
        AbstractEnchantment source = source(name);
        // Match Addon's startup: standalone listeners are registered before the factory.
        if (source instanceof Listener listener) register(listener, addonPlugin);
        factor.registerEnchantment(source);
        return factor.getEnchantmentMap().get(source.getNbtName());
    }
    private void pair() throws Exception { install("rare.EnderSword"); install("op.EnderEnvoy"); }
    private void equip(Map<String, Integer> enchantments) {
        held = new ItemStack(Material.GOLD_SWORD); levels.put(held, enchantments);
        when(attacker.getItemInHand()).thenReturn(held);
    }
    private void click() throws Exception { fire(interact(Action.RIGHT_CLICK_AIR)); }
    private PlayerInteractEvent interact(Action action) {
        return new PlayerInteractEvent(attacker, action, held, null, BlockFace.UP);
    }
    private PlayerTeleportEvent teleport(boolean cancelled) {
        PlayerTeleportEvent event = new PlayerTeleportEvent(attacker, attacker.getLocation(),
                new Location(world, 4, 70, 4), PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
        event.setCancelled(cancelled); return event;
    }
    private void register(Listener listener, Plugin owner) throws Exception {
        for (Method method : listener.getClass().getMethods()) {
            EventHandler annotation = method.getAnnotation(EventHandler.class);
            if (annotation == null) continue;
            Class<?> eventType = method.getParameterTypes()[0];
            HandlerList list = (HandlerList) eventType.getMethod("getHandlerList").invoke(null);
            handlers.add(list);
            list.register(new RegisteredListener(listener, (ignored, event) -> {
                if (!eventType.isInstance(event)) return;
                try { method.invoke(listener, event); }
                catch (InvocationTargetException e) { throw new EventException(e.getCause()); }
                catch (ReflectiveOperationException e) { throw new EventException(e); }
            }, annotation.priority(), owner, annotation.ignoreCancelled()));
        }
    }
    private void fire(Event event) throws Exception {
        for (RegisteredListener listener : event.getHandlers().getRegisteredListeners()) listener.callEvent(event);
    }
    private IAttackEntity attackHandler(String nbt) { return (IAttackEntity) factor.getEnchantmentMap().get(nbt); }
    private String text(String nbt) { return ((IActionDisplayEnchant) factor.getEnchantmentMap().get(nbt)).getText(3, attacker); }
    private void assertSharedCooldown() throws Exception {
        assertSame(field(factor.getEnchantmentMap().get("ender_envoy"), "shared"),
                field(factor.getEnchantmentMap().get("ender_sword"), "shared"));
        assertTrue(text("ender_envoy").startsWith("&c")); assertTrue(text("ender_sword").startsWith("&c"));
    }
    private static Object field(Object owner, String name) throws Exception {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    @Test public void bothEnchantsFireOneFreePearlAndShowSharedCooldown() throws Exception {
        pair(); equip(Map.of("ender_envoy", 3, "ender_sword", 3)); click();
        assertEquals(1, pearls.size()); assertSharedCooldown();
        players.verify(() -> PlayerUtil.damage(any(Player.class), any(PlayerUtil.DamageType.class), anyDouble(), anyBoolean()), never());
    }

    @Test public void switchingBetweenEnchantsCannotBypassSharedCooldown() throws Exception {
        pair(); equip(Map.of("ender_envoy", 3)); click();
        equip(Map.of("ender_sword", 3)); click();
        assertEquals(1, pearls.size()); assertSharedCooldown();
        assertTrue(text("ender_sword").startsWith("&c"));
        players.verify(() -> PlayerUtil.damage(any(Player.class), any(PlayerUtil.DamageType.class), anyDouble(), anyBoolean()), never());
    }

    @Test public void swordChargesOnceAndBlocksEnvoyUntilCooldownExpires() throws Exception {
        pair(); equip(Map.of("ender_sword", 2)); click();
        equip(Map.of("ender_envoy", 3)); click();
        players.verify(() -> PlayerUtil.damage(attacker, PlayerUtil.DamageType.TRUE, 3D, false));
        assertEquals(1, pearls.size());
        Object shared = field(factor.getEnchantmentMap().get("ender_sword"), "shared");
        Map<?, ?> cooldowns = (Map<?, ?>) field(shared, "cooldowns");
        Cooldown cooldown = (Cooldown) cooldowns.get(attacker.getUniqueId());
        assertEquals(30_000, cooldown.getDuration()); cooldown.fastExpired(); click();
        assertEquals(2, pearls.size());
        assertEquals(20_000, ((Cooldown) cooldowns.get(attacker.getUniqueId())).getDuration());
    }

    @Test public void blockRightClickWorksAndItemDenialBlocksActivation() throws Exception {
        pair(); equip(Map.of("ender_envoy", 3));
        PlayerInteractEvent denied = interact(Action.RIGHT_CLICK_AIR); denied.setUseItemInHand(Event.Result.DENY);
        fire(denied); assertTrue(pearls.isEmpty());
        fire(interact(Action.RIGHT_CLICK_BLOCK)); assertEquals(1, pearls.size());
    }

    @Test public void failedLaunchDoesNotChargeOrConsumeCooldown() throws Exception {
        pair(); equip(Map.of("ender_sword", 3));
        TestPearl denied = mock(TestPearl.class); when(denied.isDead()).thenReturn(true);
        doReturn(denied).when(attacker).launchProjectile(EnderPearl.class); click();
        assertEquals("&a&l✔", text("ender_sword"));
        players.verify(() -> PlayerUtil.damage(any(Player.class), any(PlayerUtil.DamageType.class), anyDouble(), anyBoolean()), never());
    }

    @Test public void landingGrantsSpeedOnceWithLaunchLevelAfterSwitchingWeapon() throws Exception {
        pair(); equip(Map.of("ender_envoy", 3)); click(); equip(Map.of());
        fire(teleport(false)); fire(teleport(false));
        verify(attacker).addPotionEffect(argThat(effect -> effect.getType().equals(PotionEffectType.SPEED)
                && effect.getAmplifier() == 3 && effect.getDuration() == 80), eq(true));
    }

    @Test public void cancelledLandingAndNextOrdinaryPearlCannotGrantSpeed() throws Exception {
        pair(); equip(Map.of("ender_envoy", 3)); click();
        fire(teleport(true)); fire(teleport(false));
        verify(attacker, never()).addPotionEffect(any(PotionEffect.class), anyBoolean());
    }

    @Test public void ordinaryPearlInvalidatesPendingEnchantedLanding() throws Exception {
        pair(); equip(Map.of("ender_envoy", 3)); click();
        TestPearl ordinary = mock(TestPearl.class); when(ordinary.getShooter()).thenReturn(attacker);
        fire(new ProjectileLaunchEvent(ordinary)); fire(teleport(false));
        verify(attacker, never()).addPotionEffect(any(PotionEffect.class), anyBoolean());
    }

    @Test public void strongerSpeedIsNotDowngradedButWeakerSpeedCanBeReplaced() throws Exception {
        pair(); equip(Map.of("ender_envoy", 3)); click();
        when(attacker.getActivePotionEffects()).thenReturn(List.of(new PotionEffect(PotionEffectType.SPEED, 200, 4)));
        fire(teleport(false)); verify(attacker, never()).addPotionEffect(any(PotionEffect.class), anyBoolean());
        fire(new PlayerQuitEvent(attacker, null)); click();
        when(attacker.getActivePotionEffects()).thenReturn(List.of(new PotionEffect(PotionEffectType.SPEED, 200, 0)));
        fire(teleport(false)); verify(attacker).addPotionEffect(any(PotionEffect.class), eq(true));
    }

    @Test public void deathAndSilencePreventLandingBuff() throws Exception {
        pair(); equip(Map.of("ender_envoy", 3)); click();
        fire(new PlayerDeathEvent(attacker, new ArrayList<>(), 0, null)); fire(teleport(false));
        verify(attacker, never()).addPotionEffect(any(PotionEffect.class), anyBoolean());
        fire(new PlayerQuitEvent(attacker, null)); click();
        players.when(() -> PlayerUtil.shouldIgnoreEnchant(attacker)).thenReturn(true); fire(teleport(false));
        verify(attacker, never()).addPotionEffect(any(PotionEffect.class), anyBoolean());
    }

    @Test public void envoyAirChestAndBotDamageBonusUsesCorrectHeartsAndMythicCheck() throws Exception {
        install("op.EnderEnvoy"); when(victim.isOnline()).thenReturn(false);
        when(victim.getInventory().getChestplate()).thenReturn(new ItemStack(Material.AIR));
        AtomicDouble base = new AtomicDouble(8), boost = new AtomicDouble(1);
        attackHandler("ender_envoy").handleAttackEntity(3, attacker, victim, 8, base, boost, new AtomicBoolean());
        assertEquals(2.75, boost.get(), 0.0001); assertEquals(8, base.get(), 0.0001);
        ItemStack own = new ItemStack(Material.LEATHER_LEGGINGS), other = new ItemStack(Material.LEATHER_LEGGINGS);
        when(attacker.getInventory().getLeggings()).thenReturn(own); when(victim.getInventory().getLeggings()).thenReturn(other);
        MythicLeggingsItem dyed = new MythicLeggingsItem(); dyed.setDyeColor(DyeColor.FUCHSIA);
        utils.when(() -> Utils.getMythicItem(same(own))).thenReturn(dyed);
        attackHandler("ender_envoy").handleAttackEntity(3, attacker, victim, 8, base, new AtomicDouble(1), new AtomicBoolean());
        assertEquals(8, base.get(), 0.0001); // Ordinary leather is not mythic armor.
        MythicLeggingsItem targetLeggings = new MythicLeggingsItem();
        utils.when(() -> Utils.getMythicItem(same(other))).thenReturn(targetLeggings);
        attackHandler("ender_envoy").handleAttackEntity(3, attacker, victim, 8, base, new AtomicDouble(1), new AtomicBoolean());
        assertEquals(0.2, base.get(), 0.0001);
    }

    @Test public void envoyKillBonusOnlyAppliesToPlayersWithLoadedProfile() throws Exception {
        IPlayerKilledEntity envoy = (IPlayerKilledEntity) install("op.EnderEnvoy");
        envoy.handlePlayerKilled(3, attacker, victim, new AtomicDouble(), new AtomicDouble()); // absent profile
        PlayerProfile profile = mock(PlayerProfile.class);
        profiles.when(() -> PlayerProfile.getRawCache(attacker.getUniqueId())).thenReturn(profile);
        when(profile.isLoaded()).thenReturn(true); when(profile.getStreakKills()).thenReturn(10D);
        envoy.handlePlayerKilled(3, attacker, mock(Zombie.class), new AtomicDouble(), new AtomicDouble());
        verify(profile, never()).setStreakKills(anyDouble());
        envoy.handlePlayerKilled(3, attacker, victim, new AtomicDouble(), new AtomicDouble());
        verify(profile).setStreakKills(10.2);
    }

    @Test public void registryRemovesOldListenersAndRespectsLaterThirdPartyReplacement() throws Exception {
        AbstractEnchantment source = source("op.EnderEnvoy"); register((Listener) source, addonPlugin);
        factor.registerEnchantment(source); AbstractEnchantment current = factor.getEnchantmentMap().get("ender_envoy");
        factor.registerEnchantment(source); assertSame(current, factor.getEnchantmentMap().get("ender_envoy"));
        for (RegisteredListener listener : PlayerInteractEvent.getHandlerList().getRegisteredListeners()) {
            assertNotEquals(source.getClass(), listener.getListener().getClass());
        }
        AbstractEnchantment thirdParty = mock(AbstractEnchantment.class);
        when(thirdParty.getNbtName()).thenReturn("ender_envoy"); factor.registerEnchantment(thirdParty);
        factor.removeEnchantment(source); assertSame(thirdParty, factor.getEnchantmentMap().get("ender_envoy"));
        equip(Map.of("ender_envoy", 3)); click(); assertTrue(pearls.isEmpty());
    }

    @Test public void removingSourceReferenceUnregistersItsOwnCompatibilityListener() throws Exception {
        AbstractEnchantment source = source("rare.EnderSword"); factor.registerEnchantment(source);
        factor.removeEnchantment(source); assertNull(factor.getEnchantmentMap().get("ender_sword"));
        equip(Map.of("ender_sword", 3)); click(); assertTrue(pearls.isEmpty());
    }

    private void echo(AbstractEnchantment echo) {
        ((IPlayerShootEntity) echo).handleShootEntity(3, attacker, victim, 8,
                new AtomicDouble(8), new AtomicDouble(1), new AtomicBoolean());
    }
    @Test public void echoDamagesValidOfflineFlagBotAndSplashUsesOriginalBlastLocation() throws Exception {
        AbstractEnchantment echo = install("rare.EchoArrowEnchant");
        when(victim.isOnline()).thenReturn(false); players.when(() -> PlayerUtil.isNPC(victim)).thenReturn(true);
        Player nearby = player();
        when(world.getNearbyEntities(any(Location.class), eq(3D), eq(3D), eq(3D))).thenReturn(List.of(attacker, victim, nearby));
        echo(echo); assertEquals(1, scheduled.size()); assertEquals(30, scheduled.get(0).delay());
        players.when(() -> PlayerUtil.damage(attacker, victim, PlayerUtil.DamageType.TRUE, 4D, false)).thenAnswer(call -> {
            when(victim.getLocation()).thenReturn(new Location(world, 100, 100, 100)); return null;
        });
        scheduled.get(0).action().run();
        players.verify(() -> PlayerUtil.damage(attacker, victim, PlayerUtil.DamageType.TRUE, 4D, false));
        players.verify(() -> PlayerUtil.damage(attacker, nearby, PlayerUtil.DamageType.TRUE, 2D, false));
        verify(world).getNearbyEntities(argThat(loc -> loc.getX() == 4), eq(3D), eq(3D), eq(3D));
    }

    @Test public void echoDoesNotHitRespawnedTargetAfterDeath() throws Exception {
        AbstractEnchantment echo = install("rare.EchoArrowEnchant"); echo(echo);
        fire(new PlayerDeathEvent(victim, new ArrayList<>(), 0, null));
        verify(scheduled.get(0).task()).cancel(); scheduled.get(0).action().run();
        players.verify(() -> PlayerUtil.damage(any(Player.class), any(Player.class), any(PlayerUtil.DamageType.class), anyDouble(), anyBoolean()), never());
    }

    @Test public void echoStopsOnQuitReplacementAndInvalidNpc() throws Exception {
        AbstractEnchantment echo = install("rare.EchoArrowEnchant"); echo(echo);
        fire(new PlayerQuitEvent(attacker, null)); scheduled.get(0).action().run();
        echo(echo); when(victim.isValid()).thenReturn(false); scheduled.get(1).action().run();
        fire(new PlayerQuitEvent(attacker, null)); echo(echo);
        factor.removeEnchantment(echo); verify(scheduled.get(2).task()).cancel(); scheduled.get(2).action().run();
        players.verify(() -> PlayerUtil.damage(any(Player.class), any(Player.class), any(PlayerUtil.DamageType.class), anyDouble(), anyBoolean()), never());
    }

    private void damaged(AbstractEnchantment enchant, Entity source) {
        ((IPlayerDamaged) enchant).handlePlayerDamaged(3, victim, source, 4,
                new AtomicDouble(4), new AtomicDouble(1), new AtomicBoolean());
    }
    private double boost(AbstractEnchantment enchant, boolean arrow) {
        AtomicDouble boost = new AtomicDouble(1);
        if (arrow) ((IPlayerShootEntity) enchant).handleShootEntity(3, victim, attacker, 4,
                new AtomicDouble(4), boost, new AtomicBoolean());
        else ((IAttackEntity) enchant).handleAttackEntity(3, victim, attacker, 4,
                new AtomicDouble(4), boost, new AtomicBoolean());
        return boost.get();
    }
    @Test public void microgravityResolvesArrowShooterAndStacksOnlyAfterThreeAirHits() throws Exception {
        AbstractEnchantment enchant = install("rare.RetroGravityMicrocosmEnchant");
        TestArrow arrow = mock(TestArrow.class); when(arrow.getShooter()).thenReturn(attacker);
        when(victim.isOnGround()).thenReturn(true); damaged(enchant, arrow);
        assertEquals(1, boost(enchant, false), 0);
        when(victim.isOnGround()).thenReturn(false);
        damaged(enchant, arrow); damaged(enchant, arrow);
        players.verify(() -> PlayerUtil.heal(victim, 4D), never());
        damaged(enchant, arrow); players.verify(() -> PlayerUtil.heal(victim, 4D));
        assertEquals(1.15, boost(enchant, false), 0.00001);
        for (int i = 0; i < 6; i++) damaged(enchant, attacker);
        assertEquals(1.3, boost(enchant, true), 0.00001);
        assertEquals(1.3, boost(enchant, false), 0.00001);
    }

    @Test public void microgravityExpiredWindowDoesNotHealAndExpiredBuffDoesNotBoostArrows() throws Exception {
        AbstractEnchantment enchant = install("rare.RetroGravityMicrocosmEnchant");
        damaged(enchant, attacker); damaged(enchant, attacker);
        Object state = ((Map<?, ?>) field(enchant, "states")).get(victim.getUniqueId());
        Field until = state.getClass().getDeclaredField("hitUntil"); until.setAccessible(true); until.setLong(state, 1);
        damaged(enchant, attacker); players.verify(() -> PlayerUtil.heal(victim, 4D), never());
        damaged(enchant, attacker); damaged(enchant, attacker);
        until = state.getClass().getDeclaredField("boostUntil"); until.setAccessible(true); until.setLong(state, 1);
        assertEquals(1, boost(enchant, true), 0); assertEquals(1, boost(enchant, false), 0);
        fire(new PlayerQuitEvent(victim, null)); assertTrue(((Map<?, ?>) field(enchant, "states")).isEmpty());
    }

    @Test public void unsafeAddonPlayerCastsAreFilteredBeforeCallbackButPlayerBotsAreAllowed() throws Exception {
        for (String name : List.of("normal.ComboChaosEnchant", "rare.ComboParasiteEnchant", "rare.SevereInjuryEnchant",
                "rare.NightMare", "addon.rare.VitalEchoEnchant", "rare.TearEnchant")) {
            AbstractEnchantment enchant = source(name);
            CallbackFilters filter = enchant instanceof IAttackEntity ? CallbackFilters.ATTACK : CallbackFilters.SHOOT;
            assertFalse(name, filter.allows(enchant, mock(Zombie.class)));
            assertTrue(name, filter.allows(enchant, victim));
        }
    }

    @Test public void coreRespawnInvalidatesPearlLandingWithoutResettingCooldown() throws Exception {
        pair(); equip(Map.of("ender_envoy", 3)); click();
        fire(new PitPlayerSpawnEvent(attacker)); fire(teleport(false)); click();
        verify(attacker, never()).addPotionEffect(any(PotionEffect.class), anyBoolean());
        assertEquals(1, pearls.size()); assertSharedCooldown();
    }

    @Test public void coreRespawnCancelsEchoOnEitherParticipant() throws Exception {
        AbstractEnchantment echo = install("rare.EchoArrowEnchant"); echo(echo);
        fire(new PitPlayerSpawnEvent(victim)); verify(scheduled.get(0).task()).cancel(); scheduled.get(0).action().run();
        fire(new PlayerQuitEvent(attacker, null)); echo(echo);
        fire(new PitPlayerSpawnEvent(attacker)); verify(scheduled.get(1).task()).cancel(); scheduled.get(1).action().run();
        players.verify(() -> PlayerUtil.damage(any(Player.class), any(Player.class), any(PlayerUtil.DamageType.class), anyDouble(), anyBoolean()), never());
    }

    @Test public void coreRespawnClearsMicrogravityStacksAndPartialCombo() throws Exception {
        AbstractEnchantment gravity = install("rare.RetroGravityMicrocosmEnchant");
        for (int i = 0; i < 5; i++) damaged(gravity, attacker);
        assertEquals(1.15, boost(gravity, false), 0.00001);
        fire(new PitPlayerSpawnEvent(victim)); assertEquals(1, boost(gravity, true), 0);
        damaged(gravity, attacker);
        players.verify(() -> PlayerUtil.heal(victim, 4D), times(1));
    }
}
