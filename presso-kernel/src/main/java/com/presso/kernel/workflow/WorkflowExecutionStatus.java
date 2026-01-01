/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: WorkflowExecutionStatus.java
 * RESPONSIBILITY: Workflow execution status model
 * 
 * Reference: PROJECT_DOCUMENTATION.md Phase 5 Step 1
 */
package com.presso.kernel.workflow;

import java.time.Instant;

/**
 * Represents the status of a workflow execution.
 */
public final class WorkflowExecutionStatus {
    
    private final String executionId;
    private final String workflowId;
    private final String workflowName;
    private final String status;  // "running", "completed", "failed"
    private final Instant startedAt;
    private final Instant completedAt;
    private final String errorMessage;
    
    /**
     * Construct a workflow execution status.
     */
    public WorkflowExecutionStatus(String executionId, String workflowId, String workflowName,
                                   String status, Instant startedAt, Instant completedAt, String errorMessage) {
        this.executionId = executionId;
        this.workflowId = workflowId;
        this.workflowName = workflowName;
        this.status = status;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.errorMessage = errorMessage;
    }
    
    public String getExecutionId() {
        return executionId;
    }
    
    public String getWorkflowId() {
        return workflowId;
    }
    
    public String getWorkflowName() {
        return workflowName;
    }
    
    public String getStatus() {
        return status;
    }
    
    public Instant getStartedAt() {
        return startedAt;
    }
    
    public Instant getCompletedAt() {
        return completedAt;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * Check if workflow execution is resumable (running or paused state) (Phase 5 Step 2 & 3).
     * 
     * @return true if resumable
     */
    public boolean isResumable() {
        return "running".equals(status) || "paused".equals(status) || "paused_waiting_for_approval".equals(status);
    }
}

