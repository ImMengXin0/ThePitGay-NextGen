package net.mizukilab.pit.enchantment;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.mizukilab.pit.item.MythicColor;
import net.mizukilab.pit.item.type.mythic.MythicSwordItem;
import net.mizukilab.pit.enchantment.rarity.EnchantmentRarity;
import net.mizukilab.pit.util.exception.IllegalEnchantInputException;
import org.junit.Test;
import static org.junit.Assert.*;

public class EnchantmentBudgetTest {
    @Test
    public void fullItemCannotGainAnotherLevel() {
        MythicSwordItem item = new MythicSwordItem();
        item.color = MythicColor.RED;
        item.getEnchantments().put(new UnknownEnchantment("first"), 3);
        item.getEnchantments().put(new UnknownEnchantment("second"), 3);
        item.getEnchantments().put(new UnknownEnchantment("third"), 2);
        var pool = new Object2ObjectOpenHashMap<EnchantmentRarity, ObjectArrayList<AbstractEnchantment>>();
        assertThrows(IllegalEnchantInputException.class, () -> EnchantmentTable.Tier.TIER_3.normal.invoke(0D, pool, item));
        assertEquals(8, item.getEnchantments().values().intStream().sum());
    }

    @Test
    public void bookRespectsRemainingBudgetForNewAndExistingEnchantments() {
        var pool = new Object2ObjectOpenHashMap<EnchantmentRarity, ObjectArrayList<AbstractEnchantment>>();
        var rare = new UnknownEnchantment("rare");
        pool.put(EnchantmentRarity.RARE, new ObjectArrayList<>(new AbstractEnchantment[]{rare}));
        MythicSwordItem item = new MythicSwordItem();
        item.color = MythicColor.RED;
        item.getEnchantments().defaultReturnValue(-1);
        item.getEnchantments().put(new UnknownEnchantment("existing"), 7);
        EnchantmentTable.Tier.TIER_1.useBook.invoke(0D, pool, item);
        assertEquals(1, item.getEnchantments().getInt(rare));
        assertEquals(8, item.getEnchantments().values().intStream().sum());

        item.getEnchantments().clear();
        item.getEnchantments().put(new UnknownEnchantment("a"), 3);
        item.getEnchantments().put(new UnknownEnchantment("b"), 2);
        item.getEnchantments().put(new UnknownEnchantment("c"), 2);
        EnchantmentTable.Tier.TIER_1.useBook.invoke(0D, pool, item);
        assertEquals(8, item.getEnchantments().values().intStream().sum());
    }
}
