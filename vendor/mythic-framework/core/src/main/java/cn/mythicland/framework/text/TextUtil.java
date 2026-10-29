package cn.mythicland.framework.text;

public final class TextUtil {

    private TextUtil() {
    }

    public static String color(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return translate(text, false);
    }

    public static String translate(String text) {
        return text == null ? null : translate(text, true);
    }

    private static String translate(String text, boolean lowercaseCodes) {
        char[] chars = text.toCharArray();
        for (int index = 0; index < chars.length - 1; index++) {
            if (chars[index] != '&' || !isColorCode(chars[index + 1])) continue;
            chars[index] = '\u00a7';
            if (lowercaseCodes) chars[index + 1] = Character.toLowerCase(chars[index + 1]);
        }
        return new String(chars);
    }

    public static String placeholders(String text, Object... placeholders) {
        String result = color(text);
        for (int index = 0; index < placeholders.length; index++) {
            Object value = placeholders[index];
            result = result.replace("{" + index + "}", value == null ? "" : String.valueOf(value));
        }
        return result;
    }

    private static boolean isColorCode(char value) {
        switch (Character.toLowerCase(value)) {
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
            case 'a':
            case 'b':
            case 'c':
            case 'd':
            case 'e':
            case 'f':
            case 'k':
            case 'l':
            case 'm':
            case 'n':
            case 'o':
            case 'r':
                return true;
            default:
                return false;
        }
    }
}
