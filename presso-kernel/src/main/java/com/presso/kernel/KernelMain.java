/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: KernelMain.java
 * RESPONSIBILITY: Application entry point and main orchestration loop
 * 
 * ARCHITECTURAL ROLE:
 * - Single entry point for the Java Kernel process
 * - Spawned by Electron main process as a child process
 * - Communicates with Electron via stdin/stdout (IPC)
 * - Coordinates all internal kernel components
 * 
 * LIFECYCLE:
 * 1. Startup: Initialize all managers → Signal ready
 * 2. Idle: Read IPC messages → Dispatch → Respond
 * 3. Shutdown: Drain queue → Stop engines → Exit
 * 
 * FORBIDDEN (per PROJECT_DOCUMENTATION.md):
 * - NO HTTP endpoints
 * - NO direct UI rendering
 * - NO external network calls
 * 
 * Reference: PROJECT_DOCUMENTATION.md Section 4.2
 */
package com.presso.kernel;

import com.presso.kernel.lifecycle.LifecycleManager;
import com.presso.kernel.scheduling.TaskScheduler;
import com.presso.kernel.routing.ModuleRouter;
import com.presso.kernel.state.StateManager;
import com.presso.kernel.event.EventBus;
import com.presso.kernel.security.SecurityGateway;
import com.presso.kernel.engine.EngineProcessManager;
import com.presso.kernel.persistence.DatabaseManager;
import com.presso.kernel.persistence.ExecutionHistoryService;
import com.presso.kernel.persistence.ActivityLogService;
import com.presso.kernel.persistence.ContractService;
import com.presso.kernel.query.QueryHandler;
import com.presso.kernel.workflow.WorkflowEngine;
import com.presso.kernel.workflow.WorkflowTriggerService;
import com.presso.kernel.workflow.WorkflowDefinition;
import com.presso.kernel.workflow.persistence.WorkflowPersistenceService;
import com.presso.kernel.ai.AIAdvisorService;
import com.presso.kernel.ai.GuardrailEnforcer;
import com.presso.kernel.ai.GuardrailPolicyLoader;
import com.presso.kernel.ai.DraftGenerationService;
import com.presso.kernel.ai.DraftArtifact;
import com.presso.kernel.ipc.IpcMessage;
import com.presso.kernel.ipc.KernelResponse;

import com.google.gson.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Main entry point for the PressO Orchestration Kernel.
 * <p>
 * This class bootstraps all kernel components and runs the main IPC loop.
 * It does NOT contain business logic - only orchestration wiring.
 * </p>
 */
public final class KernelMain {
    
    private static final Logger logger = LoggerFactory.getLogger(KernelMain.class);
    
    // Core kernel components - loosely coupled via interfaces
    private final LifecycleManager lifecycleManager;
    private final TaskScheduler taskScheduler;
    private final ModuleRouter moduleRouter;
    private final StateManager stateManager;
    private final EventBus eventBus;
    private final SecurityGateway securityGateway;
    private final EngineProcessManager engineProcessManager;
    private final DatabaseManager databaseManager;
    private final ExecutionHistoryService executionHistory;
    private final ActivityLogService activityLog;
    private final ContractService contractService;
    private final QueryHandler queryHandler;
    private final WorkflowEngine workflowEngine;
    private final WorkflowTriggerService workflowTriggerService;  // Phase 5 Step 2
    private final AIAdvisorService aiAdvisorService;  // Phase 6 Step 1
    private final GuardrailEnforcer guardrailEnforcer;  // Phase 6 Step 3
    private final DraftGenerationService draftGenerationService;  // Phase 6 Step 4
    
    // IPC channels
    private final BufferedReader ipcInput;
    private final PrintWriter ipcOutput;
    
    // Lifecycle state
    private volatile boolean running = false;
    
    /**
     * Private constructor - use {@link #main(String[])} for entry.
     * Initializes all kernel components in dependency order.
     */
    private KernelMain() {
        // Initialize IPC channels (stdin/stdout)
        this.ipcInput = new BufferedReader(
            new InputStreamReader(System.in, StandardCharsets.UTF_8)
        );
        this.ipcOutput = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        
        // Initialize components in dependency order
        // EventBus first - other components may subscribe to events
        this.eventBus = new EventBus();
        
        // StateManager early - provides configuration to others
        this.stateManager = new StateManager(eventBus);
        
        // DatabaseManager - initialize early for persistence services
        this.databaseManager = new DatabaseManager();
        
        // Persistence services
        this.executionHistory = new ExecutionHistoryService(databaseManager);
        this.activityLog = new ActivityLogService(databaseManager);
        this.contractService = new ContractService(databaseManager);
        
        // Query handler for read-only queries
        this.queryHandler = new QueryHandler(contractService, executionHistory, activityLog);
        
        // SecurityGateway before processing components
        this.securityGateway = new SecurityGateway(stateManager);
        
        // EngineProcessManager manages engine subprocesses
        this.engineProcessManager = new EngineProcessManager(eventBus, stateManager);
        
        // ModuleRouter needs engine manager for dispatch
        this.moduleRouter = new ModuleRouter(engineProcessManager, securityGateway);
        
        // Phase 5 Step 1: Initialize WorkflowEngine (needs moduleRouter and databaseManager)
        this.workflowEngine = new WorkflowEngine(moduleRouter, eventBus, databaseManager);
        
        // Phase 5 Step 2: Initialize WorkflowTriggerService
        this.workflowTriggerService = new WorkflowTriggerService(workflowEngine, eventBus);
        
        // Phase 6 Step 1: Initialize AI Advisor Service (read-only)
        WorkflowPersistenceService workflowPersistence = new WorkflowPersistenceService(databaseManager);
        this.aiAdvisorService = new AIAdvisorService(databaseManager, workflowPersistence);
        
        // Phase 6 Step 3: Initialize Guardrail Enforcer (load policy from config)
        com.presso.kernel.ai.GuardrailPolicy policy;
        try {
            policy = GuardrailPolicyLoader.loadPolicy(stateManager.getConfigPath());
            logger.info("Guardrail policy loaded from config");
        } catch (Exception e) {
            logger.error("Failed to load guardrail policy, using defaults: {}", e.getMessage());
            policy = null;  // Will use default policy
        }
        this.guardrailEnforcer = new GuardrailEnforcer(databaseManager, policy);
        
        // Phase 6 Step 4: Initialize Draft Generation Service (draft-only)
        this.draftGenerationService = new DraftGenerationService(databaseManager, workflowPersistence);
        
        // TaskScheduler coordinates task execution (with execution history and contract service)
        this.taskScheduler = new TaskScheduler(moduleRouter, eventBus, executionHistory, contractService);
        
        // LifecycleManager supervises overall lifecycle (with activity log)
        this.lifecycleManager = new LifecycleManager(
            eventBus, stateManager, engineProcessManager, taskScheduler, activityLog
        );
        
        logger.info("Kernel components initialized");
    }
    
    /**
     * Start the kernel and enter the main IPC loop.
     * 
     * @throws Exception if startup fails
     */
    public void start() throws Exception {
        logger.info("Starting PressO Kernel...");
        
        // Phase 0: Initialize database (before startup)
        try {
            databaseManager.initialize();
            logger.info("Database initialized: {}", databaseManager.getDbPath());
        } catch (Exception e) {
            // Fail-safe: log error but continue (database is optional for basic operation)
            logger.error("Database initialization failed, continuing without persistence: {}", e.getMessage());
        }
        
        // Phase 1: Startup
        lifecycleManager.startup();
        
        // Phase 5 Step 2: Start workflow trigger service
        workflowTriggerService.start();
        
        // Phase 5 Step 2: Resume any interrupted workflows
        workflowEngine.resumeAllWorkflows();
        
        // Phase 5 Step 3: Load pending approvals
        workflowEngine.loadPendingApprovals();
        
        running = true;
        
        // Signal ready to parent process (Electron)
        sendResponse(KernelResponse.ready());
        logger.info("Kernel ready, entering main loop");
        
        // Phase 2: Main IPC loop (blocking)
        runMainLoop();
        
        // Phase 3: Shutdown (reached when loop exits)
        // Phase 5 Step 2: Stop workflow trigger service
        workflowTriggerService.stop();
        
        lifecycleManager.shutdown();
        
        // Phase 4: Close database
        try {
            databaseManager.close();
            logger.info("Database closed");
        } catch (Exception e) {
            logger.error("Error closing database: {}", e.getMessage());
        }
        
        logger.info("Kernel shutdown complete");
    }
    
    /**
     * Main IPC message processing loop.
     * Reads messages from stdin, processes them, writes responses to stdout.
     */
    private void runMainLoop() {
        while (running) {
            try {
                // Read IPC message from stdin (blocking)
                String line = ipcInput.readLine();
                
                if (line == null) {
                    // EOF - parent process closed stdin
                    logger.info("IPC channel closed, initiating shutdown");
                    running = false;
                    break;
                }
                
                if (line.isBlank()) {
                    continue;
                }
                
                // Parse and process message
                processMessage(line);
                
            } catch (Exception e) {
                logger.error("Error in main loop: {}", e.getMessage());
                sendResponse(KernelResponse.error("INTERNAL_ERROR", e.getMessage()));
            }
        }
    }
    
    /**
     * Process a single IPC message.
     * 
     * @param rawMessage the raw JSON message string
     */
    private void processMessage(String rawMessage) {
        try {
            // Parse message
            IpcMessage message = IpcMessage.parse(rawMessage);
            logger.debug("Received message: type={}, id={}", message.getType(), message.getId());
            
            // Validate through security gateway
            if (!securityGateway.validateMessage(message)) {
                sendResponse(KernelResponse.error(
                    message.getId(), 
                    "SECURITY_VIOLATION", 
                    "Message validation failed"
                ));
                return;
            }
            
            // Check for shutdown command
            if ("SHUTDOWN".equals(message.getType())) {
                logger.info("Shutdown command received");
                running = false;
                sendResponse(KernelResponse.success(message.getId(), "Shutdown initiated"));
                return;
            }
            
            // Handle read-only query commands (Phase 3 Step 4)
            String messageType = message.getType();
            if ("QUERY_CONTRACTS".equals(messageType)) {
                handleQueryContracts(message);
                return;
            }
            
            if ("GET_CONTRACT_BY_ID".equals(messageType)) {
                handleGetContractById(message);
                return;
            }
            
            if ("QUERY_EXECUTION_HISTORY".equals(messageType)) {
                handleQueryExecutionHistory(message);
                return;
            }
            
            if ("QUERY_ACTIVITY_LOGS".equals(messageType)) {
                handleQueryActivityLogs(message);
                return;
            }
            
            // Phase 5 Step 1: Workflow management commands
            if ("LOAD_WORKFLOW".equals(messageType)) {
                handleLoadWorkflow(message);
                return;
            }
            
            if ("START_WORKFLOW".equals(messageType)) {
                handleStartWorkflow(message);
                return;
            }
            
            if ("GET_WORKFLOW_STATUS".equals(messageType)) {
                handleGetWorkflowStatus(message);
                return;
            }
            
            // Phase 5 Step 2: Workflow trigger management
            if ("REGISTER_WORKFLOW_TRIGGER".equals(messageType)) {
                handleRegisterWorkflowTrigger(message);
                return;
            }
            
            if ("UNREGISTER_WORKFLOW_TRIGGER".equals(messageType)) {
                handleUnregisterWorkflowTrigger(message);
                return;
            }
            
            if ("LIST_WORKFLOW_TRIGGERS".equals(messageType)) {
                handleListWorkflowTriggers(message);
                return;
            }
            
            // Phase 5 Step 3: Human-in-the-loop approval commands
            if ("RESOLVE_APPROVAL".equals(messageType)) {
                handleResolveApproval(message);
                return;
            }
            
            if ("GET_PENDING_APPROVALS".equals(messageType)) {
                handleGetPendingApprovals(message);
                return;
            }
            
            // ========================================================================
            // PHASE 6 SCOPE FREEZE — AI COMMANDS (READ-ONLY ONLY)
            // ========================================================================
            // AI capabilities are FROZEN at Phase 6 completion.
            // Only read-only AI commands are permitted:
            // - GET_AI_SUGGESTIONS (returns suggestions only, no execution)
            // - GENERATE_DRAFT (returns drafts only, no application)
            //
            // FORBIDDEN: Any AI command that triggers execution, modifies state,
            //            or bypasses human approval.
            //
            // Any new AI commands require new Phase approval.
            // Reference: AI_GOVERNANCE_SUMMARY.md
            // ========================================================================
            
            // Phase 6 Step 1: AI Advisor commands (read-only)
            if ("GET_AI_SUGGESTIONS".equals(messageType)) {
                handleGetAISuggestions(message);
                return;
            }
            
            // Phase 6 Step 4: Draft generation command (draft-only)
            if ("GENERATE_DRAFT".equals(messageType)) {
                handleGenerateDraft(message);
                return;
            }
            
            // Route to appropriate handler via task scheduler
            // TODO (Phase 2): Implement actual task scheduling and routing
            taskScheduler.submitTask(message, response -> sendResponse(response));
            
        } catch (Exception e) {
            logger.error("Failed to process message: {}", e.getMessage());
            sendResponse(KernelResponse.error("PARSE_ERROR", e.getMessage()));
        }
    }
    
    /**
     * Send a response back to the parent process via stdout.
     * 
     * @param response the response to send
     */
    private void sendResponse(KernelResponse response) {
        String json = response.toJson();
        ipcOutput.println(json);
        ipcOutput.flush();
        logger.debug("Sent response: {}", json);
    }
    
    /**
     * Handle QUERY_CONTRACTS IPC command.
     */
    private void handleQueryContracts(IpcMessage message) {
        try {
            JsonObject params = message.getPayload();
            if (params == null) {
                params = new JsonObject();
            }
            
            Map<String, Object> result = queryHandler.handleQueryContracts(params);
            sendResponse(KernelResponse.success(message.getId(), result));
            
        } catch (Exception e) {
            logger.error("Failed to handle QUERY_CONTRACTS: {}", e.getMessage());
            sendResponse(KernelResponse.error(message.getId(), "QUERY_ERROR", e.getMessage()));
        }
    }
    
    /**
     * Handle GET_CONTRACT_BY_ID IPC command.
     */
    private void handleGetContractById(IpcMessage message) {
        try {
            JsonObject params = message.getPayload();
            if (params == null) {
                sendResponse(KernelResponse.error(message.getId(), "INVALID_PARAMS", "payload is required"));
                return;
            }
            
            Map<String, Object> result = queryHandler.handleGetContractById(params);
            sendResponse(KernelResponse.success(message.getId(), result));
            
        } catch (Exception e) {
            logger.error("Failed to handle GET_CONTRACT_BY_ID: {}", e.getMessage());
            sendResponse(KernelResponse.error(message.getId(), "QUERY_ERROR", e.getMessage()));
        }
    }
    
    /**
     * Handle QUERY_EXECUTION_HISTORY IPC command.
     */
    private void handleQueryExecutionHistory(IpcMessage message) {
        try {
            JsonObject params = message.getPayload();
            if (params == null) {
                params = new JsonObject();
            }
            
            Map<String, Object> result = queryHandler.handleQueryExecutionHistory(params);
            sendResponse(KernelResponse.success(message.getId(), result));
            
        } catch (Exception e) {
            logger.error("Failed to handle QUERY_EXECUTION_HISTORY: {}", e.getMessage());
            sendResponse(KernelResponse.error(message.getId(), "QUERY_ERROR", e.getMessage()));
        }
    }
    
    /**
     * Handle QUERY_ACTIVITY_LOGS IPC command.
     */
    private void handleQueryActivityLogs(IpcMessage message) {
        try {
            JsonObject params = message.getPayload();
            if (params == null) {
                params = new JsonObject();
            }
            
            Map<String, Object> result = queryHandler.handleQueryActivityLogs(params);
            sendResponse(KernelResponse.success(message.getId(), result));
            
        } catch (Exception e) {
            logger.error("Failed to handle QUERY_ACTIVITY_LOGS: {}", e.getMessage());
            sendResponse(KernelResponse.error(message.getId(), "QUERY_ERROR", e.getMessage()));
        }
    }
    
    /**
     * Handle LOAD_WORKFLOW IPC command (Phase 5 Step 1).
     */
    private void handleLoadWorkflow(IpcMessage message) {
        try {
            JsonObject params = message.getPayload();
            if (params == null || !params.has("workflow_id") || !params.has("definition")) {
                sendResponse(KernelResponse.error(message.getId(), "INVALID_PARAMS", 
                    "payload must contain workflow_id and definition"));
                return;
            }
            
            String workflowId = params.get("workflow_id").getAsString();
            JsonObject definition = params.getAsJsonObject("definition");
            
            workflowEngine.loadWorkflow(workflowId, definition);
            sendResponse(KernelResponse.success(message.getId(), Map.of("workflow_id", workflowId)));
            
        } catch (Exception e) {
            logger.error("Failed to handle LOAD_WORKFLOW: {}", e.getMessage());
            sendResponse(KernelResponse.error(message.getId(), "WORKFLOW_ERROR", e.getMessage()));
        }
    }
    
    /**
     * Handle START_WORKFLOW IPC command (Phase 5 Step 1).
     */
    private void handleStartWorkflow(IpcMessage message) {
        try {
            JsonObject params = message.getPayload();
            if (params == null || !params.has("workflow_id")) {
                sendResponse(KernelResponse.error(message.getId(), "INVALID_PARAMS", 
                    "payload must contain workflow_id"));
                return;
            }
            
            String workflowId = params.get("workflow_id").getAsString();
            JsonObject initialContext = params.has("initial_context") && params.get("initial_context").isJsonObject()
                ? params.getAsJsonObject("initial_context")
                : new JsonObject();
            
            String executionId = workflowEngine.startWorkflow(workflowId, initialContext);
            sendResponse(KernelResponse.success(message.getId(), Map.of(
                "execution_id", executionId,
                "workflow_id", workflowId
            )));
            
        } catch (Exception e) {
            logger.error("Failed to handle START_WORKFLOW: {}", e.getMessage());
            sendResponse(KernelResponse.error(message.getId(), "WORKFLOW_ERROR", e.getMessage()));
        }
    }
    
    /**
     * Handle GET_WORKFLOW_STATUS IPC command (Phase 5 Step 1).
     */
    private void handleGetWorkflowStatus(IpcMessage message) {
        try {
            JsonObject params = message.getPayload();
            if (params == null || !params.has("execution_id")) {
                sendResponse(KernelResponse.error(message.getId(), "INVALID_PARAMS", 
                    "payload must contain execution_id"));
                return;
            }
            
            String executionId = params.get("execution_id").getAsString();
            com.presso.kernel.workflow.WorkflowExecutionStatus status = workflowEngine.getExecutionStatus(executionId);
            
            if (status == null) {
                sendResponse(KernelResponse.error(message.getId(), "NOT_FOUND", 
                    "Workflow execution not found: " + executionId));
                return;
            }
            
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("execution_id", status.getExecutionId());
            result.put("workflow_id", status.getWorkflowId());
            result.put("workflow_name", status.getWorkflowName());
            result.put("status", status.getStatus());
            if (status.getStartedAt() != null) {
                result.put("started_at", status.getStartedAt().toString());
            }
            if (status.getCompletedAt() != null) {
                result.put("completed_at", status.getCompletedAt().toString());
            }
            if (status.getErrorMessage() != null) {
                result.put("error_message", status.getErrorMessage());
            }
            
            sendResponse(KernelResponse.success(message.getId(), result));
            
        } catch (Exception e) {
            logger.error("Failed to handle GET_WORKFLOW_STATUS: {}", e.getMessage());
            sendResponse(KernelResponse.error(message.getId(), "WORKFLOW_ERROR", e.getMessage()));
        }
    }
    
    /**
     * Handle REGISTER_WORKFLOW_TRIGGER IPC command (Phase 5 Step 2).
     */
    private void handleRegisterWorkflowTrigger(IpcMessage message) {
        try {
            JsonObject params = message.getPayload();
            if (params == null || !params.has("event_type") || !params.has("workflow_id")) {
                sendResponse(KernelResponse.error(message.getId(), "INVALID_PARAMS", 
                    "payload must contain event_type and workflow_id"));
                return;
            }
            
            String eventType = params.get("event_type").getAsString();
            String workflowId = params.get("workflow_id").getAsString();
            
            workflowTriggerService.registerEventTrigger(eventType, workflowId);
            sendResponse(KernelResponse.success(message.getId(), Map.of(
                "event_type", eventType,
                "workflow_id", workflowId
            )));
            
        } catch (Exception e) {
            logger.error("Failed to handle REGISTER_WORKFLOW_TRIGGER: {}", e.getMessage());
            sendResponse(KernelResponse.error(message.getId(), "WORKFLOW_ERROR", e.getMessage()));
        }
    }
    
    /**
     * Handle UNREGISTER_WORKFLOW_TRIGGER IPC command (Phase 5 Step 2).
     */
    private void handleUnregisterWorkflowTrigger(IpcMessage message) {
        try {
            JsonObject params = message.getPayload();
            if (params == null || !params.has("event_type")) {
                sendResponse(KernelResponse.error(message.getId(), "INVALID_PARAMS", 
                    "payload must contain event_type"));
                return;
            }
            
            String eventType = params.get("event_type").getAsString();
            
            workflowTriggerService.unregisterEventTrigger(eventType);
            sendResponse(KernelResponse.success(message.getId(), Map.of("event_type", eventType)));
            
        } catch (Exception e) {
            logger.error("Failed to handle UNREGISTER_WORKFLOW_TRIGGER: {}", e.getMessage());
            sendResponse(KernelResponse.error(message.getId(), "WORKFLOW_ERROR", e.getMessage()));
        }
    }
    
    /**
     * Handle LIST_WORKFLOW_TRIGGERS IPC command (Phase 5 Step 2).
     */
    private void handleListWorkflowTriggers(IpcMessage message) {
        try {
            Map<String, String> triggers = workflowTriggerService.getEventTriggers();
            sendResponse(KernelResponse.success(message.getId(), Map.of("triggers", triggers)));
            
        } catch (Exception e) {
            logger.error("Failed to handle LIST_WORKFLOW_TRIGGERS: {}", e.getMessage());
            sendResponse(KernelResponse.error(message.getId(), "WORKFLOW_ERROR", e.getMessage()));
        }
    }
    
    /**
     * Handle RESOLVE_APPROVAL IPC command (Phase 5 Step 3).
     */
    private void handleResolveApproval(IpcMessage message) {
        try {
            JsonObject params = message.getPayload();
            if (params == null || !params.has("execution_id") || !params.has("step_id") || !params.has("decision")) {
                sendResponse(KernelResponse.error(message.getId(), "INVALID_PARAMS", 
                    "payload must contain execution_id, step_id, and decision"));
                return;
            }
            
            String executionId = params.get("execution_id").getAsString();
            String stepId = params.get("step_id").getAsString();
            String decision = params.get("decision").getAsString();
            String actorId = params.has("actor_id") ? params.get("actor_id").getAsString() : "unknown";
            String comment = params.has("comment") ? params.get("comment").getAsString() : null;
            
            // Resolve approval
            boolean resolved = workflowEngine.getApprovalService().resolveApproval(executionId, stepId, decision, actorId, comment);
            
            if (!resolved) {
                sendResponse(KernelResponse.error(message.getId(), "APPROVAL_ERROR", 
                    "Approval not found or already resolved"));
                return;
            }
            
            // Resume workflow based on decision
            boolean resumed = workflowEngine.resumeAfterApproval(executionId, decision);
            
            if (resumed) {
                sendResponse(KernelResponse.success(message.getId(), Map.of(
                    "execution_id", executionId,
                    "step_id", stepId,
                    "decision", decision,
                    "resumed", true
                )));
            } else {
                sendResponse(KernelResponse.error(message.getId(), "RESUME_ERROR", 
                    "Failed to resume workflow after approval"));
            }
            
        } catch (Exception e) {
            logger.error("Failed to handle RESOLVE_APPROVAL: {}", e.getMessage());
            sendResponse(KernelResponse.error(message.getId(), "APPROVAL_ERROR", e.getMessage()));
        }
    }
    
    /**
     * Handle GET_PENDING_APPROVALS IPC command (Phase 5 Step 3).
     */
    private void handleGetPendingApprovals(IpcMessage message) {
        try {
            java.util.List<Map<String, Object>> pending = new java.util.ArrayList<>();
            
            // Get all pending approvals from ApprovalService
            Map<String, com.presso.kernel.workflow.ApprovalService.ApprovalRequest> pendingApprovals = 
                workflowEngine.getApprovalService().getPendingApprovals();
            for (Map.Entry<String, com.presso.kernel.workflow.ApprovalService.ApprovalRequest> entry : pendingApprovals.entrySet()) {
                com.presso.kernel.workflow.ApprovalService.ApprovalRequest request = entry.getValue();
                Map<String, Object> approvalInfo = new java.util.HashMap<>();
                approvalInfo.put("execution_id", request.getExecutionId());
                approvalInfo.put("step_id", request.getStepId());
                approvalInfo.put("prompt", request.getPrompt());
                approvalInfo.put("allowed_actions", request.getAllowedActions());
                approvalInfo.put("requested_at", request.getRequestedAt());
                pending.add(approvalInfo);
            }
            
            sendResponse(KernelResponse.success(message.getId(), Map.of("pending_approvals", pending)));
            
        } catch (Exception e) {
            logger.error("Failed to handle GET_PENDING_APPROVALS: {}", e.getMessage());
            sendResponse(KernelResponse.error(message.getId(), "APPROVAL_ERROR", e.getMessage()));
        }
    }
    
    /**
     * Handle GET_AI_SUGGESTIONS IPC command (Phase 6 Step 1).
     */
    private void handleGetAISuggestions(IpcMessage message) {
        try {
            JsonObject params = message.getPayload();
            if (params == null) {
                sendResponse(KernelResponse.error(message.getId(), "INVALID_PARAMS", 
                    "payload required"));
                return;
            }
            
            String analysisType = params.has("analysis_type") ? params.get("analysis_type").getAsString() : "definition";
            java.util.List<com.presso.kernel.ai.AISuggestion> rawSuggestions = new java.util.ArrayList<>();
            String executionId = null;
            
            if ("definition".equals(analysisType) && params.has("workflow_id")) {
                String workflowId = params.get("workflow_id").getAsString();
                WorkflowDefinition definition = workflowEngine.getWorkflowDefinition(workflowId);
                if (definition != null) {
                    rawSuggestions = aiAdvisorService.analyzeWorkflowDefinition(workflowId, definition);
                } else {
                    sendResponse(KernelResponse.error(message.getId(), "WORKFLOW_NOT_FOUND", 
                        "Workflow definition not found: " + workflowId));
                    return;
                }
            } else if ("history".equals(analysisType) && params.has("workflow_id")) {
                String workflowId = params.get("workflow_id").getAsString();
                rawSuggestions = aiAdvisorService.analyzeExecutionHistory(workflowId);
            } else if ("state".equals(analysisType) && params.has("execution_id")) {
                executionId = params.get("execution_id").getAsString();
                rawSuggestions = aiAdvisorService.analyzeExecutionState(executionId);
            } else {
                sendResponse(KernelResponse.error(message.getId(), "INVALID_PARAMS", 
                    "Must specify analysis_type (definition|history|state) and corresponding workflow_id or execution_id"));
                return;
            }
            
            // Phase 6 Step 3: Enforce guardrails (filter, flag, block suggestions)
            java.util.List<com.presso.kernel.ai.AISuggestion> suggestions = 
                guardrailEnforcer.enforce(rawSuggestions, analysisType, executionId);
            
            // Convert suggestions to JSON
            com.google.gson.JsonArray suggestionsArray = new com.google.gson.JsonArray();
            for (com.presso.kernel.ai.AISuggestion suggestion : suggestions) {
                suggestionsArray.add(suggestion.toJson());
            }
            
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("suggestions", suggestionsArray);
            result.put("count", suggestions.size());
            
            sendResponse(KernelResponse.success(message.getId(), result));
            
        } catch (Exception e) {
            logger.error("Failed to handle GET_AI_SUGGESTIONS: {}", e.getMessage());
            sendResponse(KernelResponse.error(message.getId(), "AI_ERROR", e.getMessage()));
        }
    }
    
    /**
     * Handle GENERATE_DRAFT IPC command (Phase 6 Step 4).
     */
    private void handleGenerateDraft(IpcMessage message) {
        try {
            JsonObject params = message.getPayload();
            if (params == null || !params.has("draft_type")) {
                sendResponse(KernelResponse.error(message.getId(), "INVALID_PARAMS", 
                    "payload must contain draft_type"));
                return;
            }
            
            String draftTypeStr = params.get("draft_type").getAsString();
            DraftArtifact.DraftType draftType;
            try {
                draftType = DraftArtifact.DraftType.valueOf(draftTypeStr);
            } catch (IllegalArgumentException e) {
                sendResponse(KernelResponse.error(message.getId(), "INVALID_DRAFT_TYPE", 
                    "Unknown draft type: " + draftTypeStr));
                return;
            }
            
            JsonObject contextScope = params.has("context_scope") && params.get("context_scope").isJsonObject() ?
                params.getAsJsonObject("context_scope") : new JsonObject();
            
            JsonObject constraints = params.has("constraints") && params.get("constraints").isJsonObject() ?
                params.getAsJsonObject("constraints") : new JsonObject();
            
            // Generate draft
            DraftArtifact rawDraft = draftGenerationService.generateDraft(draftType, contextScope, constraints);
            
            // Phase 6 Step 4: Enforce guardrails on draft
            String executionId = contextScope.has("execution_id") ? 
                contextScope.get("execution_id").getAsString() : null;
            DraftArtifact draft = guardrailEnforcer.enforceDraft(rawDraft, executionId);
            
            if (draft == null) {
                // Draft was blocked by guardrails
                sendResponse(KernelResponse.error(message.getId(), "DRAFT_BLOCKED", 
                    "Draft was blocked by guardrail policy"));
                return;
            }
            
            // Convert draft to JSON
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("draft", draft.toJson());
            
            sendResponse(KernelResponse.success(message.getId(), result));
            
        } catch (Exception e) {
            logger.error("Failed to handle GENERATE_DRAFT: {}", e.getMessage());
            sendResponse(KernelResponse.error(message.getId(), "DRAFT_GENERATION_ERROR", e.getMessage()));
        }
    }
    
    /**
     * Application entry point.
     * 
     * @param args command-line arguments (reserved for future use)
     */
    public static void main(String[] args) {
        logger.info("PressO Kernel starting (Java {})", Runtime.version());
        
        try {
            KernelMain kernel = new KernelMain();
            kernel.start();
            System.exit(0);
        } catch (Exception e) {
            logger.error("Fatal error: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}

