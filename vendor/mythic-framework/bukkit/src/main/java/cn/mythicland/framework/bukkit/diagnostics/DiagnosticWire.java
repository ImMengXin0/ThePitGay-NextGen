package cn.mythicland.framework.bukkit.diagnostics;

import cn.mythicland.framework.diagnostics.DiagnosticRecorder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import net.minecraft.server.v1_8_R3.EnumProtocol;
import net.minecraft.server.v1_8_R3.EnumProtocolDirection;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketDataSerializer;
import java.lang.reflect.Constructor;

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
    private final Map<Integer, Constructor<? extends Packet>> types = new HashMap<>();
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
            for (int id = 0; id < 256; id++) {
                Packet packet = EnumProtocol.PLAY.a(EnumProtocolDirection.CLIENTBOUND, id);
                if (packet != null && options.outbound(packet.getClass().getSimpleName()))
                    this.types.put(id, packet.getClass().getDeclaredConstructor());
            }
        } catch (NoSuchAlgorithmException | ReflectiveOperationException failure) {
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
            } catch (IOException | ReflectiveOperationException | RuntimeException failure) {
                this.recorder.fail("outbound.encoded", failure);
            }
        }
        context.write(message, promise);
    }

    private void capture(ByteBuf bytes) throws IOException, ReflectiveOperationException {
        PacketDataSerializer input = new PacketDataSerializer(bytes.duplicate());
        int protocolId = input.e();
        Constructor<? extends Packet> constructor = this.types.get(protocolId);
        if (constructor == null) return;
        if (bytes.readableBytes() > 2 * 1024 * 1024)
            throw new IllegalArgumentException("Encoded diagnostic packet exceeds 2 MiB");
        Packet packet = constructor.newInstance();
        packet.a(input);
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
