package net.mizukilab.pit.menu.item;

import cn.charlotte.pit.ThePit;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import net.mizukilab.pit.util.chat.CC;
import net.mizukilab.pit.util.DatabaseTasks;
import net.mizukilab.pit.util.inventory.InventoryUtil;
import net.mizukilab.pit.util.menu.Button;
import net.mizukilab.pit.util.menu.Menu;
import org.bson.Document;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.*;

public class AllSavedMythicItemsMenu extends Menu {

    /**
     * @Author ShanguanLinG
     * @Created 2025/09/30 3:53
     */

    private static final int ITEMS_PER_PAGE = 45;

    private int page;
    private final String playerId; // 添加玩家ID字段
    private List<Document> items;
    private long totalItems;

    public AllSavedMythicItemsMenu() {
        this(1, null);
    }

    public AllSavedMythicItemsMenu(int page) {
        this(page, null);
    }

    public AllSavedMythicItemsMenu(int page, String playerId) {
        this.page = Math.max(1, page);
        this.playerId = playerId;
        this.setAutoUpdate(false);
    }

    public AllSavedMythicItemsMenu(List<Document> items, String playerId) {
        this.items = new ArrayList<>(items.subList(0, Math.min(items.size(), ITEMS_PER_PAGE)));
        this.totalItems = items.size();
        this.page = 1;
        this.playerId = playerId;
        this.setAutoUpdate(false);
    }

    @Override
    public void openMenu(Player player) {
        if (!player.isOnline() || !player.hasPermission("pit.admin")) return;
        if (items != null) {
            super.openMenu(player);
            return;
        }
        Menu previous = Menu.find(player);
        DatabaseTasks.submit(player, () -> {
            MongoCollection<Document> collection = ThePit.getInstance().getMongoDB().getDatabase().getCollection("saved_mythic_items");
            Document filter = playerId == null ? new Document() : new Document("createdByName", playerId);
            long total = collection.countDocuments(filter);
            int lastPage = (int) Math.max(1, (total + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
            int selectedPage = Math.min(page, lastPage);
            List<Document> found = collection.find(filter).sort(new Document("createdAt", -1).append("_id", -1))
                    .skip((selectedPage - 1) * ITEMS_PER_PAGE).limit(ITEMS_PER_PAGE).into(new ArrayList<>());
            return new PageResult(selectedPage, total, found);
        }, result -> {
            if (!player.hasPermission("pit.admin") || Menu.find(player) != previous) return;
            page = result.page;
            totalItems = result.total;
            items = result.items;
            super.openMenu(player);
        });
    }

    private record PageResult(int page, long total, List<Document> items) {}

    @Override
    public String getTitle(Player player) {
        long totalPages = Math.max(1, (totalItems + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        if (playerId != null) {
            return CC.translate("&8(" + page + "/" + totalPages + ") " + playerId + " 保存的物品");
        }
        return CC.translate("&8(" + page + "/" + totalPages + ") 保存的物品");
    }

    @Override
    public int getSize() {
        return 54;
    }

    @Override
    public Map<Integer, Button> getButtons(Player player) {
        Map<Integer, net.mizukilab.pit.util.menu.Button> buttons = new HashMap<>();
        if (items == null) return buttons;
        int startIndex = 0;
        int endIndex = items.size();

        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            Document doc = items.get(i);
            buttons.put(slot++, new ItemButton(doc, page, playerId));
        }

        // 上一页
        if (page > 1) {
            buttons.put(45, new net.mizukilab.pit.util.menu.Button() {
                @Override
                public ItemStack getButtonItem(Player player) {
                    ItemStack item = new ItemStack(Material.ARROW);
                    ItemMeta meta = item.getItemMeta();
                    meta.setDisplayName(CC.translate("&f上一页"));
                    item.setItemMeta(meta);
                    return item;
                }

                @Override
                public void clicked(Player player, int slot, ClickType clickType, int hotbarButton, ItemStack currentItem) {
                    if (playerId != null) {
                        new AllSavedMythicItemsMenu(page - 1, playerId).openMenu(player);
                    } else {
                        new AllSavedMythicItemsMenu(page - 1).openMenu(player);
                    }
                }
            });
        }

        // 下一页
        if ((long) page * ITEMS_PER_PAGE < totalItems) {
            buttons.put(53, new Button() {
                @Override
                public ItemStack getButtonItem(Player player) {
                    ItemStack item = new ItemStack(Material.ARROW);
                    ItemMeta meta = item.getItemMeta();
                    meta.setDisplayName(CC.translate("&f下一页"));
                    item.setItemMeta(meta);
                    return item;
                }

                @Override
                public void clicked(Player player, int slot, ClickType clickType, int hotbarButton, ItemStack currentItem) {
                    if (playerId != null) {
                        new AllSavedMythicItemsMenu(page + 1, playerId).openMenu(player);
                    } else {
                        new AllSavedMythicItemsMenu(page + 1).openMenu(player);
                    }
                }
            });
        }

//        buttons.put(49, new Button() {
//            @Override
//            public ItemStack getButtonItem(Player player) {
//                ItemStack item = new ItemStack(Material.PAPER);
//                ItemMeta meta = item.getItemMeta();
//                meta.setDisplayName(CC.translate("&e第 " + page + " 页"));
//                List<String> lore = new ArrayList<>();
//                lore.add(CC.translate("&7总共 " + ((items.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE) + " 页"));
//                lore.add(CC.translate("&7共 " + items.size() + " 个物品"));
//                meta.setLore(lore);
//                item.setItemMeta(meta);
//                return item;
//            }
//
//            @Override
//            public void clicked(Player player, int slot, ClickType clickType, int hotbarButton, ItemStack currentItem) {
//            }
//        });

        return buttons;
    }

    private static class ItemButton extends Button {
        private final Document document;
        private final int currentPage;
        private final String playerId;

        public ItemButton(Document document, int currentPage, String playerId) {
            this.document = document;
            this.currentPage = currentPage;
            this.playerId = playerId;
        }

        @Override
        public ItemStack getButtonItem(Player player) {
            String encodedItem = document.getString("item");
            ItemStack item = InventoryUtil.deserializeItemStack(encodedItem);
            ItemMeta meta = item.getItemMeta();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add("");
            lore.add(CC.translate("&7UUID: &f" + document.getString("uuid")));
            lore.add(CC.translate("&7创建者: &f" + document.getString("createdByName")));
            long createdAt = document.getLong("createdAt");
            lore.add(CC.translate("&7创建时间: &f" + sdf.format(new Date(createdAt))));
            lore.add("");
            lore.add(CC.translate("&e左键点击查看物品"));
            lore.add(CC.translate("&cShift+右键点击删除物品"));
            meta.setLore(lore);
            item.setItemMeta(meta);
            return item;
        }

        @Override
        public void clicked(Player player, int slot, ClickType clickType, int hotbarButton, ItemStack currentItem) {
            String uuid = document.getString("uuid");
            String encodedItem = document.getString("item");

            if (!player.hasPermission("pit.admin")) return;
            if (clickType == ClickType.SHIFT_RIGHT) {
                MongoCollection<Document> collection = ThePit.getInstance().getMongoDB().getDatabase().getCollection("saved_mythic_items");
                Menu previous = Menu.find(player);
                DatabaseTasks.submit(player, () -> collection.deleteOne(new Document("uuid", uuid)), result -> {
                    player.sendMessage(CC.translate(result.getDeletedCount() > 0 ? "&a成功删除物品!" : "&c记录已不存在。"));
                    if (Menu.find(player) == previous) new AllSavedMythicItemsMenu(currentPage, playerId).openMenu(player);
                });
                return;
            }

            if (uuid != null && encodedItem != null) {
                new SavedMythicItemMenu(uuid, encodedItem, currentPage, playerId).openMenu(player);
            }
        }
    }
}
