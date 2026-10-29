package cn.mythicland.framework.bukkit.item;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;

@FunctionalInterface
public interface ItemEditor {
    void apply(ItemStack itemStack);

    default ItemEditor andThen(ItemEditor next) {
        Objects.requireNonNull(next, "next");
        return itemStack -> {
            this.apply(itemStack);
            next.apply(itemStack);
        };
    }
}
