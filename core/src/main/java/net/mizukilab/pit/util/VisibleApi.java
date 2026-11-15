package net.mizukilab.pit.util;

import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.NameTagVisibility;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Araykal
 * @since 2025/1/17
 */
public class VisibleApi {
    private static final net.minecraft.world.entity.EquipmentSlot[] SLOTS = {
        net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.entity.EquipmentSlot.FEET,
        net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.CHEST,
        net.minecraft.world.entity.EquipmentSlot.HEAD
    };
    private static ClientboundSetEquipmentPacket packet(Player player, int slot, ItemStack item) {
        return new ClientboundSetEquipmentPacket(player.getEntityId(),
                List.of(com.mojang.datafixers.util.Pair.of(SLOTS[slot], item)));
    }
    private static void sendPacket(Player player, Packet<?> packet) {
        ((CraftPlayer)player).getHandle().connection.send(packet);
    }

    public static void hideArmor(Player player) {
        ItemStack nmsCopy = CraftItemStack.asNMSCopy(new org.bukkit.inventory.ItemStack(Material.AIR));
        hideNameTag(player);
        for(int i = 1; i <= 4; ++i) {
            ClientboundSetEquipmentPacket packet = packet(player, i, nmsCopy);

            for(Player target : Bukkit.getOnlinePlayers()) {
                if (!target.equals(player)) {
                    sendPacket(target, packet);
                }
            }
        }

    }

    public static void hidePlayerFromAll(Player target) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.equals(target)) {
                player.hidePlayer(target);
            }
        }
    }

    public static void showPlayerToAll(Player target) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.equals(target)) {
                player.showPlayer(target);
            }
        }
    }
    private static void hideNameTag(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam("hiddenNameTags");

        if (team == null) {
            team = scoreboard.registerNewTeam("hiddenNameTags");
            team.setNameTagVisibility(NameTagVisibility.NEVER);
        }

        team.addEntry(player.getName());
    }


    private static void showNameTag(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam("hiddenNameTags");

        if (team != null && team.hasEntry(player.getName())) {
            team.removeEntry(player.getName());
        }
    }
    public static void showArmor(Player player) {
        List<ClientboundSetEquipmentPacket> packets = new ArrayList();
        packets.add(packet(player, 1, CraftItemStack.asNMSCopy(player.getEquipment().getBoots())));
        packets.add(packet(player, 2, CraftItemStack.asNMSCopy(player.getEquipment().getLeggings())));
        packets.add(packet(player, 3, CraftItemStack.asNMSCopy(player.getEquipment().getChestplate())));
        packets.add(packet(player, 4, CraftItemStack.asNMSCopy(player.getEquipment().getHelmet())));
        showNameTag(player);
        for(Player target : Bukkit.getOnlinePlayers()) {
            if (!target.equals(player)) {
                for(ClientboundSetEquipmentPacket packet : packets) {
                    sendPacket(target, packet);
                }
            }
        }

    }

}
