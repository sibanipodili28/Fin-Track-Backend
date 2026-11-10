package com.fintrack.report;

import java.util.List;

/**
 * A report as a plain table. Cells are String or BigDecimal only. Every export format (JSON, CSV, Excel, PDF)
 * is produced from this one structure, so the numbers can never differ between formats.
 */
public record ReportData(String title, String subtitle, List<String> columns, List<List<Object>> rows, List<SummaryItem> summary) {
    public record SummaryItem(String label, Object value) {}
}
