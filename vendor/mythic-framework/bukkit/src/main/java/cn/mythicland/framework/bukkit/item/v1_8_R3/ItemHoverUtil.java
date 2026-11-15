package cn.mythicland.framework.bukkit.item.v1_8_R3;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.inventory.ItemStack;
import java.nio.charset.StandardCharsets;

public final class ItemHoverUtil {
    private ItemHoverUtil() {}
    /** Returns a modern hover-bearing component as JSON; paired with component(). */
    public static String serialize(ItemStack itemStack, String... hiddenTags) {
        ItemStack preview = itemStack.clone();
        var nms = org.bukkit.craftbukkit.inventory.CraftItemStack.unwrap(preview);
        var custom = cn.mythicland.framework.bukkit.item.PaperItemData.custom(nms);
        for (String key : hiddenTags) custom.remove(key);
        cn.mythicland.framework.bukkit.item.PaperItemData.custom(nms, custom);
        return GsonComponentSerializer.gson().serialize(Component.empty().hoverEvent(preview.asHoverEvent()));
    }
    public static net.md_5.bungee.api.chat.HoverEvent hover(ItemStack item) {
        return component("", serialize(item)).getHoverEvent();
    }
    public static TextComponent component(String text, String itemHoverJson) {
        TextComponent result = new TextComponent(TextComponent.fromLegacyText(text));
        BaseComponent[] preview = ComponentSerializer.parse(itemHoverJson);
        if (preview.length != 0) result.setHoverEvent(preview[0].getHoverEvent());
        return result;
    }
    public static void validateMessage(BaseComponent... message) {
        if (ComponentSerializer.toString(message).getBytes(StandardCharsets.UTF_8).length > 32767)
            throw new IllegalArgumentException("Item hover message exceeds the chat packet limit");
    }
}
