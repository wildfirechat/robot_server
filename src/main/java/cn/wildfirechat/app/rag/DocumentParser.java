package cn.wildfirechat.app.rag;

import org.springframework.stereotype.Component;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 本地文档解析：txt/md/csv/json、pdf、docx。
 */
@Component
public class DocumentParser {
    public ParsedDocument parse(String filename, byte[] bytes) throws IOException {
        if (filename == null || bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("文件内容为空");
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown")
                || lower.endsWith(".csv") || lower.endsWith(".json")) {
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }
            return new ParsedDocument("text", text);
        }
        if (lower.endsWith(".pdf")) {
            return new ParsedDocument("pdf", parsePdf(bytes));
        }
        if (lower.endsWith(".docx")) {
            return new ParsedDocument("docx", parseDocx(bytes));
        }
        throw new IllegalArgumentException("暂不支持该文件类型，仅支持 txt/md/csv/json/pdf/docx");
    }

    private static String parsePdf(byte[] bytes) throws IOException {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(bytes))) {
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("暂不支持加密 PDF");
            }
            return new PDFTextStripper().getText(document);
        }
    }

    private static String parseDocx(byte[] bytes) throws IOException {
        StringBuilder text = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String value = paragraph.getText();
                if (value != null && !value.trim().isEmpty()) {
                    text.append(value).append('\n');
                }
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    StringBuilder line = new StringBuilder();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        if (line.length() > 0) {
                            line.append(" | ");
                        }
                        line.append(cell.getText());
                    }
                    if (line.length() > 0) {
                        text.append(line).append('\n');
                    }
                }
            }
        }
        return text.toString();
    }

    public static class ParsedDocument {
        private final String sourceType;
        private final String text;

        public ParsedDocument(String sourceType, String text) {
            this.sourceType = sourceType;
            this.text = text;
        }

        public String getSourceType() {
            return sourceType;
        }

        public String getText() {
            return text;
        }
    }
}
