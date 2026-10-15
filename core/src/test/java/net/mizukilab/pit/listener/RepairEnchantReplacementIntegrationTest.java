package net.mizukilab.pit.listener;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.perk.PerkFactory;
import com.google.common.util.concurrent.AtomicDouble;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.server.v1_8_R3.AttributeModifier;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.mizukilab.pit.BukkitDataTestSupport;
import net.mizukilab.pit.config.PitWorldConfig;
import net.mizukilab.pit.enchantment.AbstractEnchantment;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import net.mizukilab.pit.enchantment.UnknownEnchantment;
import net.mizukilab.pit.enchantment.type.rare.SoulEarterEnchant;
import net.mizukilab.pit.item.IMythicItem;
import net.mizukilab.pit.item.factory.ItemFactory;
import net.mizukilab.pit.parm.listener.IPlayerKilledEntity;
import net.mizukilab.pit.parm.listener.ITickTask;
import net.mizukilab.pit.parm.listener.IItemDamage;
import net.mizukilab.pit.parm.listener.IPlayerRespawn;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.Utils;
import net.mizukilab.pit.util.chat.TitleUtil;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Arrow;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.plugin.PluginManager;
import spg.lgdev.iSpigot;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.Test;
import org.junit.Assume;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Exercises the supplied 3.8.8 replacement transaction and callback wrapper bytecode. */
public class RepairEnchantReplacementIntegrationTest extends BukkitDataTestSupport {
    private URLClassLoader extension() throws Exception {
        Path jar = Path.of(System.getProperty("repairExtendJar"));
        Assume.assumeTrue(Files.isRegularFile(jar));
        return new URLClassLoader(new URL[]{jar.toUri().toURL(),
                Path.of("build/libs/ThePitGay-V.jar").toUri().toURL()}, getClass().getClassLoader());
    }

    @Test
    public void actualSoulEaterTransactionInvalidatesCachedItemsAndClassIndex() throws Exception {
        try (URLClassLoader loader = extension(); MockedStatic<ThePit> pit = mockStatic(ThePit.class);
             MockedStatic<Utils> utils = mockStatic(Utils.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            EnchantmentFactor factor = new EnchantmentFactor();
            when(plugin.getEnchantmentFactor()).thenReturn(factor);
            SoulEarterEnchant original = new SoulEarterEnchant();
            factor.registerEnchantment(original);
            assertSame(original, factor.getEnchByClass(original.getClass()));
            ItemFactory items = new ItemFactory();
            ItemStack stack = mock(ItemStack.class);
            IMythicItem cached = mock(IMythicItem.class);
            cached.uuid = UUID.randomUUID();
            utils.when(() -> Utils.getMythicItem0(stack, "mythic_bow")).thenReturn(cached);
            items.getIMythicItem0(stack, "mythic_bow", true);
            long before = factor.getRevision();
            AbstractEnchantment replacement = (AbstractEnchantment) loader.loadClass(
                    "cn.mx.thepitaddon.enchantment.CoreEnchantReplacements$SoulEater").getConstructor().newInstance();
            Method transition = loader.loadClass("cn.mx.thepitaddon.enchantment.CoreEnchantRepairManager")
                    .getDeclaredMethod("transitionSoulEaterRegistry", EnchantmentFactor.class, AbstractEnchantment.class);
            transition.setAccessible(true);
            transition.invoke(null, factor, replacement);
            assertTrue("Direct writes in the real extension transaction must advance the core revision", factor.getRevision() > before);
            assertNull(items.getItem(cached.uuid));
            assertNull(factor.getEnchByClass(original.getClass()));
            assertSame(replacement, factor.getEnchByClass(replacement.getClass()));
            assertSame(replacement, factor.getAttackEntities().get(0));
            assertSame(replacement, factor.getPlayerShootEntities().get(0));
            transition.invoke(null, factor, original);
            assertSame(original, factor.getEnchByClass(original.getClass()));
            assertNull(factor.getEnchByClass(replacement.getClass()));
        }
    }

    public static class KillTickEnchant extends UnknownEnchantment implements IPlayerKilledEntity, ITickTask {
        public KillTickEnchant() { super("wrapped_replacement"); }
        public void handlePlayerKilled(int level, Player owner, Entity target, AtomicDouble coins, AtomicDouble xp) { }
        public void handle(int level, Player player) { }
        public int loopTick(int level) { return 20; }
    }

    private AbstractEnchantment wrapper(URLClassLoader loader, AbstractEnchantment delegate) throws Exception {
        Class<?> guard = loader.loadClass("cn.mx.thepitaddon.enchantment.AddonSilenceGuard");
        var guardConstructor = guard.getDeclaredConstructors()[0];
        guardConstructor.setAccessible(true);
        Object realGuard = guardConstructor.newInstance(new Object[]{null});
        var constructor = loader.loadClass("cn.mx.thepitaddon.enchantment.AddonSilenceCallbacks")
                .getDeclaredConstructor(AbstractEnchantment.class, guard);
        constructor.setAccessible(true);
        return (AbstractEnchantment) constructor.newInstance(delegate, realGuard);
    }

    @Test
    public void replacingAnEnchantImmediatelyRemovesItsActualSilenceWrapper() throws Exception {
        try (URLClassLoader loader = extension()) {
            EnchantmentFactor factor = new EnchantmentFactor();
            KillTickEnchant original = new KillTickEnchant(), replacement = new KillTickEnchant();
            factor.registerEnchantment(original);
            AbstractEnchantment wrapped = wrapper(loader, original);
            factor.getPlayerKilledEntities().set(0, (IPlayerKilledEntity) wrapped);
            factor.getTickTasks().put(original, (ITickTask) wrapped);
            factor.registerEnchantment(replacement);
            assertEquals("Old wrapper must not wait for the extension maintenance tick", 1, factor.getPlayerKilledEntities().size());
            assertSame(replacement, factor.getPlayerKilledEntities().get(0));
            assertSame(replacement, factor.getTickTasks().keySet().iterator().next());
            AbstractEnchantment nextWrapper = wrapper(loader, replacement);
            factor.getPlayerKilledEntities().set(0, (IPlayerKilledEntity) nextWrapper);
            factor.getTickTasks().put(replacement, (ITickTask) nextWrapper);
            factor.removeEnchantment(original);
            assertSame(nextWrapper, factor.getPlayerKilledEntities().get(0));
            factor.removeEnchantment(replacement);
            assertTrue(factor.getPlayerKilledEntities().isEmpty());
            assertTrue(factor.getTickTasks().isEmpty());
        }
    }

    @Test
    public void anArrowFiredBeforeReplacementUsesTheNewSprintDrainWithoutDowngradingSpeed() throws Exception {
        try (URLClassLoader loader = extension(); MockedStatic<ThePit> pit = mockStatic(ThePit.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            EnchantmentFactor factor = new EnchantmentFactor();
            when(plugin.getEnchantmentFactor()).thenReturn(factor);
            AbstractEnchantment old = new UnknownEnchantment("sprint_drain_enchant");
            Object2IntOpenHashMap<AbstractEnchantment> arrowEnchantments = new Object2IntOpenHashMap<>();
            arrowEnchantments.put(old, 3);
            AbstractEnchantment replacement = (AbstractEnchantment) loader.loadClass(
                    "cn.mx.thepitaddon.enchantment.CoreEnchantReplacements$SprintDrain").getConstructor().newInstance();
            factor.registerEnchantment(replacement);
            Player owner = mock(Player.class), victim = mock(Player.class);
            when(owner.getActivePotionEffects()).thenReturn(java.util.List.of(new PotionEffect(PotionEffectType.SPEED, 100, 3)));
            EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(mock(Arrow.class), victim,
                    EntityDamageEvent.DamageCause.PROJECTILE, 4D);
            Method shot = GameEffectListener.class.getDeclaredMethod("processShot", boolean.class,
                    EntityDamageByEntityEvent.class, Object2IntOpenHashMap.class, Player.class,
                    AtomicDouble.class, AtomicDouble.class, AtomicBoolean.class);
            shot.setAccessible(true);
            shot.invoke(new GameEffectListener(), false, hit, arrowEnchantments, owner,
                    new AtomicDouble(4), new AtomicDouble(1), new AtomicBoolean());
            verify(victim).addPotionEffect(argThat(effect -> effect.getType().equals(PotionEffectType.SLOW)
                    && effect.getDuration() == 60), eq(false));
            verify(owner, never()).addPotionEffect(any(PotionEffect.class), anyBoolean());
            assertSame(old, arrowEnchantments.keySet().iterator().next());
            factor.removeEnchantment(replacement);
            clearInvocations(victim);
            shot.invoke(new GameEffectListener(), false, hit, arrowEnchantments, owner,
                    new AtomicDouble(4), new AtomicDouble(1), new AtomicBoolean());
            verifyNoInteractions(victim);
        }
    }

    public static class DurabilityEnchant extends UnknownEnchantment implements IItemDamage, IPlayerRespawn {
        int durabilityCalls, respawnCalls;
        public DurabilityEnchant() { super("silenced_durability"); }
        public void handleItemDamaged(int level, ItemStack item, Player player, AtomicBoolean cancel) {
            durabilityCalls++;
            cancel.set(true);
        }
        public void handleRespawn(int level, Player player) { respawnCalls++; }
    }

    @Test
    public void durabilityDispatchHonorsTheActualExtensionSilenceWrapper() throws Exception {
        try (URLClassLoader loader = extension(); MockedStatic<ThePit> pit = mockStatic(ThePit.class);
             MockedStatic<Utils> utils = mockStatic(Utils.class); MockedStatic<PlayerUtil> playerUtil = mockStatic(PlayerUtil.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            EnchantmentFactor factor = new EnchantmentFactor();
            when(plugin.getEnchantmentFactor()).thenReturn(factor);
            DurabilityEnchant delegate = new DurabilityEnchant();
            factor.registerEnchantment(delegate);
            factor.getIItemDamages().set(0, (IItemDamage) wrapper(loader, delegate));
            ItemStack stack = new ItemStack(Material.GOLD_SWORD);
            IMythicItem item = mock(IMythicItem.class);
            Object2IntOpenHashMap<AbstractEnchantment> enchantments = new Object2IntOpenHashMap<>();
            enchantments.defaultReturnValue(-1);
            enchantments.put(delegate, 3);
            when(item.getEnchantments()).thenReturn(enchantments);
            utils.when(() -> Utils.getMythicItem(stack)).thenReturn(item);
            Player player = mock(Player.class);
            playerUtil.when(() -> PlayerUtil.shouldIgnoreEnchant(player)).thenReturn(true);
            PlayerItemDamageEvent blocked = new PlayerItemDamageEvent(player, stack, 1);
            new GameEffectListener().onItemDamaged(blocked);
            assertEquals(0, delegate.durabilityCalls);
            assertFalse(blocked.isCancelled());
            playerUtil.when(() -> PlayerUtil.shouldIgnoreEnchant(player)).thenReturn(false);
            PlayerItemDamageEvent allowed = new PlayerItemDamageEvent(player, stack, 1);
            new GameEffectListener().onItemDamaged(allowed);
            assertEquals(1, delegate.durabilityCalls);
            assertTrue(allowed.isCancelled());
        }
    }

    private abstract static class RespawnPlayer extends CraftPlayer {
        private RespawnPlayer() { super(null, null); }
        @Override public abstract double getMaxHealth();
    }

    @Test
    public void actualRespawnHonorsSilenceAndStopsCallingRemovedEnchantments() throws Exception {
        try (URLClassLoader loader = extension(); MockedStatic<ThePit> pit = mockStatic(ThePit.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class); MockedStatic<Utils> utils = mockStatic(Utils.class);
             MockedStatic<PlayerUtil> playerUtil = mockStatic(PlayerUtil.class);
             MockedStatic<TitleUtil> titles = mockStatic(TitleUtil.class);
             var attributes = mockConstruction(AttributeModifier.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            Server server = mock(Server.class);
            PluginManager plugins = mock(PluginManager.class);
            when(server.getPluginManager()).thenReturn(plugins);
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            PitWorldConfig config = mock(PitWorldConfig.class);
            when(plugin.getPitConfig()).thenReturn(config);
            when(config.getSpawnLocations()).thenReturn(List.of(new Location(mock(World.class), 0.5, 114, 9.5)));
            when(plugin.getPerkFactory()).thenReturn(mock(PerkFactory.class));
            EnchantmentFactor factor = new EnchantmentFactor();
            when(plugin.getEnchantmentFactor()).thenReturn(factor);
            DurabilityEnchant delegate = new DurabilityEnchant();
            factor.registerEnchantment(delegate);
            factor.getPlayerRespawns().set(0, (IPlayerRespawn) wrapper(loader, delegate));
            RespawnPlayer player = mock(RespawnPlayer.class);
            when(player.isOnline()).thenReturn(true);
            when(player.getMaxHealth()).thenReturn(24D);
            PlayerInventory inventory = mock(PlayerInventory.class);
            when(player.getInventory()).thenReturn(inventory);
            ItemStack stack = new ItemStack(Material.LEATHER_LEGGINGS);
            when(inventory.getLeggings()).thenReturn(stack);
            IMythicItem leggings = mock(IMythicItem.class);
            Object2IntOpenHashMap<AbstractEnchantment> enchantments = new Object2IntOpenHashMap<>();
            enchantments.defaultReturnValue(-1);
            enchantments.put(delegate, 3);
            when(leggings.getEnchantments()).thenReturn(enchantments);
            utils.when(() -> Utils.getMythicItem(stack)).thenReturn(leggings);
            // Run the real respawn method; isolate only the server's attribute bootstrap.
            Class<?> allocatorType = Class.forName("org.objenesis.ObjenesisStd");
            Object allocator = allocatorType.getConstructor().newInstance();
            EntityPlayer handle = (EntityPlayer) allocatorType.getMethod("newInstance", Class.class)
                    .invoke(allocator, EntityPlayer.class);
            when(player.getHandle()).thenReturn(handle);
            Method respawn = CombatListener.class.getDeclaredMethod("doRespawn", Player.class, PlayerProfile.class);
            respawn.setAccessible(true);
            CombatListener listener = mock(CombatListener.class);
            PlayerProfile profile = mock(PlayerProfile.class);
            playerUtil.when(() -> PlayerUtil.shouldIgnoreEnchant(player)).thenReturn(true);
            respawn.invoke(listener, player, profile);
            assertEquals(0, delegate.respawnCalls);
            playerUtil.when(() -> PlayerUtil.shouldIgnoreEnchant(player)).thenReturn(false);
            respawn.invoke(listener, player, profile);
            assertEquals(1, delegate.respawnCalls);
            factor.removeEnchantment(delegate);
            respawn.invoke(listener, player, profile);
            assertEquals(1, delegate.respawnCalls);
        }
    }

    @Test
    public void actualManagerInstallsAndRestoresAllOrdinaryReplacementEntries() throws Exception {
        iSpigot previous = iSpigot.INSTANCE;
        try (URLClassLoader loader = extension(); MockedStatic<ThePit> pit = mockStatic(ThePit.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            new iSpigot();
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            EnchantmentFactor factor = new EnchantmentFactor();
            when(plugin.getEnchantmentFactor()).thenReturn(factor);
            Class<?> mainType = loader.loadClass("cn.mx.thepitaddon.main.Main");
            Object extension = mock(mainType);
            Logger logger = mock(Logger.class);
            when(mainType.getMethod("getLogger").invoke(extension)).thenReturn(logger);
            Class<?> managerType = loader.loadClass("cn.mx.thepitaddon.enchantment.CoreEnchantRepairManager");
            Object manager = managerType.getConstructor(mainType).newInstance(extension);
            var specsField = managerType.getDeclaredField("replacements");
            specsField.setAccessible(true);
            List<?> specs = (List<?>) specsField.get(manager);
            var settingsField = managerType.getDeclaredField("settings");
            settingsField.setAccessible(true);
            Class<?> settingsType = loader.loadClass("cn.mx.thepitaddon.config.EnchantReplacementSettings");
            var constructor = settingsType.getDeclaredConstructor(boolean.class, boolean.class, Map.class);
            constructor.setAccessible(true);
            Map<String, Boolean> disabled = new HashMap<>();
            int tested = 0;
            for (Object spec : specs) {
                var configField = spec.getClass().getDeclaredField("configKey");
                var replacementField = spec.getClass().getDeclaredField("replacement");
                configField.setAccessible(true);
                replacementField.setAccessible(true);
                String key = (String) configField.get(spec);
                if (key.equals("soul-eater")) continue; // covered through the real transactional entry above
                disabled.put(key, false);
                AbstractEnchantment replacement = (AbstractEnchantment) replacementField.get(spec);
                AbstractEnchantment original = new UnknownEnchantment(replacement.getNbtName());
                factor.registerEnchantment(original);
                Method reconcile = managerType.getDeclaredMethod("reconcileReplacement", EnchantmentFactor.class, spec.getClass());
                reconcile.setAccessible(true);
                settingsField.set(manager, constructor.newInstance(true, true, Map.of()));
                assertEquals(true, reconcile.invoke(manager, factor, spec));
                assertSame(replacement, factor.getEnchantmentMap().get(replacement.getNbtName()));
                long revision = factor.getRevision();
                assertEquals(false, reconcile.invoke(manager, factor, spec));
                assertEquals(revision, factor.getRevision());
                settingsField.set(manager, constructor.newInstance(false, false, disabled));
                assertEquals(true, reconcile.invoke(manager, factor, spec));
                assertSame(original, factor.getEnchantmentMap().get(original.getNbtName()));
                assertFalse(factor.getTickTasks().values().stream().anyMatch(value -> value == replacement));
                tested++;
            }
            assertEquals(23, tested);
        } finally { iSpigot.INSTANCE = previous; }
    }
}
