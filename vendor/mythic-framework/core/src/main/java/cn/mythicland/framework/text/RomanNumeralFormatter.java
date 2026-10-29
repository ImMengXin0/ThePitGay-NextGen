package cn.mythicland.framework.text;

public final class RomanNumeralFormatter {
    private static final String[][] ROMAN = {{"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"},
            {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"},
            {"", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM"}, {"", "M", "MM", "MMM"}};

    private RomanNumeralFormatter() {
    }

    public static String format(int number) {
        if (number > 3999 || number < 1) return String.valueOf(number);

        return ROMAN[3][number / 1000 % 10] + ROMAN[2][number / 100 % 10] + ROMAN[1][number / 10 % 10]
                + ROMAN[0][number % 10];
    }
}
