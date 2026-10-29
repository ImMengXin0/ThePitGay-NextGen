package cn.mythicland.framework.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public final class PasswordHashes {

    private final SecureRandom secureRandom = new SecureRandom();
    private static final int AUTHME_SHA256_SALT_LENGTH = 16;

    public String hash(String plainText) {
        return hashWithSalt(plainText, newSalt());
    }

    public String hashWithSalt(String plainText, String salt) {
        return "$SHA$" + salt + "$" + sha256(sha256(plainText) + salt);
    }

    public boolean verify(String plainText, String storedHash) {
        if (storedHash == null || !storedHash.startsWith("$SHA$")) {
            return false;
        }
        String[] parts = storedHash.split("\\$");
        if (parts.length != 4) {
            return false;
        }
        return parts[3].equalsIgnoreCase(hashWithSalt(plainText, parts[2]).split("\\$")[3]);
    }

    private String newSalt() {
        byte[] bytes = new byte[AUTHME_SHA256_SALT_LENGTH / 2];
        secureRandom.nextBytes(bytes);
        return toHex(bytes);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            String hex = Integer.toHexString(value & 0xFF);
            if (hex.length() == 1) {
                builder.append('0');
            }
            builder.append(hex);
        }
        return builder.toString();
    }
}
