package cn.mythicland.framework.bukkit.message;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class BukkitActionBarSender {
    public void send(Player player, String message) {
        if (player == null || message == null || message.isBlank()) return;
        try {
            String version = this.detectVersion();
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
            Class<?> packetClass = Class.forName("net.minecraft.server." + version + ".Packet");
            Class<?> entityPlayerClass = Class.forName("net.minecraft.server." + version + ".EntityPlayer");
            Class<?> playerConnectionClass = Class.forName("net.minecraft.server." + version + ".PlayerConnection");
            Class<?> chatComponentType = Class.forName("net.minecraft.server." + version + ".IChatBaseComponent");
            Class<?> chatSerializerClass = Class.forName(
                    "net.minecraft.server." + version + ".IChatBaseComponent$ChatSerializer"
            );
            Class<?> packetType = Class.forName("net.minecraft.server." + version + ".PacketPlayOutChat");

            Object craftPlayer = craftPlayerClass.cast(player);
            Object entityPlayer = craftPlayerClass.getMethod("getHandle").invoke(craftPlayer);
            Object connection = entityPlayerClass.getField("playerConnection").get(entityPlayer);
            Object component = chatSerializerClass.getMethod("a", String.class).invoke(
                    null,
                    "{\"text\":\"" + this.escapeJson(ChatColor.translateAlternateColorCodes('&', message)) + "\"}"
            );
            Constructor<?> packetConstructor = packetType.getConstructor(chatComponentType, byte.class);
            Object packet = packetConstructor.newInstance(component, (byte) 2);
            Method sendPacket = playerConnectionClass.getMethod("sendPacket", packetClass);
            sendPacket.invoke(connection, packet);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private String detectVersion() {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
