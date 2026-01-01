/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: ApprovalService.java
 * RESPONSIBILITY: Human-in-the-loop approval management
 * 
 * ARCHITECTURAL ROLE:
 * - Manages pending approvals
 * - Records approval decisions in audit trail
 * - Ensures idempotent approval resolution
 * - NO business logic - pure orchestration
 * 
 * Reference: PROJECT_DOCUMENTATION.md Phase 5 Step 3
 */
package com.presso.kernel.workflow;

import com.presso.kernel.persistence.DatabaseManager;
import com.presso.kernel.event.EventBus;
import com.presso.kernel.workflow.persistence.WorkflowPersistenceService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Manages human-in-the-loop approvals.
 */
public final class ApprovalService {
    
    private static final Logger logger = LoggerFactory.getLogger(ApprovalService.class);
    
    private final DatabaseManager databaseManager;
    private final EventBus eventBus;
    private final WorkflowPersistenceService workflowPersistence;
    
    // Pending approvals: executionId:stepId -> ApprovalRequest
    private final Map<String, ApprovalRequest> pendingApprovals = new ConcurrentHashMap<>();
    
    /**
     * Represents a pending approval request.
     */
    public static final class ApprovalRequest {
        private final String executionId;
        private final String stepId;
        private final String prompt;
        private final java.util.List<String> allowedActions;
        private final long requestedAt;
        
        public ApprovalRequest(String executionId, String stepId, String prompt, 
                              java.util.List<String> allowedActions, long requestedAt) {
            this.executionId = executionId;
            this.stepId = stepId;
            this.prompt = prompt;
            this.allowedActions = allowedActions != null ? new java.util.ArrayList<>(allowedActions) : null;
            this.requestedAt = requestedAt;
        }
        
        public String getExecutionId() { return executionId; }
        public String getStepId() { return stepId; }
        public String getPrompt() { return prompt; }
        public java.util.List<String> getAllowedActions() { 
            return allowedActions != null ? new java.util.ArrayList<>(allowedActions) : null; 
        }
        public long getRequestedAt() { return requestedAt; }
    }
    
    /**
     * Construct an ApprovalService.
     * 
     * @param databaseManager database manager
     * @param eventBus event bus
     * @param workflowPersistence workflow persistence service
     */
    public ApprovalService(DatabaseManager databaseManager, EventBus eventBus,
                          WorkflowPersistenceService workflowPersistence) {
        this.databaseManager = databaseManager;
        this.eventBus = eventBus;
        this.workflowPersistence = workflowPersistence;
        logger.info("ApprovalService created");
    }
    
    /**
     * Request approval for a workflow step.
     * 
     * @param executionId workflow execution identifier
     * @param stepId step identifier
     * @param prompt approval prompt text
     * @param allowedActions allowed actions (e.g., ["APPROVE", "REJECT"])
     */
    public void requestApproval(String executionId, String stepId, String prompt,
                               java.util.List<String> allowedActions) {
        String key = executionId + ":" + stepId;
        ApprovalRequest request = new ApprovalRequest(executionId, stepId, prompt, allowedActions, System.currentTimeMillis());
        pendingApprovals.put(key, request);
        
        // Record approval request in audit trail
        recordApprovalRequest(executionId, stepId, prompt, allowedActions);
        
        // Emit event
        eventBus.publish("approval.requested", executionId + ":" + stepId);
        logger.info("Approval requested: executionId={}, stepId={}", executionId, stepId);
    }
    
    /**
     * Resolve an approval decision.
     * 
     * @param executionId workflow execution identifier
     * @param stepId step identifier
     * @param decision decision (APPROVE, REJECT, etc.)
     * @param actorId human identifier
     * @param comment optional comment
     * @return true if resolved successfully, false if already resolved or not found
     */
    public boolean resolveApproval(String executionId, String stepId, String decision,
                                   String actorId, String comment) {
        String key = executionId + ":" + stepId;
        ApprovalRequest request = pendingApprovals.get(key);
        
        if (request == null) {
            // Check if already resolved in database
            if (isApprovalResolved(executionId, stepId)) {
                logger.warn("Approval already resolved: executionId={}, stepId={}", executionId, stepId);
                return false; // Idempotent: already resolved
            }
            logger.warn("Approval request not found: executionId={}, stepId={}", executionId, stepId);
            return false;
        }
        
        // Validate decision
        if (!request.getAllowedActions().contains(decision)) {
            logger.error("Invalid decision for approval: executionId={}, stepId={}, decision={}, allowed={}",
                executionId, stepId, decision, request.getAllowedActions());
            return false;
        }
        
        // Record approval decision in audit trail
        recordApprovalDecision(executionId, stepId, decision, actorId, comment);
        
        // Remove from pending
        pendingApprovals.remove(key);
        
        // Emit event
        eventBus.publish("approval.resolved", executionId + ":" + stepId + ":" + decision);
        logger.info("Approval resolved: executionId={}, stepId={}, decision={}, actorId={}", 
            executionId, stepId, decision, actorId);
        
        return true;
    }
    
    /**
     * Get pending approval request.
     * 
     * @param executionId workflow execution identifier
     * @param stepId step identifier
     * @return approval request, or null if not found
     */
    public ApprovalRequest getPendingApproval(String executionId, String stepId) {
        String key = executionId + ":" + stepId;
        return pendingApprovals.get(key);
    }
    
    /**
     * Get all pending approvals (for IPC GET_PENDING_APPROVALS).
     * 
     * @return map of key -> ApprovalRequest
     */
    public Map<String, ApprovalRequest> getPendingApprovals() {
        return new ConcurrentHashMap<>(pendingApprovals);
    }
    
    /**
     * Check if approval is already resolved.
     * 
     * @param executionId workflow execution identifier
     * @param stepId step identifier
     * @return true if resolved
     */
    private boolean isApprovalResolved(String executionId, String stepId) {
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM workflow_approval WHERE execution_id = ? AND step_id = ? AND decision IS NOT NULL"
            )) {
                stmt.setString(1, executionId);
                stmt.setString(2, stepId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to check approval resolution: executionId={}, stepId={}, error={}", 
                executionId, stepId, e.getMessage());
        }
        return false;
    }
    
    /**
     * Record approval request in audit trail.
     */
    private void recordApprovalRequest(String executionId, String stepId, String prompt,
                                      java.util.List<String> allowedActions) {
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO workflow_approval (execution_id, step_id, prompt, allowed_actions, requested_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)"
            )) {
                stmt.setString(1, executionId);
                stmt.setString(2, stepId);
                stmt.setString(3, prompt);
                stmt.setString(4, String.join(",", allowedActions));
                stmt.executeUpdate();
                conn.commit();
            }
        } catch (SQLException e) {
            logger.error("Failed to record approval request: executionId={}, stepId={}, error={}", 
                executionId, stepId, e.getMessage());
        }
    }
    
    /**
     * Record approval decision in audit trail.
     */
    private void recordApprovalDecision(String executionId, String stepId, String decision,
                                       String actorId, String comment) {
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE workflow_approval SET decision = ?, actor_id = ?, comment = ?, resolved_at = CURRENT_TIMESTAMP WHERE execution_id = ? AND step_id = ? AND decision IS NULL"
            )) {
                stmt.setString(1, decision);
                stmt.setString(2, actorId);
                stmt.setString(3, comment);
                stmt.setString(4, executionId);
                stmt.setString(5, stepId);
                int updated = stmt.executeUpdate();
                conn.commit();
                
                if (updated == 0) {
                    logger.warn("No approval request found to update: executionId={}, stepId={}", executionId, stepId);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to record approval decision: executionId={}, stepId={}, error={}", 
                executionId, stepId, e.getMessage());
        }
    }
    
    /**
     * Load pending approvals from database on startup (Phase 5 Step 3).
     */
    public void loadPendingApprovals() {
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT execution_id, step_id, prompt, allowed_actions FROM workflow_approval WHERE decision IS NULL"
            )) {
                try (ResultSet rs = stmt.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        String executionId = rs.getString("execution_id");
                        String stepId = rs.getString("step_id");
                        String prompt = rs.getString("prompt");
                        String allowedActionsStr = rs.getString("allowed_actions");
                        
                        java.util.List<String> allowedActions = new java.util.ArrayList<>();
                        if (allowedActionsStr != null) {
                            for (String action : allowedActionsStr.split(",")) {
                                allowedActions.add(action.trim());
                            }
                        }
                        
                        String key = executionId + ":" + stepId;
                        ApprovalRequest request = new ApprovalRequest(executionId, stepId, prompt, allowedActions, System.currentTimeMillis());
                        pendingApprovals.put(key, request);
                        count++;
                    }
                    logger.info("Loaded {} pending approvals from database", count);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to load pending approvals: error={}", e.getMessage());
        }
    }
}

