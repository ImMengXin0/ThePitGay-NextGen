package net.mizukilab.pit.listener;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import com.google.common.util.concurrent.AtomicDouble;
import net.minecraft.server.v1_8_R3.MinecraftServer;
import net.mizukilab.pit.BukkitDataTestSupport;
import net.mizukilab.pit.enchantment.*;
import net.mizukilab.pit.enchantment.type.dark_rare.ComboDustEnchant;
import net.mizukilab.pit.enchantment.type.normal.ComboDamageEnchant;
import net.mizukilab.pit.enchantment.type.normal.ComboSwiftEnchant;
import net.mizukilab.pit.enchantment.type.normal.CounterOffensiveEnchant;
import net.mizukilab.pit.enchantment.type.op.StarJudgementEnchant;
import net.mizukilab.pit.item.factory.ItemFactory;
import net.mizukilab.pit.item.type.mythic.MythicSwordItem;
import net.mizukilab.pit.parm.listener.*;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.PublicUtil;
import net.mizukilab.pit.util.random.RandomUtil;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.*;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.*;
import org.mockito.MockedStatic;

import java.lang.reflect.*;
import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class HitTickCompatibilityTest extends BukkitDataTestSupport {
    private interface HealthPlayer extends Player { @Override double getHealth(); @Override double getMaxHealth(); }
    private interface TestArrow extends Arrow { @Override org.bukkit.projectiles.ProjectileSource getShooter(); }
    private MockedStatic<ThePit> pit;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<PlayerUtil> players;
    private MockedStatic<PlayerProfile> profiles;
    private MockedStatic<PublicUtil> publicUtil;
    private URLClassLoader extensions;
    private ThePit plugin;
    private Plugin addonPlugin;
    private EnchantmentFactor factor;
    private Player attacker, victim;
    private PlayerProfile profile;
    private ItemFactory items;
    private final Map<UUID, PlayerProfile> playerProfiles = new HashMap<>();
    private final Map<UUID, AtomicInteger> melee = new HashMap<>(), bow = new HashMap<>();
    private final GameEffectListener effects = new GameEffectListener();
    private CombatListener combat;
    private CombatListener previousCombat;
    private final Set<HandlerList> handlers = new HashSet<>();
    private final List<Runnable> delayed = new ArrayList<>();
    private int savedTick;

    @Before public void setup() throws Exception {
        savedTick = MinecraftServer.currentTick; MinecraftServer.currentTick = 100;
        extensions = new URLClassLoader(new URL[]{Path.of(System.getProperty("pitAddonJar")).toUri().toURL(),
                Path.of(System.getProperty("pitAddonLoaderJar")).toUri().toURL(),
                Path.of(System.getProperty("repairExtendJar")).toUri().toURL(),
                Path.of("build/libs/ThePitGay-V.jar").toUri().toURL()}, getClass().getClassLoader());
        bukkit = mockStatic(Bukkit.class); bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class, RETURNS_DEEP_STUBS));
        PluginManager manager = mock(PluginManager.class); bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
        pit = mockStatic(ThePit.class); plugin = mock(ThePit.class); pit.when(ThePit::getInstance).thenReturn(plugin);
        when(plugin.isEnabled()).thenReturn(true);
        addonPlugin = mock(Plugin.class); when(addonPlugin.isEnabled()).thenReturn(true);
        players = mockStatic(PlayerUtil.class); profiles = mockStatic(PlayerProfile.class);
        publicUtil = mockStatic(PublicUtil.class, call -> call.getMethod().getName().startsWith("processActionBar")
                ? null : call.callRealMethod());
        factor = new EnchantmentFactor(); when(plugin.getEnchantmentFactor()).thenReturn(factor);
        items = mock(ItemFactory.class); when(plugin.getItemFactory()).thenReturn(items);
        BukkitScheduler scheduler = mock(BukkitScheduler.class); bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        previousCombat = CombatListener.INSTANCE;
        combat = new CombatListener();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> { delayed.add(call.getArgument(1)); return null; });
        doAnswer(call -> { register(call.getArgument(0), call.getArgument(1)); return null; })
                .when(manager).registerEvents(any(Listener.class), any(Plugin.class));
        attacker = player(); victim = player(); profile = playerProfiles.get(attacker.getUniqueId());
    }

    private Player player() {
        Player player = mock(HealthPlayer.class); UUID id = UUID.randomUUID(); when(player.getUniqueId()).thenReturn(id);
        when(player.getHealth()).thenReturn(20D); when(player.getMaxHealth()).thenReturn(20D);
        when(player.isOnline()).thenReturn(true); when(player.isValid()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        World world = mock(World.class); when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        Map<String, MetadataValue> metadata = new HashMap<>();
        when(player.hasMetadata(anyString())).thenAnswer(call -> metadata.containsKey(call.getArgument(0)));
        when(player.getMetadata(anyString())).thenAnswer(call -> {
            MetadataValue entry = metadata.get(call.getArgument(0)); return entry == null ? List.of() : List.of(entry);
        });
        doAnswer(call -> { metadata.put(call.getArgument(0), call.getArgument(1)); return null; }).when(player).setMetadata(anyString(), any());
        doAnswer(call -> { metadata.remove(call.getArgument(0)); return null; }).when(player).removeMetadata(anyString(), any());
        PlayerProfile p = mock(PlayerProfile.class, RETURNS_DEEP_STUBS); playerProfiles.put(id, p);
        when(p.isLoaded()).thenReturn(true); when(p.isInArena()).thenReturn(true);
        when(p.getKillRecap().getDamageLogs()).thenReturn(new net.mizukilab.pit.util.RangedStreamLineList<>(100, value -> false));
        when(p.getDamageMap()).thenReturn(new HashMap<>());
        AtomicInteger hits = new AtomicInteger(), arrows = new AtomicInteger(); melee.put(id, hits); bow.put(id, arrows);
        when(p.getMeleeHit()).thenAnswer(call -> hits.get()); doAnswer(call -> { hits.set(call.getArgument(0)); return null; }).when(p).setMeleeHit(anyInt());
        when(p.getBowHit()).thenAnswer(call -> arrows.get()); doAnswer(call -> { arrows.set(call.getArgument(0)); return null; }).when(p).setBowHit(anyInt());
        profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(id)).thenReturn(p);
        profiles.when(() -> PlayerProfile.getRawCache(id)).thenReturn(p);
        return player;
    }
    private void register(Listener listener, Plugin owner) throws Exception {
        for (Method method : listener.getClass().getMethods()) {
            EventHandler annotation = method.getAnnotation(EventHandler.class); if (annotation == null) continue;
            HandlerList list = (HandlerList) method.getParameterTypes()[0].getMethod("getHandlerList").invoke(null); handlers.add(list);
            list.register(new RegisteredListener(listener, (instance, event) -> {
                try { method.invoke(instance, event); } catch (ReflectiveOperationException error) { throw new EventException(error); }
            }, annotation.priority(), owner, annotation.ignoreCancelled()));
        }
    }
    private void fire(Event event) throws Exception {
        for (RegisteredListener listener : event.getHandlers().getRegisteredListeners()) listener.callEvent(event);
    }
    private AbstractEnchantment addon(String name) throws Exception {
        return (AbstractEnchantment) extensions.loadClass("cn.apnf.mistral.enchantments." + name).getConstructor().newInstance();
    }
    private double attack(IAttackEntity enchant, Player owner, Entity target, boolean cancelled) throws Exception {
        Method method = GameEffectListener.class.getDeclaredMethod("processAttackEntity", IAttackEntity.class,
                int.class, Player.class, Entity.class, double.class, AtomicDouble.class, AtomicDouble.class, AtomicBoolean.class);
        method.setAccessible(true); AtomicDouble boost = new AtomicDouble(1);
        method.invoke(effects, enchant, 3, owner, target, 4D, new AtomicDouble(4), boost, new AtomicBoolean(cancelled));
        return boost.get();
    }
    private void damaged(IPlayerDamaged enchant, Player owner, Entity source) throws Exception {
        Method method = GameEffectListener.class.getDeclaredMethod("processPlayerDamaged", IPlayerDamaged.class,
                int.class, Player.class, Entity.class, double.class, AtomicDouble.class, AtomicDouble.class, AtomicBoolean.class);
        method.setAccessible(true); method.invoke(effects, enchant, 3, owner, source, 4D,
                new AtomicDouble(4), new AtomicDouble(1), new AtomicBoolean());
    }
    private void commit(Player owner, Player target, Entity projectile, boolean cancelled) throws Exception {
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(projectile == null ? owner : projectile, target,
                projectile == null ? EntityDamageEvent.DamageCause.ENTITY_ATTACK : EntityDamageEvent.DamageCause.PROJECTILE, 4D);
        event.setCancelled(cancelled);
        Method method = CombatListener.class.getDeclaredMethod("onCombat", EntityDamageByEntityEvent.class); method.setAccessible(true);
        EventHandler annotation = method.getAnnotation(EventHandler.class);
        new RegisteredListener(combat, (listener, hit) -> {
            try { method.invoke(listener, hit); } catch (ReflectiveOperationException error) { throw new EventException(error); }
        }, annotation.priority(), plugin, annotation.ignoreCancelled()).callEvent(event);
    }
    private TestArrow arrow() {
        TestArrow arrow = mock(TestArrow.class); when(arrow.getUniqueId()).thenReturn(UUID.randomUUID());
        when(arrow.getShooter()).thenReturn(attacker); return arrow;
    }

    @Test public void highCpsCountsOneMeleeHitPerServerTickButAllDamageRemainsRecorded() throws Exception {
        for (int i = 0; i < 50; i++) commit(attacker, victim, null, false);
        assertEquals(1, profile.getMeleeHit()); verify(profile, times(50)).setTotalDamage(anyLong());
        MinecraftServer.currentTick++;
        commit(attacker, victim, null, false); assertEquals(2, profile.getMeleeHit());
        assertTrue(delayed.isEmpty());
    }

    @Test public void targetSwitchCannotBypassButIndependentAttackerCanCount() throws Exception {
        Player other = player();
        commit(attacker, victim, null, false); commit(attacker, other, null, false);
        commit(other, victim, null, false);
        assertEquals(1, profile.getMeleeHit()); assertEquals(1, playerProfiles.get(other.getUniqueId()).getMeleeHit());
    }

    @Test public void cancelledDamageCannotConsumeSharedHitQuota() throws Exception {
        commit(attacker, victim, null, true); assertEquals(0, profile.getMeleeHit());
        commit(attacker, victim, null, false); assertEquals(1, profile.getMeleeHit());
    }

    @Test public void triggerBoundaryCannotRepeatWhileSharedCounterIsUnchanged() throws Exception {
        ComboSwiftEnchant swift = new ComboSwiftEnchant();
        melee.get(attacker.getUniqueId()).set(3);
        for (int i = 0; i < 50; i++) attack(swift, attacker, victim, false);
        verify(attacker, times(1)).addPotionEffect(any(PotionEffect.class), anyBoolean());
        MinecraftServer.currentTick++;
        attack(swift, attacker, victim, false);
        verify(attacker, times(2)).addPotionEffect(any(PotionEffect.class), anyBoolean());
    }

    @Test public void realComboDamageCannotReenterAfterMonitorChangesModulo() throws Exception {
        ComboDamageEnchant combo = new ComboDamageEnchant(); melee.get(attacker.getUniqueId()).set(2);
        assertEquals(1, attack(combo, attacker, victim, false), 0); commit(attacker, victim, null, false);
        for (int i = 0; i < 25; i++) { assertEquals(1, attack(combo, attacker, victim, false), 0); commit(attacker, victim, null, false); }
        assertEquals(3, profile.getMeleeHit()); MinecraftServer.currentTick++;
        assertEquals(1.4, attack(combo, attacker, victim, false), 0.000001);
    }

    @Test public void independentEnchantsStillRunAndInvalidTargetDoesNotConsumeQuota() throws Exception {
        ComboDamageEnchant damage = new ComboDamageEnchant(); ComboSwiftEnchant swift = new ComboSwiftEnchant();
        melee.get(attacker.getUniqueId()).set(3);
        assertEquals(1, attack(damage, attacker, mock(Zombie.class), false), 0);
        assertEquals(1.4, attack(damage, attacker, victim, false), 0.000001);
        attack(swift, attacker, victim, true); attack(swift, attacker, victim, false);
        verify(attacker, times(1)).addPotionEffect(any(PotionEffect.class), anyBoolean());
    }

    @Test public void localCoreHitCounterRequiresFourSeparateTicks() throws Exception {
        try (MockedStatic<Potion> potion = mockStatic(Potion.class)) {
            PotionBrewer brewer = mock(PotionBrewer.class); potion.when(Potion::getBrewer).thenReturn(brewer);
            when(brewer.createEffect(any(PotionEffectType.class), anyInt(), anyInt())).thenAnswer(call ->
                    new PotionEffect(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
            ComboDustEnchant dust = new ComboDustEnchant();
            for (int tick = 0; tick < 4; tick++) {
                for (int i = 0; i < 30; i++) attack(dust, attacker, victim, false);
                verify(victim, times(tick == 3 ? 2 : 0)).addPotionEffect(any(PotionEffect.class), eq(true));
                MinecraftServer.currentTick++;
            }
        }
    }

    @Test public void actualAddonWarGodCannotChargeInOneHighCpsTick() throws Exception {
        AbstractEnchantment war = addon("op.WarGodEnchant");
        for (int tick = 0; tick < 4; tick++) {
            for (int i = 0; i < 20; i++) {
                double boost = attack((IAttackEntity) war, attacker, victim, false);
                assertEquals(tick == 3 && i == 0 ? 2.25 : 1D, boost, 0);
            }
            MinecraftServer.currentTick++;
        }
        verify(attacker, times(1)).playSound(any(Location.class), eq(Sound.WITHER_SHOOT), anyFloat(), anyFloat());
    }

    @Test public void actualAddonComboAndRepairSpeedForceAreGuarded() throws Exception {
        AbstractEnchantment chaos = addon("normal.ComboChaosEnchant"); melee.get(attacker.getUniqueId()).set(4);
        for (int i = 0; i < 30; i++) attack((IAttackEntity) chaos, attacker, victim, false);
        verify(victim, times(2)).addPotionEffect(any(PotionEffect.class));
        AbstractEnchantment speed = (AbstractEnchantment) extensions.loadClass("cn.mx.thepitaddon.enchantment.SpeedForceEnchant").getConstructor().newInstance();
        melee.get(attacker.getUniqueId()).set(2);
        for (int i = 0; i < 30; i++) attack((IAttackEntity) speed, attacker, victim, false);
        players.verify(() -> PlayerUtil.addPotionEffect(eq(attacker), any(PotionEffect.class), eq(true)), times(1));
    }

    @Test public void defensiveCountsSeparateAttackersAndTicks() throws Exception {
        CounterOffensiveEnchant counter = new CounterOffensiveEnchant();
        for (int i = 0; i < 30; i++) damaged(counter, victim, attacker);
        verify(victim, never()).addPotionEffect(any(PotionEffect.class));
        Player other = player(); damaged(counter, victim, other);
        verify(victim, never()).addPotionEffect(any(PotionEffect.class));
        MinecraftServer.currentTick++; damaged(counter, victim, attacker);
        verify(victim, times(1)).addPotionEffect(any(PotionEffect.class));
    }

    @Test public void arrowCountAndComboKeepDistinctVolleyArrowsInSameTick() throws Exception {
        TestArrow first = arrow(), second = arrow();
        commit(attacker, victim, first, false); commit(attacker, victim, first, false); commit(attacker, victim, second, false);
        assertEquals(2, profile.getBowHit());
        ComboDamageEnchant damage = new ComboDamageEnchant();
        assertTrue(HitTickGuard.attack(damage, attacker, first, false));
        assertFalse(HitTickGuard.attack(damage, attacker, first, false));
        assertTrue(HitTickGuard.attack(damage, attacker, second, false));
        assertTrue(HitTickGuard.attack(damage, attacker, null, false));
    }

    @Test public void starJudgementRollAndChatOnlyOncePerTick() throws Exception {
        try (MockedStatic<RandomUtil> random = mockStatic(RandomUtil.class)) {
            random.when(() -> RandomUtil.hasSuccessfullyByChance(0.3)).thenReturn(true);
            World world = attacker.getWorld(); when(victim.getWorld()).thenReturn(world);
            StarJudgementEnchant star = new StarJudgementEnchant();
            for (int i = 0; i < 30; i++) attack(star, attacker, victim, false);
            random.verify(() -> RandomUtil.hasSuccessfullyByChance(0.3), times(1));
            assertEquals(1, delayed.size()); delayed.remove(0).run();
            verify(attacker, times(1)).sendMessage("§e§l星辰裁决! §7触发成功!");
            players.verify(() -> PlayerUtil.damage(attacker, victim, PlayerUtil.DamageType.TRUE, 2D, false), times(1));
        }
    }

    @Test public void realAddonRawListenerIsReplacedAndOnlyGainsOneHasteStack() throws Exception {
        AbstractEnchantment frenzy = addon("normal.FrenzyStrikeEnchant");
        ItemStack stack = new ItemStack(Material.GOLD_SWORD); when(attacker.getItemInHand()).thenReturn(stack);
        MythicSwordItem item = new MythicSwordItem(); item.getEnchantments().put(frenzy, 3);
        when(items.getItemFromStack(stack)).thenReturn(item);
        register((Listener) frenzy, addonPlugin); factor.registerEnchantment(frenzy);
        for (int i = 0; i < 30; i++) fire(new EntityDamageByEntityEvent(attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 4D));
        verify(attacker, times(1)).addPotionEffect(argThat(e -> e.getAmplifier() == 0 && e.getType().equals(PotionEffectType.FAST_DIGGING)), eq(true));
        MinecraftServer.currentTick++;
        fire(new EntityDamageByEntityEvent(attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 4D));
        verify(attacker, times(1)).addPotionEffect(argThat(e -> e.getAmplifier() == 1), eq(true));
        for (RegisteredListener listener : EntityDamageByEntityEvent.getHandlerList().getRegisteredListeners()) {
            assertNotEquals(frenzy.getClass(), listener.getListener().getClass());
        }
    }

    @Test public void ordinaryEnchantsDoNotLoseCallbacksAndTickRolloverDoesNotLockCounters() throws Exception {
        AbstractEnchantment ordinary = mock(AbstractEnchantment.class, withSettings().extraInterfaces(IAttackEntity.class));
        when(ordinary.getNbtName()).thenReturn("billionaire");
        for (int i = 0; i < 30; i++) attack((IAttackEntity) ordinary, attacker, victim, false);
        verify((IAttackEntity) ordinary, times(30)).handleAttackEntity(anyInt(), eq(attacker), eq(victim), anyDouble(), any(), any(), any());
        MinecraftServer.currentTick = Integer.MAX_VALUE; assertTrue(factor.getHitTickGuard().countMelee(attacker));
        assertFalse(factor.getHitTickGuard().countMelee(attacker)); MinecraftServer.currentTick++;
        assertTrue(factor.getHitTickGuard().countMelee(attacker));
    }

    @Test public void sameEnchantmentOnHandAndPantsCannotEvaluateTwice() throws Exception {
        ComboDamageEnchant current = new ComboDamageEnchant(); factor.registerEnchantment(current);
        MythicSwordItem hand = new MythicSwordItem(), pants = new MythicSwordItem();
        hand.getEnchantments().put(new ComboDamageEnchant(), 3); pants.getEnchantments().put(new ComboDamageEnchant(), 3);
        melee.get(attacker.getUniqueId()).set(3);
        Method method = GameEffectListener.class.getDeclaredMethod("processAttackWithLeggingAndHeldItems", boolean.class,
                EntityDamageByEntityEvent.class, Player.class, AtomicDouble.class, AtomicDouble.class, AtomicBoolean.class,
                net.mizukilab.pit.item.IMythicItem.class, net.mizukilab.pit.item.IMythicItem.class);
        method.setAccessible(true); AtomicDouble boost = new AtomicDouble(1);
        method.invoke(effects, false, new EntityDamageByEntityEvent(attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 4D),
                attacker, new AtomicDouble(4), boost, new AtomicBoolean(), hand, pants);
        assertEquals(1.4, boost.get(), 0.000001);
    }

    @Test public void nestedDamageCannotReenterSameCountedCallback() throws Exception {
        AbstractEnchantment recursive = mock(AbstractEnchantment.class, withSettings().extraInterfaces(IAttackEntity.class));
        when(recursive.getNbtName()).thenReturn("combosteal_enchant");
        IAttackEntity callback = (IAttackEntity) recursive;
        doAnswer(call -> { attack(callback, attacker, victim, false); return null; })
                .when(callback).handleAttackEntity(anyInt(), any(), any(), anyDouble(), any(), any(), any());
        attack(callback, attacker, victim, false);
        verify(callback, times(1)).handleAttackEntity(anyInt(), any(), any(), anyDouble(), any(), any(), any());
    }

    @After public void teardown() throws Exception {
        if (factor != null) for (AbstractEnchantment entry : new ArrayList<>(factor.getEnchantments())) factor.removeEnchantment(entry);
        for (HandlerList list : handlers) { list.unregister(plugin); list.unregister(addonPlugin); }
        MinecraftServer.currentTick = savedTick;
        CombatListener.INSTANCE = previousCombat;
        if (publicUtil != null) publicUtil.close(); if (profiles != null) profiles.close(); if (players != null) players.close();
        if (pit != null) pit.close(); if (bukkit != null) bukkit.close(); if (extensions != null) extensions.close();
    }
}
