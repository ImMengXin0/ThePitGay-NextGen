package cn.mythicland.framework.text;

import java.util.ArrayList;
import java.util.List;

public final class StructuredText {
    private final String indentation;
    private final String blankLine;
    private final List<String> lines = new ArrayList<>();

    public StructuredText(String indentation, String blankLine) {
        this.indentation = indentation;
        this.blankLine = blankLine;
    }

    public StructuredText line(String text) {
        this.lines.add(text);
        return this;
    }

    public StructuredText blank() {
        return this.line(this.blankLine);
    }

    public StructuredText indented(String text) {
        return this.line(this.indentation + text);
    }

    public StructuredText list(String marker, String... items) {
        for (String item : items) this.indented(marker + item);
        return this;
    }

    public List<String> build() {
        return List.copyOf(this.lines);
    }
}
