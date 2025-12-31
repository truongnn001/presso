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
        running = true;
        
        // Signal ready to parent process (Electron)
        sendResponse(KernelResponse.ready());
        logger.info("Kernel ready, entering main loop");
        
        // Phase 2: Main IPC loop (blocking)
        runMainLoop();
        
        // Phase 3: Shutdown (reached when loop exits)
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

