package cn.mythicland.framework.bukkit.diagnostics;

import cn.mythicland.framework.diagnostics.DiagnosticRecorder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@ChannelHandler.Sharable
final class DiagnosticWire extends ChannelOutboundHandlerAdapter {
    private final DiagnosticRecorder recorder;
    private final UUID observer;
    private final String connection;
    private final DiagnosticPacketSelection selection;
    private final ProtocolInfo<ClientGamePacketListener> protocol = GameProtocols.CLIENTBOUND_TEMPLATE.bind(
            RegistryFriendlyByteBuf.decorator(MinecraftServer.getServer().registryAccess()));
    private final MessageDigest digest;
    private long sequence;

    DiagnosticWire(
            DiagnosticRecorder recorder,
            UUID observer,
            String connection,
            DiagnosticPacketSelection selection,
            DiagnosticOptions options
    ) {
        this.recorder = recorder;
        this.observer = observer;
        this.connection = connection;
        this.selection = selection.copy();
        try {
            this.digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    void position(ChannelPipeline pipeline, String name) {
        String anchor = pipeline.get("compress") == null ? "prepender" : "compress";
        if (pipeline.get(anchor) == null) throw new IllegalStateException("Missing outbound frame boundary: " + pipeline.names());
        var names = pipeline.names();
        if (names.indexOf(name) == names.indexOf(anchor) + 1) return;
        if (pipeline.get(name) != null) pipeline.remove(name);
        pipeline.addAfter(anchor, name, this);
    }

    @Override
    public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
        if (this.recorder.isRecording() && message instanceof ByteBuf bytes && bytes.isReadable()) {
            try {
                String anchor = context.pipeline().get("compress") == null ? "prepender" : "compress";
                var names = context.pipeline().names();
                if (names.indexOf(context.name()) != names.indexOf(anchor) + 1)
                    throw new IllegalStateException("Outbound diagnostic boundary changed: " + names);
                this.capture(bytes);
            } catch (RuntimeException failure) {
                this.recorder.fail("outbound.encoded", failure);
            }
        }
        context.write(message, promise);
    }

    private void capture(ByteBuf bytes) {
        ByteBuf input = bytes.duplicate();
        int protocolId = net.minecraft.network.VarInt.read(input.duplicate());
        if (bytes.readableBytes() > 2 * 1024 * 1024)
            throw new IllegalArgumentException("Encoded diagnostic packet exceeds 2 MiB");
        Packet<?> packet = protocol.codec().decode(input);
        if (input.isReadable()) throw new IllegalArgumentException("Trailing bytes in encoded " + packet.getClass().getSimpleName());
        if (!this.selection.relevant(this.observer, packet, true)) return;
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("player", this.observer.toString());
        fields.put("connection", this.connection);
        fields.put("sequence", ++this.sequence);
        fields.put("protocolId", protocolId);
        fields.put("type", packet.getClass().getName());
        fields.put("bytes", bytes.readableBytes());
        fields.put("payload", this.selection.snapshot(packet));
        for (var buffer : bytes.nioBuffers(bytes.readerIndex(), bytes.readableBytes())) this.digest.update(buffer);
        fields.put("sha256", HexFormat.of().formatHex(this.digest.digest()));
        this.recorder.record("outbound.encoded", fields);
        this.selection.sent(this.observer, packet);
    }
}
