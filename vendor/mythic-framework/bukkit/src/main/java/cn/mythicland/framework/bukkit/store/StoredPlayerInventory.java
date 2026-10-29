package cn.mythicland.framework.bukkit.store;

import org.bukkit.entity.Player;

public interface StoredPlayerInventory<I> {
    I capture(Player player);

    void apply(Player player, I inventory);

    default Prepared prepare(I inventory) {
        return new Prepared() {
            @Override
            public boolean advance() {
                return true;
            }

            @Override
            public void apply(Player player) {
                StoredPlayerInventory.this.apply(player, inventory);
            }
        };
    }

    interface Prepared {
        boolean advance();

        void apply(Player player);
    }

    default I mergeCaptured(I captured, I existing) {
        return captured;
    }
}
