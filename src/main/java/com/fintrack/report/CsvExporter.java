package com.fintrack.report;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Clean table CSV (header + rows), UTF-8 with BOM so Excel shows accents correctly. */
public final class CsvExporter {
    private CsvExporter() {}

    public static byte[] toBytes(ReportData data) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        appendRow(sb, data.columns().stream().map(c -> (Object) c).toList());
        for (List<Object> row : data.rows()) appendRow(sb, row);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendRow(StringBuilder sb, List<Object> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(cell(cells.get(i)));
        }
        sb.append("\r\n");
    }

    static String cell(Object value) {
        if (value == null) return "";
        if (value instanceof BigDecimal n) return n.toPlainString();
        String s = value.toString();
        // Spreadsheet apps run text that starts with these characters as a formula. Neutralise it.
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
