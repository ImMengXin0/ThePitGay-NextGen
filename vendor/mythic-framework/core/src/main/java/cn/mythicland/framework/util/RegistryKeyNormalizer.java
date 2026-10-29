package cn.mythicland.framework.util;

import java.util.Locale;

public final class RegistryKeyNormalizer {
    private RegistryKeyNormalizer() {
    }

    public static String normalize(String rawName) {
        if (rawName == null) return null;
        String trimmed = rawName.trim();
        if (trimmed.isBlank()) return trimmed;
        if (trimmed.indexOf('_') >= 0) return trimmed.toLowerCase(Locale.ROOT);

        StringBuilder normalized = new StringBuilder(trimmed.length() + 8);
        for (int index = 0; index < trimmed.length(); index++) {
            char current = trimmed.charAt(index);
            if (Character.isUpperCase(current)) {
                boolean hasPrevious = index > 0;
                boolean previousIsLowerOrDigit = hasPrevious && (Character.isLowerCase(trimmed.charAt(index - 1))
                        || Character.isDigit(trimmed.charAt(index - 1)));
                boolean nextIsLower = index + 1 < trimmed.length() && Character.isLowerCase(trimmed.charAt(index + 1));
                if (hasPrevious && (previousIsLowerOrDigit || nextIsLower)
                        && normalized.charAt(normalized.length() - 1) != '_') {
                    normalized.append('_');
                }
                normalized.append(Character.toLowerCase(current));
                continue;
            }
            if ((current == '-' || current == ' ') && !normalized.isEmpty()
                    && normalized.charAt(normalized.length() - 1) != '_') {
                normalized.append('_');
                continue;
            }
            normalized.append(Character.toLowerCase(current));
        }
        return normalized.toString();
    }

}
