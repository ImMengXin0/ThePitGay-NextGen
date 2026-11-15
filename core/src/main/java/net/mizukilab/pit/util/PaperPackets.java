package net.mizukilab.pit.util;

import com.mojang.datafixers.util.Pair;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class PaperPackets {
    private static final EquipmentSlot[] SLOTS = { EquipmentSlot.MAINHAND, EquipmentSlot.FEET,
            EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD };
    private PaperPackets() { }
    public static ClientboundSetEquipmentPacket equipment(int id, int oldSlot, ItemStack item) {
        return new ClientboundSetEquipmentPacket(id, List.of(Pair.of(SLOTS[oldSlot], item)));
    }
    public static ClientboundAnimatePacket animation(int id, int action) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(id).writeByte(action);
            return ClientboundAnimatePacket.STREAM_CODEC.decode(buffer);
        } finally { buffer.release(); }
    }
}
