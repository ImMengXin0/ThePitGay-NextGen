package cn.charlotte.pit.data.serializer;

import cn.charlotte.pit.data.sub.PlayerInv;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import net.mizukilab.pit.util.inventory.InventoryUtil;

import java.io.IOException;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/8 18:24
 */
public class PlayerInvSerializer extends JsonSerializer<PlayerInv> {

    @Override
    public void serialize(PlayerInv value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        try {
            String encoded = InventoryUtil.playerInvToString(value);
            gen.writeStartObject();
            gen.writeStringField("inv", encoded);
            gen.writeEndObject();
        } catch (Exception e) {
            throw com.fasterxml.jackson.databind.JsonMappingException.from(gen, "Cannot serialize player inventory", e);
        }
    }
}
