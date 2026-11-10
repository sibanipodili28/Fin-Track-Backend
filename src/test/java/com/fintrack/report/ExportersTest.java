package com.fintrack.report;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExportersTest {

    private ReportData sample() {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(new ArrayList<>(Arrays.asList("Food, drinks", new BigDecimal("3000.50"), "60.0%")));
        rows.add(new ArrayList<>(Arrays.asList("=HYPERLINK(\"http://evil\")", new BigDecimal("-10.00"), "0.0%")));
        return new ReportData("Sample", "Subtitle", List.of("Category", "Amount", "Share"), rows,
                List.of(new ReportData.SummaryItem("Total", new BigDecimal("3000.50"))));
    }

    @Test
    void csvEscapesCommasQuotesAndNeutralisesFormulas() {
        assertEquals("\"a,b\"", CsvExporter.cell("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.cell("say \"hi\""));
        assertEquals("'=SUM(A1)", CsvExporter.cell("=SUM(A1)"));
        assertEquals("'@cmd", CsvExporter.cell("@cmd"));
        assertEquals("-10.00", CsvExporter.cell(new BigDecimal("-10.00")));
        assertEquals("", CsvExporter.cell(null));
    }

    @Test
    void csvHasBomHeaderAndRows() {
        String csv = new String(CsvExporter.toBytes(sample()), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFFCategory,Amount,Share\r\n"));
        assertTrue(csv.contains("\"Food, drinks\",3000.50,60.0%"));
        assertTrue(csv.contains("'=HYPERLINK"));
    }

    @Test
    void excelContainsTheSameNumbersAndKeepsTextAsText() throws Exception {
        byte[] bytes = ExcelExporter.toBytes(sample());
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("Sample", sheet.getRow(0).getCell(0).getStringCellValue());
            Row header = null;
            Row food = null;
            Row evil = null;
            for (Row r : sheet) {
                if (r.getCell(0) == null) continue;
                String v = r.getCell(0).getCellType() == CellType.STRING ? r.getCell(0).getStringCellValue() : "";
                if (v.equals("Category")) header = r;
                if (v.equals("Food, drinks")) food = r;
                if (v.startsWith("=HYPERLINK")) evil = r;
            }
            assertNotNull(header);
            assertNotNull(food);
            assertEquals(3000.50, food.getCell(1).getNumericCellValue(), 0.0001);
            assertNotNull(evil);
            assertEquals(CellType.STRING, evil.getCell(0).getCellType());
        }
    }

    @Test
    void pdfIsAValidPdfDocument() {
        byte[] bytes = PdfExporter.toBytes(sample());
        assertTrue(bytes.length > 500);
        assertEquals("%PDF", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    void pdfAndExcelHandleEmptyReports() {
        ReportData empty = new ReportData("Empty", null, List.of("A", "B"), List.of(), List.of());
        assertTrue(ExcelExporter.toBytes(empty).length > 0);
        assertEquals("%PDF", new String(PdfExporter.toBytes(empty), 0, 4, StandardCharsets.US_ASCII));
    }
}
