package net.mizukilab.pit.listener;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.api.PitInternalHook;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.operator.IProfilerOperator;
import cn.charlotte.pit.perk.PerkFactory;
import net.minecraft.server.v1_8_R3.AttributeModifier;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.mizukilab.pit.BukkitDataTestSupport;
import net.mizukilab.pit.data.operator.PackedOperator;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import net.mizukilab.pit.enchantment.type.rage.NewDealEnchant;
import net.mizukilab.pit.enchantment.type.ragerare.Regularity;
import net.mizukilab.pit.enchantment.type.rare.BillionaireEnchant;
import net.mizukilab.pit.item.factory.ItemFactory;
import net.mizukilab.pit.item.type.mythic.MythicLeggingsItem;
import net.mizukilab.pit.item.type.mythic.MythicSwordItem;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.item.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RegularityDamageIntegrationTest extends BukkitDataTestSupport {
    private abstract static class CombatPlayer extends CraftPlayer {
        private CombatPlayer() { super(null, null); }
        @Override public abstract double getHealth();
        @Override public abstract double getMaxHealth();
    }

    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private MockedStatic<PlayerProfile> profiles;
    private MockedStatic<PlayerUtil> playerUtil;
    private PitInternalHook previousApi;
    private CombatPlayer attacker, victim;
    private PlayerProfile attackerProfile;
    private final Regularity regularity = new Regularity();
    private final BillionaireEnchant billionaire = new BillionaireEnchant();
    private final GameEffectListener effects = new GameEffectListener();
    private final MythicSwordItem sword = new MythicSwordItem();
    private final List<Runnable> pending = new ArrayList<>();
    private final List<EntityDamageByEntityEvent> delayedHits = new ArrayList<>();
    private final List<MetadataValue> cooldown = new ArrayList<>();
    private double coins = 10000D;
    private double armorReduction;

    @Before public void setup() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getServer).thenReturn(mock(org.bukkit.Server.class, RETURNS_DEEP_STUBS));
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
        pit = mockStatic(ThePit.class);
        profiles = mockStatic(PlayerProfile.class);
        playerUtil = mockStatic(PlayerUtil.class);
        ThePit plugin = mock(ThePit.class);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        EnchantmentFactor factor = new EnchantmentFactor();
        factor.registerEnchantment(billionaire);
        when(plugin.getEnchantmentFactor()).thenReturn(factor);
        when(plugin.getPerkFactory()).thenReturn(new PerkFactory());
        when(plugin.getDisabledPerks()).thenReturn(Set.of());
        ItemFactory items = mock(ItemFactory.class);
        when(plugin.getItemFactory()).thenReturn(items);

        attacker = player(); victim = player();
        attackerProfile = profile(attacker);
        profile(victim);
        IProfilerOperator operators = mock(IProfilerOperator.class);
        PackedOperator operator = mock(PackedOperator.class);
        when(plugin.getProfileOperator()).thenReturn(operators);
        when(operators.getIOperator(attacker)).thenReturn(operator);
        when(operator.profile()).thenReturn(attackerProfile);
        when(attackerProfile.getCoins()).thenAnswer(call -> coins);
        doAnswer(call -> { coins = call.getArgument(0); return null; })
                .when(attackerProfile).setCoins(anyDouble());

        ItemStack held = new ItemStack(Material.GOLD_SWORD);
        when(attacker.getItemInHand()).thenReturn(held);
        when(attacker.getInventory().getItemInHand()).thenReturn(held);
        when(items.getIMythicItem(same(held))).thenReturn(sword);
        ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS);
        when(attacker.getInventory().getLeggings()).thenReturn(leggings);
        previousApi = ThePit.api;
        ThePit.api = mock(PitInternalHook.class);
        when(ThePit.api.getItemEnchantLevel(leggings, "regularity")).thenReturn(3);
        when(victim.getMetadata("regularity_cooldown")).thenAnswer(call -> List.copyOf(cooldown));
        doAnswer(call -> { cooldown.clear(); cooldown.add(call.getArgument(1)); return null; })
                .when(victim).setMetadata(eq("regularity_cooldown"), any(MetadataValue.class));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), eq(5L))).thenAnswer(call -> {
            pending.add(call.getArgument(1)); return null;
        });
        doAnswer(call -> {
            delayedHits.add(hit(call.getArgument(0)));
            return null;
        }).when(victim).damage(anyDouble(), eq(attacker));
    }

    private CombatPlayer player() {
        CombatPlayer player = mock(CombatPlayer.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        when(player.isOnline()).thenReturn(true);
        when(player.getHealth()).thenReturn(20D);
        when(player.getMaxHealth()).thenReturn(20D);
        try (var attributes = mockConstruction(AttributeModifier.class)) {
            EntityPlayer handle = mock(EntityPlayer.class);
            when(player.getHandle()).thenReturn(handle);
        }
        return player;
    }

    private PlayerProfile profile(CombatPlayer player) {
        PlayerProfile profile = mock(PlayerProfile.class, RETURNS_DEEP_STUBS);
        when(profile.getPrestige()).thenReturn(1);
        when(profile.getKillRecap().getDamageLogs()).thenReturn(
                new net.mizukilab.pit.util.RangedStreamLineList<>(100, entry -> false));
        profiles.when(() -> PlayerProfile.getPlayerProfile(player)).thenReturn(profile);
        profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(player.getUniqueId())).thenReturn(profile);
        return profile;
    }

    private EntityDamageByEntityEvent hit(double amount) {
        Map<EntityDamageEvent.DamageModifier, Double> modifiers = new EnumMap<>(EntityDamageEvent.DamageModifier.class);
        modifiers.put(EntityDamageEvent.DamageModifier.BASE, amount);
        modifiers.put(EntityDamageEvent.DamageModifier.ARMOR, -amount * armorReduction);
        Map<EntityDamageEvent.DamageModifier, com.google.common.base.Function<? super Double, Double>> functions =
                new EnumMap<>(EntityDamageEvent.DamageModifier.class);
        functions.put(EntityDamageEvent.DamageModifier.BASE, damage -> 0D);
        functions.put(EntityDamageEvent.DamageModifier.ARMOR, damage -> -damage * armorReduction);
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(attacker, victim,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, modifiers, functions);
        effects.onPlayerDamagePlayer(event);
        regularity.damage(event);
        return event;
    }

    private void runNextHit() { pending.remove(0).run(); }

    @After public void close() {
        ThePit.api = previousApi;
        if (playerUtil != null) playerUtil.close();
        if (profiles != null) profiles.close();
        if (pit != null) pit.close();
        if (bukkit != null) bukkit.close();
    }

    @Test public void multipleQueuedHitsAndRecursiveStackingRemainAvailable() {
        for (int i = 0; i < 3; i++) hit(1D);
        assertEquals(3, pending.size());
        assertTrue(cooldown.isEmpty());
        runNextHit();
        assertEquals("The first delayed hit can still create another stacked hit", 3, pending.size());
        runNextHit(); runNextHit(); runNextHit();
        assertEquals(4, delayedHits.size());
        assertTrue(pending.isEmpty());
        for (int i = 0; i < 3; i++) assertEquals(0.75D, delayedHits.get(i).getDamage(), 0.000001D);
        assertEquals(0.5625D, delayedHits.get(3).getDamage(), 0.000001D);
        verify(victim, times(4)).setNoDamageTicks(0);
    }

    @Test public void cancelledOrOfflineHitsDoNotApplyDelayedDamage() {
        hit(1D).setCancelled(true);
        runNextHit();
        hit(1D);
        when(victim.isOnline()).thenReturn(false);
        runNextHit();
        assertTrue(delayedHits.isEmpty());
        verify(victim, never()).setNoDamageTicks(anyInt());
    }

    @Test public void ordinaryBillionaireHitsKeepTheirLevelBonusAndCost() {
        for (int level = 1; level <= 3; level++) {
            sword.getEnchantments().put(billionaire, level);
            double before = coins;
            assertEquals(4D * (1D + level * 0.35D), hit(4D).getDamage(), 0.000001D);
            assertEquals(before - level * 100D - (level == 3 ? 50D : 0D), coins, 0D);
        }
        assertTrue(pending.isEmpty());
    }

    @Test public void stackedHitsApplyBillionaireAndPayItsCostAtEveryLevel() {
        for (int level = 1; level <= 3; level++) {
            cooldown.clear();
            sword.getEnchantments().put(billionaire, level);
            double original = hit(0.25D).getDamage();
            double afterFirstHit = coins;
            int start = delayedHits.size();
            runNextHit(); runNextHit();
            double multiplier = 1D + level * 0.35D;
            double first = original * 0.75D * multiplier;
            assertEquals(first, delayedHits.get(start).getDamage(), 0.000001D);
            assertEquals(first * 0.75D * multiplier, delayedHits.get(start + 1).getDamage(), 0.000001D);
            double cost = level * 100D + (level == 3 ? 50D : 0D);
            assertEquals(afterFirstHit - cost * 2, coins, 0D);
            assertFalse(Regularity.isFollowUp(attacker, victim));
            double next = hit(4D).getDamage();
            assertEquals(4D * (1D + level * 0.35D), next, 0.000001D);
            assertTrue(coins < afterFirstHit);
        }
    }

    @Test public void switchingToBillionaireAfterQueueingBoostsTheDelayedHit() {
        double original = hit(0.25D).getDamage();
        sword.getEnchantments().put(billionaire, 3);
        runNextHit(); runNextHit();
        assertEquals(original * 0.75D * 2.05D, delayedHits.get(0).getDamage(), 0.000001D);
        assertEquals(original * 0.75D * 2.05D * 0.75D * 2.05D, delayedHits.get(1).getDamage(), 0.000001D);
        assertEquals(9300D, coins, 0D);
    }

    @Test public void stackedBillionaireDamageAboveNineIsNotResetToVanillaWeaponDamage() {
        armorReduction = 0.95D;
        sword.getEnchantments().put(billionaire, 3);
        double original = hit(9D).getDamage();
        assertEquals(18.45D, original, 0.000001D);
        assertEquals(1, pending.size());
        runNextHit(); runNextHit();
        double first = original * 0.75D * 2.05D;
        assertEquals(first, delayedHits.get(0).getDamage(), 0.000001D);
        assertEquals(first * 0.75D * 2.05D, delayedHits.get(1).getDamage(), 0.000001D);
        assertEquals(8950D, coins, 0D);
        assertTrue(pending.isEmpty());
        // Ordinary hits still use the core's original weapon-damage correction.
        assertEquals(18.45D, hit(40D).getDamage(), 0.000001D);
    }

    @Test public void changingToEmptyHandDoesNotOverwriteAlreadyQueuedDamage() {
        double original = hit(0.25D).getDamage();
        when(attacker.getItemInHand()).thenReturn(null);
        when(attacker.getInventory().getItemInHand()).thenReturn(null);
        runNextHit(); runNextHit();
        assertEquals(original * 0.75D, delayedHits.get(0).getDamage(), 0.000001D);
        assertEquals(original * 0.75D * 0.75D, delayedHits.get(1).getDamage(), 0.000001D);
        assertEquals(1D, hit(40D).getDamage(), 0D);
    }

    @Test public void followUpCannotBuyBillionaireDamageAfterCoinsRunOut() {
        coins = 350D;
        sword.getEnchantments().put(billionaire, 3);
        double original = hit(0.25D).getDamage();
        assertEquals(0D, coins, 0D);
        runNextHit(); runNextHit();
        assertEquals(original * 0.75D, delayedHits.get(0).getDamage(), 0.000001D);
        assertEquals(original * 0.75D * 0.75D, delayedHits.get(1).getDamage(), 0.000001D);
        assertEquals(0D, coins, 0D);
    }

    @Test public void exceptionDuringDelayedDamageCannotSuppressFutureOrdinaryHits() {
        hit(0.25D);
        doThrow(new IllegalStateException("test damage failure")).when(victim).damage(anyDouble(), eq(attacker));
        assertThrows(IllegalStateException.class, this::runNextHit);
        assertFalse(Regularity.isFollowUp(attacker, victim));
        sword.getEnchantments().put(billionaire, 3);
        assertEquals(18.45D, hit(40D).getDamage(), 0.000001D);
        assertEquals(9650D, coins, 0D);
    }

    @Test public void newDealStillCountersBillionaireOnStackedHits() {
        NewDealEnchant newDeal = new NewDealEnchant();
        ThePit.getInstance().getEnchantmentFactor().registerEnchantment(newDeal);
        MythicLeggingsItem pants = new MythicLeggingsItem();
        pants.getEnchantments().put(newDeal, 3);
        ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS);
        when(victim.getInventory().getLeggings()).thenReturn(leggings);
        when(((ItemFactory) ThePit.getInstance().getItemFactory()).getIMythicItem(same(leggings))).thenReturn(pants);
        sword.getEnchantments().put(billionaire, 3);
        try (var itemUtil = mockStatic(ItemUtil.class);
             var parsedSwords = mockConstruction(MythicSwordItem.class, (item, context) ->
                     when(item.getEnchantments()).thenReturn(sword.getEnchantments()))) {
            itemUtil.when(() -> ItemUtil.getInternalName(attacker.getItemInHand())).thenReturn("mythic_sword");
            double original = hit(0.25D).getDamage();
            assertEquals(0.25D * 0.92D, original, 0.000001D);
            runNextHit(); runNextHit();
            assertEquals(original * 0.75D * 0.92D, delayedHits.get(0).getDamage(), 0.000001D);
            assertEquals(original * 0.75D * 0.92D * 0.75D * 0.92D, delayedHits.get(1).getDamage(), 0.000001D);
            assertEquals(8950D, coins, 0D);
        }
    }

    @Test public void followUpContextOnlyMatchesItsAttackerAndVictim() {
        hit(0.25D);
        CombatPlayer other = player();
        profile(other);
        sword.getEnchantments().put(billionaire, 3);
        doAnswer(call -> {
            assertTrue(Regularity.isFollowUp(attacker, victim));
            assertFalse(Regularity.isFollowUp(attacker, other));
            assertFalse(Regularity.isFollowUp(other, victim));
            var unrelated = new EntityDamageByEntityEvent(attacker, other,
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK, 40D);
            effects.onPlayerDamagePlayer(unrelated);
            assertEquals(18.45D, unrelated.getDamage(), 0.000001D);
            return null;
        }).when(victim).damage(anyDouble(), eq(attacker));
        runNextHit();
        assertEquals(9650D, coins, 0D);
        assertFalse(Regularity.isFollowUp(attacker, victim));
    }
}
