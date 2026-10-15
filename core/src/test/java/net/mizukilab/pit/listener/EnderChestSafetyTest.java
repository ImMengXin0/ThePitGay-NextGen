package net.mizukilab.pit.listener;
import net.mizukilab.pit.util.item.ItemUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.Material;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.Set;
import static org.mockito.Mockito.*;

public class EnderChestSafetyTest {
    // Dispatch through Bukkit annotation discovery, allowing the pre-fix listener with no drag handler.
    private void dispatch(InventoryDragEvent event) throws Exception {
        EnderChestListener listener=new EnderChestListener();
        for(var method:EnderChestListener.class.getDeclaredMethods())
            if(method.isAnnotationPresent(org.bukkit.event.EventHandler.class) && method.getParameterCount()==1
                    && method.getParameterTypes()[0]==InventoryDragEvent.class)method.invoke(listener,event);
    }
    @Test public void forbiddenCursorAndLockedSlotsCannotBeBypassedUsingDrag() throws Exception {
        try(MockedStatic<ItemUtil> items=mockStatic(ItemUtil.class)) {
            Inventory inventory=mock(Inventory.class); when(inventory.getName()).thenReturn("末影箱"); when(inventory.getSize()).thenReturn(54);
            InventoryView view=mock(InventoryView.class); when(view.getTopInventory()).thenReturn(inventory);
            InventoryDragEvent drag=mock(InventoryDragEvent.class); when(drag.getView()).thenReturn(view); when(drag.getInventory()).thenReturn(inventory);
            when(drag.getRawSlots()).thenReturn(Set.of(1)); ItemStack cursor=mock(ItemStack.class); when(cursor.getType()).thenReturn(Material.PAPER);
            when(drag.getOldCursor()).thenReturn(cursor); dispatch(drag); verify(drag).setCancelled(true);
            items.when(() -> ItemUtil.canSaveEnderChest(cursor)).thenReturn(true); clearInvocations(drag);
            ItemStack locked=mock(ItemStack.class); when(inventory.getItem(1)).thenReturn(locked);
            items.when(() -> ItemUtil.getInternalName(locked)).thenReturn("not_unlock_slot");
            dispatch(drag); verify(drag).setCancelled(true);
            when(inventory.getItem(1)).thenReturn(null); clearInvocations(drag); dispatch(drag); verify(drag,never()).setCancelled(true);
            when(drag.getRawSlots()).thenReturn(Set.of(55)); items.when(() -> ItemUtil.canSaveEnderChest(cursor)).thenReturn(false);
            dispatch(drag); verify(drag,never()).setCancelled(true);
        }
    }
    @Test public void outsideClickWithNullCursorDoesNotThrow() {
        InventoryClickEvent click=mock(InventoryClickEvent.class); Inventory inventory=mock(Inventory.class);
        when(click.getInventory()).thenReturn(inventory); when(inventory.getName()).thenReturn("末影箱");
        when(click.getClick()).thenReturn(ClickType.LEFT); when(click.getAction()).thenReturn(InventoryAction.NOTHING);
        new EnderChestListener().onInvClick(click);
    }
}
