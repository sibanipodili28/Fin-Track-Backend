package com.fintrack.report;

import com.fintrack.common.BusinessRuleException;
import org.springframework.http.HttpStatus;

public enum ReportFormat {
    JSON("application/json", "json"),
    CSV("text/csv; charset=UTF-8", "csv"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
    PDF("application/pdf", "pdf");

    public final String contentType;
    public final String extension;

    ReportFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public static ReportFormat parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Unknown format '" + value + "'. Use json, csv, xlsx or pdf");
        }
    }
}
