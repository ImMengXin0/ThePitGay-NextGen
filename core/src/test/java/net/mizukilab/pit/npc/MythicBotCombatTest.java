package net.mizukilab.pit.npc;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.operator.IProfilerOperator;
import cn.charlotte.pit.data.operator.IOperator;
import cn.charlotte.pit.data.sub.PlayerOption;
import net.mizukilab.pit.BukkitDataTestSupport;
import net.mizukilab.pit.listener.CombatListener;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import net.mizukilab.pit.util.rank.RankUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.*;
import org.junit.*;
import org.mockito.MockedStatic;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MythicBotCombatTest extends BukkitDataTestSupport {
    // The 1.8 API includes legacy float bridge methods with the same name.
    private interface HealthPlayer extends Player {
        @Override double getHealth();
        @Override double getMaxHealth();
    }
    private interface SourceArrow extends Arrow {
        @Override org.bukkit.projectiles.ProjectileSource getShooter();
    }
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private MockedStatic<NpcIdentity> identities;
    private MockedStatic<RankUtil> ranks;
    private MythicBotCombat bridge;
    private CombatListener combat, previousCombat;
    private ThePit plugin;
    private Player human, bot;
    private PlayerProfile humanProfile;
    private final List<Runnable> callbacks = new ArrayList<>();
    private final Map<Player, Double> health = new IdentityHashMap<>();

    @Before public void setup() {
        previousCombat = CombatListener.INSTANCE;
        bukkit = mockStatic(Bukkit.class);
        pit = mockStatic(ThePit.class);
        identities = mockStatic(NpcIdentity.class);
        plugin = mock(ThePit.class, RETURNS_DEEP_STUBS);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong())).thenReturn(mock(BukkitTask.class));
        when(scheduler.runTask(any(), any(Runnable.class))).thenAnswer(call -> {
            callbacks.add(call.getArgument(1)); return mock(BukkitTask.class);
        });
        ranks = mockStatic(RankUtil.class);
        ranks.when(() -> RankUtil.getPlayerColoredName(any(UUID.class))).thenReturn("Player");
        human = player("Human", false); bot = player("RepairBot", true);
        when(human.isOnline()).thenReturn(true);
        bukkit.when(() -> Bukkit.getPlayer(human.getUniqueId())).thenReturn(human);
        humanProfile = new PlayerProfile(human.getUniqueId(), "Human");
        humanProfile.getPlayerOption().setBarPriority(PlayerOption.BarPriority.ENCHANT_ONLY);
        humanProfile.setLoaded(true);
        IProfilerOperator operator = mock(IProfilerOperator.class);
        IOperator online = mock(IOperator.class);
        when(plugin.getProfileOperator()).thenReturn(operator);
        when(operator.getIOperator(human.getUniqueId())).thenReturn(online);
        when(online.isLoaded()).thenReturn(true); when(online.profile()).thenReturn(humanProfile);
        when(plugin.getEnchantmentFactor()).thenReturn(new EnchantmentFactor());
        combat = spy(new CombatListener());
        CombatListener.INSTANCE = combat;
        doNothing().when(combat).handleKill(any(), any(), any(), any(), anyBoolean());
        bridge = new MythicBotCombat(plugin); bridge.start();
    }

    @After public void close() {
        if (bridge != null) bridge.close();
        CombatListener.INSTANCE = previousCombat;
        if (ranks != null) ranks.close();
        if (identities != null) identities.close();
        if (pit != null) pit.close();
        if (bukkit != null) bukkit.close();
    }

    private Player player(String name, boolean nativeBot) {
        Player player = mock(HealthPlayer.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id); when(player.getName()).thenReturn(name);
        when(player.getHealth()).thenAnswer(call -> health.getOrDefault(player, 20D));
        when(player.getMaxHealth()).thenReturn(20D);
        when(player.isValid()).thenReturn(true);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getArmorContents()).thenReturn(new ItemStack[0]);
        when(player.getInventory()).thenReturn(inventory);
        identities.when(() -> NpcIdentity.isMythicBot(player)).thenReturn(nativeBot);
        identities.when(() -> NpcIdentity.isNpc(player)).thenReturn(nativeBot);
        return player;
    }

    private EntityDamageByEntityEvent hit(Entity source, Player target) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(target); when(event.getDamager()).thenReturn(source);
        when(event.getFinalDamage()).thenReturn(8D);
        bridge.prepareDamage(event); bridge.afterDamage(event);
        return event;
    }
    private void flush() { new ArrayList<>(callbacks).forEach(Runnable::run); callbacks.clear(); }

    @Test public void meleeDeathReachesCoreKillFlowAndDuplicatesAreIgnored() {
        hit(human, bot); health.put(bot, 0D); flush();
        combat.handlePlayerDeath(bot, human, false);
        PlayerDeathEvent duplicate = mock(PlayerDeathEvent.class);
        when(duplicate.getEntity()).thenReturn(bot);
        combat.onKilled(duplicate);
        verify(combat, times(1)).handleKill(human, humanProfile, bot, MythicBotCombat.profile(bot.getUniqueId()), true);
        verify(bot, never()).teleport(any(org.bukkit.Location.class));
    }

    @Test public void arrowKillsCountWithoutRegisteringTheBotAsAnOnlinePlayer() {
        Arrow arrow = mock(SourceArrow.class); when(arrow.getShooter()).thenReturn(human);
        hit(arrow, bot); health.put(bot, 0D); flush();
        verify(combat).handleKill(eq(human), same(humanProfile), same(bot), any(), eq(true));
        assertNull(PlayerProfile.getRawCache(bot.getUniqueId()));
    }

    @Test public void nonlethalHealingCancelledHitsAndHealthyRemovalDoNotAwardKills() {
        EntityDamageByEntityEvent cancelled = hit(human, bot);
        when(cancelled.isCancelled()).thenReturn(true); flush();
        hit(human, bot); health.put(bot, 1D); flush(); // A later listener saved the bot.
        hit(human, bot); when(bot.isValid()).thenReturn(false); flush();
        verify(combat, never()).handleKill(any(), any(), any(), any(), anyBoolean());
    }

    @Test public void cancelledLethalPredictionDoesNotAssignAKiller() {
        EntityDamageByEntityEvent cancelled = hit(human, bot);
        when(cancelled.isCancelled()).thenReturn(true);
        health.put(bot, 0D); flush();
        verify(combat, never()).handleKill(any(), any(), any(), any(), anyBoolean());
    }

    @Test public void botsHaveSeparateTransientDamageMapsAndShareOneCallbackPerTick() {
        Player second = player("Second", true);
        hit(human, bot); hit(human, second); hit(human, bot);
        assertEquals(1, callbacks.size());
        PlayerProfile firstProfile = PlayerProfile.getPlayerProfileByUuid(bot.getUniqueId());
        PlayerProfile secondProfile = PlayerProfile.getPlayerProfile(second);
        firstProfile.getDamageMap().put(human.getUniqueId(), new cn.charlotte.pit.data.sub.DamageData(human.getUniqueId()));
        assertFalse(secondProfile.getDamageMap().containsKey(human.getUniqueId()));
        assertFalse(PlayerProfile.NONE_PROFILE.getDamageMap().containsKey(human.getUniqueId()));
        assertFalse(firstProfile.isLoaded()); assertTrue(firstProfile.isBot());
        assertSame(firstProfile, PlayerProfile.getPlayerProfile(bot));
        health.put(bot, 0D); health.put(second, 0D); flush();
        verify(combat, times(2)).handleKill(eq(human), same(humanProfile), any(), any(), eq(true));
    }

    @Test public void aNativeBotsFinalBlowDoesNotAwardAnEarlierHumanAttacker() {
        hit(human, bot); flush();
        hit(player("AttackingBot", true), bot); health.put(bot, 0D); flush();
        verify(combat, never()).handleKill(any(), any(), any(), any(), anyBoolean());
    }

    @Test public void shutdownDiscardsPendingDeathsAndTemporaryProfiles() {
        hit(human, bot); health.put(bot, 0D);
        bridge.close(); flush();
        assertNull(MythicBotCombat.profile(bot.getUniqueId()));
        verify(combat, never()).handleKill(any(), any(), any(), any(), anyBoolean());
    }

    @Test public void nativeHitsUpdateTheAttackersCombatTimerAndHitStatistics() throws Exception {
        EntityDamageByEntityEvent event = hit(human, bot);
        var method = CombatListener.class.getDeclaredMethod("onCombat", EntityDamageByEntityEvent.class);
        method.setAccessible(true); method.invoke(combat, event);
        assertFalse(humanProfile.getCombatTimer().hasExpired());
        assertEquals(1, humanProfile.getMeleeHit());
        assertEquals(8D, MythicBotCombat.profile(bot.getUniqueId()).getDamageMap().get(human.getUniqueId()).getDamage(), 0D);
    }

    @Test public void nativeKillActuallyIncrementsStreakAndEmitsTheNormalRewardEvent() {
        humanProfile.setLoaded(false);
        humanProfile.setExperience(net.mizukilab.pit.util.level.LevelUtil.getLevelTotalExperience(0, 50));
        humanProfile.setLoaded(true);
        plugin.getGlobalConfig().maxLevel = 120;
        doCallRealMethod().when(combat).handleKill(any(), any(), any(), any(), anyBoolean());
        try (var chance = mockStatic(net.mizukilab.pit.util.random.RandomUtil.class)) {
            double coins = humanProfile.getCoins();
            hit(human, bot); health.put(bot, 0D); flush();
            assertEquals(1D, humanProfile.getStreakKills(), 0D);
            assertEquals(1, humanProfile.getKills());
            assertTrue(humanProfile.getCoins() > coins);
            verify(Bukkit.getPluginManager()).callEvent(isA(cn.charlotte.pit.event.PitKillEvent.class));
        }
    }
}
