package net.mizukilab.pit.listener;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.perk.PerkFactory;
import com.google.common.util.concurrent.AtomicDouble;
import net.minecraft.server.v1_8_R3.AttributeModifier;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.NBTTagList;
import net.minecraft.server.v1_8_R3.NBTTagString;
import net.mizukilab.pit.BukkitDataTestSupport;
import net.mizukilab.pit.enchantment.AbstractEnchantment;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import net.mizukilab.pit.enchantment.EnchantmentTable;
import net.mizukilab.pit.enchantment.UnknownEnchantment;
import net.mizukilab.pit.enchantment.param.item.WeaponOnly;
import net.mizukilab.pit.enchantment.rarity.EnchantmentRarity;
import net.mizukilab.pit.item.factory.ItemFactory;
import net.mizukilab.pit.item.type.mythic.MythicSwordItem;
import net.mizukilab.pit.parm.listener.IAttackEntity;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.RangedStreamLineList;
import net.mizukilab.pit.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Actual Addon 6.7 callbacks, core startup registration, NBT parsing and damage dispatch. */
public class AddonHelmetEnchantsIntegrationTest extends BukkitDataTestSupport {
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
    private ThePit plugin;
    private EnchantmentFactor factor;
    private AbstractEnchantment rageKill, addonTerminator;
    private CombatPlayer attacker, victim;
    private PlayerProfile attackerProfile, victimProfile;
    private MythicSwordItem sword;
    private final GameEffectListener effects = new GameEffectListener();

    @Before public void setup() throws Exception {
        addon = new URLClassLoader(new URL[]{Path.of(System.getProperty("pitAddonJar")).toUri().toURL(),
                Path.of(System.getProperty("pitAddonLoaderJar")).toUri().toURL(),
                Path.of("build/libs/ThePitGay-V.jar").toUri().toURL()}, getClass().getClassLoader());
        rageKill = enchant("RageKill"); addonTerminator = enchant("Terminator");
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getServer).thenReturn(mock(org.bukkit.Server.class, RETURNS_DEEP_STUBS));
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
        bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
        pit = mockStatic(ThePit.class);
        plugin = mock(ThePit.class);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        profiles = mockStatic(PlayerProfile.class);
        playerUtil = mockStatic(PlayerUtil.class);
        factor = new EnchantmentFactor();
        when(plugin.getEnchantmentFactor()).thenReturn(factor);
        when(plugin.getPerkFactory()).thenReturn(new PerkFactory());
        when(plugin.getDisabledPerks()).thenReturn(Set.of());
        factor.registerEnchantment(rageKill);
        attacker = player(); victim = player();
        attackerProfile = profile(attacker); victimProfile = profile(victim);
        useBot(true);
        sword = new MythicSwordItem();
        ItemStack held = new ItemStack(Material.GOLD_SWORD);
        when(attacker.getItemInHand()).thenReturn(held);
        when(attacker.getInventory().getItemInHand()).thenReturn(held);
        ItemFactory items = mock(ItemFactory.class);
        when(plugin.getItemFactory()).thenReturn(items);
        when(items.getIMythicItem(same(held))).thenReturn(sword);
    }

    private AbstractEnchantment enchant(String simpleName) throws Exception {
        return (AbstractEnchantment) addon.loadClass("cn.apnf.mistral.enchantments.normal." + simpleName)
                .getConstructor().newInstance();
    }

    private CombatPlayer player() {
        CombatPlayer player = mock(CombatPlayer.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        when(player.getHealth()).thenReturn(20D);
        when(player.getMaxHealth()).thenReturn(20D);
        try (var attributes = mockConstruction(AttributeModifier.class)) {
            EntityPlayer handle = mock(EntityPlayer.class);
            when(player.getHandle()).thenReturn(handle);
        }
        return player;
    }

    private PlayerProfile profile(Player player) {
        PlayerProfile profile = mock(PlayerProfile.class, RETURNS_DEEP_STUBS);
        when(profile.getPrestige()).thenReturn(1);
        when(profile.getKillRecap().getDamageLogs()).thenReturn(new RangedStreamLineList<>(100, entry -> false));
        profiles.when(() -> PlayerProfile.getPlayerProfile(player)).thenReturn(profile);
        profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(player.getUniqueId())).thenReturn(profile);
        return profile;
    }

    private void useBot(boolean bot) {
        playerUtil.when(() -> PlayerUtil.isNPC(victim)).thenReturn(bot);
        when(victim.isOnline()).thenReturn(!bot);
        when(victimProfile.isLoaded()).thenReturn(!bot);
    }

    private void equip(String nbtName, int level) {
        NBTTagList list = new NBTTagList();
        list.add(new NBTTagString(nbtName + ":" + level));
        sword.getEnchantments().clear();
        Utils.readEnchantments(sword.getEnchantments(), list);
    }

    private double hit() {
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(attacker, victim,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 4D);
        effects.onPlayerDamagePlayer(event);
        assertFalse(event.isCancelled());
        return event.getDamage();
    }

    @SuppressWarnings("unchecked")
    private AbstractEnchantment registerCoreTerminator() throws Exception {
        EnchantmentFactor capture = mock(EnchantmentFactor.class);
        when(plugin.getEnchantmentFactor()).thenReturn(capture);
        Method load = Class.forName("net.mizukilab.pit.PitHookKt").getDeclaredMethod("loadEnchants");
        load.setAccessible(true);
        load.invoke(null);
        ArgumentCaptor<Collection> classes = ArgumentCaptor.forClass(Collection.class);
        verify(capture).init(classes.capture());
        Class<? extends AbstractEnchantment> type = (Class<? extends AbstractEnchantment>) classes.getValue().stream()
                .filter(entry -> ((Class<?>) entry).getName().equals(
                        "net.mizukilab.pit.enchantment.type.addon.TerminatorEnchant"))
                .findFirst().orElse(null);
        assertNotNull("The real core startup list must contain the Terminator fallback", type);
        when(plugin.getEnchantmentFactor()).thenReturn(factor);
        factor.init(List.of(type));
        return factor.getEnchantmentMap().get("terminator");
    }

    @After public void close() throws Exception {
        if (playerUtil != null) playerUtil.close();
        if (profiles != null) profiles.close();
        if (pit != null) pit.close();
        if (bukkit != null) bukkit.close();
        if (addon != null) addon.close();
    }

    @Test public void legacyAddonReproducesTheAirHelmetBugButCoreDispatchRepairsIt() {
        when(victim.getInventory().getHelmet()).thenReturn(new ItemStack(Material.AIR));
        AtomicDouble boost = new AtomicDouble(1D);
        ((IAttackEntity) rageKill).handleAttackEntity(3, attacker, victim, 4D,
                new AtomicDouble(4D), boost, new AtomicBoolean());
        assertEquals("The actual legacy Addon treats AIR as a helmet", 1D, boost.get(), 0D);
        equip("rage_kill", 3);
        assertEquals(11D, hit(), 0.000001D);
    }

    @Test public void rageKillBoostsBothPlayersAndBotsWithNullOrAirHelmetsAtAllLevels() {
        for (boolean bot : new boolean[]{true, false}) {
            useBot(bot);
            for (ItemStack helmet : new ItemStack[]{null, new ItemStack(Material.AIR)}) {
                when(victim.getInventory().getHelmet()).thenReturn(helmet);
                for (int level = 1; level <= 3; level++) {
                    equip("rage_kill", level);
                    assertEquals(4D * (2D + level * 0.25D), hit(), 0.000001D);
                }
            }
        }
    }

    @Test public void rageKillDoesNotBoostRealHelmetsOrSuppressedEnchants() {
        equip("rage_kill", 3);
        for (Material material : List.of(Material.LEATHER_HELMET, Material.DIAMOND_HELMET, Material.SKULL_ITEM)) {
            when(victim.getInventory().getHelmet()).thenReturn(new ItemStack(material));
            assertEquals(4D, hit(), 0D);
        }
        when(victim.getInventory().getHelmet()).thenReturn(new ItemStack(Material.AIR));
        playerUtil.when(() -> PlayerUtil.shouldIgnoreEnchant(attacker)).thenReturn(true);
        assertEquals(4D, hit(), 0D);
        playerUtil.when(() -> PlayerUtil.shouldIgnoreEnchant(attacker)).thenReturn(false);
        playerUtil.when(() -> PlayerUtil.shouldIgnoreEnchant(attacker, (Entity) victim)).thenReturn(true);
        assertEquals(4D, hit(), 0D);
    }

    @Test public void terminatorIsInStartupRegistryWeaponPoolAndParsesOldLevelFourNbt() throws Exception {
        AbstractEnchantment fallback = registerCoreTerminator();
        assertEquals("终结者", fallback.getEnchantName());
        assertEquals(EnchantmentRarity.NORMAL, fallback.getRarity());
        assertEquals(3, fallback.getMaxEnchantLevel());
        assertTrue(fallback.getClass().isAnnotationPresent(WeaponOnly.class));
        EnchantmentTable table = new EnchantmentTable(factor);
        var poolField = EnchantmentTable.class.getDeclaredField("swordEnchant");
        poolField.setAccessible(true);
        Map<?, ?> pool = (Map<?, ?>) poolField.get(table);
        assertTrue(((List<?>) pool.get(EnchantmentRarity.NORMAL)).contains(fallback));
        equip("terminator", 4);
        assertSame(fallback, sword.getEnchantments().keySet().iterator().next());
        assertEquals(4, sword.getEnchantments().getInt(fallback));
        assertEquals(13D, hit(), 0.000001D);
    }

    @Test public void fallbackMatchesAddonTerminatorForPlayersBotsAndAllSupportedLevels() throws Exception {
        AbstractEnchantment fallback = registerCoreTerminator();
        for (boolean bot : new boolean[]{true, false}) {
            useBot(bot);
            for (ItemStack helmet : new ItemStack[]{null, new ItemStack(Material.AIR), new ItemStack(Material.IRON_HELMET)}) {
                when(victim.getInventory().getHelmet()).thenReturn(helmet);
                for (int level = 1; level <= 4; level++) {
                    equip("terminator", level);
                    AtomicDouble boost = new AtomicDouble(1D);
                    ((IAttackEntity) addonTerminator).handleAttackEntity(level, attacker, victim, 4D,
                            new AtomicDouble(4D), boost, new AtomicBoolean());
                    assertEquals(4D * boost.get(), hit(), 0.000001D);
                    assertEquals(addonTerminator.getUsefulnessLore(level), fallback.getUsefulnessLore(level));
                }
            }
        }
    }

    @Test public void laterAddonRegistrationReplacesFallbackWithoutDoublingDamageOrLosingOldNbt() throws Exception {
        AbstractEnchantment fallback = registerCoreTerminator();
        equip("terminator", 3);
        factor.registerEnchantment(addonTerminator);
        factor.registerEnchantment(addonTerminator);
        factor.removeEnchantment(fallback);
        assertSame(addonTerminator, factor.getEnchantmentMap().get("terminator"));
        assertSame(addonTerminator, factor.getEnchByClass(addonTerminator.getClass()));
        assertEquals(1, factor.getAttackEntities().stream().filter(entry ->
                ((AbstractEnchantment) entry).getNbtName().equals("terminator")).count());
        assertEquals(11D, hit(), 0.000001D);
        factor.unregister("terminator", null);
        assertEquals(4D, hit(), 0D);
    }

    @Test public void anotherImplementationWithTheSameRageKillNbtKeepsItsOwnBehavior() {
        ReplacementRageKill replacement = new ReplacementRageKill();
        factor.registerEnchantment(replacement);
        equip("rage_kill", 3);
        when(victim.getInventory().getHelmet()).thenReturn(new ItemStack(Material.AIR));
        assertEquals(6D, hit(), 0D);
    }

    public static class ReplacementRageKill extends UnknownEnchantment implements IAttackEntity {
        public ReplacementRageKill() { super("rage_kill"); }
        @Override public void handleAttackEntity(int level, Player attacker, Entity target, double damage,
                AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            boostDamage.addAndGet(0.5D);
        }
    }
}
