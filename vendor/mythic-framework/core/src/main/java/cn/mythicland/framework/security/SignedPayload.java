package cn.mythicland.framework.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public final class SignedPayload {
    private static final int SIGNATURE_LENGTH = 32;

    private SignedPayload() {
    }

    public static String generateSecret() {
        byte[] secret = new byte[SIGNATURE_LENGTH];
        new SecureRandom().nextBytes(secret);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    public static byte[] encode(byte[] payload, String secret) {
        byte[] result = Arrays.copyOf(payload, payload.length + SIGNATURE_LENGTH);
        System.arraycopy(sign(payload, secret), 0, result, payload.length, SIGNATURE_LENGTH);
        return result;
    }

    public static byte[] decode(byte[] bytes, String secret, int maximumLength) {
        if (bytes.length <= SIGNATURE_LENGTH || bytes.length > maximumLength) {
            throw new IllegalArgumentException("Invalid signed payload length");
        }
        byte[] payload = Arrays.copyOf(bytes, bytes.length - SIGNATURE_LENGTH);
        byte[] signature = Arrays.copyOfRange(bytes, payload.length, bytes.length);
        if (!MessageDigest.isEqual(sign(payload, secret), signature)) {
            throw new IllegalArgumentException("Invalid payload signature");
        }
        return payload;
    }

    private static byte[] sign(byte[] payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to sign payload", exception);
        }
    }
}
