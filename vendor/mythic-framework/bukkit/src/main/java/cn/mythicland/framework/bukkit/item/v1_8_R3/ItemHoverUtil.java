package cn.mythicland.framework.bukkit.item.v1_8_R3;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import net.minecraft.server.v1_8_R3.Item;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;

public final class ItemHoverUtil {
    private ItemHoverUtil() {
    }

    public static String serialize(ItemStack itemStack, String... hiddenTags) {
        net.minecraft.server.v1_8_R3.ItemStack nmsItem = CraftItemStack.asNMSCopy(itemStack);
        if (nmsItem.hasTag()) {
            NBTTagCompound displayTag = (NBTTagCompound) nmsItem.getTag().clone();
            for (String hiddenTag : hiddenTags) displayTag.remove(hiddenTag);
            nmsItem.setTag(displayTag);
        }
        NBTTagCompound compound = new NBTTagCompound();
        nmsItem.save(compound);
        return compound.toString().replaceFirst(
                "id:\"[^\"]+\"",
                "id:" + Item.getId(nmsItem.getItem())
        );
    }

    public static TextComponent component(String text, String itemHoverJson) {
        TextComponent component = new TextComponent(TextComponent.fromLegacyText(text));
        component.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_ITEM,
                new BaseComponent[]{new TextComponent(itemHoverJson)}
        ));
        return component;
    }

    public static void validateMessage(BaseComponent... message) {
        if (ComponentSerializer.toString(message).getBytes(StandardCharsets.UTF_8).length > 32767) {
            throw new IllegalArgumentException("Item hover message exceeds the chat packet limit");
        }
    }
}
