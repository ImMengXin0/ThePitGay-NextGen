package net.mizukilab.pit.listener;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.LeaderBoardEntry;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PerkData;
import cn.charlotte.pit.event.PitKillEvent;
import cn.charlotte.pit.event.PitProfileLoadedEvent;
import cn.charlotte.pit.perk.AbstractPerk;
import cn.charlotte.pit.perk.PerkFactory;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.mizukilab.pit.BukkitDataTestSupport;
import net.mizukilab.pit.enchantment.AbstractEnchantment;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import net.mizukilab.pit.enchantment.UnknownEnchantment;
import net.mizukilab.pit.enchantment.param.item.BowOnly;
import net.mizukilab.pit.handlers.TickHandler;
import net.mizukilab.pit.parm.listener.ITickTask;
import net.mizukilab.pit.item.IMythicItem;
import net.mizukilab.pit.item.factory.ItemFactory;
import net.mizukilab.pit.item.type.mythic.MythicBowItem;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.Utils;
import net.mizukilab.pit.util.item.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;
import org.junit.Assume;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.UUID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AddonBinaryIntegrationTest extends BukkitDataTestSupport {
    private URLClassLoader addon() throws Exception {
        Path server = Path.of(System.getProperty("pitAddonJar"));
        Path loader = Path.of(System.getProperty("pitAddonLoaderJar"));
        Assume.assumeTrue("Local Addon fixtures unavailable", Files.isRegularFile(server) && Files.isRegularFile(loader));
        return new URLClassLoader(new URL[]{server.toUri().toURL(), loader.toUri().toURL(),
                Path.of("build/libs/ThePitGay-V.jar").toUri().toURL()}, getClass().getClassLoader());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void bothLeaderboardReturnDescriptorsStayLinkableAndPublishOrderedSnapshots() throws Throwable {
        var oldGetter = MethodHandles.publicLookup().findStatic(LeaderBoardEntry.class, "getLeaderBoardEntries",
                MethodType.methodType(List.class));
        var newGetter = MethodHandles.publicLookup().findStatic(LeaderBoardEntry.class, "getLeaderBoardEntries",
                MethodType.methodType(Object2ObjectLinkedOpenHashMap.class));
        var before = LeaderBoardEntry.getLeaderBoardEntries();
        try {
            LeaderBoardEntry first = new LeaderBoardEntry("First", UUID.randomUUID(), 1, 500, 5);
            LeaderBoardEntry second = new LeaderBoardEntry("Second", UUID.randomUUID(), 2, 400, 4);
            var entries = new Object2ObjectLinkedOpenHashMap<UUID, LeaderBoardEntry>();
            entries.put(first.getUuid(), first);
            entries.put(second.getUuid(), second);
            LeaderBoardEntry.setLeaderBoardEntries(entries);
            List<LeaderBoardEntry> snapshot = (List<LeaderBoardEntry>) oldGetter.invokeExact();
            assertEquals(List.of(first, second), snapshot);
            assertSame(snapshot, (List<LeaderBoardEntry>) oldGetter.invokeExact());
            assertSame(entries, (Object2ObjectLinkedOpenHashMap<UUID, LeaderBoardEntry>) newGetter.invokeExact());
            LeaderBoardEntry.setLeaderBoardEntries(List.of(second));
            assertEquals(List.of(first, second), snapshot);
            assertEquals(List.of(second), (List<LeaderBoardEntry>) oldGetter.invokeExact());
        } finally {
            LeaderBoardEntry.setLeaderBoardEntries(before);
        }
    }

    @BowOnly
    public static class BowEnchant extends UnknownEnchantment {
        public BowEnchant() { super("addon_test_bow"); }
    }

    @Test
    public void actualAddonAdminButtonEditsBowAndInvalidatesItsCache() throws Exception {
        try (URLClassLoader loader = addon(); MockedStatic<ThePit> pit = mockStatic(ThePit.class);
             MockedStatic<ItemUtil> nbt = mockStatic(ItemUtil.class); MockedStatic<Utils> utils = mockStatic(Utils.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            ItemFactory factory = spy(new ItemFactory());
            when(plugin.getItemFactory()).thenReturn(factory);
            ItemStack oldStack = new ItemStack(Material.BOW), newStack = new ItemStack(Material.BOW);
            Player player = mock(Player.class);
            when(player.getItemInHand()).thenReturn(oldStack);
            when(player.getName()).thenReturn("Tester");
            MythicBowItem bow = mock(MythicBowItem.class);
            bow.uuid = UUID.randomUUID();
            when(bow.toItemStack()).thenReturn(newStack);
            when(bow.getEnchantments()).thenReturn(new Object2IntOpenHashMap<>());
            when(bow.getEnchantmentRecords()).thenReturn(new ArrayList<>());
            nbt.when(() -> ItemUtil.getInternalName(oldStack)).thenReturn("mythic_bow");
            nbt.when(() -> ItemUtil.getUUIDObj(oldStack)).thenReturn(bow.uuid);
            utils.when(() -> Utils.getMythicItem0(oldStack, "mythic_bow")).thenReturn(bow);
            factory.getIMythicItem0(oldStack, "mythic_bow", true);
            doReturn(bow).when(factory).getIMythicItem(oldStack);
            AbstractEnchantment enchant = new BowEnchant();
            Class<?> type = loader.loadClass("cn.apnf.mistral.button.MyEnchantmentsButton");
            Object button = type.getConstructor(AbstractEnchantment.class).newInstance(enchant);
            type.getMethod("clicked", Player.class, int.class, ClickType.class, int.class, ItemStack.class)
                    .invoke(button, player, 0, ClickType.LEFT, -1, null);
            assertEquals(1, bow.getEnchantments().getInt(enchant));
            assertEquals(1, bow.getEnchantmentRecords().size());
            verify(player).setItemInHand(newStack);
            assertNull(factory.getItem(bow.uuid));
            nbt.verify(() -> ItemUtil.setUUID(any(ItemStack.class), anyString()), never());
        }
    }

    @Test
    public void addonPlutocratUsesOneListenerAndMultipliesActualRewardOnce() throws Exception {
        try (URLClassLoader loader = addon(); MockedStatic<ThePit> pit = mockStatic(ThePit.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class); MockedStatic<PlayerUtil> util = mockStatic(PlayerUtil.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            Plugin owner = mock(Plugin.class);
            PluginManager manager = mock(PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            Class<?> type = loader.loadClass("cn.apnf.mistral.enchantments.rage_rare.Plutocrat");
            AbstractEnchantment separateListener = (AbstractEnchantment) type.getConstructor().newInstance();
            AbstractEnchantment canonical = (AbstractEnchantment) type.getConstructor().newInstance();
            HandlerList handlers = PitKillEvent.getHandlerList();
            doAnswer(call -> {
                Listener listener = call.getArgument(0);
                Plugin owningPlugin = call.getArgument(1);
                handlers.register(new RegisteredListener(listener, (ignored, event) -> {
                    try { type.getMethod("onKill", PitKillEvent.class).invoke(listener, event); }
                    catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                }, EventPriority.NORMAL, owningPlugin, false));
                return null;
            }).when(manager).registerEvents(any(), any());
            doAnswer(call -> {
                for (RegisteredListener registered : handlers.getRegisteredListeners()) registered.callEvent(call.getArgument(0));
                return null;
            }).when(manager).callEvent(any());
            EnchantmentFactor factor = new EnchantmentFactor();
            try {
                manager.registerEvents((Listener) separateListener, owner);
                factor.registerEnchantment(canonical);
                assertEquals(1, handlers.getRegisteredListeners().length);
                assertSame(canonical, handlers.getRegisteredListeners()[0].getListener());
                assertSame(owner, handlers.getRegisteredListeners()[0].getPlugin());
                Player killer = mock(Player.class), victim = mock(Player.class);
                EntityEquipment equipment = mock(EntityEquipment.class);
                when(killer.getEquipment()).thenReturn(equipment);
                ItemStack stack = new ItemStack(Material.LEATHER_LEGGINGS);
                when(equipment.getLeggings()).thenReturn(stack);
                ItemFactory factory = mock(ItemFactory.class);
                when(plugin.getItemFactory()).thenReturn(factory);
                IMythicItem item = mock(IMythicItem.class);
                var enchants = new Object2IntOpenHashMap<AbstractEnchantment>();
                enchants.put(canonical, 3);
                when(item.getEnchantments()).thenReturn(enchants);
                when(factory.getItemFromStack(stack)).thenReturn(item);
                PlayerProfile profile = mock(PlayerProfile.class);
                when(profile.getLevel()).thenReturn(1);
                CombatListener.awardKillRewards(killer, profile, victim, 10, 10);
                verify(profile).setCoins(20.0D); // two listener copies would award 40
                factor.removeEnchantment(canonical);
                assertEquals(0, handlers.getRegisteredListeners().length);
            } finally {
                HandlerList.unregisterAll(owner);
                HandlerList.unregisterAll((Listener) canonical);
                HandlerList.unregisterAll((Listener) separateListener);
            }
        }
    }

    @Test
    public void addonTemporaryPerkFactoryRegistersAllowedPerksInLiveCoreAndReloadIsIdempotent() throws Exception {
        try (URLClassLoader loader = addon(); MockedStatic<ThePit> pit = mockStatic(ThePit.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            PerkFactory live = new PerkFactory();
            when(plugin.getPerkFactory()).thenReturn(live);
            Class<?> type = loader.loadClass("cn.apnf.mistral.perk.MegaStreak.SeaOfTime");
            PerkFactory temporary = new PerkFactory();
            temporary.init(List.of(type));
            AbstractPerk first = live.getPerks().get(0);
            assertNotNull(first);
            assertSame(first, live.getTickTasks().values().iterator().next());
            assertSame(live.getPerks(), temporary.getPerks());
            temporary.init(List.of(type));
            AbstractPerk current = live.getPerkMap().get(first.getInternalPerkName());
            assertNotSame(first, current);
            assertEquals(1, live.getPerks().size());
            assertEquals(1, live.getAttackEntities().size());
            assertSame(current, live.getTickTasks().values().iterator().next());
            live.removePerk(first);
            assertSame(current, live.getPerkMap().get(first.getInternalPerkName()));
        }
    }

    @Test
    public void crazyDogIsRejectedByTemporaryDirectAndRepeatedAddonRegistration() throws Exception {
        try (URLClassLoader loader = addon(); MockedStatic<ThePit> pit = mockStatic(ThePit.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            PluginManager plugins = mock(PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            PerkFactory live = new PerkFactory();
            when(plugin.getPerkFactory()).thenReturn(live);
            String legacyClass = "cn.apnf.mistral.streak.CrazyDogStreakMode";
            Assume.assumeTrue("This Addon build has already removed the retired CrazyDog class",
                    loader.getResource(legacyClass.replace('.', '/') + ".class") != null);
            Class<?> type = loader.loadClass(legacyClass);
            PerkFactory temporary = new PerkFactory();
            for (int attempt = 0; attempt < 2; attempt++) {
                temporary.init(List.of(type));
                live.registerPerk((AbstractPerk) type.getConstructor().newInstance());
            }
            assertTrue(live.getPerks().isEmpty());
            assertTrue(live.getPerkMap().isEmpty());
            assertTrue(live.getTickTasks().isEmpty());
            assertTrue(live.getAttackEntities().isEmpty());
            assertTrue(live.getPlayerShootEntities().isEmpty());
            assertTrue(live.getPlayerKilledEntities().isEmpty());
            assertTrue(live.getPlayerBeKilledByEntities().isEmpty());
            assertSame(live.getPerks(), temporary.getPerks());
            PerkData legacy = new PerkData("crazy_dog_mode_mega_streak", 0);
            assertNull(legacy.getHandle(live.getPerkMap()));
            assertNull(legacy.getITickTask(live.getPerkMap(), live.getTickTasks()));
            verifyNoInteractions(plugins);
        }
    }

    @Test
    public void profileLoadClearsOnlyTheRetiredMegaStreakSelection() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            PlayerProfile profile = mock(PlayerProfile.class);
            var chosen = new HashMap<Integer, PerkData>();
            var bought = new HashMap<String, PerkData>();
            var unlocked = new HashMap<String, PerkData>();
            PerkData dog = new PerkData("crazy_dog_mode_mega_streak", 0);
            PerkData normal = new PerkData("golden_head", 1);
            chosen.put(1, normal);
            chosen.put(5, dog);
            bought.put(dog.getPerkInternalName(), dog);
            unlocked.put(dog.getPerkInternalName(), dog);
            when(profile.getChosePerk()).thenReturn(chosen);
            when(profile.getBoughtPerkMap()).thenReturn(bought);
            when(profile.getUnlockedPerkMap()).thenReturn(unlocked);
            PlayerMarco listener = new PlayerMarco();
            listener.onProfileLoadComplete(new PitProfileLoadedEvent(profile, null));
            assertNull(chosen.get(5));
            assertSame(normal, chosen.get(1));
            assertSame(dog, bought.get(dog.getPerkInternalName()));
            assertSame(dog, unlocked.get(dog.getPerkInternalName()));
            PerkData allowed = new PerkData("over_drive_mega_streak", 0);
            chosen.put(5, allowed);
            listener.onProfileLoadComplete(new PitProfileLoadedEvent(profile, null));
            assertSame(allowed, chosen.get(5));
        }
    }

    @Test
    public void actualAddonMegaStreaksTickOnlyWhenSelectedAndUseReplacementInstances() throws Exception {
        try (URLClassLoader loader = addon(); MockedStatic<ThePit> pit = mockStatic(ThePit.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            PerkFactory live = new PerkFactory();
            when(plugin.getPerkFactory()).thenReturn(live);
            when(plugin.getEnchantmentFactor()).thenReturn(new EnchantmentFactor());
            var tickPerks = TickHandler.class.getDeclaredMethod("tickPerks", Player.class, PlayerProfile.class, long.class);
            tickPerks.setAccessible(true);
            TickHandler ticker = new TickHandler();
            Player player = mock(Player.class);
            PlayerProfile profile = mock(PlayerProfile.class);
            var chosen = new HashMap<Integer, PerkData>();
            var unlocked = new HashMap<String, PerkData>();
            when(profile.getChosePerk()).thenReturn(chosen);
            when(profile.getUnlockedPerkMap()).thenReturn(unlocked);
            for (String className : List.of("SeaOfTime", "SuperPeak")) {
                Class<?> type = loader.loadClass("cn.apnf.mistral.perk.MegaStreak." + className);
                AbstractPerk first = spy((AbstractPerk) type.getConstructor().newInstance());
                AbstractPerk replacement = spy((AbstractPerk) type.getConstructor().newInstance());
                ITickTask firstTask = (ITickTask) first, replacementTask = (ITickTask) replacement;
                // Test real registration/dispatch, isolating only the server-owned gameplay effects.
                doNothing().when(firstTask).handle(anyInt(), any());
                doNothing().when(replacementTask).handle(anyInt(), any());
                try {
                    live.registerPerk(first);
                    PerkData data = new PerkData(first.getInternalPerkName(), 1);
                    unlocked.put(data.getPerkInternalName(), data);
                    tickPerks.invoke(ticker, player, profile, 0L);
                    verify(firstTask, never()).handle(anyInt(), any());
                    chosen.put(5, data);
                    tickPerks.invoke(ticker, player, profile, 0L);
                    verify(firstTask, times(1)).handle(1, player);
                    assertSame(firstTask, data.getITickTask(live.getPerkMap(), live.getTickTasks()));
                    live.registerPerk(replacement);
                    assertSame(replacement, data.getHandle(live.getPerkMap()));
                    assertSame(replacementTask, data.getITickTask(live.getPerkMap(), live.getTickTasks()));
                    tickPerks.invoke(ticker, player, profile, 0L);
                    verify(firstTask, times(1)).handle(1, player);
                    verify(replacementTask, times(1)).handle(1, player);
                    live.removePerk(replacement);
                    assertNull(data.getHandle(live.getPerkMap()));
                    assertNull(data.getITickTask(live.getPerkMap(), live.getTickTasks()));
                    tickPerks.invoke(ticker, player, profile, 0L);
                    verify(replacementTask, times(1)).handle(1, player);
                } finally {
                    live.removePerk(first);
                    live.removePerk(replacement);
                    chosen.clear();
                    unlocked.clear();
                }
            }
        }
    }
}
