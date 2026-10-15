package net.mizukilab.pit.listener;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.api.PitInternalHook;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.perk.PerkFactory;
import com.google.common.util.concurrent.AtomicDouble;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.server.v1_8_R3.AttributeModifier;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.ItemBow;
import net.mizukilab.pit.BukkitDataTestSupport;
import net.mizukilab.pit.enchantment.AbstractEnchantment;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import net.mizukilab.pit.enchantment.type.rare.EnderBowEnchant;
import net.mizukilab.pit.item.factory.ItemFactory;
import net.mizukilab.pit.item.type.mythic.MythicBowItem;
import net.mizukilab.pit.item.type.mythic.MythicSwordItem;
import net.mizukilab.pit.parm.listener.IPlayerShootEntity;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.PublicUtil;
import net.mizukilab.pit.util.RangedStreamLineList;
import net.mizukilab.pit.util.Utils;
import net.mizukilab.pit.util.cooldown.Cooldown;
import org.bukkit.*;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.*;
import org.mockito.MockedStatic;

import java.lang.reflect.*;
import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Actual Addon callbacks and Bukkit listener ordering; only NMS spawning/server services are mocked. */
public class AddonBowCompatibilityTest extends BukkitDataTestSupport {
    private interface ShotArrow extends Arrow {
        @Override org.bukkit.projectiles.ProjectileSource getShooter();
    }
    private abstract static class CombatPlayer extends CraftPlayer {
        private CombatPlayer() { super(null, null); }
        @Override public abstract double getHealth();
        @Override public abstract double getMaxHealth();
    }
    private URLClassLoader addon;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private MockedStatic<PlayerProfile> profiles;
    private MockedStatic<PlayerUtil> playerUtil;
    private MockedStatic<Utils> utils;
    private MockedStatic<PublicUtil> publicUtil;
    private PitInternalHook previousApi;
    private ThePit plugin;
    private EnchantmentFactor factor;
    private ItemFactory items;
    private AbstractEnchantment sky, piercing;
    private EnderBowEnchant ender;
    private final GameEffectListener effects = new GameEffectListener();
    private CombatPlayer attacker, victim;
    private PlayerProfile attackerProfile;
    private ItemStack bowStack;
    private MythicBowItem bow;
    private final List<Arrow> replacements = new ArrayList<>();
    private final Map<Class<?>, HandlerList> handlers = new HashMap<>();

    @Before public void setup() throws Exception {
        addon = new URLClassLoader(new URL[]{Path.of(System.getProperty("pitAddonJar")).toUri().toURL(),
                Path.of(System.getProperty("pitAddonLoaderJar")).toUri().toURL(),
                Path.of("build/libs/ThePitGay-V.jar").toUri().toURL()}, getClass().getClassLoader());
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class, RETURNS_DEEP_STUBS));
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
        bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
        pit = mockStatic(ThePit.class);
        plugin = mock(ThePit.class, RETURNS_DEEP_STUBS);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        previousApi = ThePit.api;
        ThePit.api = mock(PitInternalHook.class);
        pit.when(ThePit::getApi).thenReturn(ThePit.api);
        profiles = mockStatic(PlayerProfile.class);
        playerUtil = mockStatic(PlayerUtil.class);
        utils = mockStatic(Utils.class, CALLS_REAL_METHODS);
        publicUtil = mockStatic(PublicUtil.class, call -> call.getMethod().getName().startsWith("processActionBar")
                ? null : call.callRealMethod());
        playerUtil.when(() -> PlayerUtil.damage(any(Player.class), any(Player.class),
                any(PlayerUtil.DamageType.class), anyDouble(), anyBoolean())).thenCallRealMethod();
        playerUtil.when(() -> PlayerUtil.damage(any(Player.class), any(PlayerUtil.DamageType.class),
                anyDouble(), anyBoolean())).thenCallRealMethod();
        factor = new EnchantmentFactor();
        when(plugin.getEnchantmentFactor()).thenReturn(factor);
        when(plugin.getPerkFactory()).thenReturn(new PerkFactory());
        when(plugin.getDisabledPerks()).thenReturn(Set.of());
        when(plugin.getEventFactory().getActiveEpicEvent()).thenReturn(null);
        items = mock(ItemFactory.class); when(plugin.getItemFactory()).thenReturn(items);
        sky = enchant("rare.SkySplitterEnchant"); piercing = enchant("limit.PiercingBow");
        ender = new EnderBowEnchant();
        factor.registerEnchantment(sky); factor.registerEnchantment(piercing); factor.registerEnchantment(ender);
        attacker = player(); victim = player();
        attackerProfile = profile(attacker); profile(victim);
        bowStack = new ItemStack(Material.BOW); bow = new MythicBowItem();
        when(attacker.getItemInHand()).thenReturn(bowStack);
        when(attacker.getInventory().getItemInHand()).thenReturn(bowStack);
        when(items.getIMythicItem(same(bowStack))).thenReturn(bow);
        when(items.getItemFromStack(same(bowStack))).thenReturn(bow);
        when(ThePit.api.getItemEnchantLevel(same(bowStack), anyString())).thenAnswer(call ->
                bow.getEnchantmentLevel((String) call.getArgument(1)));
        attackerProfile.heldItem = bow; attackerProfile.heldItemStack = bowStack;
        stubNmsReplacementShot();
    }

    private AbstractEnchantment enchant(String name) throws Exception {
        return (AbstractEnchantment) addon.loadClass("cn.apnf.mistral.enchantments." + name).getConstructor().newInstance();
    }
    private CombatPlayer player() {
        CombatPlayer player = mock(CombatPlayer.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        when(player.getHealth()).thenReturn(20D); when(player.getMaxHealth()).thenReturn(20D);
        when(player.getName()).thenReturn("BowAudit"); when(player.isOnline()).thenReturn(true);
        when(player.isValid()).thenReturn(true); when(player.getActivePotionEffects()).thenReturn(List.of());
        World world = mock(World.class); when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 1, 64, 1));
        try (var attributes = mockConstruction(AttributeModifier.class)) {
            EntityPlayer handle = mock(EntityPlayer.class);
            UUID playerId = player.getUniqueId();
            when(handle.getUniqueID()).thenReturn(playerId);
            when(player.getHandle()).thenReturn(handle);
        }
        return player;
    }
    private PlayerProfile profile(Player player) {
        PlayerProfile profile = mock(PlayerProfile.class, RETURNS_DEEP_STUBS);
        when(profile.getPrestige()).thenReturn(1);
        when(profile.isLoaded()).thenReturn(true); when(profile.isInArena()).thenReturn(true);
        when(profile.getKillRecap().getDamageLogs()).thenReturn(new RangedStreamLineList<>(100, entry -> false));
        profiles.when(() -> PlayerProfile.getPlayerProfile(player)).thenReturn(profile);
        profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(player.getUniqueId())).thenReturn(profile);
        return profile;
    }
    private Arrow arrow() {
        Arrow arrow = mock(ShotArrow.class);
        when(arrow.getUniqueId()).thenReturn(UUID.randomUUID()); when(arrow.getShooter()).thenReturn(attacker);
        World world = attacker.getWorld();
        when(arrow.getLocation()).thenReturn(new Location(world, 12, 65, 8));
        Map<String, MetadataValue> metadata = new HashMap<>();
        doAnswer(call -> { metadata.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(arrow).setMetadata(anyString(), any());
        doAnswer(call -> { metadata.remove(call.getArgument(0)); return null; }).when(arrow).removeMetadata(anyString(), any());
        when(arrow.hasMetadata(anyString())).thenAnswer(call -> metadata.containsKey(call.getArgument(0)));
        when(arrow.getMetadata(anyString())).thenAnswer(call -> {
            MetadataValue value = metadata.get(call.getArgument(0)); return value == null ? List.of() : List.of(value);
        });
        return arrow;
    }
    private void register(Listener listener, String methodName, Class<? extends Event> type) throws Exception {
        Method method = listener.getClass().getMethod(methodName, type);
        method.setAccessible(true);
        EventHandler annotation = method.getAnnotation(EventHandler.class);
        assertNotNull(annotation);
        handlers.computeIfAbsent(type, ignored -> new HandlerList()).register(new RegisteredListener(listener,
                (instance, event) -> {
                    try { method.invoke(instance, event); }
                    catch (ReflectiveOperationException error) { throw new EventException(error); }
                }, annotation.priority(), plugin, annotation.ignoreCancelled()));
    }
    private void fire(Event event) throws EventException {
        HandlerList list = handlers.get(event.getClass());
        if (list != null) for (RegisteredListener listener : list.getRegisteredListeners()) listener.callEvent(event);
    }
    private void registerBowListeners(boolean addonFirst) throws Exception {
        if (addonFirst) register((Listener) piercing, "onInteract", EntityShootBowEvent.class);
        register(ender, "onBowShot", EntityShootBowEvent.class);
        register(effects, "addEnchantToArrow", EntityShootBowEvent.class);
        if (!addonFirst) register((Listener) piercing, "onInteract", EntityShootBowEvent.class);
        register(ender, "onBowHit", ProjectileHitEvent.class);
    }
    private void stubNmsReplacementShot() {
        net.minecraft.server.v1_8_R3.ItemStack nmsStack = mock(net.minecraft.server.v1_8_R3.ItemStack.class);
        ItemBow nmsBow = mock(ItemBow.class); when(nmsStack.getItem()).thenReturn(nmsBow);
        utils.when(() -> Utils.toNMStackQuick(bowStack)).thenReturn(nmsStack);
        EntityPlayer handle = attacker.getHandle();
        doAnswer(call -> {
            Arrow replacement = arrow(); replacements.add(replacement);
            fire(new EntityShootBowEvent(attacker, bowStack, replacement, 1F));
            return null;
        }).when(nmsBow).a(same(nmsStack), isNull(), same(handle), eq(0));
    }
    @SuppressWarnings("unchecked") private Object2IntOpenHashMap<AbstractEnchantment> snapshot(Arrow arrow) {
        assertTrue("The actual fired arrow needs enchantment data", arrow.hasMetadata("enchant"));
        return (Object2IntOpenHashMap<AbstractEnchantment>) arrow.getMetadata("enchant").get(0).value();
    }
    private void hit(Arrow arrow) {
        EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(arrow, victim, EntityDamageEvent.DamageCause.PROJECTILE, 4D);
        effects.onPlayerDamagePlayer(hit);
        assertFalse(hit.isCancelled());
    }
    @SuppressWarnings("unchecked") private Map<UUID, Cooldown> skyCooldowns() throws Exception {
        Field field = sky.getClass().getDeclaredField("cooldownMap"); field.setAccessible(true);
        return (Map<UUID, Cooldown>) field.get(null);
    }
    private void clearHandlers() { handlers.values().forEach(list -> list.unregister(plugin)); handlers.clear(); }

    @Test public void piercingReplacementGetsEnderTeleportInBothRegistrationOrders() throws Exception {
        for (boolean addonFirst : new boolean[]{false, true}) {
            ender.onQuit(new PlayerQuitEvent(attacker, "")); replacements.clear(); clearHandlers();
            piercing = enchant("limit.PiercingBow"); factor.registerEnchantment(piercing);
            registerBowListeners(addonFirst);
            bow.getEnchantments().put(piercing, 1); bow.getEnchantments().put(ender, 3);
            when(attacker.isSneaking()).thenReturn(true);
            Arrow original = arrow();
            EntityShootBowEvent shot = new EntityShootBowEvent(attacker, bowStack, original, 0.25F);
            fire(shot);
            assertTrue(shot.isCancelled()); assertEquals(1, replacements.size());
            Arrow replacement = replacements.get(0);
            assertFalse("The cancelled original must not consume the teleport", original.hasMetadata("ender_bow"));
            assertTrue("The full-power replacement needs the ender mark", replacement.hasMetadata("ender_bow"));
            fire(new ProjectileHitEvent(replacement)); verify(attacker).teleport(replacement);
            assertFalse(ender.getText(3, attacker).contains("✔"));
        }
    }
    @Test public void cancelledShotDoesNotConsumeEnderCooldown() throws Exception {
        registerBowListeners(false); bow.getEnchantments().put(ender, 3); when(attacker.isSneaking()).thenReturn(true);
        Listener cancel = new Listener() {
            @EventHandler(priority = EventPriority.HIGHEST) public void cancel(EntityShootBowEvent event) { event.setCancelled(true); }
        };
        register(cancel, "cancel", EntityShootBowEvent.class);
        Arrow arrow = arrow(); fire(new EntityShootBowEvent(attacker, bowStack, arrow, 1F));
        assertFalse(arrow.hasMetadata("ender_bow")); assertFalse(arrow.hasMetadata("enchant"));
        assertTrue(ender.getText(3, attacker).contains("✔"));
    }
    @Test public void aLaterProjectileReplacementReceivesBothEnchantmentAndEnderMetadata() throws Exception {
        registerBowListeners(false); when(attacker.isSneaking()).thenReturn(true);
        bow.getEnchantments().put(ender, 3); bow.getEnchantments().put(sky, 3);
        Arrow finalArrow = arrow();
        Listener replacement = new Listener() {
            @EventHandler(priority = EventPriority.HIGH) public void replace(EntityShootBowEvent event) {
                event.setProjectile(finalArrow);
            }
        };
        register(replacement, "replace", EntityShootBowEvent.class);
        Arrow original = arrow(); fire(new EntityShootBowEvent(attacker, bowStack, original, 1F));
        assertFalse(original.hasMetadata("enchant")); assertFalse(original.hasMetadata("ender_bow"));
        assertEquals(3, snapshot(finalArrow).getInt(sky)); assertTrue(finalArrow.hasMetadata("ender_bow"));
    }
    @Test public void enderReadsFiredBowIfTheHandChangesDuringShootEvent() throws Exception {
        registerBowListeners(false); bow.getEnchantments().put(ender, 3); when(attacker.isSneaking()).thenReturn(true);
        when(attacker.getItemInHand()).thenReturn(new ItemStack(Material.GOLD_SWORD));
        Arrow arrow = arrow(); fire(new EntityShootBowEvent(attacker, bowStack, arrow, 1F));
        assertTrue(arrow.hasMetadata("ender_bow"));
    }
    @Test public void enderKeepsNormalSneakingCooldownAndHitReduction() throws Exception {
        registerBowListeners(false); bow.getEnchantments().put(ender, 3);
        Arrow plain = arrow(); fire(new EntityShootBowEvent(attacker, bowStack, plain, 1F));
        assertFalse(plain.hasMetadata("ender_bow"));
        when(attacker.isSneaking()).thenReturn(true);
        Arrow first = arrow(); fire(new EntityShootBowEvent(attacker, bowStack, first, 1F));
        assertTrue(first.hasMetadata("ender_bow"));
        Arrow second = arrow(); fire(new EntityShootBowEvent(attacker, bowStack, second, 1F));
        assertFalse(second.hasMetadata("ender_bow"));
        Field field = EnderBowEnchant.class.getDeclaredField("cooldown"); field.setAccessible(true);
        Cooldown before = (Cooldown) ((Map<?, ?>) field.get(null)).get(attacker.getUniqueId());
        assertEquals(15000L, before.getDuration());
        hit(second);
        Cooldown after = (Cooldown) ((Map<?, ?>) field.get(null)).get(attacker.getUniqueId());
        assertTrue(after.getRemaining() <= 12000L && after.getRemaining() > 11000L);
    }
    @Test public void quickBowSwitchUsesEventBowInsteadOfLastTickEquipment() {
        bow.getEnchantments().put(sky, 3);
        attackerProfile.heldItem = new MythicSwordItem();
        Arrow arrow = arrow(); effects.addEnchantToArrow(new EntityShootBowEvent(attacker, bowStack, arrow, 1F));
        assertEquals(3, snapshot(arrow).getInt(sky));
        hit(arrow); verify(victim).setHealth(16D);
    }
    @Test public void firedArrowKeepsItsOwnLevelsAfterBowIsModified() {
        bow.getEnchantments().put(sky, 3);
        Arrow arrow = arrow(); effects.addEnchantToArrow(new EntityShootBowEvent(attacker, bowStack, arrow, 1F));
        bow.getEnchantments().clear(); bow.getEnchantments().put(sky, 1);
        assertEquals(3, snapshot(arrow).getInt(sky));
        hit(arrow); verify(victim).setHealth(16D);
    }
    @Test public void plainBowCannotInheritPreviousMythicBowEffects() {
        MythicBowItem stale = new MythicBowItem(); stale.getEnchantments().put(sky, 3);
        attackerProfile.heldItem = stale;
        ItemStack plain = new ItemStack(Material.BOW);
        Arrow arrow = arrow(); effects.addEnchantToArrow(new EntityShootBowEvent(attacker, plain, arrow, 1F));
        assertFalse(arrow.hasMetadata("enchant")); hit(arrow); verify(victim, never()).setHealth(anyDouble());
    }
    @Test public void skyDamageSlowCooldownAndChatMatchAllThreeLevels() throws Exception {
        int[] damage = {2, 3, 4}, seconds = {2, 3, 3}, cooldown = {15, 12, 10};
        for (int level = 1; level <= 3; level++) {
            skyCooldowns().clear(); clearInvocations(victim, victim.getWorld());
            ((IPlayerShootEntity) sky).handleShootEntity(level, attacker, victim, 4,
                    new AtomicDouble(4), new AtomicDouble(1), new AtomicBoolean());
            verify(victim).setHealth(20D - damage[level - 1]);
            final int ticks = seconds[level - 1] * 20;
            verify(victim).addPotionEffect(argThat((PotionEffect effect) -> effect.getType().equals(PotionEffectType.SLOW)
                    && effect.getDuration() == ticks && effect.getAmplifier() == 0));
            verify(victim.getWorld()).strikeLightningEffect(victim.getLocation());
            verify(victim).sendMessage("§e§l雷击! §7你被雷电击中了!");
            assertEquals(cooldown[level - 1] * 1000L, skyCooldowns().get(attacker.getUniqueId()).getDuration());
            ((IPlayerShootEntity) sky).handleShootEntity(level, attacker, victim, 4,
                    new AtomicDouble(4), new AtomicDouble(1), new AtomicBoolean());
            verify(victim).setHealth(anyDouble());
        }
    }
    @Test public void skyWorksOnBotWithoutOnlineFlagOrLoadedProfile() {
        playerUtil.when(() -> PlayerUtil.isNPC(victim)).thenReturn(true);
        when(victim.isOnline()).thenReturn(false);
        profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(victim.getUniqueId())).thenReturn(PlayerProfile.NONE_PROFILE);
        bow.getEnchantments().put(sky, 3);
        Arrow arrow = arrow(); effects.addEnchantToArrow(new EntityShootBowEvent(attacker, bowStack, arrow, 1F));
        hit(arrow); verify(victim).setHealth(16D);
    }
    @Test public void skyBecomesReadyAndTriggersAgainWhenCooldownExpires() throws Exception {
        IPlayerShootEntity callback = (IPlayerShootEntity) sky;
        assertTrue(((net.mizukilab.pit.enchantment.IActionDisplayEnchant) sky).getText(3, attacker).contains("✔"));
        callback.handleShootEntity(3, attacker, victim, 4, new AtomicDouble(4), new AtomicDouble(1), new AtomicBoolean());
        assertFalse(((net.mizukilab.pit.enchantment.IActionDisplayEnchant) sky).getText(3, attacker).contains("✔"));
        skyCooldowns().get(attacker.getUniqueId()).fastExpired();
        assertTrue(((net.mizukilab.pit.enchantment.IActionDisplayEnchant) sky).getText(3, attacker).contains("✔"));
        callback.handleShootEntity(3, attacker, victim, 4, new AtomicDouble(4), new AtomicDouble(1), new AtomicBoolean());
        verify(victim, times(2)).setHealth(16D);
        verify(victim, times(2)).sendMessage("§e§l雷击! §7你被雷电击中了!");
    }
    @Test public void suppressedArrowDoesNotTriggerSky() {
        bow.getEnchantments().put(sky, 3);
        Arrow arrow = arrow(); effects.addEnchantToArrow(new EntityShootBowEvent(attacker, bowStack, arrow, 1F));
        playerUtil.when(() -> PlayerUtil.shouldIgnoreEnchant(attacker, (Entity) victim)).thenReturn(true);
        hit(arrow); verify(victim, never()).setHealth(anyDouble());
    }
    @Test public void allThreeEnchantsWorkOnTheSameReplacementArrow() throws Exception {
        registerBowListeners(false); when(attacker.isSneaking()).thenReturn(true);
        bow.getEnchantments().put(sky, 3); bow.getEnchantments().put(piercing, 3); bow.getEnchantments().put(ender, 3);
        Arrow original = arrow(); fire(new EntityShootBowEvent(attacker, bowStack, original, 0.3F));
        Arrow replacement = replacements.get(0);
        assertEquals(3, snapshot(replacement).getInt(sky));
        assertTrue(replacement.hasMetadata("ender_bow"));
        hit(replacement); verify(victim).setHealth(16D);
        fire(new ProjectileHitEvent(replacement)); verify(attacker).teleport(replacement);
    }
    @After public void close() throws Exception {
        if (ender != null && attacker != null) ender.onQuit(new PlayerQuitEvent(attacker, ""));
        clearHandlers(); ThePit.api = previousApi;
        if (publicUtil != null) publicUtil.close(); if (utils != null) utils.close();
        if (playerUtil != null) playerUtil.close(); if (profiles != null) profiles.close();
        if (pit != null) pit.close(); if (bukkit != null) bukkit.close(); if (addon != null) addon.close();
    }
}
