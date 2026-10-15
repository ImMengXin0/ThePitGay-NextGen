package net.mizukilab.pit.listener;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.api.PitInternalHook;
import cn.charlotte.pit.event.PitPlayerSpawnEvent;
import com.google.common.util.concurrent.AtomicDouble;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.server.v1_8_R3.EntityArrow;
import net.mizukilab.pit.BukkitDataTestSupport;
import net.mizukilab.pit.enchantment.AbstractEnchantment;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import net.mizukilab.pit.enchantment.IActionDisplayEnchant;
import net.mizukilab.pit.item.factory.ItemFactory;
import net.mizukilab.pit.item.type.mythic.MythicBowItem;
import net.mizukilab.pit.parm.listener.IPlayerShootEntity;
import net.mizukilab.pit.util.PlayerUtil;
import org.bukkit.*;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftArrow;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.*;
import org.bukkit.plugin.*;
import org.bukkit.potion.*;
import org.bukkit.scheduler.*;
import org.junit.*;
import org.mockito.MockedStatic;

import java.lang.reflect.*;
import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real Addon registration and Repair metadata/checker, with Bukkit event ordering and tick scheduling. */
public class BowComboVolleyCompatibilityTest extends BukkitDataTestSupport {
    private abstract static class TestArrow extends CraftArrow {
        TestArrow() { super(null, null); }
        @Override public abstract org.bukkit.projectiles.ProjectileSource getShooter();
    }
    private record Scheduled(Runnable action, BukkitTask task) { }
    private URLClassLoader extensions;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private MockedStatic<PlayerUtil> players;
    private ThePit plugin;
    private Plugin addonPlugin, repairPlugin;
    private EnchantmentFactor factor;
    private PitInternalHook api;
    private Player shooter, target;
    private AbstractEnchantment source, enchant;
    private final Map<ItemStack, Integer> levels = new IdentityHashMap<>();
    private final List<Scheduled> scheduled = new ArrayList<>();
    private final Set<HandlerList> handlers = new HashSet<>();

    @Before public void setup() throws Exception {
        extensions = new URLClassLoader(new URL[]{Path.of(System.getProperty("pitAddonJar")).toUri().toURL(),
                Path.of(System.getProperty("pitAddonLoaderJar")).toUri().toURL(),
                Path.of(System.getProperty("repairExtendJar")).toUri().toURL(),
                Path.of("build/libs/ThePitGay-V.jar").toUri().toURL()}, getClass().getClassLoader());
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class, RETURNS_DEEP_STUBS));
        PluginManager manager = mock(PluginManager.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        pit = mockStatic(ThePit.class);
        plugin = mock(ThePit.class); when(plugin.isEnabled()).thenReturn(true);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        addonPlugin = mock(Plugin.class); when(addonPlugin.isEnabled()).thenReturn(true);
        factor = new EnchantmentFactor(); when(plugin.getEnchantmentFactor()).thenReturn(factor);
        api = mock(PitInternalHook.class); pit.when(ThePit::getApi).thenReturn(api);
        when(api.getItemEnchantLevel(any(), eq("bow_combo_enchant"))).thenAnswer(call -> levels.getOrDefault(call.getArgument(0), -1));
        ItemFactory items = mock(ItemFactory.class); when(plugin.getItemFactory()).thenReturn(items);
        when(items.getItemFromStack(any())).thenAnswer(call -> {
            MythicBowItem bow = new MythicBowItem();
            int level = levels.getOrDefault(call.getArgument(0), 0);
            if (level > 0) bow.getEnchantments().put(source, level);
            return bow;
        });
        players = mockStatic(PlayerUtil.class);
        shooter = mock(Player.class); when(shooter.getUniqueId()).thenReturn(UUID.randomUUID());
        when(shooter.isOnline()).thenReturn(true); when(shooter.getActivePotionEffects()).thenReturn(List.of());
        target = mock(Player.class); when(target.getUniqueId()).thenReturn(UUID.randomUUID());
        when(scheduler.runTaskLater(any(Plugin.class), any(Runnable.class), eq(1L))).thenAnswer(call -> enqueue(call.getArgument(1)));
        when(scheduler.runTask(any(Plugin.class), any(Runnable.class))).thenAnswer(call -> enqueue(call.getArgument(1)));
        doAnswer(call -> { register(call.getArgument(0), call.getArgument(1)); return null; })
                .when(manager).registerEvents(any(Listener.class), any(Plugin.class));
        source = (AbstractEnchantment) extensions.loadClass("cn.apnf.mistral.enchantments.normal.BowComboEnchant").getConstructor().newInstance();
        // Addon first registers a separate listener, then installs its enchantment instance.
        register((Listener) source.getClass().getConstructor().newInstance(), addonPlugin);
        factor.registerEnchantment(source);
        enchant = factor.getEnchantmentMap().get("bow_combo_enchant");
    }

    private BukkitTask enqueue(Runnable action) {
        BukkitTask task = mock(BukkitTask.class); scheduled.add(new Scheduled(action, task)); return task;
    }
    private void tick() {
        List<Scheduled> batch = new ArrayList<>(scheduled); scheduled.clear();
        for (Scheduled job : batch) job.action().run();
    }
    private void register(Listener listener, Plugin owner) throws Exception {
        for (Method method : listener.getClass().getMethods()) {
            EventHandler annotation = method.getAnnotation(EventHandler.class);
            if (annotation == null) continue;
            HandlerList list = (HandlerList) method.getParameterTypes()[0].getMethod("getHandlerList").invoke(null);
            handlers.add(list);
            list.register(new RegisteredListener(listener, (instance, event) -> {
                try { method.invoke(instance, event); } catch (ReflectiveOperationException error) { throw new EventException(error); }
            }, annotation.priority(), owner, annotation.ignoreCancelled()));
        }
    }
    private void fire(Event event) throws Exception {
        for (RegisteredListener listener : event.getHandlers().getRegisteredListeners()) listener.callEvent(event);
    }
    private TestArrow arrow() {
        TestArrow arrow = mock(TestArrow.class);
        when(arrow.getUniqueId()).thenReturn(UUID.randomUUID()); when(arrow.getShooter()).thenReturn(shooter);
        when(arrow.getHandle()).thenReturn(mock(EntityArrow.class));
        Map<String, List<MetadataValue>> metadata = new HashMap<>();
        when(arrow.getMetadata(anyString())).thenAnswer(call -> metadata.getOrDefault(call.getArgument(0), List.of()));
        when(arrow.hasMetadata(anyString())).thenAnswer(call -> metadata.containsKey(call.getArgument(0)));
        doAnswer(call -> {
            String key = call.getArgument(0); MetadataValue value = call.getArgument(1);
            List<MetadataValue> entries = new ArrayList<>(metadata.getOrDefault(key, List.of()));
            entries.removeIf(old -> old.getOwningPlugin() == value.getOwningPlugin()); entries.add(value); metadata.put(key, entries);
            return null;
        }).when(arrow).setMetadata(anyString(), any(MetadataValue.class));
        return arrow;
    }
    private ItemStack bow(int level) {
        ItemStack bow = new ItemStack(Material.BOW); levels.put(bow, level); return bow;
    }
    private TestArrow shot(int level) throws Exception {
        TestArrow arrow = arrow(); fire(new EntityShootBowEvent(shooter, bow(level), arrow, 1F)); return arrow;
    }
    private EntityDamageByEntityEvent damage(TestArrow arrow, boolean cancelled) throws Exception {
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(arrow, target, EntityDamageEvent.DamageCause.PROJECTILE, 4D);
        event.setCancelled(cancelled); fire(event); return event;
    }
    private void collide(TestArrow arrow, boolean block) throws Exception {
        fire(new ProjectileHitEvent(arrow));
        // Match NMS ordering: the flag changes only after ProjectileHitEvent returns.
        when(arrow.getHandle().isInGround()).thenReturn(block);
    }
    private String count(int level) { return ((IActionDisplayEnchant) enchant).getText(level, shooter); }
    private void speed(int level, int times) {
        verify(shooter, times(times)).addPotionEffect(argThat(effect -> effect.getType().equals(PotionEffectType.SPEED)
                && effect.getDuration() == 80 && effect.getAmplifier() == level), eq(true));
    }
    private void metadata(TestArrow arrow, int level) {
        Object2IntOpenHashMap<AbstractEnchantment> map = new Object2IntOpenHashMap<>(); map.put(source, level);
        arrow.setMetadata("enchant", new FixedMetadataValue(plugin, map));
    }

    @Test public void sameTargetInvulnerabilityHitTriggersWithoutSecondDamageCallback() throws Exception {
        TestArrow first = shot(3); collide(first, false); damage(first, false); tick();
        assertEquals("&e&l1/2", count(3)); speed(3, 0);
        TestArrow next = shot(3); collide(next, false); tick(); // No damage event during hurt resistance.
        speed(3, 1); assertEquals("&e&l0/2", count(3));
        verify(target, never()).setNoDamageTicks(anyInt()); verify(target, never()).damage(anyDouble());
    }

    @Test public void allThreeLevelsUseTheirOwnThresholdAndFourSecondSpeed() throws Exception {
        for (int level = 1; level <= 3; level++) {
            int required = level == 1 ? 3 : 2;
            for (int i = 1; i < required; i++) { collide(shot(level), false); tick(); speed(level, 0); }
            collide(shot(level), false); tick(); speed(level, 1);
            assertEquals("&e&l0/" + required, count(level));
        }
    }

    @Test public void coreOriginalIsAlsoAdaptedAndThirdPartyReplacementKeepsControl() throws Exception {
        AbstractEnchantment core = new net.mizukilab.pit.enchantment.type.normal.BowComboEnchant();
        factor.registerEnchantment(core); enchant = factor.getEnchantmentMap().get(core.getNbtName());
        collide(shot(2), false); collide(shot(2), false); tick(); speed(2, 1);
        AbstractEnchantment thirdParty = mock(AbstractEnchantment.class);
        when(thirdParty.getNbtName()).thenReturn(core.getNbtName()); factor.registerEnchantment(thirdParty);
        factor.removeEnchantment(core); assertSame(thirdParty, factor.getEnchantmentMap().get(core.getNbtName()));
    }

    @Test public void duplicateCollisionAndDamageCallbacksCountOneArrowOnce() throws Exception {
        TestArrow arrow = shot(3); collide(arrow, false); damage(arrow, false); collide(arrow, false);
        assertEquals(1, scheduled.size());
        ((IPlayerShootEntity) enchant).handleShootEntity(3, shooter, target, 4, new AtomicDouble(4), new AtomicDouble(1), new AtomicBoolean());
        tick(); collide(arrow, false); damage(arrow, false); tick();
        assertEquals("&e&l1/2", count(3)); speed(3, 0);
    }

    @Test public void wallHitResetsComboEvenIfAnotherPluginRemovesArrow() throws Exception {
        collide(shot(3), false); tick();
        TestArrow wall = shot(3); collide(wall, true); when(wall.isDead()).thenReturn(true); tick();
        assertEquals("&e&l0/2", count(3)); collide(shot(3), false); tick(); speed(3, 0);
    }

    @Test public void finalCancellationDoesNotBecomeInvulnerabilityHit() throws Exception {
        collide(shot(3), false); tick();
        TestArrow blocked = shot(3); collide(blocked, false);
        EntityDamageByEntityEvent event = damage(blocked, false); event.setCancelled(true); tick();
        assertTrue(event.isCancelled()); assertEquals(4D, event.getDamage(), 0); speed(3, 0);
        assertEquals("&e&l0/2", count(3));
    }

    @Test public void cancelledShotDoesNotAcquireHeldBowEnchant() throws Exception {
        TestArrow arrow = arrow(); EntityShootBowEvent event = new EntityShootBowEvent(shooter, bow(3), arrow, 1F);
        event.setCancelled(true); fire(event); collide(arrow, false); tick();
        assertEquals("&e&l0/2", count(3)); assertTrue(scheduled.isEmpty());
    }

    @Test public void launchedLevelSurvivesChangingHeldBowAndRepairMetadata() throws Exception {
        TestArrow first = shot(1), second = shot(1), third = shot(1);
        when(shooter.getItemInHand()).thenReturn(bow(3));
        metadata(first, 3); metadata(second, 3); metadata(third, 3);
        collide(first, false); collide(second, false); tick(); speed(3, 0); speed(1, 0);
        assertEquals("&e&l2/3", count(1)); collide(third, false); tick(); speed(1, 1);
    }

    @Test public void ordinaryShotCannotGainComboFromRepairHeldBowFallback() throws Exception {
        TestArrow arrow = shot(0); metadata(arrow, 3); collide(arrow, false); tick();
        assertEquals("&e&l0/2", count(3)); assertTrue(scheduled.isEmpty());
    }

    @Test public void shooterSilenceAndTargetSuppressionBlockEffects() throws Exception {
        collide(shot(3), false); tick();
        TestArrow arrow = shot(3); collide(arrow, false); damage(arrow, false);
        players.when(() -> PlayerUtil.shouldIgnoreEnchant(shooter, (Entity) target)).thenReturn(true); tick(); speed(3, 0);
        players.when(() -> PlayerUtil.shouldIgnoreEnchant(shooter, (Entity) target)).thenReturn(false);
        collide(shot(3), false); tick();
        TestArrow silent = shot(3); collide(silent, false);
        players.when(() -> PlayerUtil.shouldIgnoreEnchant(shooter)).thenReturn(true); tick(); speed(3, 0);
        assertEquals("&e&l0/2", count(3));
    }

    @Test public void botTargetDoesNotRequireOnlineState() throws Exception {
        when(target.isOnline()).thenReturn(false);
        TestArrow first = shot(3); collide(first, false); damage(first, false); tick();
        collide(shot(3), false); tick(); speed(3, 1);
    }

    @Test public void quitDeathAndReplacementCancelPendingActivation() throws Exception {
        collide(shot(3), false); tick(); TestArrow arrow = shot(3); collide(arrow, false);
        fire(new PlayerQuitEvent(shooter, null)); tick(); speed(3, 0); assertEquals("&e&l0/2", count(3));
        collide(shot(3), false); tick(); collide(shot(3), false);
        fire(new PlayerDeathEvent(shooter, List.of(), 0, null)); tick(); speed(3, 0);
        collide(shot(3), false); tick(); collide(shot(3), false);
        BukkitTask pendingTask = scheduled.get(0).task(); factor.removeEnchantment(source); verify(pendingTask).cancel(); tick(); speed(3, 0);
    }

    @Test public void existingStrongerSpeedIsPreserved() throws Exception {
        when(shooter.getActivePotionEffects()).thenReturn(List.of(new PotionEffect(PotionEffectType.SPEED, 120, 5)));
        collide(shot(3), false); collide(shot(3), false); tick(); speed(3, 0); assertEquals("&e&l0/2", count(3));
    }

    @Test public void coreSyntheticRespawnClearsPendingAndStillFlyingArrows() throws Exception {
        collide(shot(3), false); tick();
        TestArrow pending = shot(3), flying = shot(3); collide(pending, false);
        fire(new PitPlayerSpawnEvent(shooter)); tick();
        collide(flying, false); tick();
        speed(3, 0); assertEquals("&e&l0/2", count(3));
    }

    @Test public void damageOnlyPluginEventCanCountWithoutProjectileHitEvent() throws Exception {
        damage(shot(2), false); tick(); damage(shot(2), false); tick(); speed(2, 1);
    }

    @Test public void realRepairBridgeExtraArrowsAndDelayedCheckerDoNotDoubleCount() throws Exception {
        Class<?> mainType = extensions.loadClass("cn.mx.thepitaddon.main.Main"); repairPlugin = (Plugin) mock(mainType);
        when(repairPlugin.isEnabled()).thenReturn(true);
        Class<?> managerType = extensions.loadClass("cn.mx.thepitaddon.enchantment.CoreEnchantRepairManager");
        Object repair = managerType.getConstructor(mainType).newInstance(repairPlugin);
        Class<?> settingsType = extensions.loadClass("cn.mx.thepitaddon.config.EnchantReplacementSettings");
        Constructor<?> constructor = settingsType.getDeclaredConstructor(boolean.class, boolean.class, Map.class);
        constructor.setAccessible(true); Field settings = managerType.getDeclaredField("settings"); settings.setAccessible(true);
        settings.set(repair, constructor.newInstance(true, true, Map.of()));
        Method check = managerType.getDeclaredMethod("scheduleVolleyEntityHitCheck", Arrow.class); check.setAccessible(true);
        Class<?> bridgeType = extensions.loadClass("cn.mx.thepitaddon.enchantment.VolleyArrowMetadataBridge");
        Method apply = bridgeType.getDeclaredMethod("apply", Metadatable.class, Plugin.class, Object2IntOpenHashMap.class, boolean.class);
        apply.setAccessible(true);
        Object2IntOpenHashMap<AbstractEnchantment> map = new Object2IntOpenHashMap<>(); map.put(source, 3);
        for (int i = 0; i < 4; i++) {
            TestArrow arrow = arrow(); // Repair's direct NMS spawn has no shoot event.
            apply.invoke(null, arrow, plugin, map, true); apply.invoke(null, arrow, plugin, map, true);
            if (i % 2 == 0) check.invoke(repair, arrow); // Exercise both scheduler orders.
            collide(arrow, false);
            if (i == 0) damage(arrow, false);
            if (i % 2 == 1) check.invoke(repair, arrow);
            tick(); collide(arrow, false); tick();
            assertEquals("&e&l" + ((i + 1) % 2) + "/2", count(3));
        }
        speed(3, 2);
        for (RegisteredListener handler : ProjectileHitEvent.getHandlerList().getRegisteredListeners()) {
            assertNotEquals(source.getClass(), handler.getListener().getClass());
        }
    }

    @After public void teardown() throws Exception {
        if (factor != null) for (AbstractEnchantment entry : new ArrayList<>(factor.getEnchantments())) factor.removeEnchantment(entry);
        for (HandlerList list : handlers) { list.unregister(plugin); list.unregister(addonPlugin); if (repairPlugin != null) list.unregister(repairPlugin); }
        if (players != null) players.close(); if (pit != null) pit.close(); if (bukkit != null) bukkit.close();
        if (extensions != null) extensions.close();
    }
}
