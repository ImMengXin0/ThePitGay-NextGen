package net.mizukilab.pit.listener;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.api.PitInternalHook;
import com.google.common.util.concurrent.AtomicDouble;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.mizukilab.pit.enchantment.AbstractEnchantment;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import net.mizukilab.pit.enchantment.type.normal.TrueDamageArrowEnchant;
import net.mizukilab.pit.enchantment.type.ragerare.Regularity;
import net.mizukilab.pit.enchantment.type.rare.SoulEarterEnchant;
import net.mizukilab.pit.item.IMythicItem;
import net.mizukilab.pit.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.Assume;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Actual 3.8.8 bridge discovery and delayed core damage, using the core's real event annotations. */
public class RepairSoulEaterBridgeIntegrationTest {
    private interface HealthPlayer extends Player { @Override double getHealth(); }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static Object invoke(Object owner, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = owner.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(owner, args);
    }

    @Test
    public void realManagerEnablesSoulEaterAndRegularityDoesNotRepeatItsIsolatedDamage() throws Exception {
        Path jar = Path.of(System.getProperty("repairExtendJar"));
        Assume.assumeTrue(Files.isRegularFile(jar));
        PitInternalHook previousApi = ThePit.api;
        HandlerList handlers = EntityDamageByEntityEvent.getHandlerList();
        List<RegisteredListener> registered = new ArrayList<>();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL(),
                    Path.of("build/libs/ThePitGay-V.jar").toUri().toURL()}, getClass().getClassLoader());
             MockedStatic<ThePit> pit = mockStatic(ThePit.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<PlayerUtil> playerUtil = mockStatic(PlayerUtil.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            when(plugin.getName()).thenReturn("ThePitUltimate");
            EnchantmentFactor factor = new EnchantmentFactor();
            when(plugin.getEnchantmentFactor()).thenReturn(factor);
            SoulEarterEnchant original = new SoulEarterEnchant();
            factor.registerEnchantment(original);
            RegisteredListener regularityHandler = null;
            for (Listener listener : new Listener[]{new TrueDamageArrowEnchant(), new Regularity()}) {
                for (Method method : listener.getClass().getMethods()) {
                    EventHandler annotation = method.getAnnotation(EventHandler.class);
                    if (annotation == null || !List.of(method.getParameterTypes()).equals(List.of(EntityDamageByEntityEvent.class))) continue;
                    RegisteredListener entry = new RegisteredListener(listener, (instance, event) -> {
                        try { method.invoke(instance, event); }
                        catch (ReflectiveOperationException error) { throw new EventException(error); }
                    }, annotation.priority(), plugin, annotation.ignoreCancelled());
                    registered.add(entry);
                    handlers.register(entry);
                    if (listener instanceof Regularity) regularityHandler = entry;
                }
            }
            assertNotNull(regularityHandler);
            Class<?> mainType = loader.loadClass("cn.mx.thepitaddon.main.Main");
            Object main = mock(mainType);
            Logger logger = mock(Logger.class);
            when(mainType.getMethod("getLogger").invoke(main)).thenReturn(logger);
            Class<?> managerType = loader.loadClass("cn.mx.thepitaddon.enchantment.CoreEnchantRepairManager");
            Object manager = managerType.getConstructor(mainType).newInstance(main);
            Object isolation = field(manager, "soulEaterDamageIsolation");
            try {
                Class<?> settingsType = loader.loadClass("cn.mx.thepitaddon.config.EnchantReplacementSettings");
                var constructor = settingsType.getDeclaredConstructor(boolean.class, boolean.class, Map.class);
                constructor.setAccessible(true);
                Field settings = managerType.getDeclaredField("settings");
                settings.setAccessible(true);
                settings.set(manager, constructor.newInstance(true, true, Map.of()));
                Object soulSpec = ((List<?>) field(manager, "replacements")).stream().filter(spec -> {
                    try { return "soul-eater".equals(field(spec, "configKey")); }
                    catch (Exception error) { throw new AssertionError(error); }
                }).findFirst().orElseThrow();
                Class<?>[] reconcileTypes = {EnchantmentFactor.class, soulSpec.getClass()};
                assertEquals("Real listener discovery must allow the replacement to enable", true,
                        invoke(manager, "reconcileReplacement", reconcileTypes, factor, soulSpec));
                assertSame(field(manager, "soulEater"), factor.getEnchantmentMap().get(original.getNbtName()));
                invoke(manager, "synchronizeSoulEaterIsolation", new Class<?>[]{EnchantmentFactor.class}, factor);
                assertEquals(true, invoke(isolation, "isOperational", new Class<?>[0]));

                ThePit.api = mock(PitInternalHook.class);
                Player attacker = mock(Player.class);
                PlayerInventory inventory = mock(PlayerInventory.class);
                when(attacker.getInventory()).thenReturn(inventory);
                ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS);
                when(inventory.getLeggings()).thenReturn(leggings);
                when(ThePit.api.getItemEnchantLevel(leggings, "regularity")).thenReturn(3);
                HealthPlayer victim = mock(HealthPlayer.class);
                when(victim.getHealth()).thenReturn(20D);
                when(victim.isOnline()).thenReturn(true);
                when(attacker.isOnline()).thenReturn(true);
                BukkitScheduler scheduler = mock(BukkitScheduler.class);
                bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
                List<Runnable> pending = new ArrayList<>();
                doAnswer(call -> { pending.add(call.getArgument(1)); return null; })
                        .when(scheduler).runTaskLater(eq(plugin), any(Runnable.class), eq(5L));
                IMythicItem item = mock(IMythicItem.class);
                Object2IntOpenHashMap<AbstractEnchantment> enchantments = new Object2IntOpenHashMap<>();
                when(item.getEnchantments()).thenReturn(enchantments);
                GameEffectListener effects = new GameEffectListener();
                Class<?>[] attackTypes = {boolean.class, EntityDamageByEntityEvent.class, Player.class,
                        AtomicDouble.class, AtomicDouble.class, AtomicBoolean.class, IMythicItem.class, IMythicItem.class};
                for (int tier = 1; tier <= 4; tier++) {
                    boolean cancelled = tier == 4;
                    enchantments.put(original, Math.min(tier, 3)); // A cached item still contains the old instance.
                    EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(attacker, victim,
                            EntityDamageEvent.DamageCause.ENTITY_ATTACK, 1D);
                    invoke(isolation, "captureDamage", new Class<?>[]{EntityDamageByEntityEvent.class}, hit);
                    invoke(effects, "processAttackWithLeggingAndHeldItems", attackTypes, false, hit, attacker,
                            new AtomicDouble(1), new AtomicDouble(1), new AtomicBoolean(), item, null);
                    regularityHandler.callEvent(hit);
                    assertEquals(1, pending.size());
                    hit.setDamage(8D); // A later modifier affects ordinary damage only.
                    hit.setCancelled(cancelled);
                    invoke(isolation, "commitDamage", new Class<?>[]{EntityDamageByEntityEvent.class}, hit);
                    assertEquals(cancelled ? 8D : 8D + tier * 0.5D, hit.getFinalDamage(), 0.00001D);
                    clearInvocations(victim);
                    pending.remove(0).run();
                    if (cancelled) verify(victim, never()).damage(anyDouble(), any(Player.class));
                    else verify(victim).damage(6D, attacker); // 8 * 75%, excluding Soul Eater's bonus.
                }
                settings.set(manager, constructor.newInstance(false, false, Map.of("soul-eater", false)));
                assertEquals(true, invoke(manager, "reconcileReplacement", reconcileTypes, factor, soulSpec));
                assertSame(original, factor.getEnchantmentMap().get(original.getNbtName()));
            } finally { invoke(isolation, "shutdown", new Class<?>[0]); }
        } finally {
            for (RegisteredListener listener : registered) handlers.unregister(listener);
            ThePit.api = previousApi;
        }
    }
}
