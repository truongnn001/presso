/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: IpcMessage.java
 * RESPONSIBILITY: IPC message structure for UI ↔ Kernel communication
 * 
 * ARCHITECTURAL ROLE:
 * - Represents messages from Electron UI to Java Kernel
 * - Parsed from JSON received on stdin
 * - Contains command type, payload, and metadata
 * 
 * MESSAGE FORMAT (JSON-RPC 2.0 inspired):
 * {
 *   "id": "unique-request-id",
 *   "type": "OPERATION_TYPE",
 *   "payload": { ... operation-specific data ... },
 *   "timestamp": 1703894400000
 * }
 * 
 * Reference: PROJECT_DOCUMENTATION.md Section 3.3 (Communication Patterns)
 */
package com.presso.kernel.ipc;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an IPC message from the UI layer to the Kernel.
 * <p>
 * Messages are received as JSON on stdin and parsed into this structure
 * for processing by the kernel components.
 * </p>
 */
public final class IpcMessage {
    
    private static final Logger logger = LoggerFactory.getLogger(IpcMessage.class);
    private static final Gson GSON = new Gson();
    
    private final String id;
    private final String type;
    private final JsonObject payload;
    private final long timestamp;
    
    /**
     * Construct an IpcMessage.
     * 
     * @param id the unique message identifier
     * @param type the operation type
     * @param payload the message payload (can be null)
     * @param timestamp the message timestamp
     */
    public IpcMessage(String id, String type, JsonObject payload, long timestamp) {
        this.id = id;
        this.type = type;
        this.payload = payload;
        this.timestamp = timestamp;
    }
    
    /**
     * Parse an IpcMessage from a JSON string.
     * 
     * @param json the JSON string to parse
     * @return the parsed message
     * @throws IllegalArgumentException if parsing fails
     */
    public static IpcMessage parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Empty message");
        }
        
        try {
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            
            // Extract required fields
            String id = getStringOrNull(obj, "id");
            String type = getStringOrNull(obj, "type");
            
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Missing message id");
            }
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("Missing message type");
            }
            
            // Extract optional fields
            JsonObject payload = obj.has("payload") ? obj.getAsJsonObject("payload") : null;
            long timestamp = obj.has("timestamp") ? obj.get("timestamp").getAsLong() : System.currentTimeMillis();
            
            return new IpcMessage(id, type, payload, timestamp);
            
        } catch (JsonSyntaxException e) {
            logger.error("Failed to parse IPC message: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid JSON: " + e.getMessage());
        }
    }
    
    /**
     * Helper to get a string field or null.
     */
    private static String getStringOrNull(JsonObject obj, String field) {
        if (obj.has(field) && !obj.get(field).isJsonNull()) {
            return obj.get(field).getAsString();
        }
        return null;
    }
    
    /**
     * Get the message ID.
     * 
     * @return the unique message identifier
     */
    public String getId() {
        return id;
    }
    
    /**
     * Get the operation type.
     * 
     * @return the operation type (e.g., "EXPORT_EXCEL", "PDF_MERGE")
     */
    public String getType() {
        return type;
    }
    
    /**
     * Get the message payload.
     * 
     * @return the payload JSON object, or null if no payload
     */
    public JsonObject getPayload() {
        return payload;
    }
    
    /**
     * Get a string value from the payload.
     * 
     * @param key the payload key
     * @return the string value, or null if not found
     */
    public String getPayloadString(String key) {
        if (payload == null || !payload.has(key)) {
            return null;
        }
        return payload.get(key).getAsString();
    }
    
    /**
     * Get the message timestamp.
     * 
     * @return the timestamp in milliseconds
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Convert the message to JSON.
     * 
     * @return the JSON string representation
     */
    public String toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("type", type);
        if (payload != null) {
            obj.add("payload", payload);
        }
        obj.addProperty("timestamp", timestamp);
        return GSON.toJson(obj);
    }
    
    @Override
    public String toString() {
        return "IpcMessage{id='" + id + "', type='" + type + "', timestamp=" + timestamp + "}";
    }
}

