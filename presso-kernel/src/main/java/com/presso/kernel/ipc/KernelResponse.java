/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: KernelResponse.java
 * RESPONSIBILITY: Response structure for Kernel → UI communication
 * 
 * ARCHITECTURAL ROLE:
 * - Represents responses sent from Kernel to Electron UI
 * - Written to stdout for IPC transmission
 * - Contains success/error status, result data, and metadata
 * 
 * RESPONSE FORMAT:
 * Success:
 * {
 *   "id": "request-id",
 *   "success": true,
 *   "result": { ... response data ... },
 *   "timestamp": 1703894400000
 * }
 * 
 * Error:
 * {
 *   "id": "request-id",
 *   "success": false,
 *   "error": {
 *     "code": "ERROR_CODE",
 *     "message": "Human-readable message"
 *   },
 *   "timestamp": 1703894400000
 * }
 * 
 * Reference: PROJECT_DOCUMENTATION.md Section 3.3
 */
package com.presso.kernel.ipc;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Represents a response from the Kernel to the UI layer.
 * <p>
 * Responses are serialized to JSON and written to stdout for
 * transmission back to the Electron main process.
 * </p>
 */
public final class KernelResponse {
    
    private static final Gson GSON = new Gson();
    
    private final String id;
    private final boolean success;
    private final Object result;
    private final String errorCode;
    private final String errorMessage;
    private final long timestamp;
    
    /**
     * Private constructor - use factory methods.
     */
    private KernelResponse(
            String id,
            boolean success,
            Object result,
            String errorCode,
            String errorMessage) {
        this.id = id;
        this.success = success;
        this.result = result;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Create a success response with result data.
     * 
     * @param requestId the original request ID
     * @param result the result data
     * @return the success response
     */
    public static KernelResponse success(String requestId, Object result) {
        return new KernelResponse(requestId, true, result, null, null);
    }
    
    /**
     * Create an error response.
     * 
     * @param requestId the original request ID
     * @param errorCode the error code
     * @param errorMessage the human-readable error message
     * @return the error response
     */
    public static KernelResponse error(String requestId, String errorCode, String errorMessage) {
        return new KernelResponse(requestId, false, null, errorCode, errorMessage);
    }
    
    /**
     * Create an error response without a request ID (for parse errors).
     * 
     * @param errorCode the error code
     * @param errorMessage the human-readable error message
     * @return the error response
     */
    public static KernelResponse error(String errorCode, String errorMessage) {
        return new KernelResponse(null, false, null, errorCode, errorMessage);
    }
    
    /**
     * Create a "ready" response for kernel startup completion.
     * 
     * @return the ready response
     */
    public static KernelResponse ready() {
        return new KernelResponse(
            "KERNEL_READY",
            true,
            java.util.Map.of(
                "status", "ready",
                "version", "0.1.0",
                "java", Runtime.version().toString()
            ),
            null,
            null
        );
    }
    
    /**
     * Get the request ID this response corresponds to.
     * 
     * @return the request ID
     */
    public String getId() {
        return id;
    }
    
    /**
     * Check if this is a success response.
     * 
     * @return true if successful
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * Get the result data (for success responses).
     * 
     * @return the result, or null for error responses
     */
    public Object getResult() {
        return result;
    }
    
    /**
     * Get the error code (for error responses).
     * 
     * @return the error code, or null for success responses
     */
    public String getErrorCode() {
        return errorCode;
    }
    
    /**
     * Get the error message (for error responses).
     * 
     * @return the error message, or null for success responses
     */
    public String getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * Get the response timestamp.
     * 
     * @return the timestamp in milliseconds
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Serialize the response to JSON.
     * 
     * @return the JSON string
     */
    public String toJson() {
        JsonObject obj = new JsonObject();
        
        if (id != null) {
            obj.addProperty("id", id);
        }
        
        obj.addProperty("success", success);
        obj.addProperty("timestamp", timestamp);
        
        if (success) {
            // Add result
            if (result != null) {
                obj.add("result", GSON.toJsonTree(result));
            }
        } else {
            // Add error object
            JsonObject errorObj = new JsonObject();
            errorObj.addProperty("code", errorCode);
            errorObj.addProperty("message", errorMessage);
            obj.add("error", errorObj);
        }
        
        return GSON.toJson(obj);
    }
    
    @Override
    public String toString() {
        if (success) {
            return "KernelResponse{id='" + id + "', success=true}";
        } else {
            return "KernelResponse{id='" + id + "', success=false, error=" + errorCode + "}";
        }
    }
}

