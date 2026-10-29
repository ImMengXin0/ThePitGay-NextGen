package cn.mythicland.framework.bukkit.message;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class BukkitTitleSender {
    public void send(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player == null) return;
        try {
            String version = this.detectVersion();
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
            Class<?> entityPlayerClass = Class.forName("net.minecraft.server." + version + ".EntityPlayer");
            Class<?> playerConnectionClass = Class.forName("net.minecraft.server." + version + ".PlayerConnection");
            Class<?> packetClass = Class.forName("net.minecraft.server." + version + ".Packet");
            Class<?> titlePacketClass = Class.forName("net.minecraft.server." + version + ".PacketPlayOutTitle");
            Class<?> titleActionClass = Class.forName(
                    "net.minecraft.server." + version + ".PacketPlayOutTitle$EnumTitleAction"
            );
            Class<?> chatComponentTextClass = Class.forName(
                    "net.minecraft.server." + version + ".ChatComponentText"
            );
            Class<?> chatComponentClass = Class.forName(
                    "net.minecraft.server." + version + ".IChatBaseComponent"
            );

            Method getHandle = craftPlayerClass.getMethod("getHandle");
            Field playerConnection = entityPlayerClass.getField("playerConnection");
            Method sendPacket = playerConnectionClass.getMethod("sendPacket", packetClass);
            Constructor<?> chatComponentConstructor = chatComponentTextClass.getConstructor(String.class);
            Constructor<?> titleConstructor = titlePacketClass.getConstructor(
                    titleActionClass,
                    chatComponentClass,
                    int.class,
                    int.class,
                    int.class
            );

            Object handle = getHandle.invoke(player);
            Object connection = playerConnection.get(handle);
            Object timesPacket = titleConstructor.newInstance(
                    this.enumConstant(titleActionClass, "TIMES"),
                    chatComponentConstructor.newInstance(""),
                    fadeIn,
                    stay,
                    fadeOut
            );
            sendPacket.invoke(connection, timesPacket);
            if (subtitle != null) {
                Object subtitlePacket = titleConstructor.newInstance(
                        this.enumConstant(titleActionClass, "SUBTITLE"),
                        chatComponentConstructor.newInstance(ChatColor.translateAlternateColorCodes('&', subtitle)),
                        fadeIn,
                        stay,
                        fadeOut
                );
                sendPacket.invoke(connection, subtitlePacket);
            }
            if (title != null) {
                Object titlePacket = titleConstructor.newInstance(
                        this.enumConstant(titleActionClass, "TITLE"),
                        chatComponentConstructor.newInstance(ChatColor.translateAlternateColorCodes('&', title)),
                        fadeIn,
                        stay,
                        fadeOut
                );
                sendPacket.invoke(connection, titlePacket);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private String detectVersion() {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }

    private Object enumConstant(Class<?> enumType, String name) {
        for (Object constant : enumType.getEnumConstants()) {
            if (constant instanceof Enum<?> enumConstant && enumConstant.name().equals(name)) return constant;
        }
        throw new IllegalArgumentException("Unknown enum constant " + enumType.getName() + "." + name);
    }
}
