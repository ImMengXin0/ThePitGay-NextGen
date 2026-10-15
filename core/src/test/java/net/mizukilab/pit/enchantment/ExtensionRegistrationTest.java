package net.mizukilab.pit.enchantment;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import com.google.common.util.concurrent.AtomicDouble;
import net.mizukilab.pit.enchantment.param.item.ArmorOnly;
import net.mizukilab.pit.parm.listener.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ExtensionRegistrationTest {
    @ArmorOnly
    public static class Callbacks extends UnknownEnchantment implements ITickTask, IPlayerAssist,
            IActionDisplayEnchant, IPlayerRespawn, IItemDamage, Listener {
        public Callbacks() { super("replacement_test"); }
        public void handle(int level, Player player) { }
        public int loopTick(int level) { return 20; }
        public String getText(int level, Player player) { return "test"; }
        public void handleRespawn(int level, Player player) { }
        public void handleItemDamaged(int level, org.bukkit.inventory.ItemStack stack, Player player,
                                      java.util.concurrent.atomic.AtomicBoolean cancel) { }
        public void handlePlayerAssist(int level, Player player, Entity target, double damage,
                                       double finalDamage, AtomicDouble coins, AtomicDouble xp) { }
        @EventHandler public void quit(PlayerQuitEvent event) { }
    }

    @Test
    public void replacementAndShutdownRemoveAllOldCallbacksByIdentity() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            EnchantmentFactor factor = new EnchantmentFactor();
            Callbacks original = new Callbacks(), replacement = new Callbacks();
            factor.registerEnchantment(original);
            factor.registerEnchantment(replacement);
            factor.registerEnchantment(replacement);
            // A delayed shutdown of the equal-by-NBT original cannot unregister the replacement.
            factor.removeEnchantment(original);
            assertSame(replacement, factor.getEnchantmentMap().get(original.getNbtName()));
            assertSame(replacement, factor.getEnchByClass(Callbacks.class));
            assertSame(replacement, factor.getTickTasks().values().iterator().next());
            assertSame(replacement, factor.getActionDisplayEnchants().values().iterator().next());
            assertEquals(1, factor.getPlayerAssists().size());
            assertSame(replacement, factor.getPlayerAssists().get(0));
            assertEquals(1, factor.getPlayerRespawns().size());
            assertSame(replacement, factor.getPlayerRespawns().get(0));
            // Both JVM method names must expose the same live list, including replacements.
            assertSame(factor.getIItemDamages(), factor.getiItemDamages());
            assertEquals(1, factor.getiItemDamages().size());
            assertSame(replacement, factor.getiItemDamages().get(0));
            factor.unregister(replacement.getNbtName(), null);
            assertTrue(factor.getTickTasks().isEmpty());
            assertTrue(factor.getPlayerAssists().isEmpty());
            assertTrue(factor.getPlayerRespawns().isEmpty());
            assertTrue(factor.getiItemDamages().isEmpty());
            assertTrue(factor.getActionDisplayEnchants().isEmpty());
            assertNull(factor.getEnchByClass(Callbacks.class));
        }
    }

    @Test
    public void bukkitListenerWithoutAutoRegisterAnnotationIsUnregistered() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            PluginManager manager = mock(PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            EnchantmentFactor factor = new EnchantmentFactor();
            Callbacks original = new Callbacks();
            HandlerList handlers = new HandlerList();
            RegisteredListener registration = mock(RegisteredListener.class);
            when(registration.getListener()).thenReturn(original);
            when(registration.getPriority()).thenReturn(org.bukkit.event.EventPriority.NORMAL);
            handlers.register(registration);
            try {
                factor.registerEnchantment(original);
                factor.removeEnchantment(original);
                assertEquals(0, handlers.getRegisteredListeners().length);
                verify(manager).registerEvents(eq(original), any());
            } finally {
                handlers.unregister(registration);
            }
        }
    }

    @Test
    public void enchantPoolReloadsOnceAfterExtensionReplacement() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            EnchantmentFactor factor = new EnchantmentFactor();
            Callbacks original = new Callbacks(), replacement = new Callbacks();
            factor.registerEnchantment(original);
            EnchantmentTable table = spy(new EnchantmentTable(factor));
            factor.removeEnchantment(original);
            factor.registerEnchantment(replacement);
            Player player = mock(Player.class); // rejected request still synchronizes the pool
            PlayerProfile profile = mock(PlayerProfile.class);
            table.enchantItem(player, profile, null, false);
            table.enchantItem(player, profile, null, false);
            assertSame(replacement, table.armorEnchant.get(replacement.getRarity()).get(0));
            assertEquals(1, table.armorEnchant.get(replacement.getRarity()).size());
            verify(table, times(1)).reload();
        }
    }
}
