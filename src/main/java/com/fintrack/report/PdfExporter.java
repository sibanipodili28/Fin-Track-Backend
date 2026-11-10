package com.fintrack.report;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;

/** Uses the built-in Helvetica font, so characters outside Latin-1 (such as the rupee sign) are not drawn. Amounts are plain numbers. */
public final class PdfExporter {
    private PdfExporter() {}

    public static byte[] toBytes(ReportData data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(data.columns().size() > 5 ? PageSize.A4.rotate() : PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph(data.title(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16)));
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            if (data.subtitle() != null) doc.add(new Paragraph(data.subtitle(), normal));
            doc.add(new Paragraph(" ", normal));
            for (ReportData.SummaryItem s : data.summary()) {
                doc.add(new Paragraph(s.label() + ": " + format(s.value()), bold));
            }
            if (!data.summary().isEmpty()) doc.add(new Paragraph(" ", normal));

            PdfPTable table = new PdfPTable(data.columns().size());
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (String column : data.columns()) {
                PdfPCell cell = new PdfPCell(new Phrase(column, bold));
                cell.setBackgroundColor(new java.awt.Color(225, 232, 229));
                cell.setPadding(4);
                table.addCell(cell);
            }
            for (List<Object> row : data.rows()) {
                for (Object value : row) {
                    PdfPCell cell = new PdfPCell(new Phrase(format(value), normal));
                    cell.setPadding(3);
                    if (value instanceof BigDecimal) cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    table.addCell(cell);
                }
            }
            doc.add(table);
        } finally {
            if (doc.isOpen()) doc.close();
        }
        return out.toByteArray();
    }

    private static String format(Object value) {
        if (value == null) return "";
        if (value instanceof BigDecimal n) return new DecimalFormat("#,##0.00").format(n);
        return value.toString();
    }
}
