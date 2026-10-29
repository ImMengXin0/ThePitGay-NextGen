package cn.mythicland.framework.codec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.IntConsumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class GzipCodec {
    private GzipCodec() {
    }

    public static byte[] compress(byte[] payload) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream output = new GZIPOutputStream(buffer)) {
            output.write(payload);
        }
        return buffer.toByteArray();
    }

    public static byte[] decompress(byte[] payload, int bufferSize, IntConsumer validateSize) throws IOException {
        if (bufferSize <= 0) throw new IllegalArgumentException("Gzip buffer size must be positive: " + bufferSize);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(payload))) {
            byte[] chunk = new byte[bufferSize];
            int total = 0;
            int read;
            while ((read = input.read(chunk)) != -1) {
                total += read;
                validateSize.accept(total);
                buffer.write(chunk, 0, read);
            }
        }
        return buffer.toByteArray();
    }

    public static boolean isGzip(byte[] payload) {
        return payload.length >= 2 && (payload[0] & 0xFF) == 0x1F && (payload[1] & 0xFF) == 0x8B;
    }

    public static String sha256(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
