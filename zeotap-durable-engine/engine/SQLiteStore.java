package engine;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite-based persistent storage for workflow steps.
 * 
 * Features:
 * - Synchronized writes for SQLITE_BUSY prevention
 * - Transaction-safe operations
 * - Workflow-level state management
 */
public class SQLiteStore {

    private final Connection conn;
    private final Object dbLock = new Object();

    public SQLiteStore(String dbPath) throws Exception {
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        initializeSchema();
    }

    private void initializeSchema() throws Exception {
        // Create steps table
        String stepsTable = """
                CREATE TABLE IF NOT EXISTS steps (
                    workflow_id TEXT NOT NULL,
                    step_key TEXT NOT NULL,
                    step_name TEXT NOT NULL,
                    sequence_id INTEGER NOT NULL,
                    status TEXT CHECK(status IN ('RUNNING','COMPLETED','FAILED')) NOT NULL,
                    error TEXT,
                    output TEXT,
                    retry_count INTEGER DEFAULT 0,
                    next_retry_at INTEGER,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (workflow_id, step_key)
                )
                """;

        // Create workflows table
        String workflowsTable = """
                CREATE TABLE IF NOT EXISTS workflows (
                    workflow_id TEXT PRIMARY KEY,
                    status TEXT CHECK(status IN ('RUNNING','CANCELLED','COMPLETED')) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;

        synchronized (dbLock) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(stepsTable);
                stmt.execute(workflowsTable);
            }
        }
    }

    /**
     * Find a step record by workflow ID and step key.
     */
    public StepRecord find(String workflowId, String stepKey) throws Exception {
        String sql = """
                SELECT workflow_id, step_key, step_name, sequence_id, status,
                       output, error, retry_count, next_retry_at
                FROM steps
                WHERE workflow_id=? AND step_key=?
                """;

        synchronized (dbLock) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, workflowId);
                ps.setString(2, stepKey);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return mapToStepRecord(rs);
                }
                return null;
            }
        }
    }

    /**
     * Mark a step as running.
     */
    public void markRunning(String workflowId, String stepKey, String name, long seq) throws Exception {
        String sql = """
                INSERT OR REPLACE INTO steps
                (workflow_id, step_key, step_name, sequence_id, status, retry_count)
                VALUES (?, ?, ?, ?, 'RUNNING', 0)
                """;

        synchronized (dbLock) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, workflowId);
                ps.setString(2, stepKey);
                ps.setString(3, name);
                ps.setLong(4, seq);
                ps.executeUpdate();
            }
        }
    }

    /**
     * Mark a step as completed with its output.
     */
    public void markCompleted(String workflowId, String stepKey, String output) throws Exception {
        String sql = """
                UPDATE steps
                SET status='COMPLETED', output=?, updated_at=CURRENT_TIMESTAMP
                WHERE workflow_id=? AND step_key=?
                """;

        synchronized (dbLock) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, output);
                ps.setString(2, workflowId);
                ps.setString(3, stepKey);
                ps.executeUpdate();
            }
        }
    }

    /**
     * Mark a step as failed with error message.
     */
    public void markFailed(String workflowId, String stepKey, String error) throws Exception {
        String sql = """
                UPDATE steps
                SET status='FAILED', error=?, updated_at=CURRENT_TIMESTAMP
                WHERE workflow_id=? AND step_key=?
                """;

        synchronized (dbLock) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, error);
                ps.setString(2, workflowId);
                ps.setString(3, stepKey);
                ps.executeUpdate();
            }
        }
    }

    /**
     * Mark a step as failed with retry information.
     */
    public void markFailed(String workflowId, String stepKey, String error,
            int retryCount, long nextRetryAt) throws Exception {
        String sql = """
                UPDATE steps
                SET status='FAILED', error=?, retry_count=?, next_retry_at=?, updated_at=CURRENT_TIMESTAMP
                WHERE workflow_id=? AND step_key=?
                """;

        synchronized (dbLock) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, error);
                ps.setInt(2, retryCount);
                ps.setLong(3, nextRetryAt);
                ps.setString(4, workflowId);
                ps.setString(5, stepKey);
                ps.executeUpdate();
            }
        }
    }

    /**
     * Create or update a workflow record.
     */
    public void upsertWorkflow(String workflowId, String status) throws Exception {
        String sql = """
                INSERT OR REPLACE INTO workflows (workflow_id, status)
                VALUES (?, ?)
                """;

        synchronized (dbLock) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, workflowId);
                ps.setString(2, status);
                ps.executeUpdate();
            }
        }
    }

    /**
     * Get workflow status.
     */
    public String getWorkflowStatus(String workflowId) throws Exception {
        String sql = "SELECT status FROM workflows WHERE workflow_id=?";

        synchronized (dbLock) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, workflowId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getString("status");
                }
                return null;
            }
        }
    }

    /**
     * Check if workflow is cancelled.
     */
    public boolean isWorkflowCancelled(String workflowId) throws Exception {
        String status = getWorkflowStatus(workflowId);
        return "CANCELLED".equals(status);
    }

    /**
     * Cancel a workflow.
     */
    public void cancelWorkflow(String workflowId) throws Exception {
        upsertWorkflow(workflowId, "CANCELLED");
    }

    /**
     * Get all completed steps for a workflow.
     */
    public List<StepRecord> getCompletedSteps(String workflowId) throws Exception {
        String sql = """
                SELECT workflow_id, step_key, step_name, sequence_id, status,
                       output, error, retry_count, next_retry_at
                FROM steps
                WHERE workflow_id=? AND status='COMPLETED'
                ORDER BY sequence_id
                """;

        List<StepRecord> steps = new ArrayList<>();
        synchronized (dbLock) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, workflowId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    steps.add(mapToStepRecord(rs));
                }
            }
        }
        return steps;
    }

    private StepRecord mapToStepRecord(ResultSet rs) throws SQLException {
        Long nextRetry = rs.getObject("next_retry_at") != null ? rs.getLong("next_retry_at") : null;

        return new StepRecord(
                rs.getString("workflow_id"),
                rs.getString("step_key"),
                rs.getString("step_name"),
                rs.getLong("sequence_id"),
                rs.getString("status"),
                rs.getString("output"),
                rs.getString("error"),
                rs.getInt("retry_count"),
                nextRetry);
    }

    /**
     * Close the database connection.
     */
    public void close() throws Exception {
        synchronized (dbLock) {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        }
    }
}
