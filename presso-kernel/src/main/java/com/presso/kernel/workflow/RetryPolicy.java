/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: RetryPolicy.java
 * RESPONSIBILITY: Retry policy configuration for workflow steps
 * 
 * ARCHITECTURAL ROLE:
 * - Defines deterministic retry behavior
 * - Simple fixed backoff (exponential in future)
 * 
 * Reference: PROJECT_DOCUMENTATION.md Phase 5 Step 1
 */
package com.presso.kernel.workflow;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * Represents a retry policy for workflow steps.
 */
public final class RetryPolicy {
    
    private final int maxAttempts;
    private final long backoffMs;  // Fixed delay between retries (milliseconds)
    
    /**
     * Construct a retry policy.
     * 
     * @param maxAttempts maximum number of attempts (including initial)
     * @param backoffMs fixed delay between retries in milliseconds
     */
    public RetryPolicy(int maxAttempts, long backoffMs) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (backoffMs < 0) {
            throw new IllegalArgumentException("backoffMs must be >= 0");
        }
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
    }
    
    public int getMaxAttempts() {
        return maxAttempts;
    }
    
    public long getBackoffMs() {
        return backoffMs;
    }
    
    /**
     * Create default retry policy (no retries).
     * 
     * @return default policy
     */
    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy(1, 0);  // No retries
    }
    
    /**
     * Parse a retry policy from JSON.
     * 
     * @param json the JSON object
     * @return parsed retry policy
     * @throws JsonParseException if JSON is invalid
     */
    public static RetryPolicy fromJson(JsonObject json) throws JsonParseException {
        int maxAttempts = json.has("max_attempts")
            ? json.get("max_attempts").getAsInt()
            : 1;
        
        long backoffMs = json.has("backoff_ms")
            ? json.get("backoff_ms").getAsLong()
            : 1000;  // Default 1 second
        
        return new RetryPolicy(maxAttempts, backoffMs);
    }
    
    /**
     * Convert to JSON for serialization.
     * 
     * @return JSON object
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("max_attempts", maxAttempts);
        json.addProperty("backoff_ms", backoffMs);
        return json;
    }
}

