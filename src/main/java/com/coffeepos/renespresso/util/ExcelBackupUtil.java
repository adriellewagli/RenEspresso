package com.coffeepos.renespresso.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.*;
import java.time.YearMonth;
import java.util.List;

public final class ExcelBackupUtil {

    private ExcelBackupUtil() {}

    public static int backupToExcel(Connection conn, File outFile, List<YearMonth> months)
            throws SQLException, IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {

            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);
            headerStyle.setFillForegroundColor(IndexedColors.TAN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            List<String> tables = BackupSupport.listTables(conn);

            for (String table : tables) {
                Sheet sheet = wb.createSheet(WorkbookUtil.createSafeSheetName(table));

                try (PreparedStatement ps = BackupSupport.select(conn, table, months);
                     ResultSet rs = ps.executeQuery()) {

                    ResultSetMetaData md = rs.getMetaData();
                    int cols = md.getColumnCount();

                    Row header = sheet.createRow(0);
                    for (int c = 1; c <= cols; c++) {
                        Cell cell = header.createCell(c - 1);
                        cell.setCellValue(md.getColumnLabel(c));
                        cell.setCellStyle(headerStyle);
                    }

                    int r = 1;
                    while (rs.next()) {
                        Row row = sheet.createRow(r++);
                        for (int c = 1; c <= cols; c++) {
                            Object v = rs.getObject(c);
                            if (v == null) continue;
                            Cell cell = row.createCell(c - 1);
                            if (v instanceof Number n)       cell.setCellValue(n.doubleValue());
                            else if (v instanceof Boolean b) cell.setCellValue(b);
                            else if (v instanceof byte[])    cell.setCellValue("[binary]");
                            else                             cell.setCellValue(v.toString());
                        }
                    }

                    for (int c = 0; c < cols; c++) sheet.autoSizeColumn(c);
                    sheet.createFreezePane(0, 1);
                }
            }

            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                wb.write(fos);
            }
            return tables.size();
        }
    }
}