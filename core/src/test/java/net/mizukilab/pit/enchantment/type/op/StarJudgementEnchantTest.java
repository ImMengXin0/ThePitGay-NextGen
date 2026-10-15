package net.mizukilab.pit.enchantment.type.op;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.KillRecap;
import cn.charlotte.pit.data.sub.PlayerOption;
import cn.charlotte.pit.perk.PerkFactory;
import com.google.common.util.concurrent.AtomicDouble;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.mizukilab.pit.BukkitDataTestSupport;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import net.mizukilab.pit.enchantment.EnchantmentTable;
import net.mizukilab.pit.enchantment.rarity.EnchantmentRarity;
import net.mizukilab.pit.event.CanImmuneEvent;
import net.mizukilab.pit.event.TrueDamageByEntityEvent;
import net.mizukilab.pit.item.IMythicItem;
import net.mizukilab.pit.listener.GameEffectListener;
import net.mizukilab.pit.listener.ProtectListener;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.PublicUtil;
import net.mizukilab.pit.util.RangedStreamLineList;
import net.mizukilab.pit.util.random.RandomUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class StarJudgementEnchantTest extends BukkitDataTestSupport {
    // Disambiguate the legacy int health getters also present in the 1.8 server JAR.
    private interface HealthPlayer extends Player {
        @Override double getHealth();
        @Override double getMaxHealth();
    }

    private abstract static class CombatPlayer extends CraftPlayer {
        private CombatPlayer() { super(null, null); }
        @Override public abstract double getHealth();
        @Override public abstract double getMaxHealth();
    }

    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private MockedStatic<PlayerProfile> profiles;
    private MockedStatic<RandomUtil> random;
    private MockedStatic<PublicUtil> publicUtil;
    private MockedStatic<PlayerUtil> playerUtil;
    private boolean npcVictim;
    private final List<Runnable> pending = new ArrayList<>();
    private final AtomicBoolean cancel = new AtomicBoolean();
    private final AtomicDouble finalDamage = new AtomicDouble(4);
    private final AtomicDouble boost = new AtomicDouble(1);
    private final StarJudgementEnchant enchant = new StarJudgementEnchant();
    private Player attacker, victim;
    private PlayerProfile attackerProfile, victimProfile;
    private PluginManager manager;
    private BukkitScheduler scheduler;
    private ThePit plugin;

    @Before
    public void setup() {
        bukkit = mockStatic(Bukkit.class);
        pit = mockStatic(ThePit.class);
        profiles = mockStatic(PlayerProfile.class);
        random = mockStatic(RandomUtil.class);
        playerUtil = mockStatic(PlayerUtil.class, invocation ->
                invocation.getMethod().getName().equals("isNPC")
                        ? npcVictim && invocation.getArgument(0) == victim : invocation.callRealMethod());
        // Exercise real true damage, but keep UI packet delivery outside this test.
        publicUtil = mockStatic(PublicUtil.class, invocation ->
                invocation.getMethod().getName().equals("processActionBarWithSetting")
                        ? null : invocation.callRealMethod());
        plugin = mock(ThePit.class);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        manager = mock(PluginManager.class);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        when(server.getPluginManager()).thenReturn(manager);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
        scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            pending.add(invocation.getArgument(1));
            return mock(BukkitTask.class);
        });
        World world = mock(World.class);
        attacker = player(world);
        victim = player(world);
        attackerProfile = profile(attacker);
        victimProfile = profile(victim);
        random.when(() -> RandomUtil.hasSuccessfullyByChance(anyDouble())).thenReturn(true);
    }

    @After
    public void tearDown() {
        if (publicUtil != null) publicUtil.close();
        if (playerUtil != null) playerUtil.close();
        if (random != null) random.close();
        if (profiles != null) profiles.close();
        if (pit != null) pit.close();
        if (bukkit != null) bukkit.close();
    }

    private Player player(World world) {
        return player(world, mock(HealthPlayer.class));
    }

    private Player player(World world, Player player) {
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.isValid()).thenReturn(true);
        when(player.getHealth()).thenReturn(20D);
        when(player.getMaxHealth()).thenReturn(20D);
        when(player.getWorld()).thenReturn(world);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        return player;
    }

    private PlayerProfile profile(Player player) {
        PlayerProfile profile = mock(PlayerProfile.class);
        profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(player.getUniqueId())).thenReturn(profile);
        when(profile.isLoaded()).thenReturn(true);
        when(profile.isInArena()).thenReturn(true);
        return profile;
    }

    private void attack(int level, int completedHits) {
        when(attackerProfile.getMeleeHit()).thenReturn(completedHits);
        enchant.handleAttackEntity(level, attacker, victim, 4, finalDamage, boost, cancel);
    }

    private void flush() {
        List<Runnable> tasks = new ArrayList<>(pending);
        pending.clear();
        tasks.forEach(Runnable::run);
    }

    private void routedAttack() throws Exception {
        routedAttack(new EntityDamageByEntityEvent(attacker, victim,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 4D));
    }

    private void routedAttack(EntityDamageByEntityEvent event) throws Exception {
        EnchantmentFactor factor = new EnchantmentFactor();
        factor.registerEnchantment(enchant);
        when(plugin.getEnchantmentFactor()).thenReturn(factor);
        IMythicItem sword = mock(IMythicItem.class);
        Object2IntOpenHashMap<net.mizukilab.pit.enchantment.AbstractEnchantment> enchants = new Object2IntOpenHashMap<>();
        enchants.put(enchant, 3);
        when(sword.getEnchantments()).thenReturn(enchants);
        new ProtectListener().onDamage(event);
        assertFalse("The core permits this combat target", event.isCancelled());
        Method route = GameEffectListener.class.getDeclaredMethod("processAttackWithLeggingAndHeldItems",
                boolean.class, EntityDamageByEntityEvent.class, Player.class, AtomicDouble.class,
                AtomicDouble.class, AtomicBoolean.class, IMythicItem.class, IMythicItem.class);
        route.setAccessible(true);
        route.invoke(new GameEffectListener(), false, event, attacker, finalDamage, boost, cancel, sword, null);
    }

    private void useNpcWithoutProfile(boolean online) {
        npcVictim = true;
        when(victim.isOnline()).thenReturn(online);
        profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(victim.getUniqueId()))
                .thenReturn(PlayerProfile.NONE_PROFILE);
        assertFalse(PlayerProfile.NONE_PROFILE.isLoaded());
        assertFalse(PlayerProfile.NONE_PROFILE.isInArena());
    }

    @Test
    public void registeredEnchantTriggersOnBotWithoutPlayerProfileThroughCombatDispatch() throws Exception {
        useNpcWithoutProfile(true);
        routedAttack();
        assertEquals(1, pending.size());
        flush();
        verify(victim).setHealth(18D);
        verify(attacker).sendMessage("§e§l星辰裁决! §7触发成功!");
    }

    @Test
    public void spawnedBotDoesNotNeedAnOnlinePlayerConnection() throws Exception {
        useNpcWithoutProfile(false);
        routedAttack();
        assertEquals(1, pending.size());
        flush();
        verify(victim).setHealth(18D);
        verify(attacker).sendMessage("§e§l星辰裁决! §7触发成功!");
    }

    @Test
    public void absorbedOrdinaryHitStillTriggersIndependentTrueDamageThroughCombatDispatch() throws Exception {
        Map<EntityDamageEvent.DamageModifier, Double> modifiers = new java.util.EnumMap<>(EntityDamageEvent.DamageModifier.class);
        modifiers.put(EntityDamageEvent.DamageModifier.BASE, 4D);
        modifiers.put(EntityDamageEvent.DamageModifier.ABSORPTION, -4D);
        Map<EntityDamageEvent.DamageModifier, com.google.common.base.Function<? super Double, Double>> functions =
                new java.util.EnumMap<>(EntityDamageEvent.DamageModifier.class);
        functions.put(EntityDamageEvent.DamageModifier.BASE, amount -> 0D);
        functions.put(EntityDamageEvent.DamageModifier.ABSORPTION, amount -> -Math.min(4D, amount));
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(attacker, victim,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, modifiers, functions);
        assertEquals(0D, event.getFinalDamage(), 0D);
        routedAttack(event);
        assertEquals(1, pending.size());
        flush();
        verify(victim).setHealth(18D);
        verify(attacker).sendMessage("§e§l星辰裁决! §7触发成功!");
    }

    @Test
    public void limitedRegistrationKeepsLegacyIdentityAndLeavesRarePool() throws Exception {
        EnchantmentFactor factor = new EnchantmentFactor();
        factor.init(List.of(StarJudgementEnchant.class));
        StarJudgementEnchant registered = (StarJudgementEnchant) factor.getEnchantmentMap().get("star_judgement_enchant");
        assertNotNull(registered);
        assertSame(registered, factor.getEnchByClass(StarJudgementEnchant.class));
        assertTrue(factor.getAttackEntities().contains(registered));
        assertTrue(factor.getActionDisplayEnchants().containsKey(registered));
        assertEquals(EnchantmentRarity.OP, registered.getRarity());
        assertEquals(EnchantmentRarity.RarityType.LIMITED, registered.getRarity().getParentType());
        Field pool = EnchantmentTable.class.getDeclaredField("swordEnchant");
        pool.setAccessible(true);
        Map<?, ?> swords = (Map<?, ?>) pool.get(new EnchantmentTable(factor));
        assertTrue(((List<?>) swords.get(EnchantmentRarity.OP)).contains(registered));
        assertFalse(swords.containsKey(EnchantmentRarity.NOSTALGIA_RARE));
    }

    @Test
    public void firstTwoLevelsRollOnThirdAndSecondHitsInsteadOfFirstHit() {
        for (int level = 1; level <= 2; level++) {
            int interval = 4 - level;
            for (int completed = 0; completed < interval * 2; completed++) {
                int before = pending.size();
                attack(level, completed);
                assertEquals("level " + level + ", attack " + (completed + 1),
                        (completed + 1) % interval == 0 ? 1 : 0, pending.size() - before);
            }
        }
        random.verify(() -> RandomUtil.hasSuccessfullyByChance(0.2), times(4));
        random.verifyNoMoreInteractions();
    }

    @Test
    public void thirdLevelRollsThirtyPercentOnEveryAttack() {
        for (int completed = 0; completed < 5; completed++) attack(3, completed);
        assertEquals(5, pending.size());
        random.verify(() -> RandomUtil.hasSuccessfullyByChance(0.3), times(5));
        random.verifyNoMoreInteractions();
    }

    @Test
    public void realChanceThresholdsProduceDamageAndChatOnlyForSuccessfulRollsAtEveryLevel() {
        java.util.Random source = mock(java.util.Random.class);
        random.when(RandomUtil::random).thenReturn(source);
        random.when(() -> RandomUtil.hasSuccessfullyByChance(anyDouble())).thenCallRealMethod();
        for (int level = 1; level <= 3; level++) {
            clearInvocations(attacker, victim);
            double chance = level == 3 ? 0.3D : 0.2D;
            when(source.nextDouble()).thenReturn(Math.nextDown(chance), chance);
            attack(level, 3 - level);
            assertEquals(1, pending.size());
            flush();
            verify(victim).setHealth(18D);
            verify(attacker).sendMessage("§e§l星辰裁决! §7触发成功!");

            attack(level, 3 - level);
            assertTrue(pending.isEmpty());
            verify(victim, times(1)).setHealth(18D);
            verify(attacker, times(1)).sendMessage("§e§l星辰裁决! §7触发成功!");
            assertTrue(enchant.getUsefulnessLore(level).contains(level == 3 ? "30%" : "20%"));
        }
        verify(source, times(6)).nextDouble();
    }

    @Test
    public void unsuccessfulRollDoesNotScheduleDamageOrAnnounceSuccess() {
        random.when(() -> RandomUtil.hasSuccessfullyByChance(0.3)).thenReturn(false);
        attack(3, 0);
        assertTrue(pending.isEmpty());
        verify(victim, never()).setHealth(anyDouble());
        verify(attacker, never()).sendMessage(anyString());
    }

    @Test
    public void secondHitDealsOneHeartAndSendsExactColoredChatOnce() {
        attack(1, 2);
        verify(victim, never()).setHealth(anyDouble());
        verify(attacker, never()).sendMessage(anyString());
        flush();
        verify(victim).setHealth(18D);
        publicUtil.verify(() -> PublicUtil.processActionBarWithSetting(victim, attacker, 2, 2D));
        verify(attacker).sendMessage("§e§l星辰裁决! §7触发成功!");
        verify(victim, never()).sendMessage(anyString());
        assertEquals(4D, finalDamage.get(), 0D);
    }

    @Test
    public void lethalFollowUpUsesCoreDeathDamageInsteadOfSettingZeroHealth() {
        attack(3, 0);
        when(victim.getHealth()).thenReturn(2D);
        flush();
        verify(victim).setNoDamageTicks(0);
        verify(victim).damage(2000D);
        verify(victim, never()).setHealth(anyDouble());
    }

    @Test
    public void guaranteedDamageBypassesImmunityWithoutMirrorReflection() {
        doAnswer(invocation -> {
            ((CanImmuneEvent) invocation.getArgument(0)).setCanImmune(true);
            return null;
        }).when(manager).callEvent(any(CanImmuneEvent.class));
        attack(3, 0);
        flush();
        verify(victim).setHealth(18D);
        verify(victim, never()).damage(anyDouble());
        verify(manager, never()).callEvent(any(CanImmuneEvent.class));
        verify(manager, never()).callEvent(any(TrueDamageByEntityEvent.class));
        verify(attacker).sendMessage("§e§l星辰裁决! §7触发成功!");
    }

    @Test
    public void guaranteedDamageIgnoresMirrorLeggingsAndLoreMatchesDamage() {
        var leggings = new org.bukkit.inventory.ItemStack(org.bukkit.Material.LEATHER_LEGGINGS);
        when(victim.getInventory().getLeggings()).thenReturn(leggings);
        var api = mock(cn.charlotte.pit.api.PitInternalHook.class);
        pit.when(ThePit::getApi).thenReturn(api);
        when(api.getItemEnchantLevel(leggings, "Mirror")).thenReturn(3);
        attack(3, 0);
        flush();
        verify(victim).setHealth(18D);
        verify(manager, never()).callEvent(any(TrueDamageByEntityEvent.class));
        assertTrue(enchant.getUsefulnessLore(3).contains("必中"));
    }

    @Test
    public void nonPlayerAndDisabledOrCancelledAttacksDoNotRoll() {
        enchant.handleAttackEntity(3, attacker, mock(Entity.class), 4, finalDamage, boost, cancel);
        attack(0, 0);
        cancel.set(true);
        attack(3, 0);
        random.verifyNoInteractions();
        assertTrue(pending.isEmpty());
    }

    @Test
    public void laterCancellationSuppressesFollowUpAndMessage() {
        attack(3, 0);
        cancel.set(true);
        flush();
        verify(victim, never()).setHealth(anyDouble());
        verify(attacker, never()).sendMessage(anyString());
    }

    @Test
    public void zeroOrdinaryDamageDoesNotSuppressIndependentTrueDamage() {
        attack(3, 0);
        finalDamage.set(0);
        flush();
        finalDamage.set(4);
        attack(3, 0);
        boost.set(0);
        flush();
        verify(victim, times(2)).setHealth(18D);
        verify(attacker, times(2)).sendMessage("§e§l星辰裁决! §7触发成功!");
    }

    @Test
    public void deadDespawnedOrRespawnedBotCannotReceivePendingDamage() throws Exception {
        useNpcWithoutProfile(false);
        routedAttack();
        when(victim.isDead()).thenReturn(true);
        flush();
        when(victim.isDead()).thenReturn(false);
        routedAttack();
        when(victim.isValid()).thenReturn(false);
        flush();
        when(victim.isValid()).thenReturn(true);
        when(victim.getTicksLived()).thenReturn(200);
        routedAttack();
        when(victim.getTicksLived()).thenReturn(1);
        flush();
        verify(victim, never()).setHealth(anyDouble());
        verify(attacker, never()).sendMessage(anyString());
    }

    @Test
    public void onlineRealPlayerStillTriggersThroughCombatDispatch() throws Exception {
        routedAttack();
        assertEquals(1, pending.size());
        flush();
        verify(victim).setHealth(18D);
        verify(attacker).sendMessage("§e§l星辰裁决! §7触发成功!");
    }

    @Test
    public void realDamageEventDispatchesEnchantmentFromLoadedWeaponNbt() throws Exception {
        try (var attributes = mockConstruction(net.minecraft.server.v1_8_R3.AttributeModifier.class)) {
            World world = attacker.getWorld();
            attacker = player(world, mock(CombatPlayer.class));
            victim = player(world, mock(CombatPlayer.class));
            Class<?> allocatorType = Class.forName("org.objenesis.ObjenesisStd");
            Object allocator = allocatorType.getConstructor().newInstance();
            Method allocate = allocatorType.getMethod("newInstance", Class.class);
            var attackerHandle = (net.minecraft.server.v1_8_R3.EntityPlayer) allocate.invoke(allocator, net.minecraft.server.v1_8_R3.EntityPlayer.class);
            var victimHandle = (net.minecraft.server.v1_8_R3.EntityPlayer) allocate.invoke(allocator, net.minecraft.server.v1_8_R3.EntityPlayer.class);
            when(((CraftPlayer) attacker).getHandle()).thenReturn(attackerHandle);
            when(((CraftPlayer) victim).getHandle()).thenReturn(victimHandle);
            attackerProfile = profile(attacker);
            victimProfile = profile(victim);
            profiles.when(() -> PlayerProfile.getPlayerProfile(attacker)).thenReturn(attackerProfile);
            for (PlayerProfile profile : List.of(attackerProfile, victimProfile)) {
                when(profile.getPlayerOption()).thenReturn(new PlayerOption());
                KillRecap recap = mock(KillRecap.class);
                when(profile.getKillRecap()).thenReturn(recap);
                when(recap.getDamageLogs()).thenReturn(new RangedStreamLineList<>(100, entry -> false));
            }
            EnchantmentFactor factor = new EnchantmentFactor();
            factor.registerEnchantment(enchant);
            when(plugin.getEnchantmentFactor()).thenReturn(factor);
            when(plugin.getPerkFactory()).thenReturn(new PerkFactory());
            when(plugin.getDisabledPerks()).thenReturn(Set.of());
            net.minecraft.server.v1_8_R3.NBTTagList nbt = new net.minecraft.server.v1_8_R3.NBTTagList();
            nbt.add(new net.minecraft.server.v1_8_R3.NBTTagString("star_judgement_enchant:3"));
            IMythicItem sword = new net.mizukilab.pit.item.type.mythic.MythicSwordItem();
            net.mizukilab.pit.util.Utils.readEnchantments(sword.getEnchantments(), nbt);
            org.bukkit.inventory.ItemStack held = new org.bukkit.inventory.ItemStack(org.bukkit.Material.GOLD_SWORD);
            when(attacker.getInventory().getItemInHand()).thenReturn(held);
            when(attacker.getItemInHand()).thenReturn(held);
            net.mizukilab.pit.item.factory.ItemFactory items = mock(net.mizukilab.pit.item.factory.ItemFactory.class);
            when(plugin.getItemFactory()).thenReturn(items);
            when(items.getIMythicItem(held)).thenReturn(sword);
            EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(attacker, victim,
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK, 4D);
            new ProtectListener().onDamage(event);
            assertFalse(event.isCancelled());
            new GameEffectListener().onPlayerDamagePlayer(event);
            assertFalse(event.isCancelled());
            assertEquals(1, pending.size());
            flush();
            verify(victim).setHealth(18D);
            verify(attacker).sendMessage("§e§l星辰裁决! §7触发成功!");
        }
    }

    @Test
    public void unloadedRealPlayerDoesNotGainTheBotException() {
        when(victimProfile.isLoaded()).thenReturn(false);
        attack(3, 0);
        assertTrue(pending.isEmpty());
        random.verifyNoInteractions();
    }

    @Test
    public void deathAndInstantRespawnCannotDamageTheNextLife() {
        attack(3, 0);
        when(victim.isDead()).thenReturn(true);
        flush();
        when(victim.isDead()).thenReturn(false);
        attack(3, 0);
        when(victimProfile.getDeaths()).thenReturn(1);
        flush();
        attack(3, 0);
        when(attackerProfile.getDeaths()).thenReturn(1);
        flush();
        verify(victim, never()).setHealth(anyDouble());
        verify(attacker, never()).sendMessage(anyString());
    }

    @Test
    public void quittingChangingWorldOrReturningToSpawnCancelsPendingHit() {
        attack(3, 0);
        when(victim.isOnline()).thenReturn(false);
        flush();
        when(victim.isOnline()).thenReturn(true);
        attack(3, 0);
        when(victim.getWorld()).thenReturn(mock(World.class));
        flush();
        World originalWorld = attacker.getWorld();
        when(victim.getWorld()).thenReturn(originalWorld);
        attack(3, 0);
        when(victimProfile.isInArena()).thenReturn(false);
        flush();
        verify(victim, never()).setHealth(anyDouble());
        verify(attacker, never()).sendMessage(anyString());
    }

    @Test
    public void deathMarkersAndSpectatorModeBlockFollowUp() {
        for (String marker : List.of("pit_death_handled", "mx_suppressed_duplicate_death")) {
            attack(3, 0);
            when(victim.hasMetadata(marker)).thenReturn(true);
            flush();
            when(victim.hasMetadata(marker)).thenReturn(false);
        }
        attack(3, 0);
        when(victim.getGameMode()).thenReturn(GameMode.SPECTATOR);
        flush();
        verify(victim, never()).setHealth(anyDouble());
        verify(attacker, never()).sendMessage(anyString());
    }

    @Test
    public void actionBarCountsDownToNextEligibleHit() {
        String[][] expected = {
                {"&e&l3", "&e&l2", "&a&l✔", "&e&l3"},
                {"&e&l2", "&a&l✔", "&e&l2", "&a&l✔"},
                {"&a&l✔", "&a&l✔", "&a&l✔", "&a&l✔"}
        };
        for (int level = 1; level <= 3; level++) {
            for (int completed = 0; completed < expected[level - 1].length; completed++) {
                when(attackerProfile.getMeleeHit()).thenReturn(completed);
                assertEquals(expected[level - 1][completed], enchant.getText(level, attacker));
            }
        }
    }
}
