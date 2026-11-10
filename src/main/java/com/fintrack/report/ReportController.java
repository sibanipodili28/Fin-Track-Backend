package com.fintrack.report;

import com.fintrack.report.ReportService.ReportParams;
import com.fintrack.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService service;

    /**
     * type: monthly, yearly, category, account-statement, budget, savings.
     * format: json (default, for the on-screen preview), csv, xlsx or pdf.
     */
    @GetMapping("/{type}")
    public ResponseEntity<?> report(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable String type,
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long accountId) {
        ReportType reportType = ReportType.parse(type);
        ReportFormat reportFormat = ReportFormat.parse(format);
        ReportData data = service.generate(me.id(), reportType, new ReportParams(month, year, from, to, accountId));

        if (reportFormat == ReportFormat.JSON) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(data);
        }
        byte[] bytes = switch (reportFormat) {
            case CSV -> CsvExporter.toBytes(data);
            case XLSX -> ExcelExporter.toBytes(data);
            default -> PdfExporter.toBytes(data);
        };
        // The file name is built only from fixed tokens, never from user input.
        String fileName = "fintrack-" + reportType.name().toLowerCase().replace('_', '-') + "-" + LocalDate.now() + "." + reportFormat.extension;
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .contentType(MediaType.parseMediaType(reportFormat.contentType))
                .body(bytes);
    }
}
