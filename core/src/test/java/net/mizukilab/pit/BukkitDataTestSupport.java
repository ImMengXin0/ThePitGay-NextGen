package net.mizukilab.pit;

import cn.charlotte.pit.data.sub.PlayerWarehouse;
import cn.charlotte.pit.data.sub.PlayerEnderChest;
import net.mizukilab.pit.util.item.ItemBuilder;
import org.junit.After;
import org.junit.Before;
import org.mockito.MockedConstruction;

import static org.mockito.Mockito.*;

/** Isolates server-owned inventories and NMS item construction in data-flow tests. */
public abstract class BukkitDataTestSupport {
    private MockedConstruction<PlayerWarehouse> warehouses;
    private MockedConstruction<PlayerEnderChest> enderChests;
    private MockedConstruction<ItemBuilder> itemBuilders;

    @Before
    public void isolateServerObjects() {
        warehouses = mockConstruction(PlayerWarehouse.class);
        enderChests = mockConstruction(PlayerEnderChest.class);
        itemBuilders = mockConstruction(ItemBuilder.class, withSettings().defaultAnswer(RETURNS_SELF));
    }

    @After
    public void releaseServerObjects() {
        itemBuilders.close();
        enderChests.close();
        warehouses.close();
    }
}
