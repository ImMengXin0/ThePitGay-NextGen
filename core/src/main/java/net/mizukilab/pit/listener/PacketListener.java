package net.mizukilab.pit.listener;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.event.PotionAddEvent;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.mizukilab.pit.events.impl.major.RedVSBlueEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;

/** Equipment overrides are applied to a viewer's copy, never to a shared server packet. */
public class PacketListener extends PacketAdapter implements Listener {
    public PacketListener() {
        super(ThePit.getInstance(), PacketType.Play.Server.ENTITY_EQUIPMENT);
        Bukkit.getPluginManager().registerEvents(this, ThePit.getInstance());
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (!(ThePit.getInstance().getEventFactory().getActiveEpicEvent() instanceof RedVSBlueEvent active)) return;
        ClientboundSetEquipmentPacket packet = (ClientboundSetEquipmentPacket) event.getPacket().getHandle();
        Player source = Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getEntityId() == packet.getEntity()).findFirst().orElse(null);
        if (source == null) return;
        Material wool = active.getRedTeam().contains(source.getUniqueId()) ? Material.RED_WOOL
                : active.getBlueTeam().contains(source.getUniqueId()) ? Material.BLUE_WOOL : null;
        if (wool == null) return;
        var slots = packet.getSlots().stream().map(pair -> pair.getFirst() == EquipmentSlot.HEAD
                ? Pair.of(EquipmentSlot.HEAD, CraftItemStack.asNMSCopy(new org.bukkit.inventory.ItemStack(wool)))
                : pair).toList();
        event.setPacket(new PacketContainer(PacketType.Play.Server.ENTITY_EQUIPMENT,
                new ClientboundSetEquipmentPacket(packet.getEntity(), slots)));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotion(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getNewEffect() == null) return;
        PotionAddEvent added = new PotionAddEvent(player, event.getNewEffect());
        added.callEvent();
        if (added.isCancelled()) event.setCancelled(true);
    }
}
