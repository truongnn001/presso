/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: KernelCommand.java
 * RESPONSIBILITY: Command structure for Kernel → Engine communication
 * 
 * ARCHITECTURAL ROLE:
 * - Represents commands sent from Kernel to engine subprocesses
 * - Uses JSON-RPC 2.0 protocol
 * - Serialized to JSON for stdin transmission
 * 
 * JSON-RPC 2.0 FORMAT:
 * {
 *   "jsonrpc": "2.0",
 *   "id": "request-id",
 *   "method": "operation_name",
 *   "params": { ... operation parameters ... }
 * }
 * 
 * Reference: PROJECT_DOCUMENTATION.md Section 3.3 (JSON-RPC 2.0 protocol)
 */
package com.presso.kernel.ipc;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.UUID;

/**
 * Represents a command to be sent to an engine subprocess.
 * <p>
 * Follows JSON-RPC 2.0 specification for structured messaging
 * between the Kernel and engine processes.
 * </p>
 */
public final class KernelCommand {
    
    private static final Gson GSON = new Gson();
    private static final String JSON_RPC_VERSION = "2.0";
    
    private final String id;
    private final String method;
    private final JsonObject params;
    
    /**
     * Construct a KernelCommand.
     * 
     * @param id the request identifier (must be unique)
     * @param method the method/operation name
     * @param params the operation parameters
     */
    public KernelCommand(String id, String method, JsonObject params) {
        this.id = id;
        this.method = method;
        this.params = params;
    }
    
    /**
     * Create a new command with auto-generated ID.
     * 
     * @param method the method name
     * @param params the parameters
     * @return the new command
     */
    public static KernelCommand create(String method, JsonObject params) {
        return new KernelCommand(generateId(), method, params);
    }
    
    /**
     * Create a new command with auto-generated ID and no params.
     * 
     * @param method the method name
     * @return the new command
     */
    public static KernelCommand create(String method) {
        return new KernelCommand(generateId(), method, null);
    }
    
    /**
     * Generate a unique command ID.
     * 
     * @return the generated ID
     */
    private static String generateId() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Get the command ID.
     * 
     * @return the request identifier
     */
    public String getId() {
        return id;
    }
    
    /**
     * Get the method name.
     * 
     * @return the operation method
     */
    public String getMethod() {
        return method;
    }
    
    /**
     * Get the command parameters.
     * 
     * @return the parameters JSON object
     */
    public JsonObject getParams() {
        return params;
    }
    
    /**
     * Serialize the command to JSON-RPC 2.0 format.
     * 
     * @return the JSON string
     */
    public String toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("jsonrpc", JSON_RPC_VERSION);
        obj.addProperty("id", id);
        obj.addProperty("method", method);
        
        if (params != null) {
            obj.add("params", params);
        }
        
        return GSON.toJson(obj);
    }
    
    /**
     * Create a shutdown command for engine termination.
     * 
     * @return the shutdown command
     */
    public static KernelCommand shutdown() {
        return create("shutdown");
    }
    
    /**
     * Create a health check command.
     * 
     * @return the health check command
     */
    public static KernelCommand healthCheck() {
        return create("health_check");
    }
    
    @Override
    public String toString() {
        return "KernelCommand{id='" + id + "', method='" + method + "'}";
    }
}

