package net.mizukilab.pit.util.sign;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.PacketPlayOutOpenSignEditor;
import net.minecraft.server.v1_8_R3.PlayerConnection;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/19 21:33
 */
public class SignGui {

    protected Map<String, Vector> signLocations = new ConcurrentHashMap<>();
    protected Map<String, SignGUIListener> listeners = new ConcurrentHashMap<>();

    public SignGui(JavaPlugin plugin) {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(plugin, PacketType.Play.Client.UPDATE_SIGN) {
                    @Override
                    public void onPacketReceiving(PacketEvent event) {
                        final Player player = event.getPlayer();
                        Vector v = signLocations.get(player.getName());
                        if (v == null) return;
                        com.comphenix.protocol.wrappers.BlockPosition bp = event.getPacket().getBlockPositionModifier().
                                getValues().get(0);
                        if (bp.getX() != v.getBlockX()) {
                            return;
                        }
                        if (bp.getY() != v.getBlockY()) {
                            return;
                        }
                        if (bp.getZ() != v.getBlockZ()) {
                            return;
                        }
                        event.setCancelled(true);
                        final WrappedChatComponent[] chatarray = event.getPacket().getChatComponentArrays().readSafely(0);
                        if (chatarray == null || chatarray.length != 4) return;
                        final String[] lines = new String[4];
                        try {
                            for (int i = 0; i < 4; i++) lines[i] = plainText(chatarray[i].getJson());
                        } catch (RuntimeException malformed) { return; }
                        if (!signLocations.remove(player.getName(), v)) return;
                        final SignGUIListener response = listeners.remove(player.getName());
                        if (response != null) {
                            event.setCancelled(true);
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                if (player.isOnline()) response.onSignDone(player, lines);
                            }, 1);
                        }
                    }
                });
    }

    static String plainText(String json) {
        return TextComponent.toPlainText(ComponentSerializer.parse(json));
    }

    public void cancel(Player player) {
        signLocations.remove(player.getName());
        listeners.remove(player.getName());
    }

    public void open(Player player, String[] messages, SignGUIListener response) {
        Location loc = new Location(player.getWorld(), 0, 1, 0);
        player.sendBlockChange(loc, Material.SIGN_POST, (byte) 0);
        player.sendSignChange(loc, messages);

        try {
            EntityPlayer handle = ((CraftPlayer) player).getHandle();
            PlayerConnection connection = handle.playerConnection;

            PacketPlayOutOpenSignEditor packet = new PacketPlayOutOpenSignEditor(
                    new BlockPosition(
                            loc.getX(),
                            loc.getY(),
                            loc.getZ()
                    )
            );

            signLocations.put(player.getName(), loc.toVector());
            listeners.put(player.getName(), response);
            connection.sendPacket(packet);
        } catch (Exception x) {
            cancel(player);
            throw new IllegalStateException("Unable to open sign editor", x);
        }
    }

    public interface SignGUIListener {

        void onSignDone(Player player, String[] lines);
    }

}
