package net.mizukilab.pit.menu;

import cn.charlotte.pit.ThePit;
import net.mizukilab.pit.BukkitDataTestSupport;
import net.mizukilab.pit.config.NewConfiguration;
import net.mizukilab.pit.enchantment.menu.MythicWellMenu;
import net.mizukilab.pit.handlers.AnimationHandler;
import net.mizukilab.pit.handlers.TickHandler;
import cn.charlotte.pit.perk.PerkFactory;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import net.mizukilab.pit.item.type.mythic.*;
import net.mizukilab.pit.util.menu.ButtonListener;
import net.mizukilab.pit.util.menu.Menu;
import net.mizukilab.pit.util.menu.MenuUpdateTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MythicMenuLifecycleTest extends BukkitDataTestSupport {
    @Test
    public void openingAndRefreshingWellDoNotFinishAnimationWhenBukkitClosesOldContainer() throws Exception {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<ThePit> pit = mockStatic(ThePit.class);
             var swords = mockConstruction(MythicSwordItem.class);
             var bows = mockConstruction(MythicBowItem.class);
             var rods = mockConstruction(MagicFishingRod.class);
             var leggings = mockConstruction(MythicLeggingsItem.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            AtomicReference<Runnable> restore = new AtomicReference<>();
            when(scheduler.runTask(any(org.bukkit.plugin.Plugin.class), any(Runnable.class))).thenAnswer(call -> {
                restore.set(call.getArgument(1));
                return mock(org.bukkit.scheduler.BukkitTask.class);
            });
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.isOnline()).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            when(plugin.getEnchantmentFactor()).thenReturn(new EnchantmentFactor());
            when(plugin.getPerkFactory()).thenReturn(new PerkFactory());
            AnimationHandler animator = mock(AnimationHandler.class);
            when(plugin.getAnimator()).thenReturn(animator);
            AnimationHandler.AnimationData data = new AnimationHandler.AnimationData(player);
            when(animator.holdOrGetAnimation(player)).thenReturn(data);
            Inventory crafting = mock(Inventory.class), well = mock(Inventory.class);
            when(crafting.getType()).thenReturn(InventoryType.CRAFTING);
            when(well.getType()).thenReturn(InventoryType.CHEST);
            InventoryView view = mock(InventoryView.class);
            AtomicReference<Inventory> top = new AtomicReference<>(crafting);
            when(view.getTopInventory()).thenAnswer(call -> top.get());
            when(player.getOpenInventory()).thenReturn(view);
            when(well.getSize()).thenReturn(45);
            when(well.getTitle()).thenReturn("神话之井");
            bukkit.when(() -> Bukkit.createInventory(player, 45, "神话之井")).thenReturn(well);
            ButtonListener listener = new ButtonListener();
            doAnswer(call -> {
                listener.onInventoryClose(closeEvent(player, top.get()));
                top.set(crafting);
                return null;
            }).when(player).closeInventory();
            when(player.openInventory(well)).thenAnswer(call -> {
                // CraftPlayer.openInventory also closes the previously active container.
                listener.onInventoryClose(closeEvent(player, top.get()));
                top.set(well);
                return view;
            });
            MythicWellMenu menu = spy(new MythicWellMenu(player));
            doReturn(Map.of()).when(menu).getButtons(player);
            try {
                menu.openMenu(player);
                assertSame(menu, Menu.find(player, well));
                assertFalse(data.isFinished());
                verify(menu, never()).onClose(player);
                data.setStartEnchanting(true);
                menu.openMenu(player);
                listener.onInventoryClose(closeEvent(player, crafting));
                assertFalse(data.isFinished());
                assertSame(menu, Menu.find(player));
                verify(player, times(1)).openInventory(well);
                // ESC is restored on the next tick. Auto-refresh must not race that restoration.
                boolean rapid = NewConfiguration.INSTANCE.getRapidEnchanting();
                NewConfiguration.INSTANCE.setRapidEnchanting(false);
                try {
                    menu.setAutoUpdate(true);
                    player.closeInventory();
                    assertNotNull(restore.get());
                    new MenuUpdateTask().run();
                    var tickMenus = TickHandler.class.getDeclaredMethod("tickMenus");
                    tickMenus.setAccessible(true);
                    tickMenus.invoke(new TickHandler());
                    verify(player, times(1)).openInventory(well);
                    restore.get().run();
                    assertFalse(data.isFinished());
                    assertSame(menu, Menu.find(player, well));
                    verify(player, times(2)).openInventory(well);
                } finally {
                    NewConfiguration.INSTANCE.setRapidEnchanting(rapid);
                }
                // A genuine programmatic close releases the animation and menu together.
                menu.setClosedByMenu(true);
                listener.onInventoryClose(closeEvent(player, well));
                assertTrue(data.isFinished());
                assertNull(Menu.find(player));
                verify(animator).releaseAnimation(player, data);
            } finally {
                menu.setClosedByMenu(true);
                listener.onInventoryClose(closeEvent(player, well));
            }
        }
    }

    private static InventoryCloseEvent closeEvent(Player player, Inventory inventory) {
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getInventory()).thenReturn(inventory);
        return event;
    }
}
