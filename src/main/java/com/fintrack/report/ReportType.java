package com.fintrack.report;

import com.fintrack.common.BusinessRuleException;
import org.springframework.http.HttpStatus;

public enum ReportType {
    MONTHLY, YEARLY, CATEGORY, ACCOUNT_STATEMENT, BUDGET, SAVINGS;

    public static ReportType parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Unknown report type '" + value + "'");
        }
    }
}
