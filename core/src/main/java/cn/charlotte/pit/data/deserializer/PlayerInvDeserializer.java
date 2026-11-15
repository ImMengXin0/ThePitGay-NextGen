package cn.charlotte.pit.data.deserializer;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.sub.PlayerInv;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import net.mizukilab.pit.util.inventory.InventoryUtil;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/8 18:28
 */
public class PlayerInvDeserializer extends JsonDeserializer<PlayerInv> {

    @Override
    public PlayerInv deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        try {
            JsonNode node = p.getCodec().readTree(p);
            if (!node.has("inv") || !node.get("inv").isTextual())
                throw new IllegalArgumentException("Stored player inventory requires an inv string");
            String inv = node.get("inv").asText();

            return this.refreshInv(InventoryUtil.playerInventoryFromString(inv));
        } catch (Exception e) {
            throw com.fasterxml.jackson.databind.JsonMappingException.from(p, "Invalid stored player inventory", e);
        }
    }

    private PlayerInv refreshInv(PlayerInv inv) {
        if (inv == null) return null;
        ItemStack[] clone = inv.getContents().clone();
        for (int i = 0; i < clone.length; i++) {
            ItemStack itemStack = clone[i];
            refreshItem(itemStack, inv.getContents(), i);
        }

        clone = inv.getArmorContents().clone();

        for (int i = 0; i < clone.length; i++) {
            ItemStack itemStack = clone[i];
            refreshItem(itemStack, inv.getArmorContents(), i);
        }

        return inv;
    }

    private void refreshItem(ItemStack itemStack, ItemStack[] inv, int slot) {
        if (ThePit.getApi() == null) {
            return;
        }

        if (itemStack != null) {
            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null) {
                String displayName = meta.getDisplayName();
                if (displayName != null) {
                    meta.setDisplayName(displayName.replace("§b§c§d§e", ""));
                    itemStack.setItemMeta(meta);
                }
            }
        }

        inv[slot] = ThePit.getApi().reformatPitItem(itemStack);
    }
}
