package cn.mythicland.framework.bukkit.proxy;

import cn.mythicland.framework.proxy.ServerShutdownNotice;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;
import org.bukkit.Server;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

public final class BukkitShutdownNotifier {
    private BukkitShutdownNotifier() {}
    public static void notifyProxy(Server server) {
        if (!server.isStopping()) return;
        var packet = new ClientboundCustomPayloadPacket(new DiscardedPayload(
                Identifier.parse(ServerShutdownNotice.CHANNEL), ServerShutdownNotice.encode()));
        for (Player player : server.getOnlinePlayers()) {
            if (player.getListeningPluginChannels().contains(ServerShutdownNotice.CHANNEL))
                ((CraftPlayer) player).getHandle().connection.send(packet);
        }
    }
}
