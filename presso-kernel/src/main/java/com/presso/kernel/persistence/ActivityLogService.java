/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: ActivityLogService.java
 * RESPONSIBILITY: Record high-level user/system activity with structured events
 * 
 * ARCHITECTURAL ROLE:
 * - Records activity events (task started, completed, failed, engine crash)
 * - Provides audit trail for system operations
 * - Supports querying by multiple dimensions
 * - Fail-safe: logging failures don't crash execution
 * 
 * BOUNDARIES:
 * - Does NOT contain business logic
 * - Append-only at this stage
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
 * Service for recording and querying activity logs.
 */
public final class ActivityLogService {
    
    private static final Logger logger = LoggerFactory.getLogger(ActivityLogService.class);
    
    private final DatabaseManager databaseManager;
    
    /**
     * Activity event types (taxonomy).
     */
    public enum ActivityAction {
        TASK_STARTED,
        TASK_COMPLETED,
        TASK_FAILED,
        ENGINE_STARTED,
        ENGINE_CRASHED,
        ENGINE_RESTARTED,
        SYSTEM_STARTUP,
        SYSTEM_SHUTDOWN
    }
    
    /**
     * Severity levels for activity events.
     */
    public enum Severity {
        INFO,
        WARN,
        ERROR
    }
    
    /**
     * Structured activity event record.
     */
    public static class ActivityEvent {
        private final ActivityAction action;
        private final Severity severity;
        private final String module;
        private final String shortMessage;
        private final String entityType;
        private final Integer entityId;
        private final Long relatedExecutionId;
        private final String metadata;
        
        private ActivityEvent(Builder builder) {
            this.action = builder.action;
            this.severity = builder.severity;
            this.module = builder.module;
            this.shortMessage = builder.shortMessage;
            this.entityType = builder.entityType;
            this.entityId = builder.entityId;
            this.relatedExecutionId = builder.relatedExecutionId;
            this.metadata = builder.metadata;
        }
        
        public ActivityAction getAction() { return action; }
        public Severity getSeverity() { return severity; }
        public String getModule() { return module; }
        public String getShortMessage() { return shortMessage; }
        public String getEntityType() { return entityType; }
        public Integer getEntityId() { return entityId; }
        public Long getRelatedExecutionId() { return relatedExecutionId; }
        public String getMetadata() { return metadata; }
        
        /**
         * Builder for ActivityEvent.
         */
        public static class Builder {
            private final ActivityAction action;
            private Severity severity = Severity.INFO;
            private String module;
            private String shortMessage;
            private String entityType;
            private Integer entityId;
            private Long relatedExecutionId;
            private String metadata;
            
            public Builder(ActivityAction action) {
                this.action = action;
            }
            
            public Builder severity(Severity severity) {
                this.severity = severity;
                return this;
            }
            
            public Builder module(String module) {
                this.module = module;
                return this;
            }
            
            public Builder shortMessage(String shortMessage) {
                this.shortMessage = shortMessage;
                return this;
            }
            
            public Builder entity(String entityType, Integer entityId) {
                this.entityType = entityType;
                this.entityId = entityId;
                return this;
            }
            
            public Builder relatedExecutionId(Long relatedExecutionId) {
                this.relatedExecutionId = relatedExecutionId;
                return this;
            }
            
            public Builder metadata(String metadata) {
                this.metadata = metadata;
                return this;
            }
            
            public ActivityEvent build() {
                return new ActivityEvent(this);
            }
        }
    }
    
    /**
     * Query result record.
     */
    public static class ActivityLogEntry {
        private final long id;
        private final Timestamp timestamp;
        private final String action;
        private final String severity;
        private final String module;
        private final String shortMessage;
        private final String entityType;
        private final Integer entityId;
        private final Long relatedExecutionId;
        private final String details;
        private final String metadata;
        
        public ActivityLogEntry(ResultSet rs) throws SQLException {
            this.id = rs.getLong("id");
            this.timestamp = rs.getTimestamp("timestamp");
            this.action = rs.getString("user_action");
            this.severity = rs.getString("severity");
            this.module = rs.getString("module");
            this.shortMessage = rs.getString("short_message");
            this.entityType = rs.getString("entity_type");
            this.entityId = rs.getObject("entity_id", Integer.class);
            this.relatedExecutionId = rs.getObject("related_execution_id", Long.class);
            this.details = rs.getString("details");
            this.metadata = rs.getString("metadata");
        }
        
        public long getId() { return id; }
        public Timestamp getTimestamp() { return timestamp; }
        public String getAction() { return action; }
        public String getSeverity() { return severity; }
        public String getModule() { return module; }
        public String getShortMessage() { return shortMessage; }
        public String getEntityType() { return entityType; }
        public Integer getEntityId() { return entityId; }
        public Long getRelatedExecutionId() { return relatedExecutionId; }
        public String getDetails() { return details; }
        public String getMetadata() { return metadata; }
    }
    
    /**
     * Query parameters for activity logs.
     */
    public static class QueryParams {
        private Timestamp fromTime;
        private Timestamp toTime;
        private ActivityAction action;
        private Severity severity;
        private String module;
        private Long relatedExecutionId;
        private String entityType;
        private Integer entityId;
        private Integer limit = 100;
        private Integer offset = 0;
        
        public QueryParams fromTime(Timestamp fromTime) {
            this.fromTime = fromTime;
            return this;
        }
        
        public QueryParams toTime(Timestamp toTime) {
            this.toTime = toTime;
            return this;
        }
        
        public QueryParams action(ActivityAction action) {
            this.action = action;
            return this;
        }
        
        public QueryParams severity(Severity severity) {
            this.severity = severity;
            return this;
        }
        
        public QueryParams module(String module) {
            this.module = module;
            return this;
        }
        
        public QueryParams relatedExecutionId(Long relatedExecutionId) {
            this.relatedExecutionId = relatedExecutionId;
            return this;
        }
        
        public QueryParams entity(String entityType, Integer entityId) {
            this.entityType = entityType;
            this.entityId = entityId;
            return this;
        }
        
        public QueryParams limit(Integer limit) {
            this.limit = limit;
            return this;
        }
        
        public QueryParams offset(Integer offset) {
            this.offset = offset;
            return this;
        }
        
        // Getters
        public Timestamp getFromTime() { return fromTime; }
        public Timestamp getToTime() { return toTime; }
        public ActivityAction getAction() { return action; }
        public Severity getSeverity() { return severity; }
        public String getModule() { return module; }
        public Long getRelatedExecutionId() { return relatedExecutionId; }
        public String getEntityType() { return entityType; }
        public Integer getEntityId() { return entityId; }
        public Integer getLimit() { return limit; }
        public Integer getOffset() { return offset; }
    }
    
    /**
     * Construct an ActivityLogService.
     * 
     * @param databaseManager the database manager
     */
    public ActivityLogService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        logger.debug("ActivityLogService created");
    }
    
    /**
     * Record a structured activity event.
     * 
     * @param event the activity event
     */
    public void recordActivity(ActivityEvent event) {
        if (!databaseManager.isInitialized()) {
            logger.debug("Database not initialized, skipping activity log");
            return;
        }
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO activity_log (" +
                 "timestamp, user_action, entity_type, entity_id, details, " +
                 "related_execution_id, module, severity, short_message, metadata" +
                 ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            
            Timestamp now = Timestamp.from(Instant.now());
            stmt.setTimestamp(1, now);
            stmt.setString(2, event.getAction().name());
            stmt.setString(3, event.getEntityType());
            stmt.setObject(4, event.getEntityId());
            stmt.setString(5, null); // details field kept for backward compatibility
            stmt.setObject(6, event.getRelatedExecutionId());
            stmt.setString(7, event.getModule());
            stmt.setString(8, event.getSeverity() != null ? event.getSeverity().name() : null);
            stmt.setString(9, truncate(event.getShortMessage(), 500));
            stmt.setString(10, truncate(event.getMetadata(), 5000));
            
            int rows = stmt.executeUpdate();
            conn.commit();
            
            if (rows > 0) {
                logger.debug("Recorded activity: action={}, severity={}, module={}",
                    event.getAction(), event.getSeverity(), event.getModule());
            }
            
        } catch (SQLException e) {
            // Fail-safe: log error but don't throw
            logger.error("Failed to record activity: action={}, error={}", event.getAction(), e.getMessage());
        }
    }
    
    /**
     * Record an activity event (backward compatibility method).
     * 
     * @param action the activity action
     * @param entityType optional entity type
     * @param entityId optional entity ID
     * @param details optional details
     */
    public void recordActivity(ActivityAction action, String entityType, Integer entityId, String details) {
        // Convert to structured event
        ActivityEvent.Builder builder = new ActivityEvent.Builder(action)
            .entity(entityType, entityId);
        
        // Infer severity from action
        if (action == ActivityAction.TASK_FAILED || action == ActivityAction.ENGINE_CRASHED) {
            builder.severity(Severity.ERROR);
        } else if (action == ActivityAction.ENGINE_RESTARTED) {
            builder.severity(Severity.WARN);
        }
        
        // Use details as short message
        if (details != null) {
            builder.shortMessage(truncate(details, 500));
        }
        
        recordActivity(builder.build());
    }
    
    /**
     * Record an activity event without entity (backward compatibility method).
     * 
     * @param action the activity action
     * @param details optional details
     */
    public void recordActivity(ActivityAction action, String details) {
        recordActivity(action, null, null, details);
    }
    
    /**
     * Query activity logs with filters.
     * 
     * @param params query parameters
     * @return list of activity log entries
     */
    public List<ActivityLogEntry> queryActivityLogs(QueryParams params) {
        List<ActivityLogEntry> results = new ArrayList<>();
        
        if (!databaseManager.isInitialized()) {
            logger.debug("Database not initialized, returning empty results");
            return results;
        }
        
        try (Connection conn = databaseManager.getConnection()) {
            StringBuilder sql = new StringBuilder(
                "SELECT id, timestamp, user_action, severity, module, short_message, " +
                "entity_type, entity_id, related_execution_id, details, metadata " +
                "FROM activity_log WHERE 1=1"
            );
            
            List<Object> queryParams = new ArrayList<>();
            
            if (params.getFromTime() != null) {
                sql.append(" AND timestamp >= ?");
                queryParams.add(params.getFromTime());
            }
            
            if (params.getToTime() != null) {
                sql.append(" AND timestamp <= ?");
                queryParams.add(params.getToTime());
            }
            
            if (params.getAction() != null) {
                sql.append(" AND user_action = ?");
                queryParams.add(params.getAction().name());
            }
            
            if (params.getSeverity() != null) {
                sql.append(" AND severity = ?");
                queryParams.add(params.getSeverity().name());
            }
            
            if (params.getModule() != null) {
                sql.append(" AND module = ?");
                queryParams.add(params.getModule());
            }
            
            if (params.getRelatedExecutionId() != null) {
                sql.append(" AND related_execution_id = ?");
                queryParams.add(params.getRelatedExecutionId());
            }
            
            if (params.getEntityType() != null) {
                sql.append(" AND entity_type = ?");
                queryParams.add(params.getEntityType());
            }
            
            if (params.getEntityId() != null) {
                sql.append(" AND entity_id = ?");
                queryParams.add(params.getEntityId());
            }
            
            sql.append(" ORDER BY timestamp DESC");
            
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
                        results.add(new ActivityLogEntry(rs));
                    }
                }
            }
            
            logger.debug("Query returned {} activity log entries", results.size());
            
        } catch (SQLException e) {
            logger.error("Failed to query activity logs: {}", e.getMessage());
        }
        
        return results;
    }
    
    /**
     * Get count of activity logs matching query parameters.
     * 
     * @param params query parameters (limit/offset ignored)
     * @return count of matching entries
     */
    public int countActivityLogs(QueryParams params) {
        if (!databaseManager.isInitialized()) {
            return 0;
        }
        
        try (Connection conn = databaseManager.getConnection()) {
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM activity_log WHERE 1=1");
            List<Object> queryParams = new ArrayList<>();
            
            if (params.getFromTime() != null) {
                sql.append(" AND timestamp >= ?");
                queryParams.add(params.getFromTime());
            }
            
            if (params.getToTime() != null) {
                sql.append(" AND timestamp <= ?");
                queryParams.add(params.getToTime());
            }
            
            if (params.getAction() != null) {
                sql.append(" AND user_action = ?");
                queryParams.add(params.getAction().name());
            }
            
            if (params.getSeverity() != null) {
                sql.append(" AND severity = ?");
                queryParams.add(params.getSeverity().name());
            }
            
            if (params.getModule() != null) {
                sql.append(" AND module = ?");
                queryParams.add(params.getModule());
            }
            
            if (params.getRelatedExecutionId() != null) {
                sql.append(" AND related_execution_id = ?");
                queryParams.add(params.getRelatedExecutionId());
            }
            
            if (params.getEntityType() != null) {
                sql.append(" AND entity_type = ?");
                queryParams.add(params.getEntityType());
            }
            
            if (params.getEntityId() != null) {
                sql.append(" AND entity_id = ?");
                queryParams.add(params.getEntityId());
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
            logger.error("Failed to count activity logs: {}", e.getMessage());
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
