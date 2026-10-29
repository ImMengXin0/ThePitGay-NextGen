package cn.mythicland.framework.bukkit.diagnostics;

import cn.mythicland.framework.diagnostics.DiagnosticRecorder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class DiagnosticFrames extends ChannelInboundHandlerAdapter {
    private final DiagnosticRecorder recorder;
    private final UUID uuid;
    private final ThreadLocal<Long> frame;
    private final DiagnosticOptions options;

    DiagnosticFrames(DiagnosticRecorder recorder, UUID uuid, ThreadLocal<Long> frame, DiagnosticOptions options) {
        this.recorder = recorder;
        this.uuid = uuid;
        this.frame = frame;
        this.options = options;
    }

    @Override
    public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
        long previous = frame.get();
        try {
            if (recorder.isRecording() && message instanceof ByteBuf buffer) {
                int[] cursor = {buffer.readerIndex()};
                int type = varInt(buffer, cursor);
                if (this.options.frame(type)) {
                    long id = recorder.nextId();
                    frame.set(id);
                    Map<String, Object> fields = base(this.uuid, context.channel());
                    fields.put("frameId", id);
                    fields.put("protocolId", type);
                    fields.put("bytes", buffer.readableBytes());
                    if (type == 2) {
                        fields.put("targetEntityId", varInt(buffer, cursor));
                        fields.put("action", varInt(buffer, cursor));
                    }
                    byte[] payload = new byte[Math.min(256, buffer.readableBytes())];
                    buffer.getBytes(buffer.readerIndex(), payload);
                    fields.put("hex", HexFormat.of().formatHex(payload));
                    fields.put("payloadTruncated", buffer.readableBytes() > payload.length);
                    recorder.record("ingress.frame", fields);
                }
            }
        } catch (RuntimeException failure) {
            recorder.fail("ingress.frame", failure);
        }
        try {
            context.fireChannelRead(message);
        } finally {
            if (previous == 0) frame.remove();
            else frame.set(previous);
        }
    }

    private static Map<String, Object> base(UUID uuid, Channel channel) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("player", uuid.toString());
        out.put("connection", channel.toString());
        return out;
    }

    private static int varInt(ByteBuf buffer, int[] cursor) {
        int value = 0;
        for (int index = 0; index < 5; index++) {
            if (cursor[0] >= buffer.writerIndex()) throw new IllegalArgumentException("Incomplete diagnostic VarInt");
            int part = buffer.getUnsignedByte(cursor[0]++);
            value |= (part & 127) << (index * 7);
            if (part < 128) return value;
        }
        throw new IllegalArgumentException("Invalid diagnostic VarInt");
    }

}
