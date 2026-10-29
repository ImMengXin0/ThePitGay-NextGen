package cn.mythicland.framework.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public abstract class AbstractPaginatedMenu<T> extends AbstractMenu {
    protected abstract String buildPageTitle(Player player, int page);

    protected abstract List<T> pageEntries(Player player);

    protected abstract int[] pageSlots();

    protected abstract AbstractButton createEntryButton(Player player, T entry, int absoluteIndex);

    protected boolean hasFooterRow() {
        return true;
    }

    protected void populateFooter(Player player, Menu menu, int page, int maxPage) {
    }

    protected int previousPageSlot(Player player) {
        return this.slot(this.resolveRowCount(player) - 1, 3);
    }

    protected int nextPageSlot(Player player) {
        return this.slot(this.resolveRowCount(player) - 1, 5);
    }

    protected final int currentPage(Player player) {
        if (player == null) return 0;
        return this.clampPage(player, PaginatedMenuSessionRegistry.currentPage(player.getUniqueId(), this));
    }

    protected final void resetPage(Player player) {
        if (player == null) return;
        PaginatedMenuSessionRegistry.reset(player.getUniqueId(), this);
    }

    final void markOpened(Player player) {
        if (player == null) return;
        PaginatedMenuSessionRegistry.open(player.getUniqueId(), this);
    }

    final void clearPageSession(Player player) {
        if (player == null) return;
        PaginatedMenuSessionRegistry.clear(player.getUniqueId(), this);
    }

    @Override
    public final String buildTitle(Player player) {
        return this.buildPageTitle(player, this.currentPage(player));
    }

    @Override
    protected final void populateMenu(Player player, Menu menu) {
        int page = this.currentPage(player);
        List<T> entries = this.pageEntries(player);
        int[] slots = this.pageSlots();
        int startIndex = page * slots.length;
        for (int index = 0; index < slots.length; index++) {
            int absoluteIndex = startIndex + index;
            if (absoluteIndex >= entries.size()) continue;
            menu.setButton(slots[index], this.createEntryButton(player, entries.get(absoluteIndex), absoluteIndex));
        }

        int maxPage = this.maxPage(entries.size(), slots.length);
        if (page > 0) menu.setButton(this.previousPageSlot(player), new PageChangeButton(-1));
        if (page < maxPage) menu.setButton(this.nextPageSlot(player), new PageChangeButton(1));
        if (this.hasFooterRow()) this.populateFooter(player, menu, page, maxPage);
    }

    protected final int clampPage(Player player, int page) {
        return cn.mythicland.framework.math.Clamps.clamp(page, 0, this.maxPage(this.pageEntries(player).size(), this.pageSlots().length));
    }

    private int maxPage(int entryCount, int pageSize) {
        if (pageSize <= 0) return 0;
        return Math.max(0, (entryCount - 1) / pageSize);
    }

    protected final void changePage(Player player, MenuManager menuManager, int delta) {
        int nextPage = this.clampPage(player, this.currentPage(player) + delta);
        PaginatedMenuSessionRegistry.setPage(player.getUniqueId(), this, nextPage);
        menuManager.refresh(player);
    }

    private final class PageChangeButton extends AbstractButton {
        private final int delta;

        private PageChangeButton(int delta) {
            this.delta = delta;
        }

        @Override
        public ItemStack buildDisplayItem(Player player) {
            boolean next = this.delta > 0;
            ItemStack item = new ItemStack(Material.ARROW);
            if (item.getItemMeta() != null) {
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', next ? "&a下一页" : "&a上一页"));
                item.setItemMeta(meta);
            }
            return item;
        }

        @Override
        public void onClick(Player player, ClickType clickType, MenuManager menuManager) {
            AbstractPaginatedMenu.this.changePage(player, menuManager, this.delta);
        }
    }
}
