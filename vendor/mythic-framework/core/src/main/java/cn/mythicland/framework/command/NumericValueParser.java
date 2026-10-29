package cn.mythicland.framework.command;

public final class NumericValueParser {
    private NumericValueParser() {
    }

    public static ParsedValue parse(String text) {
        if (text == null) return null;
        String normalized = text.trim().toLowerCase();
        if (normalized.isEmpty()) return null;
        boolean percent = normalized.endsWith("%");
        String numberText = percent
                ? normalized.substring(0, normalized.length() - 1).trim()
                : normalized;
        if (numberText.isEmpty()) return null;

        try {
            double value = Double.parseDouble(numberText);
            if (percent) return new ParsedValue(value / 100.0D, true);
            return new ParsedValue(value, false);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public record ParsedValue(double value, boolean percent) {
    }
}
