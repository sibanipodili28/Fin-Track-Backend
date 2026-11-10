package com.fintrack.report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;

public final class ExcelExporter {
    private ExcelExporter() {}

    public static byte[] toBytes(ReportData data) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Report");

            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle boldStyle = wb.createCellStyle();
            boldStyle.setFont(bold);
            CellStyle header = wb.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));

            int r = 0;
            Cell title = sheet.createRow(r++).createCell(0);
            title.setCellValue(data.title());
            title.setCellStyle(boldStyle);
            if (data.subtitle() != null) sheet.createRow(r++).createCell(0).setCellValue(data.subtitle());
            r++;
            for (ReportData.SummaryItem item : data.summary()) {
                Row row = sheet.createRow(r++);
                Cell label = row.createCell(0);
                label.setCellValue(item.label());
                label.setCellStyle(boldStyle);
                write(row.createCell(1), item.value(), money);
            }
            if (!data.summary().isEmpty()) r++;

            Row head = sheet.createRow(r++);
            for (int c = 0; c < data.columns().size(); c++) {
                Cell cell = head.createCell(c);
                cell.setCellValue(data.columns().get(c));
                cell.setCellStyle(header);
            }
            for (List<Object> values : data.rows()) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < values.size(); c++) write(row.createCell(c), values.get(c), money);
            }
            // Fixed widths: autoSizeColumn needs fonts that slim server images do not have.
            for (int c = 0; c < Math.max(data.columns().size(), 2); c++) sheet.setColumnWidth(c, 22 * 256);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Text is always written as text, never as a formula. */
    private static void write(Cell cell, Object value, CellStyle money) {
        if (value instanceof BigDecimal n) {
            cell.setCellValue(n.doubleValue());
            cell.setCellStyle(money);
        } else if (value != null) {
            cell.setCellValue(value.toString());
        }
    }
}
