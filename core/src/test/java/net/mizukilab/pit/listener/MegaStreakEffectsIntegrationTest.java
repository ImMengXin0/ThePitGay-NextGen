package net.mizukilab.pit.listener;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.api.PitInternalHook;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PerkData;
import cn.charlotte.pit.perk.AbstractPerk;
import cn.charlotte.pit.perk.PerkFactory;
import cn.charlotte.pit.perk.PerkType;
import com.google.common.util.concurrent.AtomicDouble;
import net.mizukilab.pit.BukkitDataTestSupport;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import net.mizukilab.pit.handlers.TickHandler;
import net.mizukilab.pit.menu.perk.normal.buy.button.PerkBuyButton;
import net.mizukilab.pit.parm.listener.IPlayerDamaged;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Runs the shipped addon callbacks and core damage helpers; only Bukkit-owned state is simulated. */
public class MegaStreakEffectsIntegrationTest extends BukkitDataTestSupport {
    private interface HealthPlayer extends Player {
        @Override double getHealth();
        @Override double getMaxHealth();
    }

    private URLClassLoader loader;
    private MockedStatic<ThePit> pit;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<PlayerProfile> profiles;
    private PerkFactory perks;
    private PitInternalHook api;
    private Player player;
    private PlayerProfile profile;
    private final Map<Integer, PerkData> chosen = new HashMap<>();
    private final Map<String, PerkData> unlocked = new HashMap<>();
    private final Map<String, PerkData> bought = new HashMap<>();
    private final AtomicDouble health = new AtomicDouble(20), maxHealth = new AtomicDouble(20);
    private final List<PotionEffect> effects = new ArrayList<>();
    private TickHandler ticker;
    private Method tickPerks, damagedPerks, attackingPerks;
    private final GameEffectListener listener = new GameEffectListener();
    private EntityDamageByEntityEvent hit;
    private final ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS);

    @Before
    public void setUpGameplay() throws Exception {
        Path server = Path.of(System.getProperty("pitAddonJar"));
        Path addonLoader = Path.of(System.getProperty("pitAddonLoaderJar"));
        Assume.assumeTrue("Local Addon fixtures unavailable", Files.isRegularFile(server) && Files.isRegularFile(addonLoader));
        loader = new URLClassLoader(new URL[]{server.toUri().toURL(), addonLoader.toUri().toURL(),
                Path.of("build/libs/ThePitGay-V.jar").toUri().toURL()}, getClass().getClassLoader());
        pit = mockStatic(ThePit.class);
        bukkit = mockStatic(Bukkit.class);
        profiles = mockStatic(PlayerProfile.class);
        ThePit plugin = mock(ThePit.class);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        api = mock(PitInternalHook.class);
        pit.when(ThePit::getApi).thenReturn(api);
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        perks = new PerkFactory();
        when(plugin.getPerkFactory()).thenReturn(perks);
        when(plugin.getEnchantmentFactor()).thenReturn(new EnchantmentFactor());
        player = mock(HealthPlayer.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getHealth()).thenAnswer(call -> health.get());
        when(player.getMaxHealth()).thenAnswer(call -> maxHealth.get());
        doAnswer(call -> { health.set(call.getArgument(0)); return null; }).when(player).setHealth(anyDouble());
        doAnswer(call -> { maxHealth.set(call.getArgument(0)); return null; }).when(player).setMaxHealth(anyDouble());
        doAnswer(call -> { effects.add(call.getArgument(0)); return true; }).when(player).addPotionEffect(any(PotionEffect.class));
        doAnswer(call -> { effects.add(call.getArgument(0)); return true; }).when(player).addPotionEffect(any(PotionEffect.class), eq(true));
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getLeggings()).thenReturn(leggings);
        profile = mock(PlayerProfile.class);
        profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(uuid)).thenReturn(profile);
        profiles.when(() -> PlayerProfile.getRawCache(uuid)).thenReturn(profile);
        when(profile.getChosePerk()).thenReturn(chosen);
        when(profile.getUnlockedPerkMap()).thenReturn(unlocked);
        when(profile.getBoughtPerkMap()).thenReturn(bought);
        when(profile.getLevel()).thenReturn(120);
        when(profile.getPrestige()).thenReturn(100);
        when(profile.getCoins()).thenReturn(1_000_000_000D);
        ticker = new TickHandler();
        tickPerks = TickHandler.class.getDeclaredMethod("tickPerks", Player.class, PlayerProfile.class, long.class);
        tickPerks.setAccessible(true);
        damagedPerks = GameEffectListener.class.getDeclaredMethod("processPerksDMGed", EntityDamageByEntityEvent.class,
                Player.class, PlayerProfile.class, PerkFactory.class, Set.class, AtomicDouble.class, AtomicDouble.class, AtomicBoolean.class);
        damagedPerks.setAccessible(true);
        attackingPerks = GameEffectListener.class.getDeclaredMethod("processPerkATK", EntityDamageByEntityEvent.class,
                PlayerProfile.class, PerkFactory.class, Set.class, Player.class, AtomicDouble.class, AtomicDouble.class, AtomicBoolean.class);
        attackingPerks.setAccessible(true);
        hit = new EntityDamageByEntityEvent(mock(Player.class), player, DamageCause.ENTITY_ATTACK, 4D);
    }

    @After
    public void closeGameplay() throws Exception {
        if (perks != null) {
            for (AbstractPerk perk : new ArrayList<>(perks.getPerks())) perks.removePerk(perk);
        }
        if (profiles != null) profiles.close();
        if (bukkit != null) bukkit.close();
        if (pit != null) pit.close();
        if (loader != null) loader.close();
    }

    private AbstractPerk purchase(String name) throws Exception {
        AbstractPerk perk = (AbstractPerk) loader.loadClass("cn.apnf.mistral.perk.MegaStreak." + name).getConstructor().newInstance();
        perks.registerPerk(perk);
        new PerkBuyButton(perk, 5, PerkType.MEGA_STREAK).clicked(player, 0, ClickType.LEFT, -1, null);
        assertNotNull("Normal menu purchase must equip the perk", chosen.get(5));
        assertEquals(perk.getInternalPerkName(), chosen.get(5).getPerkInternalName());
        assertEquals("Normal purchases legitimately use level zero", 0, chosen.get(5).getLevel());
        return perk;
    }

    private void kills(double kills) { when(profile.getStreakKills()).thenReturn(kills); }
    private void tick(long time) throws Exception { tickPerks.invoke(ticker, player, profile, time); }

    private double damaged(Set<AbstractPerk> disabled) throws Exception {
        AtomicDouble damage = new AtomicDouble(4), boost = new AtomicDouble(1);
        AtomicBoolean cancel = new AtomicBoolean();
        damagedPerks.invoke(listener, hit, player, profile, perks, disabled, damage, boost, cancel);
        assertEquals(4D, damage.get(), 0D);
        assertFalse(cancel.get());
        return boost.get();
    }

    private double attacking() throws Exception {
        AtomicDouble damage = new AtomicDouble(4), boost = new AtomicDouble(1);
        attackingPerks.invoke(listener, hit, profile, perks, Set.of(), player, damage, boost, new AtomicBoolean());
        return boost.get();
    }

    private void effect(PotionEffectType type, int duration, int amplifier) {
        // The server registers potion display names at startup; IDs are available without it.
        assertTrue("Missing potion ID " + type.getId() + " duration=" + duration + " amplifier=" + amplifier,
                effects.stream().anyMatch(value -> value.getType().equals(type)
                        && value.getDuration() == duration && value.getAmplifier() == amplifier));
    }

    @Test
    public void purchasedSeaOfTimeDealsPeriodicDamageAndSlowOnlyAtItsThresholdAndCadence() throws Exception {
        purchase("SeaOfTime");
        kills(499);
        tick(0);
        kills(500);
        tick(19);
        verify(player, never()).damage(anyDouble());
        assertTrue(effects.isEmpty());
        tick(20);
        verify(player, times(1)).damage(3D);
        assertEquals(1, effects.size());
        effect(PotionEffectType.SLOW, 20, 1);
        tick(21);
        verify(player, times(1)).damage(3D);
        tick(40);
        verify(player, times(2)).damage(3D);
        assertEquals(2, effects.size());
        unlocked.put(chosen.get(5).getPerkInternalName(), chosen.remove(5));
        tick(60);
        verify(player, times(2)).damage(3D);
        assertEquals(2, effects.size());
    }

    @Test
    public void seaOfTimeHunterLeggingsHealAndBoostAttackInsteadOfApplyingPeriodicDebuffs() throws Exception {
        purchase("SeaOfTime");
        when(api.getItemEnchantLevel(leggings, "chrono_realm_hunter")).thenReturn(1);
        health.set(10);
        kills(500);
        tick(20);
        assertEquals(13D, health.get(), 0D);
        assertEquals(1.2D, attacking(), 0.00001D);
        verify(player, never()).damage(anyDouble());
        assertTrue(effects.isEmpty());
    }

    @Test
    public void seaOfTimeHitDealsTenPercentOfStreakOnceEvenWhenAlsoUnlocked() throws Exception {
        AbstractPerk perk = purchase("SeaOfTime");
        unlocked.put(perk.getInternalPerkName(), new PerkData(perk.getInternalPerkName(), 1));
        health.set(200);
        maxHealth.set(200);
        kills(499);
        damaged(Set.of());
        assertEquals(200D, health.get(), 0D);
        kills(500);
        assertEquals(1D, damaged(Set.of()), 0D);
        assertEquals(150D, health.get(), 0D);
        verify(player, times(1)).setHealth(anyDouble());
        damaged(Set.of(perk));
        assertEquals(150D, health.get(), 0D);
        chosen.clear();
        damaged(Set.of());
        assertEquals(150D, health.get(), 0D);
    }

    @Test
    public void lethalSeaOfTimeHitReachesCoreDeathDamagePath() throws Exception {
        purchase("SeaOfTime");
        // Newer Addon builds cap this hit at half max health; keep the victim
        // below that cap to exercise a lethal hit with both Addon versions.
        health.set(5);
        kills(500);
        damaged(Set.of());
        verify(player).setNoDamageTicks(0);
        verify(player).damage(2000D);
    }

    @Test
    public void superPeakIncomingDamageStartsAtFiveThousandAndAppliesOnce() throws Exception {
        AbstractPerk perk = purchase("SuperPeak");
        unlocked.put(perk.getInternalPerkName(), new PerkData(perk.getInternalPerkName(), 1));
        kills(4999);
        assertEquals(1D, damaged(Set.of()), 0D);
        kills(5000);
        assertEquals(1.5D, damaged(Set.of()), 0D);
        assertEquals(1D, damaged(Set.of(perk)), 0D);
        chosen.get(5).setLevel(-1);
        assertEquals(1D, damaged(Set.of()), 0D);
        chosen.clear();
        assertEquals(1D, damaged(Set.of()), 0D);
    }

    @Test
    public void superPeakDebuffsAndTrueDamageFollowAllMilestones() throws Exception {
        purchase("SuperPeak");
        for (int kills : new int[]{5000, 19999, 20000, 39999, 40000, 79999, 80000, 99999, 100000}) {
            effects.clear();
            maxHealth.set(20);
            health.set(20);
            kills(kills);
            tick(20);
            double expectedDamage = kills >= 100000 ? 10 : kills >= 40000 ? 2 : 0;
            // The separate max-health test covers the pre-20000 phase.
            if (kills != 19999) assertEquals("Streak " + kills, 20 - expectedDamage, health.get(), 0D);
            assertEquals("Debuff count at streak " + kills, kills >= 80000 ? 4 : kills >= 20000 ? 3 : 0, effects.size());
            if (kills >= 20000) {
                effect(PotionEffectType.CONFUSION, 200, 0);
                effect(PotionEffectType.SLOW, 200, 2);
                effect(PotionEffectType.SLOW_DIGGING, 200, 0);
            }
            if (kills >= 80000) effect(PotionEffectType.BLINDNESS, 40, 0);
        }
        verify(player, never()).damage(anyDouble());
        effects.clear();
        health.set(20);
        tick(21);
        assertEquals(20D, health.get(), 0D);
        assertTrue(effects.isEmpty());
        chosen.clear();
        tick(40);
        assertEquals(20D, health.get(), 0D);
        assertTrue(effects.isEmpty());
    }

    @Test
    public void superPeakHealthReductionAndOutgoingDamageCallbacksRemainEffective() throws Exception {
        purchase("SuperPeak");
        kills(12000);
        tick(20);
        assertEquals(19D, maxHealth.get(), 0D);
        assertEquals(19D, health.get(), 0D);
        kills(19999);
        tick(40);
        assertEquals(16D, maxHealth.get(), 0D);
        assertEquals(16D, health.get(), 0D);
        kills(59999);
        assertEquals(1D, attacking(), 0D);
        kills(60000);
        assertEquals(0.7D, attacking(), 0.00001D);
        chosen.clear();
        assertEquals(1D, attacking(), 0D);
    }

    @Test
    public void purchasedPassiveDamagePerkRunsOnceButUnpurchasedAndDisabledDoNot() throws Exception {
        AbstractPerk passive = mock(AbstractPerk.class, withSettings().extraInterfaces(IPlayerDamaged.class));
        when(passive.isPassive()).thenReturn(true);
        when(passive.getInternalPerkName()).thenReturn("test_passive");
        perks.getPerkMap().put("test_passive", passive);
        IPlayerDamaged callback = (IPlayerDamaged) passive;
        doAnswer(call -> { ((AtomicDouble) call.getArgument(5)).addAndGet(-0.2); return null; })
                .when(callback).handlePlayerDamaged(anyInt(), any(), any(), anyDouble(), any(), any(), any());
        PerkData data = new PerkData("test_passive", 1);
        unlocked.put("test_passive", data);
        chosen.put(1, data);
        assertEquals(0.8D, damaged(Set.of()), 0.00001D);
        verify(callback, times(1)).handlePlayerDamaged(eq(1), eq(player), any(), eq(4D), any(), any(), any());
        assertEquals(1D, damaged(Set.of(passive)), 0D);
        data.setLevel(0);
        assertEquals(1D, damaged(Set.of()), 0D);
        unlocked.clear();
        data.setLevel(1);
        assertEquals(1D, damaged(Set.of()), 0D);
        verify(callback, times(1)).handlePlayerDamaged(anyInt(), any(), any(), anyDouble(), any(), any(), any());
    }
}
