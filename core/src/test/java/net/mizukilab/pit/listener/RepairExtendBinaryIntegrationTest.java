package net.mizukilab.pit.listener;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.event.PitAssistEvent;
import cn.charlotte.pit.event.PitKillEvent;
import net.mizukilab.pit.config.NewConfiguration;
import net.mizukilab.pit.config.PitWorldConfig;
import net.mizukilab.pit.item.IMythicItem;
import net.mizukilab.pit.item.IItemFactory;
import net.mizukilab.pit.item.factory.ItemFactory;
import net.mizukilab.pit.util.Utils;
import net.mizukilab.pit.util.item.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.junit.Test;
import org.junit.Assume;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Runs the locally supplied extension bytecode against current core classes, without editing the extension. */
public class RepairExtendBinaryIntegrationTest {
    private URLClassLoader extension() throws Exception {
        Path jar = Path.of(System.getProperty("repairExtendJar",
                "../../ThePitRepairExTend/build/libs/ThePitRepairExTend-3.5.2.jar"));
        Assume.assumeTrue("Local extension fixture not available: " + jar.toAbsolutePath(), Files.isRegularFile(jar));
        return new URLClassLoader(new URL[]{jar.toUri().toURL(), Path.of("build/libs/ThePitGay-V.jar").toUri().toURL()},
                getClass().getClassLoader());
    }

    @Test
    public void bothExtensionManagersRebuildThroughTheirActualLegacyBytecode() throws Exception {
        try (URLClassLoader loader = extension();
             MockedStatic<Utils> utils = mockStatic(Utils.class);
             MockedStatic<ItemUtil> nbt = mockStatic(ItemUtil.class)) {
            ItemStack stack = mock(ItemStack.class);
            when(stack.getType()).thenReturn(org.bukkit.Material.LEATHER_LEGGINGS);
            IMythicItem result = mock(IMythicItem.class);
            result.uuid = UUID.randomUUID();
            nbt.when(() -> ItemUtil.getInternalName(stack)).thenReturn("mythic_leggings");
            utils.when(() -> Utils.getMythicItem0(stack, "mythic_leggings")).thenReturn(result);
            ItemFactory factory = new ItemFactory();
            for (String name : new String[]{"CoreEnchantRepairManager", "EnchantReplacementManager"}) {
                Class<?> type = loader.loadClass("cn.mx.thepitaddon.enchantment." + name);
                Object manager = mock(type);
                Method rebuild = type.getDeclaredMethod("rebuildItem", IItemFactory.class, ItemStack.class);
                rebuild.setAccessible(true);
                assertSame(result, rebuild.invoke(manager, factory, stack));
                assertSame(result, factory.getItem(result.uuid));
            }
        }
    }

    @Test
    public void eternalNightArenaCheckWorksInsteadOfSilentlyCatchingLinkageError() throws Exception {
        try (URLClassLoader loader = extension(); MockedStatic<ThePit> pit = mockStatic(ThePit.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            PitWorldConfig config = mock(PitWorldConfig.class);
            when(plugin.getPitConfig()).thenReturn(config);
            World world = mock(World.class);
            when(config.getPitLocA()).thenReturn(new Location(world, -5, 0, -5));
            when(config.getPitLocB()).thenReturn(new Location(world, 5, 100, 5));
            Player player = mock(Player.class);
            when(player.isOnline()).thenReturn(true);
            when(player.getWorld()).thenReturn(world);
            Class<?> type = loader.loadClass("cn.mx.thepitaddon.enchantment.EternalNightEchoManager");
            Object manager = mock(type);
            Method check = type.getDeclaredMethod("isInCoreArena", Player.class);
            check.setAccessible(true);
            when(player.getLocation()).thenReturn(new Location(world, 0, 50, 0));
            assertEquals(false, check.invoke(manager, player));
            when(player.getLocation()).thenReturn(new Location(world, 30, 50, 30));
            assertEquals(true, check.invoke(manager, player));
            when(player.getWorld()).thenReturn(mock(World.class));
            assertEquals(false, check.invoke(manager, player));
        }
    }

    @Test
    public void extensionNormalAndGiftMultipliersChangeActualCoreDeposits() throws Exception {
        try (URLClassLoader loader = extension(); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            Class<?> mainType = loader.loadClass("cn.mx.thepitaddon.main.Main");
            Class<?> settingsType = loader.loadClass("cn.mx.thepitaddon.config.EnhancementSettings");
            Class<?> giftType = loader.loadClass("cn.mx.thepitaddon.feature.reward.GiftEventManager");
            Class<?> listenerType = loader.loadClass("cn.mx.thepitaddon.feature.RewardMultiplierListener");
            Object main = mock(mainType), settings = mock(settingsType), gift = mock(giftType);
            when(mainType.getMethod("getEnhancementSettings").invoke(main)).thenReturn(settings);
            when(settingsType.getMethod("isNormalRewardMultiplierEnabled").invoke(settings)).thenReturn(true);
            for (String name : new String[]{"getNormalKillCoinMultiplier", "getNormalAssistCoinMultiplier"}) {
                when(settingsType.getMethod(name).invoke(settings)).thenReturn(2.0D);
            }
            for (String name : new String[]{"getNormalKillExpMultiplier", "getNormalAssistExpMultiplier"}) {
                when(settingsType.getMethod(name).invoke(settings)).thenReturn(3.0D);
            }
            Object listener = listenerType.getConstructor(giftType).newInstance(gift);
            PluginManager manager = mock(PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            doAnswer(call -> {
                Object event = call.getArgument(0);
                listenerType.getMethod(event instanceof PitKillEvent ? "onKill" : "onAssist", event.getClass())
                        .invoke(listener, event);
                return null;
            }).when(manager).callEvent(any());
            Field instance = mainType.getField("instance");
            Object previous = instance.get(null);
            instance.set(null, main);
            try {
                Player player = mock(Player.class), victim = mock(Player.class);
                PlayerProfile profile = mock(PlayerProfile.class);
                when(profile.getLevel()).thenReturn(1);
                when(profile.getCoins()).thenReturn(100.0D);
                when(profile.getExperience()).thenReturn(200.0D);
                PitKillEvent kill = CombatListener.awardKillRewards(player, profile, victim, 20, 10);
                verify(profile).setCoins(140.0D);
                verify(profile).setExperience(230.0D);
                assertEquals(40.0D, kill.getCoins(), 0.0D);
                clearInvocations(profile);
                PitAssistEvent assist = CombatListener.awardAssistRewards(player, profile, victim, 8, 4);
                verify(profile).setCoins(116.0D);
                verify(profile).setExperience(212.0D);
                assertEquals(16.0D, assist.getCoins(), 0.0D);
                clearInvocations(profile);
                when(giftType.getMethod("isActive").invoke(gift)).thenReturn(true);
                when(giftType.getMethod("getActiveMultiplier").invoke(gift)).thenReturn(5.0D);
                CombatListener.awardKillRewards(player, profile, victim, 20, 10);
                verify(profile).setCoins(200.0D);
                verify(profile).setExperience(250.0D);
                clearInvocations(profile);
                when(profile.getLevel()).thenReturn(NewConfiguration.INSTANCE.getMaxLevel());
                PitKillEvent capped = CombatListener.awardKillRewards(player, profile, victim, 20, 10);
                assertEquals(0.0D, capped.getExp(), 0.0D);
                verify(profile, never()).setExperience(anyDouble());
            } finally {
                instance.set(null, previous);
            }
        }
    }
}
