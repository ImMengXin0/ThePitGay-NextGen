package cn.mythicland.framework.text;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DecimalFormatter {
    private DecimalFormatter() {
    }

    public static String format(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    public static String format(double value, double unit, String suffix) {
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(unit), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + suffix;
    }
}
