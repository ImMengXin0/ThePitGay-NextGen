package net.mizukilab.pit.handlers;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PerkData;
import cn.charlotte.pit.perk.AbstractPerk;
import cn.charlotte.pit.perk.PerkFactory;
import net.mizukilab.pit.BukkitDataTestSupport;
import net.mizukilab.pit.enchantment.AbstractEnchantment;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import net.mizukilab.pit.enchantment.UnknownEnchantment;
import net.mizukilab.pit.item.type.mythic.MythicSwordItem;
import net.mizukilab.pit.item.IMythicItem;
import net.mizukilab.pit.park.Parker;
import net.mizukilab.pit.parm.listener.ITickTask;
import net.mizukilab.pit.util.PublicUtil;
import net.mizukilab.pit.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.Test;
import org.mockito.MockedStatic;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.Opcodes;
import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TickHandlerTest extends BukkitDataTestSupport {
    @Test
    public void unlockedPassiveTicksOnceAndLevelZeroDoesNotTick() throws Exception {
        try (MockedStatic<ThePit> pit = mockStatic(ThePit.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            PerkFactory perks = new PerkFactory();
            when(plugin.getPerkFactory()).thenReturn(perks);
            when(plugin.getEnchantmentFactor()).thenReturn(new EnchantmentFactor());
            AbstractPerk passive = mock(AbstractPerk.class);
            when(passive.isPassive()).thenReturn(true);
            ITickTask task = mock(ITickTask.class);
            when(task.loopTick(anyInt())).thenReturn(20);
            perks.getPerkMap().put("passive", passive);
            perks.getTickTasks().put(passive, task);
            PerkData data = new PerkData("passive", 1);
            PlayerProfile profile = mock(PlayerProfile.class);
            when(profile.getChosePerk()).thenReturn(Map.of(1, data));
            when(profile.getUnlockedPerkMap()).thenReturn(Map.of("passive", data));
            Player player = mock(Player.class);
            var method = TickHandler.class.getDeclaredMethod("tickPerks", Player.class, PlayerProfile.class, long.class);
            method.setAccessible(true);
            TickHandler ticker = new TickHandler();
            method.invoke(ticker, player, profile, 0L);
            verify(task).loopTick(1);
            verify(task, times(1)).handle(1, player);
            data.setLevel(0);
            method.invoke(ticker, player, profile, 0L);
            verifyNoMoreInteractions(task);
        }
    }

    @Test
    public void registryReplacementRefreshesOnlineHeldAndLeggingsCachesOnlyWhenChanged() throws Exception {
        Player player = mock(Player.class);
        // Bukkit 1.8 has return-only overloads that Mockito's method matching cannot
        // reliably distinguish. Use the real Bukkit facade and an exact-descriptor server stub.
        Server server = onlinePlayerServer(player);
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previousServer = serverField.get(null);
        serverField.set(null, server);
        try (MockedStatic<ThePit> pit = mockStatic(ThePit.class);
             MockedStatic<PlayerProfile> profiles = mockStatic(PlayerProfile.class);
             MockedStatic<Utils> utils = mockStatic(Utils.class);
             MockedStatic<net.mizukilab.pit.util.item.ItemUtil> itemUtil = mockStatic(net.mizukilab.pit.util.item.ItemUtil.class)) {
            ThePit plugin = mock(ThePit.class, RETURNS_DEEP_STUBS);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            EnchantmentFactor factor = new EnchantmentFactor();
            when(plugin.getEnchantmentFactor()).thenReturn(factor);
            when(plugin.getPerkFactory()).thenReturn(new PerkFactory());
            when(plugin.getParker()).thenReturn(mock(Parker.class));
            UUID uuid = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(uuid);
            PlayerProfile profile = mock(PlayerProfile.class);
            profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(uuid)).thenReturn(profile);
            when(profile.isLoaded()).thenReturn(true);
            PlayerInventory inventory = mock(PlayerInventory.class);
            when(player.getInventory()).thenReturn(inventory);
            ItemStack sword = mock(ItemStack.class), leggings = mock(ItemStack.class);
            when(sword.getType()).thenReturn(Material.GOLD_SWORD);
            when(sword.clone()).thenReturn(sword);
            when(leggings.clone()).thenReturn(leggings);
            when(inventory.getItemInHand()).thenReturn(sword);
            when(inventory.getLeggings()).thenReturn(leggings);
            IMythicItem firstSword = spy(new MythicSwordItem()), nextSword = spy(new MythicSwordItem());
            IMythicItem firstLeggings = spy(new MythicSwordItem()), nextLeggings = spy(new MythicSwordItem());
            for (IMythicItem parsed : List.of(firstSword, nextSword))
                doReturn(true).when(parsed).matchesEnchantmentSource(sword);
            for (IMythicItem parsed : List.of(firstLeggings, nextLeggings))
                doReturn(true).when(parsed).matchesEnchantmentSource(leggings);
            utils.when(() -> Utils.getMythicItem(sword)).thenReturn(firstSword, nextSword);
            utils.when(() -> Utils.getMythicItem(leggings)).thenReturn(firstLeggings, nextLeggings);
            TickHandler ticker = new TickHandler();
            ticker.run();
            assertSame(firstSword, profile.heldItem);
            assertSame(firstLeggings, profile.leggings);
            ticker.run();
            utils.verify(() -> Utils.getMythicItem(sword), times(1));
            utils.verify(() -> Utils.getMythicItem(leggings), times(1));
            factor.registerEnchantment(new UnknownEnchantment("replacement"));
            ticker.run();
            assertSame(nextSword, profile.heldItem);
            assertSame(nextLeggings, profile.leggings);
            ticker.run();
            utils.verify(() -> Utils.getMythicItem(sword), times(2));
            utils.verify(() -> Utils.getMythicItem(leggings), times(2));
            verify(profile, times(4)).onTick(player);
            when(profile.isLoaded()).thenReturn(false);
            ticker.run();
            verify(profile, times(4)).onTick(player);
        } finally {
            serverField.set(null, previousServer);
        }
    }

    private static Server onlinePlayerServer(Player... players) throws Exception {
        String name = "net/mizukilab/pit/handlers/OnlinePlayerServerStub";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, name, null,
                "java/lang/Object", new String[]{"org/bukkit/Server"});
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        for (var field : Map.of("players", "Ljava/util/Collection;",
                "legacyPlayers", "[Lorg/bukkit/entity/Player;").entrySet()) {
            writer.visitField(Opcodes.ACC_PUBLIC, field.getKey(), field.getValue(), null, null).visitEnd();
            var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "getOnlinePlayers", "()" + field.getValue(), null, null);
            method.visitCode();
            method.visitVarInsn(Opcodes.ALOAD, 0);
            method.visitFieldInsn(Opcodes.GETFIELD, name, field.getKey(), field.getValue());
            method.visitInsn(Opcodes.ARETURN);
            method.visitMaxs(1, 1);
            method.visitEnd();
        }
        writer.visitEnd();
        byte[] bytes = writer.toByteArray();
        Class<?> type = new ClassLoader(Server.class.getClassLoader()) {
            Class<?> define() { return defineClass(null, bytes, 0, bytes.length); }
        }.define();
        Object server = type.getConstructor().newInstance();
        type.getField("players").set(server, List.of(players));
        type.getField("legacyPlayers").set(server, players);
        return (Server) server;
    }

    @Test
    public void looksUpEnchantmentObjectAndDoesNotSkipLaterTasks() {
        try (MockedStatic<ThePit> pit = mockStatic(ThePit.class); MockedStatic<Utils> utils = mockStatic(Utils.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            EnchantmentFactor factor = new EnchantmentFactor();
            when(plugin.getEnchantmentFactor()).thenReturn(factor);
            when(plugin.getPerkFactory()).thenReturn(mock(PerkFactory.class));
            MythicSwordItem item = new MythicSwordItem();
            item.getEnchantments().put(new UnknownEnchantment("first"), 2);
            item.getEnchantments().put(new UnknownEnchantment("second"), 1);
            var entries = item.getEnchantments().object2IntEntrySet().iterator();
            AbstractEnchantment disabledEnchant = entries.next().getKey();
            var activeEntry = entries.next();
            AbstractEnchantment activeEnchant = activeEntry.getKey();
            int level = activeEntry.getIntValue();
            ITickTask disabled = mock(ITickTask.class);
            ITickTask active = mock(ITickTask.class);
            when(disabled.loopTick(anyInt())).thenReturn(PublicUtil.TICK_OFF_MAGIC_CODE);
            when(active.loopTick(level)).thenReturn(20);
            factor.getTickTasks().put(disabledEnchant, disabled);
            factor.getTickTasks().put(activeEnchant, active);
            ItemStack stack = mock(ItemStack.class);
            Player player = mock(Player.class);
            utils.when(() -> Utils.getMythicItem(stack)).thenReturn(item);
            utils.when(() -> Utils.shouldTick(20L, 20)).thenReturn(true);
            new TickHandler().handleIMythicItemTickTasks(stack, player, 20L);
            verify(active).handle(level, player);
            verify(disabled, never()).handle(anyInt(), any());
        }
    }

    @Test public void throwingEnchantmentDoesNotSkipLaterEffects() {
        try(MockedStatic<ThePit> pit=mockStatic(ThePit.class); MockedStatic<Utils> utils=mockStatic(Utils.class)) {
            ThePit plugin=mock(ThePit.class); pit.when(ThePit::getInstance).thenReturn(plugin);
            when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
            EnchantmentFactor factor=new EnchantmentFactor(); when(plugin.getEnchantmentFactor()).thenReturn(factor);
            when(plugin.getPerkFactory()).thenReturn(new PerkFactory());
            MythicSwordItem item=new MythicSwordItem(); item.getEnchantments().put(new UnknownEnchantment("bad"),1); item.getEnchantments().put(new UnknownEnchantment("good"),1);
            var entries=item.getEnchantments().object2IntEntrySet().iterator(); var bad=entries.next().getKey(); var good=entries.next().getKey();
            ITickTask broken=mock(ITickTask.class), working=mock(ITickTask.class);
            when(broken.loopTick(1)).thenReturn(1); when(working.loopTick(1)).thenReturn(1);
            doThrow(new NoSuchMethodError("old extension")).when(broken).handle(anyInt(),any());
            factor.getTickTasks().put(bad,broken);factor.getTickTasks().put(good,working);
            ItemStack stack=mock(ItemStack.class); Player player=mock(Player.class);
            utils.when(() -> Utils.getMythicItem(stack)).thenReturn(item);utils.when(() -> Utils.shouldTick(1L,1)).thenReturn(true);
            new TickHandler().handleIMythicItemTickTasks(stack,player,1L);verify(working).handle(1,player);
        }
    }
    @Test public void emptyHandClearsTheOldParsedWeapon() throws Exception {
        try(MockedStatic<ThePit> pit=mockStatic(ThePit.class)) {
            ThePit plugin=mock(ThePit.class);pit.when(ThePit::getInstance).thenReturn(plugin);
            when(plugin.getEnchantmentFactor()).thenReturn(new EnchantmentFactor());when(plugin.getPerkFactory()).thenReturn(new PerkFactory());
            PlayerProfile profile=mock(PlayerProfile.class);profile.heldItem=new MythicSwordItem();profile.heldItemStack=mock(ItemStack.class);
            var method=TickHandler.class.getDeclaredMethod("tickItemInHand",Player.class,PlayerInventory.class,PlayerProfile.class,long.class);method.setAccessible(true);
            method.invoke(new TickHandler(),mock(Player.class),mock(PlayerInventory.class),profile,1L);
            assertNull(profile.heldItem);assertNull(profile.heldItemStack);
        }
    }

    @Test public void brokenPlayerAndMapDoNotStopOtherPlayersOrTheGlobalClock() throws Exception {
        Player first = mock(Player.class), second = mock(Player.class);
        when(first.getUniqueId()).thenReturn(UUID.randomUUID());
        when(second.getUniqueId()).thenReturn(UUID.randomUUID());
        when(second.getInventory()).thenReturn(mock(PlayerInventory.class));
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previousServer = serverField.get(null);
        serverField.set(null, onlinePlayerServer(first, second));
        try (MockedStatic<ThePit> pit = mockStatic(ThePit.class);
             MockedStatic<PlayerProfile> profiles = mockStatic(PlayerProfile.class)) {
            ThePit plugin = mock(ThePit.class, RETURNS_DEEP_STUBS);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            when(plugin.getEnchantmentFactor()).thenReturn(new EnchantmentFactor());
            when(plugin.getPerkFactory()).thenReturn(new PerkFactory());
            Parker parker = mock(Parker.class); when(plugin.getParker()).thenReturn(parker);
            PlayerProfile bad = mock(PlayerProfile.class), good = mock(PlayerProfile.class);
            when(bad.isLoaded()).thenReturn(true); when(good.isLoaded()).thenReturn(true);
            profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(first.getUniqueId())).thenReturn(bad);
            profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(second.getUniqueId())).thenReturn(good);
            doThrow(new IllegalStateException("broken profile")).when(bad).onTick(first);
            var mapSelector = plugin.getMapSelector();
            doThrow(new NoSuchMethodError("broken map callback")).when(mapSelector).tick();
            TickHandler ticker = new TickHandler(); ticker.run(); ticker.run();
            verify(good, times(2)).onTick(second); verify(parker, times(2)).tick();
            var clock = TickHandler.class.getDeclaredField("tick"); clock.setAccessible(true);
            assertEquals(2, clock.getInt(ticker));
            verify(plugin.getLogger(), times(2)).log(eq(java.util.logging.Level.WARNING), anyString(), any(Throwable.class));
        } finally { serverField.set(null, previousServer); }
    }

    @Test public void brokenPerkDoesNotSkipTheNextPerk() throws Exception {
        try (MockedStatic<ThePit> pit = mockStatic(ThePit.class)) {
            ThePit plugin = mock(ThePit.class, RETURNS_DEEP_STUBS);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            when(plugin.getEnchantmentFactor()).thenReturn(new EnchantmentFactor());
            PerkFactory perks = new PerkFactory(); when(plugin.getPerkFactory()).thenReturn(perks);
            AbstractPerk bad = mock(AbstractPerk.class), good = mock(AbstractPerk.class);
            ITickTask broken = mock(ITickTask.class), working = mock(ITickTask.class);
            when(broken.loopTick(0)).thenThrow(new NoSuchMethodError("legacy callback"));
            when(working.loopTick(0)).thenReturn(1);
            perks.getPerkMap().put("bad", bad); perks.getPerkMap().put("good", good);
            perks.getTickTasks().put(bad, broken); perks.getTickTasks().put(good, working);
            var selected = new java.util.LinkedHashMap<Integer, PerkData>();
            selected.put(1, new PerkData("bad", 0)); selected.put(2, new PerkData("good", 0));
            PlayerProfile profile = mock(PlayerProfile.class); when(profile.getChosePerk()).thenReturn(selected);
            Player player = mock(Player.class);
            var method = TickHandler.class.getDeclaredMethod("tickPerks", Player.class, PlayerProfile.class, long.class);
            method.setAccessible(true); method.invoke(new TickHandler(), player, profile, 0L);
            verify(working).handle(0, player);
        }
    }
}
