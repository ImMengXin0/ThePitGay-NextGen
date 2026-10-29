package cn.mythicland.framework.codec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class StringArrayCodec {
    private final int version;
    private final int maxFieldBytes;
    private final String description;

    public StringArrayCodec(int version, int maxFieldBytes, String description) {
        this.version = version;
        this.maxFieldBytes = maxFieldBytes;
        this.description = description;
    }

    public byte[] encode(String... values) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(this.version);
            output.writeInt(values.length);
            for (String value : values) {
                if (value == null) {
                    output.writeInt(-1);
                } else {
                    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                    output.writeInt(bytes.length);
                    output.write(bytes);
                }
            }
        }
        return buffer.toByteArray();
    }

    public String[] decode(byte[] raw, int expectedCount) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(raw))) {
            int actualVersion = input.readInt();
            if (actualVersion != this.version)
                throw new IllegalStateException("Unsupported " + this.description + " codec version: " + actualVersion);
            int count = input.readInt();
            if (count != expectedCount)
                throw new IllegalStateException("Invalid " + this.description + " field count: expected "
                        + expectedCount + ", actual " + count);
            String[] values = new String[count];
            for (int index = 0; index < count; index++) {
                int length = input.readInt();
                if (length < 0) continue;
                if (length > this.maxFieldBytes)
                    throw new IllegalStateException(Character.toUpperCase(this.description.charAt(0))
                            + this.description.substring(1) + " field is too large: " + length);
                byte[] bytes = input.readNBytes(length);
                if (bytes.length != length) throw new IOException("Unexpected end of " + this.description);
                values[index] = new String(bytes, StandardCharsets.UTF_8);
            }
            if (input.read() != -1) throw new IOException("Unexpected trailing " + this.description + " data");
            return values;
        }
    }
}
