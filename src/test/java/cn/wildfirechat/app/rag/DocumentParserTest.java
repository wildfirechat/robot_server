package cn.wildfirechat.app.rag;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class DocumentParserTest {
    private final DocumentParser parser = new DocumentParser();

    @Test
    public void parsesPlainText() throws Exception {
        DocumentParser.ParsedDocument document = parser.parse("readme.md",
                "# 标题\n\n退款政策是七天无理由退款。".getBytes(StandardCharsets.UTF_8));
        assertTrue(document.getText().contains("退款政策"));
    }

    @Test
    public void parsesDocx() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText("Word 文档中的退款政策");
            document.write(output);
        }
        DocumentParser.ParsedDocument parsed = parser.parse("policy.docx", output.toByteArray());
        assertTrue(parsed.getText().contains("退款政策"));
    }

    @Test
    public void parsesPdf() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.newLineAtOffset(50, 700);
                content.showText("RAG knowledge base");
                content.endText();
            }
            document.save(output);
        }
        DocumentParser.ParsedDocument parsed = parser.parse("doc.pdf", output.toByteArray());
        assertTrue(parsed.getText().contains("RAG"));
    }
}
