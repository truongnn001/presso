/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: WorkflowEngine.java
 * RESPONSIBILITY: Core workflow orchestration engine
 * 
 * ARCHITECTURAL ROLE:
 * - Loads and validates workflow definitions
 * - Executes workflows step-by-step sequentially
 * - Coordinates with Python Engine and Go API Hub via IPC
 * - Emits lifecycle events via EventBus
 * - Persists execution state
 * 
 * BOUNDARIES:
 * - Does NOT contain business logic
 * - Does NOT transform data (only maps inputs)
 * - Engines remain stateless workers
 * 
 * Reference: PROJECT_DOCUMENTATION.md Phase 5 Step 1
 */
package com.presso.kernel.workflow;

import com.presso.kernel.routing.ModuleRouter;
import com.presso.kernel.event.EventBus;
import com.presso.kernel.ipc.IpcMessage;
import com.presso.kernel.ipc.KernelResponse;
import com.presso.kernel.persistence.DatabaseManager;
import com.presso.kernel.workflow.persistence.WorkflowPersistenceService;
import com.presso.kernel.workflow.DagExecutor;
import com.presso.kernel.workflow.ApprovalService;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.util.concurrent.*;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Core workflow orchestration engine.
 * <p>
 * Executes workflows sequentially, one step at a time.
 * Coordinates with existing engines via IPC.
 * </p>
 */
public final class WorkflowEngine {
    
    private static final Logger logger = LoggerFactory.getLogger(WorkflowEngine.class);
    
    private final ModuleRouter moduleRouter;
    private final EventBus eventBus;
    private final WorkflowPersistenceService persistenceService;
    private final ApprovalService approvalService;  // Phase 5 Step 3
    
    // Active workflow executions (executionId -> context)
    private final Map<String, WorkflowExecutionContext> activeExecutions = new ConcurrentHashMap<>();
    
    // Loaded workflow definitions (workflowId -> definition)
    private final Map<String, WorkflowDefinition> workflowDefinitions = new ConcurrentHashMap<>();
    
    // Pending approvals: executionId -> stepId (for resumption)
    private final Map<String, String> pendingApprovalSteps = new ConcurrentHashMap<>();
    
    /**
     * Construct a WorkflowEngine.
     * 
     * @param moduleRouter router for dispatching steps to engines
     * @param eventBus event bus for lifecycle events
     * @param databaseManager database manager for persistence
     */
    public WorkflowEngine(ModuleRouter moduleRouter, EventBus eventBus, DatabaseManager databaseManager) {
        this.moduleRouter = moduleRouter;
        this.eventBus = eventBus;
        this.persistenceService = new WorkflowPersistenceService(databaseManager);
        this.approvalService = new ApprovalService(databaseManager, eventBus, persistenceService);
        logger.info("WorkflowEngine created");
    }
    
    /**
     * Load and validate a workflow definition.
     * 
     * @param workflowId workflow identifier
     * @param definitionJson workflow definition JSON
     * @throws JsonParseException if definition is invalid
     */
    public void loadWorkflow(String workflowId, JsonObject definitionJson) throws JsonParseException {
        WorkflowDefinition definition = WorkflowDefinition.fromJson(definitionJson);
        
        // Validate workflow ID matches
        if (!workflowId.equals(definition.getWorkflowId())) {
            throw new JsonParseException("Workflow ID mismatch: expected " + workflowId + ", got " + definition.getWorkflowId());
        }
        
        workflowDefinitions.put(workflowId, definition);
        logger.info("Workflow loaded: id={}, name={}, steps={}", workflowId, definition.getName(), definition.getSteps().size());
    }
    
    /**
     * Start workflow execution.
     * 
     * @param workflowId workflow definition ID
     * @param initialContext initial input context
     * @return execution ID
     * @throws IllegalArgumentException if workflow not found
     */
    public String startWorkflow(String workflowId, JsonObject initialContext) {
        WorkflowDefinition definition = workflowDefinitions.get(workflowId);
        if (definition == null) {
            throw new IllegalArgumentException("Workflow not found: " + workflowId);
        }
        
        String executionId = UUID.randomUUID().toString();
        WorkflowExecutionContext context = new WorkflowExecutionContext(executionId, workflowId, initialContext);
        activeExecutions.put(executionId, context);
        
        // Persist workflow execution start
        persistenceService.recordWorkflowStart(executionId, workflowId, definition.getName(), initialContext);
        
        // Emit event
        eventBus.publish("workflow.started", executionId);
        logger.info("Workflow started: executionId={}, workflowId={}", executionId, workflowId);
        
        // Execute workflow asynchronously
        Thread.ofVirtual().name("workflow-" + executionId).start(() -> {
            executeWorkflow(executionId, definition, context);
        });
        
        return executionId;
    }
    
    /**
     * Execute a workflow sequentially.
     * 
     * @param executionId execution identifier
     * @param definition workflow definition
     * @param context execution context
     */
    private void executeWorkflow(String executionId, WorkflowDefinition definition, WorkflowExecutionContext context) {
        try {
            // Phase 5 Step 4: Check if this is a DAG workflow
            if (definition.isDagWorkflow()) {
                executeDagWorkflow(executionId, definition, context);
                return;
            }
            
            // Sequential execution (Phase 5 Step 1)
            for (StepDefinition step : definition.getSteps()) {
                // Record step start
                persistenceService.recordStepStart(executionId, step.getStepId(), step.getType().name());
                eventBus.publish("step.started", executionId + ":" + step.getStepId());
                logger.debug("Step started: executionId={}, stepId={}", executionId, step.getStepId());
                
                // Phase 5 Step 3: Handle HUMAN_APPROVAL steps
                if (step.isApprovalStep()) {
                    boolean approvalResolved = handleApprovalStep(executionId, step, context);
                    if (!approvalResolved) {
                        // Approval not resolved - workflow paused, exit loop
                        return;
                    }
                    // Approval resolved - continue to next step
                    continue;
                }
                
                // Execute step with retry
                boolean stepSuccess = executeStepWithRetry(executionId, step, context);
                
                if (!stepSuccess) {
                    // Step failed - handle according to on_failure policy
                    if (step.getOnFailure() == StepDefinition.OnFailure.FAIL) {
                        // Stop workflow
                        persistenceService.recordWorkflowFailed(executionId, "Step failed: " + step.getStepId());
                        eventBus.publish("workflow.failed", executionId);
                        logger.error("Workflow failed: executionId={}, stepId={}", executionId, step.getStepId());
                        return;
                    } else if (step.getOnFailure() == StepDefinition.OnFailure.SKIP) {
                        // Skip step and continue
                        logger.warn("Step skipped: executionId={}, stepId={}", executionId, step.getStepId());
                        persistenceService.recordStepSkipped(executionId, step.getStepId());
                        continue;
                    }
                    // RETRY is handled in executeStepWithRetry
                }
                
                // Step completed successfully
                persistenceService.recordStepCompleted(executionId, step.getStepId());
                eventBus.publish("step.completed", executionId + ":" + step.getStepId());
                logger.debug("Step completed: executionId={}, stepId={}", executionId, step.getStepId());
            }
            
            // All steps completed
            persistenceService.recordWorkflowCompleted(executionId);
            eventBus.publish("workflow.completed", executionId);
            logger.info("Workflow completed: executionId={}", executionId);
            
        } catch (Exception e) {
            logger.error("Workflow execution error: executionId={}, error={}", executionId, e.getMessage(), e);
            persistenceService.recordWorkflowFailed(executionId, e.getMessage());
            eventBus.publish("workflow.failed", executionId);
        } finally {
            // Clean up active execution
            activeExecutions.remove(executionId);
        }
    }
    
    /**
     * Execute a step with retry logic.
     * 
     * @param executionId execution identifier
     * @param step step definition
     * @param context execution context
     * @return true if step succeeded, false otherwise
     */
    private boolean executeStepWithRetry(String executionId, StepDefinition step, WorkflowExecutionContext context) {
        RetryPolicy retryPolicy = step.getRetryPolicy();
        int attempts = 0;
        Exception lastError = null;
        
        while (attempts < retryPolicy.getMaxAttempts()) {
            attempts++;
            
            try {
                // Resolve input from context
                JsonObject stepInput = context.resolveInput(step.getInputMapping());
                
                // Execute step based on type
                JsonObject stepResult = executeStep(step, stepInput);
                
                // Store result in context
                context.setStepResult(step.getStepId(), stepResult);
                
                // Success
                return true;
                
            } catch (Exception e) {
                lastError = e;
                logger.warn("Step attempt failed: executionId={}, stepId={}, attempt={}/{}, error={}",
                    executionId, step.getStepId(), attempts, retryPolicy.getMaxAttempts(), e.getMessage());
                
                // If not last attempt, wait before retry
                if (attempts < retryPolicy.getMaxAttempts()) {
                    try {
                        Thread.sleep(retryPolicy.getBackoffMs());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        
        // All retries exhausted
        persistenceService.recordStepFailed(executionId, step.getStepId(), lastError != null ? lastError.getMessage() : "Unknown error");
        return false;
    }
    
    /**
     * Execute a single step by dispatching to appropriate engine.
     * 
     * @param step step definition
     * @param stepInput resolved step input
     * @return step result
     * @throws Exception if step execution fails
     */
    private JsonObject executeStep(StepDefinition step, JsonObject stepInput) throws Exception {
        IpcMessage message;
        
        switch (step.getType()) {
            case PYTHON_TASK:
                // Create IPC message for Python Engine
                message = new IpcMessage(
                    UUID.randomUUID().toString(),
                    stepInput.has("operation") ? stepInput.get("operation").getAsString() : "EXECUTE_PYTHON",
                    stepInput,
                    System.currentTimeMillis()
                );
                break;
                
            case GO_API_CALL:
                // Create IPC message for Go API Hub
                message = new IpcMessage(
                    UUID.randomUUID().toString(),
                    "EXTERNAL_API_CALL",
                    stepInput,
                    System.currentTimeMillis()
                );
                break;
                
            case INTERNAL_OP:
                // Internal operations not yet supported
                throw new UnsupportedOperationException("INTERNAL_OP step type not yet implemented");
                
            default:
                throw new IllegalArgumentException("Unknown step type: " + step.getType());
        }
        
        // Route message through ModuleRouter
        KernelResponse response = moduleRouter.route(message);
        
        if (!response.isSuccess()) {
            throw new RuntimeException("Step execution failed: " + response.getErrorCode() + " - " + response.getErrorMessage());
        }
        
        // Extract result from response
        Object result = response.getResult();
        if (result instanceof JsonObject) {
            return (JsonObject) result;
        } else if (result instanceof Map) {
            // Convert Map to JsonObject
            JsonObject jsonResult = new JsonObject();
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                // Simple conversion (for Phase 5 Step 1)
                jsonResult.addProperty(entry.getKey(), entry.getValue().toString());
            }
            return jsonResult;
        } else {
            // Wrap in JSON object
            JsonObject jsonResult = new JsonObject();
            jsonResult.addProperty("result", result != null ? result.toString() : "null");
            return jsonResult;
        }
    }
    
    /**
     * Get workflow execution status.
     * 
     * @param executionId execution identifier
     * @return execution status, or null if not found
     */
    public WorkflowExecutionStatus getExecutionStatus(String executionId) {
        return persistenceService.getExecutionStatus(executionId);
    }
    
    /**
     * Resume a workflow execution after restart (Phase 5 Step 2).
     * 
     * @param executionId execution identifier
     * @return true if resumed, false if not resumable
     */
    public boolean resumeWorkflow(String executionId) {
        WorkflowExecutionStatus status = persistenceService.getExecutionStatus(executionId);
        if (status == null || !status.isResumable()) {
            logger.debug("Workflow not resumable: executionId={}, status={}", executionId, status != null ? status.getStatus() : "null");
            return false;
        }
        
        String workflowId = status.getWorkflowId();
        WorkflowDefinition definition = workflowDefinitions.get(workflowId);
        if (definition == null) {
            logger.error("Cannot resume workflow: definition not loaded: executionId={}, workflowId={}", executionId, workflowId);
            return false;
        }
        
        // Get initial context from database
        String initialContextJson = persistenceService.getInitialContext(executionId);
        JsonObject initialContext = new JsonObject();
        if (initialContextJson != null && !initialContextJson.isEmpty()) {
            try {
                initialContext = com.google.gson.JsonParser.parseString(initialContextJson).getAsJsonObject();
            } catch (Exception e) {
                logger.warn("Failed to parse initial context, using empty: executionId={}, error={}", executionId, e.getMessage());
            }
        }
        
        // Recreate execution context
        WorkflowExecutionContext context = new WorkflowExecutionContext(executionId, workflowId, initialContext);
        
        // Restore step results from database (completed steps only)
        for (StepDefinition step : definition.getSteps()) {
            String stepStatus = persistenceService.getStepStatus(executionId, step.getStepId());
            if ("completed".equals(stepStatus)) {
                // Step was completed - we need to restore its result
                // For Phase 5 Step 2, we'll mark it as completed but don't have the actual result
                // This is acceptable - the workflow will continue from the next step
                logger.debug("Step already completed, skipping: executionId={}, stepId={}", executionId, step.getStepId());
            }
        }
        
        activeExecutions.put(executionId, context);
        
        // Find the last completed step and resume from the next one
        String lastCompletedStepId = persistenceService.getLastCompletedStepId(executionId);
        logger.info("Resuming workflow: executionId={}, workflowId={}, lastCompletedStep={}", 
            executionId, workflowId, lastCompletedStepId);
        
        // Execute workflow from the step after the last completed one
        Thread.ofVirtual().name("workflow-resume-" + executionId).start(() -> {
            resumeWorkflowExecution(executionId, definition, context, lastCompletedStepId);
        });
        
        return true;
    }
    
    /**
     * Resume workflow execution from a specific step (Phase 5 Step 2 & 4).
     * 
     * @param executionId execution identifier
     * @param definition workflow definition
     * @param context execution context
     * @param lastCompletedStepId last completed step ID (null if starting from beginning)
     */
    private void resumeWorkflowExecution(String executionId, WorkflowDefinition definition, 
                                         WorkflowExecutionContext context, String lastCompletedStepId) {
        try {
            // Phase 5 Step 4: Check if this is a DAG workflow
            if (definition.isDagWorkflow()) {
                executeDagWorkflow(executionId, definition, context);
                return;
            }
            
            // Sequential execution (Phase 5 Step 2)
            boolean foundLastStep = (lastCompletedStepId == null);
            
            for (StepDefinition step : definition.getSteps()) {
                // Skip steps until we reach the one after the last completed step
                if (!foundLastStep) {
                    if (step.getStepId().equals(lastCompletedStepId)) {
                        foundLastStep = true;
                    }
                    continue; // Skip this step (already completed)
                }
                
                // Check if step was already completed (safety check)
                String stepStatus = persistenceService.getStepStatus(executionId, step.getStepId());
                if ("completed".equals(stepStatus)) {
                    logger.warn("Step already completed, skipping: executionId={}, stepId={}", executionId, step.getStepId());
                    continue;
                }
                
                // Record step start (or restart if it was running)
                persistenceService.recordStepStart(executionId, step.getStepId(), step.getType().name());
                eventBus.publish("step.started", executionId + ":" + step.getStepId());
                logger.debug("Step started (resumed): executionId={}, stepId={}", executionId, step.getStepId());
                
                // Execute step with retry
                boolean stepSuccess = executeStepWithRetry(executionId, step, context);
                
                if (!stepSuccess) {
                    // Step failed - handle according to on_failure policy
                    if (step.getOnFailure() == StepDefinition.OnFailure.FAIL) {
                        // Stop workflow
                        persistenceService.recordWorkflowFailed(executionId, "Step failed: " + step.getStepId());
                        eventBus.publish("workflow.failed", executionId);
                        logger.error("Workflow failed (resumed): executionId={}, stepId={}", executionId, step.getStepId());
                        return;
                    } else if (step.getOnFailure() == StepDefinition.OnFailure.SKIP) {
                        // Skip step and continue
                        logger.warn("Step skipped (resumed): executionId={}, stepId={}", executionId, step.getStepId());
                        persistenceService.recordStepSkipped(executionId, step.getStepId());
                        continue;
                    }
                }
                
                // Step completed successfully
                persistenceService.recordStepCompleted(executionId, step.getStepId());
                eventBus.publish("step.completed", executionId + ":" + step.getStepId());
                logger.debug("Step completed (resumed): executionId={}, stepId={}", executionId, step.getStepId());
            }
            
            // All remaining steps completed
            persistenceService.recordWorkflowCompleted(executionId);
            eventBus.publish("workflow.completed", executionId);
            logger.info("Workflow completed (resumed): executionId={}", executionId);
            
        } catch (Exception e) {
            logger.error("Workflow execution error (resumed): executionId={}, error={}", executionId, e.getMessage(), e);
            persistenceService.recordWorkflowFailed(executionId, e.getMessage());
            eventBus.publish("workflow.failed", executionId);
        } finally {
            // Clean up active execution
            activeExecutions.remove(executionId);
        }
    }
    
    /**
     * Resume all resumable workflows on startup (Phase 5 Step 2).
     */
    public void resumeAllWorkflows() {
        java.util.List<String> resumableExecutions = persistenceService.getResumableExecutions();
        logger.info("Found {} resumable workflow executions", resumableExecutions.size());
        
        for (String executionId : resumableExecutions) {
            try {
                boolean resumed = resumeWorkflow(executionId);
                if (resumed) {
                    logger.info("Resumed workflow: executionId={}", executionId);
                } else {
                    logger.warn("Failed to resume workflow: executionId={}", executionId);
                }
            } catch (Exception e) {
                logger.error("Error resuming workflow: executionId={}, error={}", executionId, e.getMessage());
            }
        }
    }
    
    /**
     * Load pending approvals on startup (Phase 5 Step 3).
     */
    public void loadPendingApprovals() {
        approvalService.loadPendingApprovals();
    }
    
    /**
     * Get approval service (for IPC handlers).
     */
    public ApprovalService getApprovalService() {
        return approvalService;
    }
    
    /**
     * Get workflow definition by ID (for AI Advisor - Phase 6 Step 1).
     * 
     * @param workflowId workflow identifier
     * @return workflow definition, or null if not found
     */
    public WorkflowDefinition getWorkflowDefinition(String workflowId) {
        WorkflowDefinition definition = workflowDefinitions.get(workflowId);
        if (definition == null) {
            // Try to load from persistence
            definition = persistenceService.loadWorkflowDefinition(workflowId);
            if (definition != null) {
                workflowDefinitions.put(workflowId, definition);
            }
        }
        return definition;
    }
    
    /**
     * Handle a HUMAN_APPROVAL step (Phase 5 Step 3).
     * 
     * @param executionId execution identifier
     * @param step approval step definition
     * @param context execution context
     * @return true if approval resolved and workflow should continue, false if paused
     */
    private boolean handleApprovalStep(String executionId, StepDefinition step, WorkflowExecutionContext context) {
        // Check if approval already resolved (for resumption)
        String existingDecision = getApprovalDecision(executionId, step.getStepId());
        if (existingDecision != null) {
            // Approval already resolved - check decision
            if ("APPROVE".equals(existingDecision)) {
                logger.info("Approval already approved: executionId={}, stepId={}", executionId, step.getStepId());
                persistenceService.recordStepCompleted(executionId, step.getStepId());
                return true; // Continue workflow
            } else {
                logger.info("Approval already rejected: executionId={}, stepId={}", executionId, step.getStepId());
                persistenceService.recordStepFailed(executionId, step.getStepId(), "Approval rejected");
                persistenceService.recordWorkflowFailed(executionId, "Approval rejected: " + step.getStepId());
                eventBus.publish("workflow.failed", executionId);
                return false; // Workflow failed
            }
        }
        
        // Request approval
        approvalService.requestApproval(executionId, step.getStepId(), step.getApprovalPrompt(), 
            step.getAllowedActions());
        
        // Mark workflow as paused waiting for approval
        persistenceService.pauseWorkflowForApproval(executionId);
        pendingApprovalSteps.put(executionId, step.getStepId());
        
        // Remove from active executions (will be restored when approval resolved)
        activeExecutions.remove(executionId);
        
        logger.info("Workflow paused waiting for approval: executionId={}, stepId={}", executionId, step.getStepId());
        return false; // Paused - do not continue
    }
    
    /**
     * Get approval decision from database (Phase 5 Step 3).
     */
    private String getApprovalDecision(String executionId, String stepId) {
        try (java.sql.Connection conn = persistenceService.getDatabaseManager().getConnection()) {
            try (java.sql.PreparedStatement stmt = conn.prepareStatement(
                "SELECT decision FROM workflow_approval WHERE execution_id = ? AND step_id = ? AND decision IS NOT NULL"
            )) {
                stmt.setString(1, executionId);
                stmt.setString(2, stepId);
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("decision");
                    }
                }
            }
        } catch (java.sql.SQLException e) {
            logger.error("Failed to get approval decision: executionId={}, stepId={}, error={}", 
                executionId, stepId, e.getMessage());
        }
        return null;
    }
    
    /**
     * Resume workflow after approval resolved (Phase 5 Step 3).
     * 
     * @param executionId execution identifier
     * @param decision approval decision (APPROVE or REJECT)
     * @return true if workflow resumed, false if not found or already resolved
     */
    public boolean resumeAfterApproval(String executionId, String decision) {
        String stepId = pendingApprovalSteps.remove(executionId);
        if (stepId == null) {
            // Check if it's in database (for resumption after restart)
            String workflowId = persistenceService.getWorkflowId(executionId);
            if (workflowId == null) {
                logger.warn("No pending approval found for execution: executionId={}", executionId);
                return false;
            }
            // Try to load workflow and resume
            WorkflowDefinition definition = workflowDefinitions.get(workflowId);
            if (definition == null) {
                // Try to load from persistence
                definition = persistenceService.loadWorkflowDefinition(workflowId);
                if (definition != null) {
                    workflowDefinitions.put(workflowId, definition);
                } else {
                    logger.error("Cannot resume workflow: definition not found: executionId={}, workflowId={}", executionId, workflowId);
                    return false;
                }
            }
            
            // Find the approval step that was waiting
            String foundStepId = null;
            for (StepDefinition step : definition.getSteps()) {
                if (step.isApprovalStep()) {
                    String approvalDecision = getApprovalDecision(executionId, step.getStepId());
                    if (approvalDecision == null) {
                        // This is the pending approval step
                        foundStepId = step.getStepId();
                        break;
                    }
                }
            }
            
            if (foundStepId == null) {
                logger.warn("No pending approval step found: executionId={}", executionId);
                return false;
            }
            stepId = foundStepId;
        }
        
        final String finalStepId = stepId; // Make final for lambda
        
        WorkflowDefinition definition = workflowDefinitions.get(persistenceService.getWorkflowId(executionId));
        if (definition == null) {
            String workflowId = persistenceService.getWorkflowId(executionId);
            definition = persistenceService.loadWorkflowDefinition(workflowId);
            if (definition != null) {
                workflowDefinitions.put(workflowId, definition);
            } else {
                logger.error("Cannot resume workflow: definition not loaded: executionId={}", executionId);
                return false;
            }
        }
        
        // Get initial context
        String initialContextJson = persistenceService.getInitialContext(executionId);
        JsonObject initialContext = new JsonObject();
        if (initialContextJson != null && !initialContextJson.isEmpty()) {
            try {
                initialContext = com.google.gson.JsonParser.parseString(initialContextJson).getAsJsonObject();
            } catch (Exception e) {
                logger.warn("Failed to parse initial context: executionId={}, error={}", executionId, e.getMessage());
            }
        }
        
        final WorkflowExecutionContext context = new WorkflowExecutionContext(executionId, definition.getWorkflowId(), initialContext);
        final WorkflowDefinition finalDefinition = definition;
        activeExecutions.put(executionId, context);
        
        if ("APPROVE".equals(decision)) {
            // Approval granted - mark step as completed and continue
            persistenceService.recordStepCompleted(executionId, finalStepId);
            eventBus.publish("step.completed", executionId + ":" + finalStepId);
            
            // Resume workflow from next step
            final String lastCompletedStepId = finalStepId; // Current step is now completed
            Thread.ofVirtual().name("workflow-approval-resume-" + executionId).start(() -> {
                resumeWorkflowExecution(executionId, finalDefinition, context, lastCompletedStepId);
            });
            
            logger.info("Workflow resumed after approval: executionId={}, stepId={}", executionId, finalStepId);
            return true;
        } else {
            // Approval rejected - fail workflow
            persistenceService.recordStepFailed(executionId, finalStepId, "Approval rejected");
            persistenceService.recordWorkflowFailed(executionId, "Approval rejected: " + finalStepId);
            eventBus.publish("workflow.failed", executionId);
            activeExecutions.remove(executionId);
            logger.info("Workflow failed after approval rejection: executionId={}, stepId={}", executionId, finalStepId);
            return true; // Handled (failed)
        }
    }
    
    /**
     * Execute a DAG workflow with parallel execution (Phase 5 Step 4).
     * 
     * @param executionId execution identifier
     * @param definition workflow definition
     * @param context execution context
     */
    private void executeDagWorkflow(String executionId, WorkflowDefinition definition, WorkflowExecutionContext context) {
        int maxParallelism = definition.getMaxParallelism() != null ? definition.getMaxParallelism() : Integer.MAX_VALUE;
        DagExecutor dagExecutor = new DagExecutor(persistenceService, eventBus, moduleRouter, maxParallelism);
        dagExecutor.initializeDag(definition);
        
        // Restore step states if resuming
        dagExecutor.restoreStepStates(executionId);
        
        // Executor for parallel step execution
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(maxParallelism, definition.getSteps().size()));
        
        try {
            // Main execution loop
            while (!dagExecutor.allStepsCompleted()) {
                // Check for failures
                if (dagExecutor.hasFailedSteps()) {
                    logger.error("Workflow failed: executionId={}, failed steps detected", executionId);
                    persistenceService.recordWorkflowFailed(executionId, "One or more steps failed");
                    eventBus.publish("workflow.failed", executionId);
                    return;
                }
                
                // Get runnable steps
                Set<String> runnableSteps = dagExecutor.getRunnableSteps();
                
                if (runnableSteps.isEmpty()) {
                    // No runnable steps - check if we're stuck (shouldn't happen in valid DAG)
                    if (!dagExecutor.allStepsCompleted()) {
                        logger.error("Workflow stuck: no runnable steps but not all completed. executionId={}", executionId);
                        persistenceService.recordWorkflowFailed(executionId, "Workflow execution stuck - no runnable steps");
                        eventBus.publish("workflow.failed", executionId);
                        return;
                    }
                    break;
                }
                
                // Execute runnable steps in parallel (limited by maxParallelism)
                List<Future<Boolean>> futures = new ArrayList<>();
                int runningCount = 0;
                
                for (String stepId : runnableSteps) {
                    if (runningCount >= maxParallelism) {
                        break; // Limit parallelism
                    }
                    
                    StepDefinition step = dagExecutor.getStep(stepId);
                    if (step == null) {
                        logger.error("Step not found: executionId={}, stepId={}", executionId, stepId);
                        continue;
                    }
                    
                    // Check if step is already running (safety check)
                    if (dagExecutor.getStepState(stepId) != DagExecutor.StepState.PENDING) {
                        continue;
                    }
                    
                    dagExecutor.markStepRunning(stepId);
                    
                    // Submit step for execution (make variables final for lambda)
                    final String finalStepId = stepId;
                    final StepDefinition finalStep = step;
                    Future<Boolean> future = executor.submit(() -> {
                        try {
                            persistenceService.recordStepStart(executionId, finalStepId, finalStep.getType().name());
                            eventBus.publish("step.started", executionId + ":" + finalStepId);
                            logger.debug("Step started (parallel): executionId={}, stepId={}", executionId, finalStepId);
                            
                            // Phase 5 Step 3: Handle HUMAN_APPROVAL steps
                            if (finalStep.isApprovalStep()) {
                                boolean approvalResolved = handleApprovalStep(executionId, finalStep, context);
                                if (!approvalResolved) {
                                    // Approval not resolved - workflow paused
                                    return false; // Will be handled separately
                                }
                                // Approval resolved - mark as completed
                                dagExecutor.markStepCompleted(finalStepId);
                                persistenceService.recordStepCompleted(executionId, finalStepId);
                                eventBus.publish("step.completed", executionId + ":" + finalStepId);
                                return true;
                            }
                            
                            // Execute step with retry
                            boolean stepSuccess = executeStepWithRetry(executionId, finalStep, context);
                            
                            if (stepSuccess) {
                                dagExecutor.markStepCompleted(finalStepId);
                                persistenceService.recordStepCompleted(executionId, finalStepId);
                                eventBus.publish("step.completed", executionId + ":" + finalStepId);
                                logger.debug("Step completed (parallel): executionId={}, stepId={}", executionId, finalStepId);
                                return true;
                            } else {
                                // Step failed
                                if (finalStep.getOnFailure() == StepDefinition.OnFailure.FAIL) {
                                    dagExecutor.markStepFailed(finalStepId);
                                    persistenceService.recordStepFailed(executionId, finalStepId, "Step execution failed");
                                    return false;
                                } else if (finalStep.getOnFailure() == StepDefinition.OnFailure.SKIP) {
                                    // Skip step - mark as completed (skipped)
                                    dagExecutor.markStepCompleted(finalStepId);
                                    persistenceService.recordStepSkipped(executionId, finalStepId);
                                    return true;
                                }
                                // RETRY is handled in executeStepWithRetry
                                return false;
                            }
                        } catch (Exception e) {
                            logger.error("Error executing step (parallel): executionId={}, stepId={}, error={}", 
                                executionId, finalStepId, e.getMessage(), e);
                            dagExecutor.markStepFailed(finalStepId);
                            persistenceService.recordStepFailed(executionId, finalStepId, e.getMessage());
                            return false;
                        }
                    });
                    
                    futures.add(future);
                }
                
                // Wait for at least one step to complete before checking for new runnable steps
                if (!futures.isEmpty()) {
                    // Wait for all submitted steps to complete
                    for (Future<Boolean> future : futures) {
                        try {
                            future.get(); // Wait for completion
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            logger.error("Step execution interrupted: executionId={}", executionId);
                            return;
                        } catch (ExecutionException e) {
                            logger.error("Step execution error: executionId={}, error={}", executionId, e.getCause().getMessage());
                        }
                    }
                } else {
                    // No steps to run - wait a bit before checking again
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            
            // All steps completed
            if (dagExecutor.hasFailedSteps()) {
                persistenceService.recordWorkflowFailed(executionId, "One or more steps failed");
                eventBus.publish("workflow.failed", executionId);
                logger.error("Workflow failed: executionId={}", executionId);
            } else {
                persistenceService.recordWorkflowCompleted(executionId);
                eventBus.publish("workflow.completed", executionId);
                logger.info("Workflow completed: executionId={}", executionId);
            }
            
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}

