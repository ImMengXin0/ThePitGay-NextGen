package net.mizukilab.pit.enchantment.menu;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import io.irina.backports.utils.SWMRHashTable;
import lombok.Getter;
import lombok.Setter;
import net.mizukilab.pit.config.NewConfiguration;
import net.mizukilab.pit.enchantment.menu.button.*;
import net.mizukilab.pit.handlers.AnimationHandler;
import net.mizukilab.pit.item.IMythicItem;
import net.mizukilab.pit.item.MythicColor;
import net.mizukilab.pit.item.type.mythic.MagicFishingRod;
import net.mizukilab.pit.item.type.mythic.MythicBowItem;
import net.mizukilab.pit.item.type.mythic.MythicLeggingsItem;
import net.mizukilab.pit.item.type.mythic.MythicSwordItem;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.PublicUtil;
import net.mizukilab.pit.util.Utils;
import net.mizukilab.pit.util.chat.CC;
import net.mizukilab.pit.util.exception.CancelOp;
import net.mizukilab.pit.util.inventory.InventoryUtil;
import net.mizukilab.pit.util.item.ItemBuilder;
import net.mizukilab.pit.util.item.ItemUtil;
import net.mizukilab.pit.util.menu.Button;
import net.mizukilab.pit.util.menu.Menu;
import net.mizukilab.pit.util.menu.buttons.DisplayButton;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/17 14:23
 */
@Getter
@Setter
public class MythicWellMenu extends Menu {

    private final int[] ANIMATIONS_SLOT = new int[]{
            19, 10, 11, 12, 21, 30, 29, 28
    };
    private final int INPUT_SLOT = 20;
    private final int CLICK_SLOT = 25;
    private final MythicWellMenu instance;
    private AnimationHandler.AnimationData animationData;

    static ItemStack[] stacks = null;

    static {
        List<ItemStack> stacksList = new ArrayList<>();
        stacksList.add(new MythicSwordItem().toItemStack());
        stacksList.add(new MythicBowItem().toItemStack());
        stacksList.add(new MagicFishingRod().toItemStack());
        MythicLeggingsItem mythicLeggingsItem = new MythicLeggingsItem();
        for (MythicColor color : MythicColor.values()) {
            mythicLeggingsItem.color = color;
            stacksList.add(mythicLeggingsItem.toItemStack());
        }
        stacks = stacksList.toArray(new ItemStack[0]);
    }


    public MythicWellMenu(Player player) {
        this.instance = this;
        this.animationData = new AnimationHandler.AnimationData(player);
    }

    @Override
    public void onOpen(Player player) {
        // Menu.openMenu closes the previous container before invoking onOpen.
        // Register here so the old menu cannot release the new menu's animation.
        AnimationHandler animator = ThePit.getInstance().getAnimator();
        AnimationHandler.AnimationData active = animator.holdOrGetAnimation(player);
        if (this.animationData != active) {
            this.animationData = active;
            animator.sendStart(player);
        }
    }

    @Override
    public String getTitle(Player player) {
        return "神话之井";
    }

    @Override
    public Map<Integer, Button> getButtons(Player player) {
        Map<Integer, Button> button = new SWMRHashTable<>();
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        // Keep remaining scrolls accessible after the item reaches its last tier or is removed.
        if ((animationData.isFinished() || (!animationData.isStartEnchanting() && animationData.isEnd()))
                && profile.getEnchantingBook() != null) {
            button.put(23, new EnchantBookButton(this, MythicColor.NONE));
        }
        if (animationData.isFinished()) {
            String mythic_color = ItemUtil.getItemStringData(InventoryUtil.deserializeItemStack(profile.getEnchantingItem()), "mythic_color");
            MythicColor foundColor = null;
            for (MythicColor color : MythicColor.values()) {
                if (color.getInternalName().equals(mythic_color)) {
                    foundColor = color;
                    break;
                }
            }
            if (foundColor == null) {
                return button;
            }

            for (int i : ANIMATIONS_SLOT) {
                button.put(i, new DisplayButton(new ItemBuilder(Material.STAINED_GLASS_PANE).name("&c").durability(foundColor.getColorByte()).build(), true));
            }
            String enchantingItem = profile.getEnchantingItem();
            ItemStack itemStack = InventoryUtil.deserializeItemStack(enchantingItem);
            if (itemStack == null || itemStack.getType() == Material.AIR) {
                return button;
            }
            button.put(INPUT_SLOT, new EnchantDisplayButton(itemStack, this, false));
            button.put(CLICK_SLOT, new EnchantButton(itemStack, this));

            button.put(17, new EnchantmentDisplayButton(0));
            button.put(26, new EnchantmentDisplayButton(1));
            button.put(35, new EnchantmentDisplayButton(2));

            Integer tier = ItemUtil.getItemIntData(itemStack, "tier");
            if (tier != null && tier == (foundColor == MythicColor.DARK ? 1 : 2)) {
                button.put(14, new EnchantSinceButton(this, foundColor));
                button.remove(23);
                button.put(32, new EnchantBookButton(this, foundColor));
            } else if (tier != null && tier != (foundColor == MythicColor.DARK ? 2 : 3)) {
                button.put(23, new EnchantBookButton(this, foundColor));
            }

            return button;
        }


        if (animationData.isStartEnchanting()) {
            MythicColor foundColor = animationData.resolveColor(profile.getEnchantingItem());
            if (foundColor == null) {
                return button;
            }

            int tick = animationData.getAnimationTick();
            if (tick <= 11) {
                byte[] colors = {14, 1, 4, 5, 11, 10, 6, 0};
                byte currentColor = colors[tick % colors.length];
                for (int i : ANIMATIONS_SLOT) {
                    button.put(i, new DisplayButton(new ItemBuilder(Material.STAINED_GLASS_PANE).name("&c").durability(currentColor).build(), true));
                }
                button.put(INPUT_SLOT, new DisplayButton(new ItemBuilder(Material.STAINED_GLASS_PANE)
                        .name("&a能量共鸣中!")
                        .durability(currentColor)
                        .build(), true));
            } else if (tick <= 35) {
                int rotationIndex = ((tick - 12) / 3) % 8;
                for (int i = 0; i < ANIMATIONS_SLOT.length; i++) {
                    if (rotationIndex == i) {
                        button.put(ANIMATIONS_SLOT[i], new DisplayButton(new ItemBuilder(Material.STAINED_GLASS_PANE).name("&c").durability(foundColor.getColorByte()).build(), true));
                    } else {
                        button.put(ANIMATIONS_SLOT[i], new DisplayButton(new ItemBuilder(Material.STAINED_GLASS_PANE).name("&c").durability(0).build(), true));
                    }
                }
                button.put(INPUT_SLOT, new DisplayButton(new ItemBuilder(Material.INK_SACK)
                        .name("&a正在聚集能量!")
                        .durability(Math.min((tick - 12) / 3, 15))
                        .build(), true));

            } else if (tick <= 59) {
                int burstIndex = ((tick - 36) / 3) % 8;
                for (int i = 0; i < ANIMATIONS_SLOT.length; i++) {
                    if (i <= burstIndex) {
                        button.put(ANIMATIONS_SLOT[i], new DisplayButton(new ItemBuilder(Material.STAINED_GLASS_PANE).name("&c").durability(foundColor.getColorByte()).build(), true));
                    } else {
                        button.put(ANIMATIONS_SLOT[i], new DisplayButton(new ItemBuilder(Material.STAINED_GLASS_PANE).name("&c").durability(0).build(), true));
                    }
                }
                button.put(INPUT_SLOT, new DisplayButton(new ItemBuilder(Material.INK_SACK)
                        .name("&a能量汇聚中!")
                        .durability(Math.min(burstIndex + 8, 15))
                        .build(), true));
            } else if (animationData.isEnd()) {
                String enchantingItem = profile.getEnchantingItem();
                ItemStack itemStack = InventoryUtil.deserializeItemStack(enchantingItem);
                for (int i : ANIMATIONS_SLOT) {
                    button.put(i, new DisplayButton(new ItemBuilder(Material.STAINED_GLASS_PANE).name("&c").durability(foundColor.getColorByte()).build(), true));
                }
                if (itemStack != null && itemStack.getType() != Material.AIR) {
                    button.put(INPUT_SLOT, new EnchantDisplayButton(itemStack, this, false));
                    button.put(17, new EnchantmentDisplayButton(0));
                    button.put(26, new EnchantmentDisplayButton(1));
                    button.put(35, new EnchantmentDisplayButton(2));
                    button.put(CLICK_SLOT, new EnchantButton(itemStack, this));
                    Integer tier = ItemUtil.getItemIntData(itemStack, "tier");
                    if (tier != null && tier == (foundColor == MythicColor.DARK ? 1 : 2)) {
                        button.put(14, new EnchantSinceButton(this, foundColor));
                    } else if (tier != null && tier != (foundColor == MythicColor.DARK ? 2 : 3)) {
                    }
                } else {
                    button.put(INPUT_SLOT, new DisplayButton(new ItemBuilder(Material.EMERALD)
                            .name("&c&lWRONG!")
                            .lore("&7YOU MIGHT MEET A BUG, IT NEVER HAPPENED")
                            .build(), true));
                }
            }
        } else if(animationData.isEnd()){
            for (int i = 0; i < ANIMATIONS_SLOT.length; i++) {
                int currentPosition = (animationData.getAnimationTick() / 4) % 8;
                if (currentPosition == i) {
                    button.put(ANIMATIONS_SLOT[i], new AnimationButton(animationData.getColor()));
                } else {
                    button.put(ANIMATIONS_SLOT[i], new AnimationButton(0));
                }
            }
            ItemStack enchantingItem;
            String enchantingItemStr = profile.getEnchantingItem();
            if (enchantingItemStr == null) {
                enchantingItem = new ItemStack(Material.AIR);
            } else {
                enchantingItem = InventoryUtil.deserializeItemStack(enchantingItemStr);
                if (enchantingItem == null) {
                    enchantingItem = new ItemStack(Material.AIR);
                }
            }
            if (enchantingItem.getType() != Material.AIR) {

                String mythic_color = ItemUtil.getItemStringData(enchantingItem, "mythic_color");
                if (mythic_color != null) {
                    for (MythicColor color : MythicColor.values()) {
                        if (color.getInternalName().equals(mythic_color)) {
                            byte colorByte = color.getColorByte();
                            this.animationData.setColor(colorByte);

                            Integer tier = ItemUtil.getItemIntData(enchantingItem, "tier");
                            if (tier != null && tier == (color == MythicColor.DARK ? 1 : 2)) {
                                button.put(14, new EnchantSinceButton(this, color));
                                button.remove(23);
                                button.put(32, new EnchantBookButton(this, color));
                            } else if (tier != null && tier != (color == MythicColor.DARK ? 2 : 3)) {
                                button.put(23, new EnchantBookButton(this, color));
                            }
                            break;
                        }
                    }
                }


            } else {
                this.animationData.setColor((byte) 6);
            }
            if (enchantingItem.getType() != Material.AIR) {
                button.put(INPUT_SLOT, new EnchantDisplayButton(enchantingItem, this, false));
                button.put(CLICK_SLOT, new EnchantButton(enchantingItem, this));

                button.put(17, new EnchantmentDisplayButton(0));
                button.put(26, new EnchantmentDisplayButton(1));
                button.put(35, new EnchantmentDisplayButton(2));
            } else {
                int currentTick = (int) (Utils.toUnsignedInt(animationData.getAnimationGlobalTick()) / 8L);
                if (!button.containsKey(CLICK_SLOT - 2)) {
                    button.put(CLICK_SLOT - 2, new DisplayButton(stacks[currentTick % stacks.length], true));
                }
                button.put(CLICK_SLOT, new EnchantButton(enchantingItem, this));
            }
        }

        return button;
    }

    @Override
    public void onClickEvent(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (animationData.isStartEnchanting() && !animationData.isFinished()) {
            return;
        }

        if (event.getClickedInventory() instanceof PlayerInventory) {
            event.setCancelled(true);
            ClickType click = event.getClick();
            if (click != ClickType.LEFT && click != ClickType.RIGHT
                    && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return;
            if (event.getCursor() != null && event.getCursor().getType() != Material.AIR) {
                player.sendMessage(CC.translate("&cDont cursor sth.."));
                return;
            }
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType() == Material.AIR) {
                return;
            }

            String internalName = ItemUtil.getInternalName(item);
            boolean isIMythicItem = "mythic_sword".equals(internalName) || "mythic_bow".equals(internalName) || "mythic_leggings".equals(internalName) || "mythic_reel".equals(internalName);

            if (!isIMythicItem) {
                player.sendMessage(CC.translate("&c你必须放入未被附魔的神话武器才可以进行附魔!"));
                player.sendMessage(CC.translate("&c神话武器有概率从战斗中掉落!"));

                player.playSound(player.getLocation(), Sound.VILLAGER_NO, 1, 1.2F);
                return;
            }


            PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
            if ("mythic_reel".equals(internalName)) {
                insertScrolls(event, player, profile, item);
                return;
            }
            if (InventoryUtil.isInvFull(player)) {
                player.sendMessage(CC.translate("&c背包已满,无法操作背包物品!"));
                return;
            }

            if (item.getType() == Material.LEATHER_LEGGINGS) {
                if (PlayerUtil.getPlayerUnlockedPerkLevel(player, "Mythicism") < 9 && profile.getEnchantingItem() == null) {
                    player.sendMessage(CC.translate("&c你还不能升级 &c神&6话&9之&a甲 &c!"));
                    player.sendMessage(CC.translate("&c请升级天赋 &6神话附魔师 &c至等级 &eIX &c后重试!"));
                    return;
                }
            }

            ItemStack returnItem = null;
            if (profile.getEnchantingItem() != null) {
                //检查是否为tier 2
                ItemStack itemStack = InventoryUtil.deserializeItemStack(profile.getEnchantingItem());
                if (itemStack != null && itemStack.getType() != Material.AIR) {
                    Integer tier = ItemUtil.getItemIntData(itemStack, "tier");
                    String mythic_color = ItemUtil.getItemStringData(itemStack, "mythic_color");
                    if (tier != null && tier == ("DARK".equalsIgnoreCase(mythic_color) ? 1 : 2)) {
                        //判断是否放入的是神话甲
                        if (item.getType() == Material.LEATHER_LEGGINGS) {
                            //是tier2 所以获取物品颜色
                            //edit: 因为dark pants在t1需要额外材料 此步骤代码提前
                            for (MythicColor color : MythicColor.values()) {
                                if (color.getInternalName().equals(mythic_color)) {
                                    //找到点击物品颜色
                                    String mythicColor = ItemUtil.getItemStringData(item, "mythic_color");
                                    for (MythicColor targetColor : MythicColor.values()) {
                                        if (targetColor.getInternalName().equals(mythicColor)) {
                                            //再检查点击物品颜色是否符合附魔物品
                                            if (color == targetColor) {
                                                //如果之前放了一条，那么退还给玩家
                                                if (profile.getEnchantingScience() != null) {
                                                    ItemStack oldSince = InventoryUtil.deserializeItemStack(profile.getEnchantingScience());
                                                    if (oldSince != null && oldSince.getType() != Material.AIR) {
                                                        if (InventoryUtil.isInvFull(player)) {
                                                            player.sendMessage("&c你的背包已满,无法进行此操作!");
                                                            return;
                                                        }
                                                        player.getInventory().addItem(oldSince);
                                                    }
                                                }
                                                //放入材料Slot
                                                profile.setEnchantingScience(InventoryUtil.serializeItemStack(event.getCurrentItem()));
                                                event.getClickedInventory().setItem(event.getSlot(), new ItemBuilder(Material.AIR).build());
                                                this.openMenu(player);
                                                player.playSound(player.getLocation(), Sound.CHICKEN_EGG_POP, 1, 0.9F);
                                                return;
                                            }
                                        }
                                    }
                                    player.playSound(player.getLocation(), Sound.VILLAGER_NO, 1, 1.2F);
                                    player.sendMessage(CC.translate("&c颜色不符合! 您需要放入 " + color.getChatColor() + color.getDisplayName() + "色神话之甲 &c作为附魔材料."));
                                    return;
                                }
                            }
                        } else {
                            return;
                        }
                    } else {
                        returnItem = itemStack;
                    }
                }
            }
            // A cached object may belong to an older copy or a colliding UUID hash.
            // Depositing is not enchanting: only the actual input NBT is authoritative.
            // Bukkit 1.8 ItemMeta.clone can share custom NBT compounds; copy the NMS
            // tags as well before initializing an identity on a legacy/blank item.
            ItemStack inputCopy = CraftItemStack.asCraftMirror(Utils.toNMStackQuick(item).cloneItemStack());
            IMythicItem mythicItem = Utils.getMythicItem0(inputCopy, internalName);
            if (mythicItem == null) return;
            if (mythicItem.getUuid() == null || IMythicItem.getDefUUID().equals(mythicItem.getUuid())
                    || ItemUtil.shouldUpdateUUIDAndItem(inputCopy)) {
                mythicItem.setUuid(ItemUtil.randomUUIDItem(inputCopy));
            }
            mythicItem.setVersion(PublicUtil.itemVersion);
            String encoded = InventoryUtil.serializeItemStack(mythicItem.toItemStack());
            if (encoded == null) {
                player.sendMessage(CC.translate("&c物品保存失败，请重试。"));
                return;
            }
            // Prepare the new value before moving either item, then swap through the
            // clicked slot so returning an identical item cannot merge and be erased.
            profile.setEnchantingItem(encoded);
            event.getClickedInventory().setItem(event.getSlot(), returnItem);
            IMythicItem.clearCache(item);
            this.openMenu(player);
            player.playSound(player.getLocation(), Sound.CHICKEN_EGG_POP, 1, 0.9F);
        }
    }

    private void insertScrolls(InventoryClickEvent event, Player player, PlayerProfile profile, ItemStack scrolls) {
        ItemStack target = InventoryUtil.deserializeItemStack(profile.getEnchantingItem());
        if (target == null || target.getType() == Material.AIR || scrolls.getAmount() <= 0) return;
        Integer tier = ItemUtil.getItemIntData(target, "tier");
        String color = ItemUtil.getItemStringData(target, "mythic_color");
        if (tier == null || color == null || tier >= ("dark".equalsIgnoreCase(color) ? 2 : 3)) return;

        String originalBook = profile.getEnchantingBook();
        ItemStack oldBook = InventoryUtil.deserializeItemStack(originalBook);
        if (originalBook != null && !"null".equals(originalBook)
                && (oldBook == null || oldBook.getType() == Material.AIR || oldBook.getAmount() <= 0)) {
            player.sendMessage(CC.translate("&c卷轴数据异常，未移动任何物品，请联系管理员。"));
            return;
        }
        String encoded = InventoryUtil.serializeItemStack(scrolls.clone());
        if (encoded == null) {
            player.sendMessage(CC.translate("&c卷轴保存失败，请重试。"));
            return;
        }
        // Swap through the clicked slot: addItem could merge the old scrolls into the
        // incoming stack just before that slot is cleared, losing them both.
        event.getClickedInventory().setItem(event.getSlot(), oldBook);
        profile.setEnchantingBook(encoded);
        this.openMenu(player);
        player.playSound(player.getLocation(), Sound.CHICKEN_EGG_POP, 1, 0.9F);
    }

    @Override
    public int getSize() {
        return 5 * 9;
    }

    @Override
    public void onClose(Player player) {

        if (!isClosedByMenu() && player.isOnline() && !player.isDead() && !NewConfiguration.INSTANCE.getRapidEnchanting()) {
            if (animationData.isStartEnchanting() && !animationData.isFinished()) {
                player.sendMessage(CC.translate("&c附魔进行中，无法关闭界面！"));
                player.playSound(player.getLocation(), Sound.VILLAGER_NO, 1, 1.2F);
                throw CancelOp.INST;
            }
        }

        // 正常关闭逻辑
        ThePit.getInstance().getAnimator().releaseAnimation(player, animationData);
        this.animationData.setFinished(true);
    }
}
