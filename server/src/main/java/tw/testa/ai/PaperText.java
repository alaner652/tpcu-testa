package tw.testa.ai;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

/** Plain text of an uploaded exam paper (.docx, .pdf or .txt), optionally with its answer sheet as a second file. */
final class PaperText {
    /** A small local model handles a few thousand characters per request; longer papers are imported in parts. */
    static final int MAX_CHARS = 8000;

    /** A paper and, when answers are in a separate file, the answer sheet. */
    static final int MAX_FILES = 2;

    record Upload(String filename, byte[] bytes) {}

    private PaperText() {}

    /** The files' text in upload order, so the answer sheet follows the paper as if it were printed at the end. */
    static String extract(List<Upload> files) {
        if (files.isEmpty() || files.size() > MAX_FILES) {
            throw ApiException.badRequest("too_many_files", "請上傳一份考卷；答案在另一個檔案時，可以再加上答案卷，最多 2 個檔案。");
        }
        StringBuilder sb = new StringBuilder();
        for (Upload f : files) {
            if (!sb.isEmpty()) sb.append("\n\n");
            sb.append(extract(f.filename(), f.bytes()));
        }
        String text = sb.toString();
        if (text.length() > MAX_CHARS) {
            throw ApiException.badRequest("file_too_long",
                    "這份考卷有 %d 個字，一次最多 %d 個字。請分成幾個檔案匯入。".formatted(text.length(), MAX_CHARS));
        }
        return text;
    }

    private static String extract(String filename, byte[] bytes) {
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
        text = symbolPunctuation(text).replace("\r", "").replaceAll("[ \\t\\u3000]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").strip();
        if (text.isEmpty()) {
            throw ApiException.badRequest("empty_file", "「%s」裡沒有可以讀取的文字。掃描成圖片的 PDF 目前無法辨識。".formatted(filename));
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

    /** By position, so answer tables read row by row and equation editor output keeps its order ("p = 120 - q1"). */
    private static String pdf(byte[] bytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    /**
     * Symbol-font text from equation editors arrives as private-use characters (U+F02D for a minus sign). Digits and
     * punctuation sit at their ASCII position plus U+F000; letters do not (U+F061 is alpha), so they are left alone.
     */
    static String symbolPunctuation(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean ascii = (c >= '\uF020' && c <= '\uF040') || (c >= '\uF05B' && c <= '\uF060') || (c >= '\uF07B' && c <= '\uF07E');
            sb.append(ascii ? (char) (c - 0xF000) : c);
        }
        return sb.toString();
    }
}
