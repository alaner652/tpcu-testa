package tw.testa.exam;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import tw.testa.common.ApiException;

/**
 * Reads an uploaded roster (.xlsx or .csv) into rows of text.
 * CSV is read as UTF-8, falling back to Big5 when invalid (Excel in Taiwan saves CSV as Big5 by default).
 */
final class RosterParser {
    static final int MAX_ROWS = 5000;

    private RosterParser() {}

    /** One raw row; row is the line number in the file, from 1. */
    record RawRow(int row, List<String> cells) {}

    static List<RawRow> parse(String fileName, byte[] bytes) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        List<RawRow> rows;
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) rows = parseExcel(bytes);
        else if (lower.endsWith(".csv") || lower.endsWith(".txt")) rows = parseCsv(decode(bytes));
        else throw ApiException.badRequest("bad_file", "請上傳 Excel（.xlsx）或 CSV 檔。");
        if (rows.size() > MAX_ROWS + 1) {
            throw ApiException.badRequest("too_many_rows", "名單太長了，一次最多匯入 " + MAX_ROWS + " 位考生。");
        }
        return rows;
    }

    private static List<RawRow> parseExcel(byte[] bytes) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            List<RawRow> rows = new ArrayList<>();
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                for (int i = 0; i < Math.max(row.getLastCellNum(), 0); i++) {
                    Cell cell = row.getCell(i);
                    cells.add(cell == null ? "" : fmt.formatCellValue(cell).trim());
                }
                if (cells.stream().anyMatch(c -> !c.isEmpty())) rows.add(new RawRow(row.getRowNum() + 1, cells));
                if (rows.size() > MAX_ROWS + 1) break;
            }
            return rows;
        } catch (IOException | RuntimeException e) {
            throw ApiException.badRequest("bad_file", "讀不懂這個 Excel 檔。請確認檔案沒有損毀、沒有設定密碼，或改存成 .xlsx 再試一次。");
        }
    }

    private static String decode(byte[] bytes) {
        try {
            String s = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            return s.startsWith("﻿") ? s.substring(1) : s;
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("MS950"));
        }
    }

    /** Minimal CSV parsing: quotes, commas and newlines inside quotes, doubled quotes. */
    static List<RawRow> parseCsv(String text) {
        List<RawRow> rows = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        int line = 1;
        int rowStart = 1;
        for (int i = 0; i <= text.length(); i++) {
            char ch = i < text.length() ? text.charAt(i) : '\n';
            if (quoted) {
                if (ch == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    if (ch == '\n') line++;
                    if (i < text.length()) cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',' || ch == '\t') {
                cells.add(cell.toString().trim());
                cell.setLength(0);
            } else if (ch == '\n') {
                cells.add(cell.toString().trim());
                cell.setLength(0);
                if (cells.stream().anyMatch(c -> !c.isEmpty())) rows.add(new RawRow(rowStart, List.copyOf(cells)));
                cells.clear();
                line++;
                rowStart = line;
                if (rows.size() > MAX_ROWS + 1) break;
            } else if (ch != '\r') {
                cell.append(ch);
            }
        }
        return rows;
    }
}
