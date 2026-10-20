package ar.futbol.cli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A plain-text table: a header, a dashed line, and columns padded to their widest cell. */
public final class TextTable {

    private enum Align { LEFT, RIGHT }

    private record Column(String header, Align align) {}

    private static final String GAP = "  ";

    private final List<Column> columns = new ArrayList<>();
    private final List<List<String>> rows = new ArrayList<>();

    /** Adds a left-aligned column (for text). */
    public TextTable left(String header) {
        columns.add(new Column(header, Align.LEFT));
        return this;
    }

    /** Adds a right-aligned column (for numbers). */
    public TextTable right(String header) {
        columns.add(new Column(header, Align.RIGHT));
        return this;
    }

    public TextTable row(Object... cells) {
        if (cells.length != columns.size()) {
            throw new IllegalArgumentException(
                    "expected " + columns.size() + " cells but got " + cells.length);
        }
        rows.add(Arrays.stream(cells).map(String::valueOf).toList());
        return this;
    }

    public String render() {
        int[] widths = new int[columns.size()];
        for (int c = 0; c < columns.size(); c++) {
            widths[c] = columns.get(c).header().length();
            for (List<String> row : rows) {
                widths[c] = Math.max(widths[c], row.get(c).length());
            }
        }

        StringBuilder text = new StringBuilder();
        appendLine(text, widths, columns.stream().map(Column::header).toList());
        appendLine(text, widths, Arrays.stream(widths).mapToObj("-"::repeat).toList());
        rows.forEach(row -> appendLine(text, widths, row));
        return text.toString();
    }

    private void appendLine(StringBuilder text, int[] widths, List<String> cells) {
        StringBuilder line = new StringBuilder();
        for (int c = 0; c < cells.size(); c++) {
            if (c > 0) {
                line.append(GAP);
            }
            String padding = " ".repeat(widths[c] - cells.get(c).length());
            line.append(columns.get(c).align() == Align.LEFT ? cells.get(c) + padding : padding + cells.get(c));
        }
        text.append(line.toString().stripTrailing()).append('\n');
    }
}
