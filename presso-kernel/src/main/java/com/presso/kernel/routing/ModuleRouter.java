/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: ModuleRouter.java
 * RESPONSIBILITY: Route requests to appropriate engine based on operation type
 * 
 * ARCHITECTURAL ROLE:
 * - Determines which engine should handle each operation
 * - Validates operations against security gateway
 * - Dispatches work to EngineProcessManager
 * - Returns responses from engines
 * 
 * ROUTING RULES (per PROJECT_DOCUMENTATION.md Section 3.2):
 * - Python Engine: PDF, Image, Excel, OCR, AI/LLM operations
 * - Rust Engine: Crypto, performance-critical, parallel compute
 * - Go API Hub: All external API calls
 * 
 * BOUNDARIES:
 * - Does NOT contain business logic
 * - Does NOT directly spawn processes
 * - Delegates process management to EngineProcessManager
 */
package com.presso.kernel.routing;

import com.google.gson.JsonObject;
import com.presso.kernel.engine.EngineProcessManager;
import com.presso.kernel.security.SecurityGateway;
import com.presso.kernel.ipc.IpcMessage;
import com.presso.kernel.ipc.KernelResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * Routes incoming requests to the appropriate processing engine.
 * <p>
 * Maintains a mapping of operation types to engine targets and
 * validates all operations through the security gateway.
 * </p>
 */
public final class ModuleRouter {
    
    private static final Logger logger = LoggerFactory.getLogger(ModuleRouter.class);
    
    /**
     * Engine identifiers.
     */
    public enum Engine {
        PYTHON,
        RUST,
        GO_API_HUB,
        KERNEL  // Operations handled by kernel itself
    }
    
    // Operation type to engine mapping
    // Based on PROJECT_DOCUMENTATION.md Section 3.2 and 4.3-4.5
    private static final Map<String, Engine> OPERATION_ROUTES = Map.ofEntries(
        // Python Engine operations
        Map.entry("PYTHON_PING", Engine.PYTHON),  // Test Python engine IPC
        Map.entry("EXPORT_EXCEL", Engine.PYTHON),
        Map.entry("EXPORT_PDF", Engine.PYTHON),
        Map.entry("EXPORT_IMAGE", Engine.PYTHON),
        Map.entry("PDF_MERGE", Engine.PYTHON),
        Map.entry("PDF_SPLIT", Engine.PYTHON),
        Map.entry("PDF_ROTATE", Engine.PYTHON),
        Map.entry("PDF_WATERMARK", Engine.PYTHON),
        Map.entry("IMAGE_COMPRESS", Engine.PYTHON),
        Map.entry("IMAGE_CONVERT", Engine.PYTHON),
        Map.entry("IMAGE_RESIZE", Engine.PYTHON),
        Map.entry("LIST_TEMPLATES", Engine.PYTHON),
        Map.entry("LOAD_TEMPLATE", Engine.PYTHON),
        Map.entry("GET_TEMPLATE_PATH", Engine.PYTHON),
        Map.entry("OCR_EXTRACT", Engine.PYTHON),
        Map.entry("AI_QUERY", Engine.PYTHON),
        Map.entry("AI_LEARN", Engine.PYTHON),
        
        // Rust Engine operations
        Map.entry("CRYPTO_ENCRYPT", Engine.RUST),
        Map.entry("CRYPTO_DECRYPT", Engine.RUST),
        Map.entry("CRYPTO_HASH", Engine.RUST),
        Map.entry("PARALLEL_PROCESS", Engine.RUST),
        Map.entry("COMPRESS_DATA", Engine.RUST),
        
        // Go API Hub operations (Phase 4 Step 1+)
        Map.entry("GO_PING", Engine.GO_API_HUB),  // Test Go engine IPC
        // Phase 4 Step 2: External API integration (MOCK)
        Map.entry("EXTERNAL_API_CALL", Engine.GO_API_HUB),  // Mock external API calls
        Map.entry("LIST_PROVIDERS", Engine.GO_API_HUB),     // List available mock providers
        Map.entry("GET_PROVIDER_INFO", Engine.GO_API_HUB),  // Get provider details
        // Future (Phase 4 Step 3+):
        Map.entry("API_CALL", Engine.GO_API_HUB),           // TODO: Real HTTP calls
        Map.entry("TAX_CODE_LOOKUP", Engine.GO_API_HUB),    // TODO: Real tax API
        Map.entry("AUTH_REFRESH", Engine.GO_API_HUB),       // TODO: OAuth refresh
        
        // Kernel-handled operations
        Map.entry("PING", Engine.KERNEL),
        Map.entry("GET_STATUS", Engine.KERNEL),
        Map.entry("GET_CONFIG", Engine.KERNEL),
        Map.entry("SET_CONFIG", Engine.KERNEL),
        Map.entry("GET_ENGINE_STATUS", Engine.KERNEL),
        Map.entry("SHUTDOWN", Engine.KERNEL),
        // Query operations (Phase 3 Step 4)
        Map.entry("QUERY_CONTRACTS", Engine.KERNEL),
        Map.entry("GET_CONTRACT_BY_ID", Engine.KERNEL),
        Map.entry("QUERY_EXECUTION_HISTORY", Engine.KERNEL),
        Map.entry("QUERY_ACTIVITY_LOGS", Engine.KERNEL)
    );
    
    // Operations that are allowed (whitelist)
    private static final Set<String> ALLOWED_OPERATIONS = OPERATION_ROUTES.keySet();
    
    private final EngineProcessManager engineProcessManager;
    private final SecurityGateway securityGateway;
    
    /**
     * Construct a ModuleRouter with required dependencies.
     * 
     * @param engineProcessManager the engine process manager
     * @param securityGateway the security gateway for validation
     */
    public ModuleRouter(EngineProcessManager engineProcessManager, SecurityGateway securityGateway) {
        this.engineProcessManager = engineProcessManager;
        this.securityGateway = securityGateway;
        logger.debug("ModuleRouter created with {} routes", OPERATION_ROUTES.size());
    }
    
    /**
     * Route a message to the appropriate engine and return the response.
     * 
     * @param message the IPC message to route
     * @return the response from the engine
     */
    public KernelResponse route(IpcMessage message) {
        String operationType = message.getType();
        String messageId = message.getId();
        
        // Validate operation is in whitelist
        if (!ALLOWED_OPERATIONS.contains(operationType)) {
            logger.warn("Unknown operation type: {}", operationType);
            return KernelResponse.error(
                messageId,
                "UNKNOWN_OPERATION",
                "Operation type not recognized: " + operationType
            );
        }
        
        // Get target engine
        Engine targetEngine = OPERATION_ROUTES.get(operationType);
        logger.debug("Routing operation {} to engine {}", operationType, targetEngine);
        
        // Handle kernel-internal operations
        if (targetEngine == Engine.KERNEL) {
            return handleKernelOperation(message);
        }
        
        // Dispatch to engine
        return dispatchToEngine(targetEngine, message);
    }
    
    /**
     * Handle operations that are processed by the kernel itself.
     * 
     * @param message the IPC message
     * @return the response
     */
    private KernelResponse handleKernelOperation(IpcMessage message) {
        String operationType = message.getType();
        String messageId = message.getId();
        
        switch (operationType) {
            case "PING":
                // IPC round-trip test: respond with PONG (kernel only)
                logger.info("PING received, sending PONG");
                return KernelResponse.success(messageId, Map.of(
                    "message", "PONG",
                    "source", "kernel",
                    "kernel", "Java Orchestration Kernel",
                    "java", Runtime.version().toString(),
                    "timestamp", System.currentTimeMillis()
                ));
                
            case "GET_STATUS":
                return KernelResponse.success(messageId, Map.of(
                    "status", "running",
                    "engines", engineProcessManager.getRunningEngineCount(),
                    "uptime", System.currentTimeMillis()
                ));
                
            case "GET_ENGINE_STATUS":
                // Get status of all engines
                return KernelResponse.success(messageId, Map.of(
                    "python", engineProcessManager.getEngineInfo("python"),
                    "rust", engineProcessManager.getEngineInfo("rust"),
                    "go", engineProcessManager.getEngineInfo("go")
                ));
                
            case "GET_CONFIG":
                // TODO: Return actual configuration
                return KernelResponse.success(messageId, Map.of());
                
            case "SET_CONFIG":
                // TODO: Apply configuration changes
                return KernelResponse.success(messageId, "Configuration updated");
                
            default:
                return KernelResponse.error(messageId, "NOT_IMPLEMENTED", 
                    "Kernel operation not implemented: " + operationType);
        }
    }
    
    /**
     * Dispatch a message to an external engine.
     * 
     * @param engine the target engine
     * @param message the IPC message
     * @return the response from the engine
     */
    private KernelResponse dispatchToEngine(Engine engine, IpcMessage message) {
        String messageId = message.getId();
        String operationType = message.getType();
        // Map Engine enum to engine process name
        String engineName = mapEngineToProcessName(engine);
        
        // Check if engine is available
        if (!engineProcessManager.isEngineAvailable(engineName)) {
            logger.warn("Engine {} not available for operation {}", engine, operationType);
            return KernelResponse.error(
                messageId,
                "ENGINE_UNAVAILABLE",
                "Engine " + engine.name() + " is not available"
            );
        }
        
        try {
            // Build message for engine
            JsonObject engineMessage = new JsonObject();
            engineMessage.addProperty("id", messageId);
            engineMessage.addProperty("type", mapOperationToEngineMethod(operationType));
            
            // Add payload if present
            if (message.getPayload() != null) {
                engineMessage.add("params", message.getPayload());
            }
            
            logger.debug("Dispatching to engine {}: {}", engine, operationType);
            
            // Send to engine and wait for response
            JsonObject response = engineProcessManager.sendMessage(engineName, engineMessage);
            
            // Convert engine response to KernelResponse
            return convertEngineResponse(messageId, response);
            
        } catch (Exception e) {
            logger.error("Error dispatching to engine {}: {}", engine, e.getMessage());
            return KernelResponse.error(
                messageId,
                "ENGINE_ERROR",
                "Failed to communicate with engine: " + e.getMessage()
            );
        }
    }
    
    /**
     * Map Engine enum to process name used by EngineProcessManager.
     */
    private String mapEngineToProcessName(Engine engine) {
        switch (engine) {
            case PYTHON:
                return "python";
            case RUST:
                return "rust";
            case GO_API_HUB:
                return "go";
            default:
                return engine.name().toLowerCase();
        }
    }
    
    /**
     * Map kernel operation type to engine method name.
     */
    private String mapOperationToEngineMethod(String operationType) {
        // For PYTHON_PING, send as PING to the engine
        if ("PYTHON_PING".equals(operationType)) {
            return "PING";
        }
        
        // For GO_PING, send as PING to the engine (Phase 4 Step 1)
        if ("GO_PING".equals(operationType)) {
            return "PING";
        }
        
        // Default: pass through as-is
        return operationType;
    }
    
    /**
     * Convert engine JSON response to KernelResponse.
     */
    private KernelResponse convertEngineResponse(String originalMessageId, JsonObject response) {
        // Check if engine response indicates success
        boolean success = response.has("success") && response.get("success").getAsBoolean();
        
        if (success) {
            // Extract result
            Object result = null;
            if (response.has("result")) {
                result = response.get("result");
            }
            return KernelResponse.success(originalMessageId, result);
        } else {
            // Extract error
            String errorCode = "ENGINE_ERROR";
            String errorMessage = "Unknown error";
            
            if (response.has("error")) {
                JsonObject error = response.getAsJsonObject("error");
                if (error.has("code")) {
                    errorCode = error.get("code").getAsString();
                }
                if (error.has("message")) {
                    errorMessage = error.get("message").getAsString();
                }
            }
            
            return KernelResponse.error(originalMessageId, errorCode, errorMessage);
        }
    }
    
    /**
     * Get the target engine for an operation type.
     * 
     * @param operationType the operation type
     * @return the engine, or null if unknown
     */
    public Engine getTargetEngine(String operationType) {
        return OPERATION_ROUTES.get(operationType);
    }
    
    /**
     * Check if an operation type is known.
     * 
     * @param operationType the operation type
     * @return true if the operation is in the whitelist
     */
    public boolean isKnownOperation(String operationType) {
        return ALLOWED_OPERATIONS.contains(operationType);
    }
}
