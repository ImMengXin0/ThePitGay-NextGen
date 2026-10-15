package net.mizukilab.pit.menu;

import net.mizukilab.pit.util.menu.Button;
import net.mizukilab.pit.util.menu.ButtonListener;
import net.mizukilab.pit.util.menu.Menu;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.Test;
import java.util.Map;
import java.util.UUID;
import static org.mockito.Mockito.*;

public class ReadOnlyMenuTest {
    @Test
    public void allClicksAreCancelledAndOnlyOrdinaryTopClicksNavigate() {
        Player viewer = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(viewer.getUniqueId()).thenReturn(id);
        Menu menu = mock(Menu.class);
        when(menu.isReadOnly()).thenReturn(true);
        Button button = mock(Button.class);
        when(menu.getButtons()).thenReturn(Map.of(11, button));
        InventoryView view = mock(InventoryView.class);
        Inventory top = mock(Inventory.class);
        when(view.getTopInventory()).thenReturn(top);
        when(top.getSize()).thenReturn(27);
        Menu.currentlyOpenedMenus.put(id, menu);
        try (org.mockito.MockedStatic<Menu> menus = mockStatic(Menu.class)) {
            menus.when(() -> Menu.find(viewer, top)).thenReturn(menu);
            ButtonListener listener = new ButtonListener();
            for (int slot : new int[]{11, 30, -999}) {
                for (ClickType click : ClickType.values()) {
                    InventoryClickEvent event = mock(InventoryClickEvent.class);
                    when(event.getWhoClicked()).thenReturn(viewer);
                    when(event.getView()).thenReturn(view);
                    when(event.getRawSlot()).thenReturn(slot);
                    when(event.getClick()).thenReturn(click);
                    listener.onButtonPress(event);
                    verify(event).setCancelled(true);
                }
            }
            verify(button).clicked(viewer, 11, ClickType.LEFT, 0, null);
            verify(button).clicked(viewer, 11, ClickType.RIGHT, 0, null);
            verifyNoMoreInteractions(button);
        } finally {
            Menu.currentlyOpenedMenus.remove(id);
        }
    }

    @Test
    public void dragIsBlockedOnlyForReadOnlyMenus() {
        Player viewer = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(viewer.getUniqueId()).thenReturn(id);
        Menu menu = mock(Menu.class);
        when(menu.isReadOnly()).thenReturn(true);
        Menu.currentlyOpenedMenus.put(id, menu);
        try (org.mockito.MockedStatic<Menu> menus = mockStatic(Menu.class)) {
            InventoryDragEvent event = mock(InventoryDragEvent.class);
            InventoryView view = mock(InventoryView.class);
            Inventory top = mock(Inventory.class);
            when(view.getTopInventory()).thenReturn(top);
            when(event.getView()).thenReturn(view);
            menus.when(() -> Menu.find(viewer, top)).thenReturn(menu);
            when(event.getWhoClicked()).thenReturn(viewer);
            ButtonListener listener = new ButtonListener();
            listener.onInventoryDrag(event);
            verify(event).setCancelled(true);
            when(menu.isReadOnly()).thenReturn(false);
            clearInvocations(event);
            listener.onInventoryDrag(event);
            verify(event, never()).setCancelled(anyBoolean());
        } finally {
            Menu.currentlyOpenedMenus.remove(id);
        }
    }
}
