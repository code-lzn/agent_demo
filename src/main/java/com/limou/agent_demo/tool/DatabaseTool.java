package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Database query tool using the configured Spring DataSource.
 */
@Component
public class DatabaseTool {

    private final DataSource dataSource;

    public DatabaseTool(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Tool(description = "Execute a SELECT query and return the results as formatted text." +
            " For safety, only SELECT statements are allowed." +
            " Results limited to 200 rows")
    public String query(
            @ToolParam(description = "SQL SELECT query to execute") String sql) {
        String trimmed = sql.trim();
        if (!trimmed.toLowerCase().startsWith("select")) {
            return "Only SELECT queries are allowed for safety. Got: " + sql;
        }
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(trimmed)) {

            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            // Header
            List<String> headers = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                headers.add(meta.getColumnName(i));
            }

            // Rows
            List<List<String>> rows = new ArrayList<>();
            int rowCount = 0;
            while (rs.next() && rowCount < 200) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    String val = rs.getString(i);
                    row.add(val == null ? "NULL" : val);
                }
                rows.add(row);
                rowCount++;
            }

            // Format output
            StringBuilder sb = new StringBuilder();
            sb.append(String.join("\t", headers)).append("\n");
            sb.append("-".repeat(Math.min(headers.size() * 12, 80))).append("\n");
            for (List<String> row : rows) {
                sb.append(String.join("\t", row)).append("\n");
            }
            sb.append("\n").append(rows.size()).append(" row(s) returned");
            if (rowCount >= 200) {
                sb.append(" (limited to 200)");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Query failed: " + e.getMessage();
        }
    }

    @Tool(description = "List all tables in the current database")
    public String listTables() {
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                sb.append(rs.getString("TABLE_NAME")).append("\n");
            }
            return sb.isEmpty() ? "No tables found" : sb.toString().stripTrailing();
        } catch (Exception e) {
            return "Failed to list tables: " + e.getMessage();
        }
    }
}