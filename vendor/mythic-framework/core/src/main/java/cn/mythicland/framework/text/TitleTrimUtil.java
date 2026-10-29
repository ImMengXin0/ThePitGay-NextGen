package cn.mythicland.framework.text;


public final class TitleTrimUtil {
    private TitleTrimUtil() {
    }

    public static String trim(String title, int maxTitleLength) {
        if (title == null || title.length() <= maxTitleLength) return title;

        String trimmed = title.substring(0, maxTitleLength);
        if (!trimmed.isEmpty() && trimmed.charAt(trimmed.length() - 1) == '\u00a7') {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
