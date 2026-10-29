package cn.mythicland.framework.bukkit.proxy;

import cn.mythicland.framework.proxy.ServerShutdownNotice;
import io.netty.buffer.Unpooled;
import net.minecraft.server.v1_8_R3.MinecraftServer;
import net.minecraft.server.v1_8_R3.PacketDataSerializer;
import net.minecraft.server.v1_8_R3.PacketPlayOutCustomPayload;
import org.bukkit.Server;
import org.bukkit.craftbukkit.v1_8_R3.CraftServer;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;

public final class BukkitShutdownNotifier {
    private BukkitShutdownNotifier() {
    }

    public static void notifyProxy(Server server) {
        if (!isStopping(((CraftServer) server).getServer())) return;
        for (Player player : server.getOnlinePlayers()) {
            if (!player.getListeningPluginChannels().contains(ServerShutdownNotice.CHANNEL)) continue;
            PacketDataSerializer payload =
                    new PacketDataSerializer(Unpooled.wrappedBuffer(ServerShutdownNotice.encode()));
            ((CraftPlayer) player).getHandle().playerConnection.sendPacket(
                    new PacketPlayOutCustomPayload(ServerShutdownNotice.CHANNEL, payload));
        }
    }

    private static boolean isStopping(MinecraftServer server) {
        try {
            Field stopped = MinecraftServer.class.getDeclaredField("hasStopped");
            stopped.setAccessible(true);
            return stopped.getBoolean(server);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot read server shutdown state", exception);
        }
    }
}
