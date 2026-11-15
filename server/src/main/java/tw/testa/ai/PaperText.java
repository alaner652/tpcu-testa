package tw.testa.ai;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import tw.testa.common.ApiException;

/** Plain text of an uploaded exam paper (.docx, .pdf or .txt). */
final class PaperText {
    /** A small local model handles a few thousand characters per request; longer papers are imported in parts. */
    static final int MAX_CHARS = 8000;

    private PaperText() {}

    static String extract(String filename, byte[] bytes) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String text;
        try {
            if (name.endsWith(".docx")) text = docx(bytes);
            else if (name.endsWith(".pdf")) text = pdf(bytes);
            else if (name.endsWith(".txt")) text = new String(bytes, StandardCharsets.UTF_8);
            else throw ApiException.badRequest("unsupported_file", "只支援 Word（.docx）、PDF 和純文字（.txt）。舊版的 .doc 請先另存成 .docx。");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("unreadable_file", "這個檔案讀不出來，請確認它沒有損壞或加密。");
        }
        text = text.replace("\r", "").replaceAll("[ \\t\\u3000]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").strip();
        if (text.isEmpty()) {
            throw ApiException.badRequest("empty_file", "檔案裡沒有可以讀取的文字。掃描成圖片的 PDF 目前無法辨識。");
        }
        if (text.length() > MAX_CHARS) {
            throw ApiException.badRequest("file_too_long",
                    "這份考卷有 %d 個字，一次最多 %d 個字。請分成幾個檔案匯入。".formatted(text.length(), MAX_CHARS));
        }
        return text;
    }

    private static String docx(byte[] bytes) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            for (IBodyElement el : doc.getBodyElements()) {
                if (el instanceof XWPFParagraph p) {
                    sb.append(p.getText()).append('\n');
                } else if (el instanceof XWPFTable t) {
                    for (XWPFTableRow row : t.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) sb.append(cell.getText()).append('\t');
                        sb.append('\n');
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String pdf(byte[] bytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(doc);
        }
    }
}
