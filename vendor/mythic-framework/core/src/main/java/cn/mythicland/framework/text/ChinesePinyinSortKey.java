package cn.mythicland.framework.text;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

import java.text.Normalizer;
import java.util.Locale;

public final class ChinesePinyinSortKey {
    private static final char LEVEL_SEPARATOR = '\u0000';
    private static final char TOKEN_SEPARATOR = '\u0001';
    private static final HanyuPinyinOutputFormat PINYIN_FORMAT = ChinesePinyinSortKey.createFormat();

    private ChinesePinyinSortKey() {
    }

    public static String from(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Chinese pinyin sort key source text cannot be null");
        }
        StringBuilder initials = new StringBuilder(text.length());
        StringBuilder fullPinyin = new StringBuilder(text.length() * 8);
        StringBuilder literal = new StringBuilder(text.length());
        text.codePoints()
                .forEach(codePoint -> ChinesePinyinSortKey.appendCodePoint(
                        initials,
                        fullPinyin,
                        literal,
                        codePoint,
                        text
                ));
        return initials.append(LEVEL_SEPARATOR)
                .append(fullPinyin)
                .append(LEVEL_SEPARATOR)
                .append(literal)
                .toString();
    }

    private static void appendCodePoint(
            StringBuilder initials,
            StringBuilder fullPinyin,
            StringBuilder literal,
            int codePoint,
            String sourceText
    ) {
        if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
            if (!Character.isBmpCodePoint(codePoint)) {
                throw new IllegalStateException("Cannot build pinyin sort key for non-BMP Chinese character U+"
                        + Integer.toHexString(codePoint).toUpperCase(Locale.ROOT) + " in text: " + sourceText);
            }
            String pinyin = ChinesePinyinSortKey.pinyin((char) codePoint, sourceText);
            initials.append(pinyin.charAt(0));
            ChinesePinyinSortKey.appendToken(fullPinyin, pinyin);
            literal.appendCodePoint(codePoint);
            return;
        }
        if (Character.isLetterOrDigit(codePoint)) {
            String normalized = Normalizer.normalize(
                    new String(Character.toChars(codePoint)),
                    Normalizer.Form.NFKC
            ).toLowerCase(Locale.ROOT);
            initials.append(normalized);
            fullPinyin.append(normalized);
            literal.append(normalized);
            return;
        }
        ChinesePinyinSortKey.appendSeparator(initials);
        ChinesePinyinSortKey.appendSeparator(fullPinyin);
        ChinesePinyinSortKey.appendSeparator(literal);
    }

    private static void appendToken(StringBuilder builder, String token) {
        ChinesePinyinSortKey.appendSeparator(builder);
        builder.append(token);
        ChinesePinyinSortKey.appendSeparator(builder);
    }

    private static void appendSeparator(StringBuilder builder) {
        if (builder.isEmpty() || builder.charAt(builder.length() - 1) == TOKEN_SEPARATOR) return;
        builder.append(TOKEN_SEPARATOR);
    }

    private static String pinyin(char character, String sourceText) {
        try {
            String[] candidates = PinyinHelper.toHanyuPinyinStringArray(character, PINYIN_FORMAT);
            if (candidates == null || candidates.length == 0) {
                throw new IllegalStateException("Cannot build pinyin sort key for Chinese character '" + character
                        + "' in text: " + sourceText);
            }
            return candidates[0];
        } catch (BadHanyuPinyinOutputFormatCombination exception) {
            throw new IllegalStateException("Invalid pinyin output format while sorting text: " + sourceText,
                    exception);
        }
    }

    private static HanyuPinyinOutputFormat createFormat() {
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_V);
        return format;
    }
}
