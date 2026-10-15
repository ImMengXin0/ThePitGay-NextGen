package net.mizukilab.pit.item;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.api.PitInternalHook;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.perk.PerkFactory;
import net.minecraft.server.v1_8_R3.AttributeModifier;
import net.minecraft.server.v1_8_R3.DispenserRegistry;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.NBTTagList;
import net.mizukilab.pit.enchantment.AbstractEnchantment;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import net.mizukilab.pit.enchantment.type.rage.NewDealEnchant;
import net.mizukilab.pit.handlers.TickHandler;
import net.mizukilab.pit.item.factory.ItemFactory;
import net.mizukilab.pit.item.type.mythic.MythicLeggingsItem;
import net.mizukilab.pit.listener.GameEffectListener;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.PublicUtil;
import net.mizukilab.pit.util.Utils;
import net.mizukilab.pit.util.item.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemFactory;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.*;
import org.mockito.MockedStatic;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Uses RepairExTend's actual SpeedForce callback with real equipment NBT. */
public class BlankRageEffectsIntegrationTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private MockedStatic<PlayerProfile> profiles;
    private MockedStatic<PlayerUtil> effects;
    private URLClassLoader loader;
    private PitInternalHook oldApi;
    private AbstractEnchantment speed;
    private ItemFactory factory;
    private CraftPlayer attacker, target;
    private PlayerProfile profile;
    private ItemStack equipped;

    @BeforeClass public static void vanilla() {
        PrintStream out = System.out, err = System.err;
        try (var attributes = mockConstruction(AttributeModifier.class)) { DispenserRegistry.c(); }
        finally { System.setOut(out); System.setErr(err); }
    }

    @Before public void setup() throws Exception {
        loader = new URLClassLoader(new URL[]{Path.of(System.getProperty("repairExtendJar")).toUri().toURL(),
                Path.of("build/libs/ThePitGay-V.jar").toUri().toURL()}, getClass().getClassLoader());
        speed = (AbstractEnchantment) loader.loadClass("cn.mx.thepitaddon.enchantment.SpeedForceEnchant")
                .getConstructor().newInstance();
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getItemFactory).thenReturn(CraftItemFactory.instance());
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        org.bukkit.Server server = mock(org.bukkit.Server.class, RETURNS_DEEP_STUBS);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        pit = mockStatic(ThePit.class);
        ThePit plugin = mock(ThePit.class);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        EnchantmentFactor factor = new EnchantmentFactor();
        factor.registerEnchantment(speed); factor.registerEnchantment(new NewDealEnchant());
        when(plugin.getEnchantmentFactor()).thenReturn(factor);
        when(plugin.getPerkFactory()).thenReturn(new PerkFactory());
        when(plugin.getDisabledPerks()).thenReturn(Set.of());
        factory = new ItemFactory(); when(plugin.getItemFactory()).thenReturn(factory);
        oldApi = ThePit.api; ThePit.api = mock(PitInternalHook.class);
        pit.when(ThePit::getApi).thenReturn(ThePit.api);
        when(ThePit.api.getWatermark()).thenReturn("test");
        attacker = mock(CraftPlayer.class); target = mock(CraftPlayer.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        when(target.getUniqueId()).thenReturn(UUID.randomUUID());
        EntityPlayer attackerHandle, targetHandle;
        try (var attributes = mockConstruction(AttributeModifier.class)) {
            attackerHandle = mock(EntityPlayer.class); targetHandle = mock(EntityPlayer.class);
        }
        when(attacker.getHandle()).thenReturn(attackerHandle);
        when(target.getHandle()).thenReturn(targetHandle);
        when(target.getHealth()).thenReturn(20D);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(attacker.getInventory()).thenReturn(inventory);
        when(inventory.getLeggings()).thenAnswer(c -> equipped);
        try (var warehouses = mockConstruction(cn.charlotte.pit.data.sub.PlayerWarehouse.class);
             var chests = mockConstruction(cn.charlotte.pit.data.sub.PlayerEnderChest.class)) {
            profile = mock(PlayerProfile.class, RETURNS_DEEP_STUBS);
        }
        when(profile.getPrestige()).thenReturn(1);
        when(profile.getMeleeHit()).thenReturn(2);
        profiles = mockStatic(PlayerProfile.class);
        profiles.when(() -> PlayerProfile.getPlayerProfile(attacker)).thenReturn(profile);
        profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(attacker.getUniqueId())).thenReturn(profile);
        profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(target.getUniqueId())).thenReturn(profile);
        effects = mockStatic(PlayerUtil.class);
        effects.when(() -> PlayerUtil.isNPC(target)).thenReturn(true);
        when(attacker.hasPotionEffect(PotionEffectType.SPEED)).thenReturn(true);
    }

    @After public void close() throws Exception {
        ThePit.api = oldApi;
        if (effects != null) effects.close(); if (profiles != null) profiles.close();
        if (pit != null) pit.close(); if (bukkit != null) bukkit.close();
        if (loader != null) loader.close();
    }

    private ItemStack pants(UUID id, AbstractEnchantment enchant, int level) {
        MythicLeggingsItem item = new MythicLeggingsItem();
        item.setUuid(id); item.setVersion(PublicUtil.itemVersion); item.setColor(MythicColor.RAGE);
        if (enchant != null) {
            item.setTier(1); item.setLive(5); item.setMaxLive(8); item.getEnchantments().put(enchant, level);
        }
        return item.toItemStack();
    }

    private void tickEquipment(TickHandler ticker) throws Exception {
        var method = TickHandler.class.getDeclaredMethod("tickLeggings", Player.class, PlayerProfile.class, long.class);
        method.setAccessible(true); method.invoke(ticker, attacker, profile, 0L);
    }

    private void hit() {
        new GameEffectListener().onPlayerDamagePlayer(new EntityDamageByEntityEvent(attacker, target,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 4D));
    }

    private void noSpeedForce() {
        effects.verify(() -> PlayerUtil.addPotionEffect(eq(attacker), any(PotionEffect.class), anyBoolean()), never());
        effects.verify(() -> PlayerUtil.damage(eq(attacker), eq(target), eq(PlayerUtil.DamageType.TRUE), anyDouble(), anyBoolean()), never());
    }

    @Test public void blankPantsSharingAnEnchantedUuidCannotTriggerSpeedOrBonusDamage() throws Exception {
        UUID id = UUID.randomUUID();
        IMythicItem old = factory.getIMythicItem(pants(id, speed, 3));
        equipped = pants(id, null, 0);
        assertEquals(0, ItemUtil.getExtra(equipped).getList("ench", 8).size());
        tickEquipment(new TickHandler());
        hit(); noSpeedForce();
        assertNotSame(old, profile.leggings);
        assertTrue(profile.leggings.getEnchantments().isEmpty());
    }

    @Test public void removingNbtInPlaceInvalidatesTheProfileEvenWhenTheStackObjectIsUnchanged() throws Exception {
        equipped = CraftItemStack.asCraftMirror(Utils.toNMStackQuick(pants(UUID.randomUUID(), speed, 3)).cloneItemStack());
        TickHandler ticker = new TickHandler(); tickEquipment(ticker);
        assertEquals(3, profile.leggings.getEnchantments().getInt(speed));
        ItemUtil.getExtra(equipped).set("ench", new NBTTagList());
        tickEquipment(ticker);
        assertTrue(profile.leggings.getEnchantments().isEmpty());
        hit(); noSpeedForce();
    }

    @Test public void swappingOrRemovingPantsImmediatelyBeforeAttackDoesNotUseThePreviousTick() throws Exception {
        for (boolean remove : new boolean[]{false, true}) {
            equipped = pants(UUID.randomUUID(), speed, 3); tickEquipment(new TickHandler());
            equipped = remove ? null : pants(UUID.randomUUID(), null, 0);
            hit(); noSpeedForce();
        }
    }

    @Test public void realSpeedForceStillGrantsSpeedAndBonusDamageAndKeepsStableCache() throws Exception {
        equipped = pants(UUID.randomUUID(), speed, 3);
        IMythicItem cached = factory.getIMythicItem(equipped);
        for (int i = 0; i < 1000; i++) assertSame(cached, factory.getIMythicItem(equipped));
        tickEquipment(new TickHandler()); hit();
        effects.verify(() -> PlayerUtil.addPotionEffect(eq(attacker), argThat(effect ->
                effect.getType().equals(PotionEffectType.SPEED) && effect.getAmplifier() == 3 && effect.getDuration() == 60), eq(true)));
        effects.verify(() -> PlayerUtil.damage(attacker, target, PlayerUtil.DamageType.TRUE, 4D, false));
    }

    @Test public void sameUuidWithAnotherEnchantOrLevelUsesTheActualNbt() {
        UUID id = UUID.randomUUID();
        factory.getIMythicItem(pants(id, speed, 3));
        ItemStack lower = pants(id, speed, 1);
        assertEquals(1, factory.getIMythicItem(lower).getEnchantments().getInt(speed));
        ItemStack other = pants(id, new NewDealEnchant(), 1);
        assertFalse(factory.getIMythicItem(other).getEnchantments().containsKey(speed));
    }

    @Test public void reusingAParsedItemWithNoEnchantTagCannotRetainOldEnchantments() {
        ItemStack source = pants(UUID.randomUUID(), speed, 3);
        IMythicItem parsed = Utils.getMythicItem0(source);
        ItemStack blank = CraftItemStack.asCraftMirror(Utils.toNMStackQuick(pants(parsed.getUuid(), null, 0)).cloneItemStack());
        ItemUtil.getExtra(blank).remove("ench");
        parsed.loadFromItemStack(blank);
        assertTrue(parsed.getEnchantments().isEmpty());
        assertEquals(0, parsed.getTier());
    }
}
