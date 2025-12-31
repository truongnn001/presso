/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: ExecutionHistoryService.java
 * RESPONSIBILITY: Record execution history for all engine tasks
 * 
 * ARCHITECTURAL ROLE:
 * - Records every engine task execution
 * - Tracks task lifecycle (pending, running, completed, failed)
 * - Stores input/output summaries
 * - Fail-safe: logging failures don't crash execution
 * 
 * BOUNDARIES:
 * - Does NOT contain business logic
 * - Does NOT perform task execution
 * - Append-only at this stage (no updates/deletes)
 * 
 * Reference: PROJECT_DOCUMENTATION.md Section 5.2
 */
package com.presso.kernel.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for recording execution history.
 */
public final class ExecutionHistoryService {
    
    private static final Logger logger = LoggerFactory.getLogger(ExecutionHistoryService.class);
    
    private final DatabaseManager databaseManager;
    
    /**
     * Execution status enumeration.
     */
    public enum ExecutionStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED
    }
    
    /**
     * Construct an ExecutionHistoryService.
     * 
     * @param databaseManager the database manager
     */
    public ExecutionHistoryService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        logger.debug("ExecutionHistoryService created");
    }
    
    /**
     * Record task start.
     * 
     * @param taskId the task identifier
     * @param operationType the operation type (e.g., "EXPORT_EXCEL", "PDF_MERGE")
     * @param module the module name (e.g., "excel", "pdf", "image")
     * @param inputSummary optional input summary (truncated if too long)
     * @return the execution record ID, or -1 if recording failed
     */
    public long recordTaskStart(String taskId, String operationType, String module, String inputSummary) {
        if (!databaseManager.isInitialized()) {
            logger.debug("Database not initialized, skipping execution history record");
            return -1;
        }
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO execution_history (operation_type, module, started_at, status, input_summary) " +
                 "VALUES (?, ?, ?, ?, ?)",
                 PreparedStatement.RETURN_GENERATED_KEYS)) {
            
            Timestamp now = Timestamp.from(Instant.now());
            stmt.setString(1, operationType);
            stmt.setString(2, module);
            stmt.setTimestamp(3, now);
            stmt.setString(4, ExecutionStatus.PENDING.name().toLowerCase());
            stmt.setString(5, truncate(inputSummary, 1000));
            
            int rows = stmt.executeUpdate();
            conn.commit();
            
            if (rows > 0) {
                var keys = stmt.getGeneratedKeys();
                if (keys.next()) {
                    long id = keys.getLong(1);
                    logger.debug("Recorded task start: id={}, taskId={}, operation={}, module={}",
                        id, taskId, operationType, module);
                    return id;
                }
            }
            
            return -1;
            
        } catch (SQLException e) {
            // Fail-safe: log error but don't throw
            logger.error("Failed to record task start: taskId={}, error={}", taskId, e.getMessage());
            return -1;
        }
    }
    
    /**
     * Update task status to running.
     * 
     * @param executionId the execution record ID
     * @param taskId the task identifier
     */
    public void recordTaskRunning(long executionId, String taskId) {
        if (executionId < 0 || !databaseManager.isInitialized()) {
            return;
        }
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE execution_history SET status = ? WHERE id = ?")) {
            
            stmt.setString(1, ExecutionStatus.RUNNING.name().toLowerCase());
            stmt.setLong(2, executionId);
            
            int rows = stmt.executeUpdate();
            conn.commit();
            
            if (rows > 0) {
                logger.debug("Updated task status to running: executionId={}, taskId={}", executionId, taskId);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to update task status to running: executionId={}, error={}",
                executionId, e.getMessage());
        }
    }
    
    /**
     * Record task completion.
     * 
     * @param executionId the execution record ID
     * @param taskId the task identifier
     * @param outputSummary optional output summary (truncated if too long)
     */
    public void recordTaskCompleted(long executionId, String taskId, String outputSummary) {
        if (executionId < 0 || !databaseManager.isInitialized()) {
            return;
        }
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE execution_history SET status = ?, completed_at = ?, output_summary = ? WHERE id = ?")) {
            
            Timestamp now = Timestamp.from(Instant.now());
            stmt.setString(1, ExecutionStatus.COMPLETED.name().toLowerCase());
            stmt.setTimestamp(2, now);
            stmt.setString(3, truncate(outputSummary, 1000));
            stmt.setLong(4, executionId);
            
            int rows = stmt.executeUpdate();
            conn.commit();
            
            if (rows > 0) {
                logger.debug("Recorded task completion: executionId={}, taskId={}", executionId, taskId);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to record task completion: executionId={}, error={}",
                executionId, e.getMessage());
        }
    }
    
    /**
     * Record task failure.
     * 
     * @param executionId the execution record ID
     * @param taskId the task identifier
     * @param errorMessage error message (truncated if too long)
     */
    public void recordTaskFailed(long executionId, String taskId, String errorMessage) {
        if (executionId < 0 || !databaseManager.isInitialized()) {
            return;
        }
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE execution_history SET status = ?, completed_at = ?, error_message = ? WHERE id = ?")) {
            
            Timestamp now = Timestamp.from(Instant.now());
            stmt.setString(1, ExecutionStatus.FAILED.name().toLowerCase());
            stmt.setTimestamp(2, now);
            stmt.setString(3, truncate(errorMessage, 1000));
            stmt.setLong(4, executionId);
            
            int rows = stmt.executeUpdate();
            conn.commit();
            
            if (rows > 0) {
                logger.debug("Recorded task failure: executionId={}, taskId={}", executionId, taskId);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to record task failure: executionId={}, error={}",
                executionId, e.getMessage());
        }
    }
    
    /**
     * Link a contract to an execution history record.
     * 
     * @param executionId the execution record ID
     * @param contractId the contract ID
     */
    public void linkContractToExecution(long executionId, Long contractId) {
        if (executionId < 0 || contractId == null || !databaseManager.isInitialized()) {
            return;
        }
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE execution_history SET contract_id = ? WHERE id = ?")) {
            
            stmt.setLong(1, contractId);
            stmt.setLong(2, executionId);
            
            int rows = stmt.executeUpdate();
            conn.commit();
            
            if (rows > 0) {
                logger.debug("Linked contract to execution: executionId={}, contractId={}", executionId, contractId);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to link contract to execution: executionId={}, contractId={}, error={}",
                executionId, contractId, e.getMessage());
        }
    }
    
    /**
     * Execution history entry record.
     */
    public static class ExecutionHistoryEntry {
        private final long id;
        private final String operationType;
        private final String module;
        private final Timestamp startedAt;
        private final Timestamp completedAt;
        private final String status;
        private final String inputSummary;
        private final String outputSummary;
        private final String errorMessage;
        private final Long contractId;
        
        public ExecutionHistoryEntry(ResultSet rs) throws SQLException {
            this.id = rs.getLong("id");
            this.operationType = rs.getString("operation_type");
            this.module = rs.getString("module");
            this.startedAt = rs.getTimestamp("started_at");
            this.completedAt = rs.getTimestamp("completed_at");
            this.status = rs.getString("status");
            this.inputSummary = rs.getString("input_summary");
            this.outputSummary = rs.getString("output_summary");
            this.errorMessage = rs.getString("error_message");
            this.contractId = rs.getObject("contract_id", Long.class);
        }
        
        public long getId() { return id; }
        public String getOperationType() { return operationType; }
        public String getModule() { return module; }
        public Timestamp getStartedAt() { return startedAt; }
        public Timestamp getCompletedAt() { return completedAt; }
        public String getStatus() { return status; }
        public String getInputSummary() { return inputSummary; }
        public String getOutputSummary() { return outputSummary; }
        public String getErrorMessage() { return errorMessage; }
        public Long getContractId() { return contractId; }
    }
    
    /**
     * Query parameters for execution history.
     */
    public static class ExecutionQueryParams {
        private String operationType;
        private String module;
        private ExecutionStatus status;
        private Timestamp fromTime;
        private Timestamp toTime;
        private Long contractId;
        private Integer limit = 100;
        private Integer offset = 0;
        
        public ExecutionQueryParams operationType(String operationType) {
            this.operationType = operationType;
            return this;
        }
        
        public ExecutionQueryParams module(String module) {
            this.module = module;
            return this;
        }
        
        public ExecutionQueryParams status(ExecutionStatus status) {
            this.status = status;
            return this;
        }
        
        public ExecutionQueryParams fromTime(Timestamp fromTime) {
            this.fromTime = fromTime;
            return this;
        }
        
        public ExecutionQueryParams toTime(Timestamp toTime) {
            this.toTime = toTime;
            return this;
        }
        
        public ExecutionQueryParams contractId(Long contractId) {
            this.contractId = contractId;
            return this;
        }
        
        public ExecutionQueryParams limit(Integer limit) {
            this.limit = limit;
            return this;
        }
        
        public ExecutionQueryParams offset(Integer offset) {
            this.offset = offset;
            return this;
        }
        
        // Getters
        public String getOperationType() { return operationType; }
        public String getModule() { return module; }
        public ExecutionStatus getStatus() { return status; }
        public Timestamp getFromTime() { return fromTime; }
        public Timestamp getToTime() { return toTime; }
        public Long getContractId() { return contractId; }
        public Integer getLimit() { return limit; }
        public Integer getOffset() { return offset; }
    }
    
    /**
     * Query execution history with filters.
     * 
     * @param params query parameters
     * @return list of execution history entries
     */
    public List<ExecutionHistoryEntry> queryExecutionHistory(ExecutionQueryParams params) {
        List<ExecutionHistoryEntry> results = new ArrayList<>();
        
        if (!databaseManager.isInitialized()) {
            return results;
        }
        
        try (Connection conn = databaseManager.getConnection()) {
            StringBuilder sql = new StringBuilder(
                "SELECT id, operation_type, module, started_at, completed_at, status, " +
                "input_summary, output_summary, error_message, contract_id " +
                "FROM execution_history WHERE 1=1"
            );
            
            List<Object> queryParams = new ArrayList<>();
            
            if (params.getOperationType() != null && !params.getOperationType().isEmpty()) {
                sql.append(" AND operation_type = ?");
                queryParams.add(params.getOperationType());
            }
            
            if (params.getModule() != null && !params.getModule().isEmpty()) {
                sql.append(" AND module = ?");
                queryParams.add(params.getModule());
            }
            
            if (params.getStatus() != null) {
                sql.append(" AND status = ?");
                queryParams.add(params.getStatus().name().toLowerCase());
            }
            
            if (params.getFromTime() != null) {
                sql.append(" AND started_at >= ?");
                queryParams.add(params.getFromTime());
            }
            
            if (params.getToTime() != null) {
                sql.append(" AND started_at <= ?");
                queryParams.add(params.getToTime());
            }
            
            if (params.getContractId() != null) {
                sql.append(" AND contract_id = ?");
                queryParams.add(params.getContractId());
            }
            
            sql.append(" ORDER BY started_at DESC");
            
            if (params.getLimit() != null && params.getLimit() > 0) {
                sql.append(" LIMIT ?");
                queryParams.add(params.getLimit());
            }
            
            if (params.getOffset() != null && params.getOffset() > 0) {
                sql.append(" OFFSET ?");
                queryParams.add(params.getOffset());
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < queryParams.size(); i++) {
                    stmt.setObject(i + 1, queryParams.get(i));
                }
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(new ExecutionHistoryEntry(rs));
                    }
                }
            }
            
            logger.debug("Execution history query returned {} results", results.size());
            
        } catch (SQLException e) {
            logger.error("Failed to query execution history: {}", e.getMessage());
        }
        
        return results;
    }
    
    /**
     * Count execution history entries matching query parameters.
     * 
     * @param params query parameters (limit/offset ignored)
     * @return count of matching entries
     */
    public int countExecutionHistory(ExecutionQueryParams params) {
        if (!databaseManager.isInitialized()) {
            return 0;
        }
        
        try (Connection conn = databaseManager.getConnection()) {
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM execution_history WHERE 1=1");
            List<Object> queryParams = new ArrayList<>();
            
            if (params.getOperationType() != null && !params.getOperationType().isEmpty()) {
                sql.append(" AND operation_type = ?");
                queryParams.add(params.getOperationType());
            }
            
            if (params.getModule() != null && !params.getModule().isEmpty()) {
                sql.append(" AND module = ?");
                queryParams.add(params.getModule());
            }
            
            if (params.getStatus() != null) {
                sql.append(" AND status = ?");
                queryParams.add(params.getStatus().name().toLowerCase());
            }
            
            if (params.getFromTime() != null) {
                sql.append(" AND started_at >= ?");
                queryParams.add(params.getFromTime());
            }
            
            if (params.getToTime() != null) {
                sql.append(" AND started_at <= ?");
                queryParams.add(params.getToTime());
            }
            
            if (params.getContractId() != null) {
                sql.append(" AND contract_id = ?");
                queryParams.add(params.getContractId());
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < queryParams.size(); i++) {
                    stmt.setObject(i + 1, queryParams.get(i));
                }
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to count execution history: {}", e.getMessage());
        }
        
        return 0;
    }
    
    /**
     * Truncate string to maximum length.
     * 
     * @param str the string to truncate
     * @param maxLength maximum length
     * @return truncated string
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }
}

