/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: WorkflowPersistenceService.java
 * RESPONSIBILITY: Persist workflow execution state to SQLite
 * 
 * ARCHITECTURAL ROLE:
 * - Persists workflow executions and step executions
 * - Enables workflow resumption after restart
 * - Tracks execution status and timestamps
 * 
 * Reference: PROJECT_DOCUMENTATION.md Phase 5 Step 1
 */
package com.presso.kernel.workflow.persistence;

import com.presso.kernel.persistence.DatabaseManager;
import com.presso.kernel.workflow.WorkflowExecutionStatus;

import com.google.gson.JsonObject;
import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Persists workflow execution state to database.
 */
public final class WorkflowPersistenceService {
    
    private static final Logger logger = LoggerFactory.getLogger(WorkflowPersistenceService.class);
    private static final Gson gson = new Gson();
    
    private final DatabaseManager databaseManager;
    
    /**
     * Get database manager (for ApprovalService access).
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    /**
     * Construct a WorkflowPersistenceService.
     * 
     * @param databaseManager database manager
     */
    public WorkflowPersistenceService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }
    
    /**
     * Record workflow execution start.
     * 
     * @param executionId execution identifier
     * @param workflowId workflow definition ID
     * @param workflowName workflow name
     * @param initialContext initial input context
     */
    public void recordWorkflowStart(String executionId, String workflowId, String workflowName, JsonObject initialContext) {
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO workflow_execution (execution_id, workflow_id, workflow_name, status, initial_context, started_at) VALUES (?, ?, ?, 'running', ?, CURRENT_TIMESTAMP)"
            )) {
                stmt.setString(1, executionId);
                stmt.setString(2, workflowId);
                stmt.setString(3, workflowName);
                stmt.setString(4, gson.toJson(initialContext));
                stmt.executeUpdate();
                conn.commit();
            }
        } catch (SQLException e) {
            logger.error("Failed to record workflow start: executionId={}, error={}", executionId, e.getMessage());
        }
    }
    
    /**
     * Record workflow execution completion.
     * 
     * @param executionId execution identifier
     */
    public void recordWorkflowCompleted(String executionId) {
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE workflow_execution SET status = 'completed', completed_at = CURRENT_TIMESTAMP WHERE execution_id = ?"
            )) {
                stmt.setString(1, executionId);
                stmt.executeUpdate();
                conn.commit();
            }
        } catch (SQLException e) {
            logger.error("Failed to record workflow completion: executionId={}, error={}", executionId, e.getMessage());
        }
    }
    
    /**
     * Record workflow execution failure.
     * 
     * @param executionId execution identifier
     * @param errorMessage error message
     */
    public void recordWorkflowFailed(String executionId, String errorMessage) {
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE workflow_execution SET status = 'failed', error_message = ?, completed_at = CURRENT_TIMESTAMP WHERE execution_id = ?"
            )) {
                stmt.setString(1, errorMessage);
                stmt.setString(2, executionId);
                stmt.executeUpdate();
                conn.commit();
            }
        } catch (SQLException e) {
            logger.error("Failed to record workflow failure: executionId={}, error={}", executionId, e.getMessage());
        }
    }
    
    /**
     * Record step execution start.
     * 
     * @param executionId workflow execution identifier
     * @param stepId step identifier
     * @param stepType step type
     */
    public void recordStepStart(String executionId, String stepId, String stepType) {
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO workflow_step_execution (execution_id, step_id, step_type, status, started_at) VALUES (?, ?, ?, 'running', CURRENT_TIMESTAMP)"
            )) {
                stmt.setString(1, executionId);
                stmt.setString(2, stepId);
                stmt.setString(3, stepType);
                stmt.executeUpdate();
                conn.commit();
            }
        } catch (SQLException e) {
            logger.error("Failed to record step start: executionId={}, stepId={}, error={}", executionId, stepId, e.getMessage());
        }
    }
    
    /**
     * Record step execution completion.
     * 
     * @param executionId workflow execution identifier
     * @param stepId step identifier
     */
    public void recordStepCompleted(String executionId, String stepId) {
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE workflow_step_execution SET status = 'completed', completed_at = CURRENT_TIMESTAMP WHERE execution_id = ? AND step_id = ?"
            )) {
                stmt.setString(1, executionId);
                stmt.setString(2, stepId);
                stmt.executeUpdate();
                conn.commit();
            }
        } catch (SQLException e) {
            logger.error("Failed to record step completion: executionId={}, stepId={}, error={}", executionId, stepId, e.getMessage());
        }
    }
    
    /**
     * Record step execution failure.
     * 
     * @param executionId workflow execution identifier
     * @param stepId step identifier
     * @param errorMessage error message
     */
    public void recordStepFailed(String executionId, String stepId, String errorMessage) {
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE workflow_step_execution SET status = 'failed', error_message = ?, completed_at = CURRENT_TIMESTAMP WHERE execution_id = ? AND step_id = ?"
            )) {
                stmt.setString(1, errorMessage);
                stmt.setString(2, executionId);
                stmt.setString(3, stepId);
                stmt.executeUpdate();
                conn.commit();
            }
        } catch (SQLException e) {
            logger.error("Failed to record step failure: executionId={}, stepId={}, error={}", executionId, stepId, e.getMessage());
        }
    }
    
    /**
     * Record step skipped.
     * 
     * @param executionId workflow execution identifier
     * @param stepId step identifier
     */
    public void recordStepSkipped(String executionId, String stepId) {
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE workflow_step_execution SET status = 'skipped', completed_at = CURRENT_TIMESTAMP WHERE execution_id = ? AND step_id = ?"
            )) {
                stmt.setString(1, executionId);
                stmt.setString(2, stepId);
                stmt.executeUpdate();
                conn.commit();
            }
        } catch (SQLException e) {
            logger.error("Failed to record step skipped: executionId={}, stepId={}, error={}", executionId, stepId, e.getMessage());
        }
    }
    
    /**
     * Get workflow execution status.
     * 
     * @param executionId execution identifier
     * @return execution status, or null if not found
     */
    public WorkflowExecutionStatus getExecutionStatus(String executionId) {
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT execution_id, workflow_id, workflow_name, status, started_at, completed_at, error_message, initial_context FROM workflow_execution WHERE execution_id = ?"
            )) {
                stmt.setString(1, executionId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String status = rs.getString("status");
                        Timestamp startedAt = rs.getTimestamp("started_at");
                        Timestamp completedAt = rs.getTimestamp("completed_at");
                        
                        return new WorkflowExecutionStatus(
                            rs.getString("execution_id"),
                            rs.getString("workflow_id"),
                            rs.getString("workflow_name"),
                            status,
                            startedAt != null ? startedAt.toInstant() : null,
                            completedAt != null ? completedAt.toInstant() : null,
                            rs.getString("error_message")
                        );
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get execution status: executionId={}, error={}", executionId, e.getMessage());
        }
        return null;
    }
    
    /**
     * Get initial context for a workflow execution (Phase 5 Step 2: Resumption).
     * 
     * @param executionId execution identifier
     * @return initial context JSON string, or null if not found
     */
    public String getInitialContext(String executionId) {
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT initial_context FROM workflow_execution WHERE execution_id = ?"
            )) {
                stmt.setString(1, executionId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("initial_context");
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get initial context: executionId={}, error={}", executionId, e.getMessage());
        }
        return null;
    }
    
    /**
     * Get all running or paused workflow executions (Phase 5 Step 2: Resumption).
     * 
     * @return list of execution IDs that need resumption
     */
    public java.util.List<String> getResumableExecutions() {
        java.util.List<String> executions = new java.util.ArrayList<>();
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT execution_id FROM workflow_execution WHERE status IN ('running', 'paused')"
            )) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        executions.add(rs.getString("execution_id"));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get resumable executions: error={}", e.getMessage());
        }
        return executions;
    }
    
    /**
     * Get the last completed step ID for an execution (Phase 5 Step 2: Resumption).
     * 
     * @param executionId execution identifier
     * @return last completed step ID, or null if no steps completed
     */
    public String getLastCompletedStepId(String executionId) {
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT step_id FROM workflow_step_execution WHERE execution_id = ? AND status = 'completed' ORDER BY completed_at DESC LIMIT 1"
            )) {
                stmt.setString(1, executionId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("step_id");
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get last completed step: executionId={}, error={}", executionId, e.getMessage());
        }
        return null;
    }
    
    /**
     * Get step execution state (Phase 5 Step 2: Resumption).
     * 
     * @param executionId execution identifier
     * @param stepId step identifier
     * @return step status, or null if not found
     */
    public String getStepStatus(String executionId, String stepId) {
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT status FROM workflow_step_execution WHERE execution_id = ? AND step_id = ?"
            )) {
                stmt.setString(1, executionId);
                stmt.setString(2, stepId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("status");
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get step status: executionId={}, stepId={}, error={}", executionId, stepId, e.getMessage());
        }
        return null;
    }
    
    /**
     * Mark workflow as paused (Phase 5 Step 2: Resumption).
     * 
     * @param executionId execution identifier
     */
    public void pauseWorkflow(String executionId) {
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE workflow_execution SET status = 'paused' WHERE execution_id = ?"
            )) {
                stmt.setString(1, executionId);
                stmt.executeUpdate();
                conn.commit();
            }
        } catch (SQLException e) {
            logger.error("Failed to pause workflow: executionId={}, error={}", executionId, e.getMessage());
        }
    }
    
    /**
     * Mark workflow as paused waiting for approval (Phase 5 Step 3).
     * 
     * @param executionId execution identifier
     */
    public void pauseWorkflowForApproval(String executionId) {
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE workflow_execution SET status = 'paused_waiting_for_approval' WHERE execution_id = ?"
            )) {
                stmt.setString(1, executionId);
                stmt.executeUpdate();
                conn.commit();
            }
        } catch (SQLException e) {
            logger.error("Failed to pause workflow for approval: executionId={}, error={}", executionId, e.getMessage());
        }
    }
    
    /**
     * Get workflow ID for an execution (Phase 5 Step 3).
     * 
     * @param executionId execution identifier
     * @return workflow ID, or null if not found
     */
    public String getWorkflowId(String executionId) {
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT workflow_id FROM workflow_execution WHERE execution_id = ?"
            )) {
                stmt.setString(1, executionId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("workflow_id");
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get workflow ID: executionId={}, error={}", executionId, e.getMessage());
        }
        return null;
    }
    
    /**
     * Load workflow definition from database (Phase 5 Step 3).
     * 
     * @param workflowId workflow identifier
     * @return workflow definition, or null if not found
     */
    public com.presso.kernel.workflow.WorkflowDefinition loadWorkflowDefinition(String workflowId) {
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT definition_json FROM workflow_definition WHERE workflow_id = ?"
            )) {
                stmt.setString(1, workflowId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String json = rs.getString("definition_json");
                        return com.presso.kernel.workflow.WorkflowDefinition.fromJson(
                            com.google.gson.JsonParser.parseString(json).getAsJsonObject());
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to load workflow definition: workflowId={}, error={}", workflowId, e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to parse workflow definition: workflowId={}, error={}", workflowId, e.getMessage());
        }
        return null;
    }
}

