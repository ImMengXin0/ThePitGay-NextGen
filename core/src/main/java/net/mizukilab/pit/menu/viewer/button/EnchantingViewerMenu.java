package net.mizukilab.pit.menu.viewer.button;

import cn.charlotte.pit.data.PlayerProfile;
import net.mizukilab.pit.menu.viewer.StatusViewerMenu;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.inventory.InventoryUtil;
import net.mizukilab.pit.util.item.ItemBuilder;
import net.mizukilab.pit.util.menu.Button;
import net.mizukilab.pit.util.menu.Menu;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Read-only view of the live profile or the last available offline profile. */
public class EnchantingViewerMenu extends Menu {
    private final UUID targetId;
    private final String targetName;
    private PlayerProfile lastKnownProfile;
    private final String[] encodedItems = new String[3];
    private final ItemStack[] decodedItems = new ItemStack[3];

    public EnchantingViewerMenu(PlayerProfile profile) {
        lastKnownProfile = profile;
        targetId = profile.getPlayerUuid();
        targetName = profile.getPlayerName();
        setAutoUpdate(true);
        setPlaceholder(true);
    }

    @Override
    public String getTitle(Player player) {
        return "附魔台物品 - " + targetName;
    }

    @Override
    public int getSize() {
        return 27;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    private PlayerProfile currentProfile() {
        Player target = Bukkit.getPlayer(targetId);
        boolean online = target != null && target.isOnline();
        PlayerProfile cached = PlayerProfile.getRawCache(targetId);
        if (cached != null && cached.isLoaded()) {
            // Keep the latest session even after logout removes it from the global cache.
            lastKnownProfile = cached;
        } else if (online) {
            // A reconnect must finish loading before old saved items can be shown as live data.
            return null;
        }
        // /view already loaded this offline record; its online-only loaded flag may be false.
        PlayerProfile profile = lastKnownProfile;
        // Apply the current owner's eligibility to legacy settings and membership expiry.
        // Offline permissions cannot be checked, so preserve the saved privacy choice.
        if (online && !profile.getPlayerOption().isEnchantingVisibility()
                && !profile.isSupporter() && !PlayerUtil.isStaff(target)) {
            profile.getPlayerOption().setEnchantingVisibility(true);
        }
        return profile;
    }

    private String unavailableReason(Player viewer, PlayerProfile profile) {
        if (profile == null) return "玩家档案尚未加载，暂时无法查看。";
        if (!targetId.equals(viewer.getUniqueId()) && !viewer.hasPermission("pit.admin")
                && !PlayerUtil.isStaff(viewer) && !profile.getPlayerOption().isEnchantingVisibility()) {
            return "这名玩家已隐藏附魔台内物品。";
        }
        return null;
    }

    public String getUnavailableReason(Player viewer) {
        return unavailableReason(viewer, currentProfile());
    }

    @Override
    public Map<Integer, Button> getButtons(Player viewer) {
        Map<Integer, Button> buttons = new HashMap<>();
        PlayerProfile profile = currentProfile();
        String unavailable = unavailableReason(viewer, profile);
        buttons.put(22, new Button() {
            @Override
            public ItemStack getButtonItem(Player player) {
                return new ItemBuilder(Material.ARROW).name("&e返回玩家档案").build();
            }

            @Override
            public void clicked(Player player, int slot, ClickType click, int hotbarButton, ItemStack currentItem) {
                PlayerProfile current = currentProfile();
                new StatusViewerMenu(current == null ? lastKnownProfile : current).openMenu(player);
            }
        });
        if (unavailable != null) {
            clearItems();
            buttons.put(13, display(new ItemBuilder(Material.BARRIER).name("&c无法查看")
                    .lore("&c" + unavailable, "&7状态恢复后自动更新。").build()));
            return buttons;
        }

        Player target = Bukkit.getPlayer(targetId);
        boolean online = target != null && target.isOnline();
        buttons.put(18, display(new ItemBuilder(Material.WATCH)
                .name(online ? "&a玩家在线" : "&7玩家离线")
                .lore(online ? "&7实时显示附魔台内物品。" : "&7显示离线前保存的附魔台物品。").build()));

        String[] current = {profile.getEnchantingItem(), profile.getEnchantingBook(), profile.getEnchantingScience()};
        String[] labels = {"附魔物品", "附魔书", "附魔材料"};
        int[] slots = {11, 13, 15};
        for (int i = 0; i < current.length; i++) {
            if (!Objects.equals(encodedItems[i], current[i])) {
                encodedItems[i] = current[i];
                decodedItems[i] = decode(current[i]);
            }
            buttons.put(slots[i] - 9, display(new ItemBuilder(Material.PAPER).name("&e" + labels[i]).build()));
            ItemStack item = decodedItems[i];
            if (item == null || item.getType() == Material.AIR) {
                item = new ItemBuilder(current[i] == null ? Material.STAINED_GLASS_PANE : Material.BARRIER)
                        .name(current[i] == null ? "&7" + labels[i] + "槽为空" : "&c" + labels[i] + "无法读取").build();
            }
            buttons.put(slots[i], display(item));
        }
        return buttons;
    }

    private ItemStack decode(String encoded) {
        if (encoded == null) return null;
        try {
            return InventoryUtil.deserializeItemStack(encoded);
        } catch (RuntimeException invalidItem) {
            return null;
        }
    }

    private Button display(ItemStack item) {
        return new Button() {
            @Override
            public ItemStack getButtonItem(Player player) {
                return item == null ? null : item.clone();
            }

            @Override
            public void clicked(Player player, int slot, ClickType click, int hotbarButton, ItemStack currentItem) {
                // Display copies never modify inventories or profile data.
            }
        };
    }

    private void clearItems() {
        Arrays.fill(encodedItems, null);
        Arrays.fill(decodedItems, null);
    }

    @Override
    public void onClose(Player player) {
        clearItems();
    }
}
