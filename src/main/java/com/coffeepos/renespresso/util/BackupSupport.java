package com.coffeepos.renespresso.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class BackupSupport {

    private BackupSupport() {}

    private record Source(String baseSql, String dateColumn) {}

    private static final Map<String, Source> SOURCES = new LinkedHashMap<>();
    static {
        SOURCES.put("transactions", new Source(
                "SELECT t.* FROM transactions t",
                "t.created_at"));
    }

    static List<String> listTables(Connection conn) throws SQLException {
        Set<String> existing = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try (ResultSet rs = conn.getMetaData()
                .getTables(conn.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (rs.next()) existing.add(rs.getString("TABLE_NAME"));
        }

        List<String> tables = new ArrayList<>();
        for (String t : SOURCES.keySet()) {
            if (existing.contains(t)) tables.add(t);
        }
        if (tables.isEmpty()) {
            throw new SQLException("Transaction tables were not found in the database.");
        }
        return tables;
    }

    static PreparedStatement select(Connection conn, String table, List<YearMonth> months) throws SQLException {
        Source src = SOURCES.get(table);

        StringBuilder sql = new StringBuilder(src.baseSql()).append(" WHERE ");
        for (int i = 0; i < months.size(); i++) {
            if (i > 0) sql.append(" OR ");
            sql.append("(").append(src.dateColumn()).append(" >= ? AND ")
                    .append(src.dateColumn()).append(" < ?)");
        }
        sql.append(" ORDER BY ").append(src.dateColumn()).append(", ")
                .append(table.equals("transactions") ? "t.transaction_id" : "ti.transaction_id");

        PreparedStatement ps = conn.prepareStatement(sql.toString());
        int p = 1;
        for (YearMonth ym : months) {
            ps.setTimestamp(p++, Timestamp.valueOf(ym.atDay(1).atStartOfDay()));
            ps.setTimestamp(p++, Timestamp.valueOf(ym.plusMonths(1).atDay(1).atStartOfDay()));
        }
        return ps;
    }
}