/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: StepDefinition.java
 * RESPONSIBILITY: Step definition model within a workflow
 * 
 * ARCHITECTURAL ROLE:
 * - Represents a single step in a workflow
 * - Contains step configuration (type, input mapping, retry policy)
 * - NO business logic
 * 
 * Reference: PROJECT_DOCUMENTATION.md Phase 5 Step 1
 */
package com.presso.kernel.workflow;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * Represents a step definition within a workflow.
 */
public final class StepDefinition {
    
    public enum StepType {
        PYTHON_TASK,        // Execute Python Engine task
        GO_API_CALL,        // Execute Go API Hub call
        INTERNAL_OP,        // Internal Kernel operation (future)
        HUMAN_APPROVAL      // Phase 5 Step 3: Human-in-the-loop approval step
    }
    
    public enum OnFailure {
        FAIL,   // Stop workflow on failure
        RETRY,  // Retry step (if retry policy allows)
        SKIP    // Skip step and continue
    }
    
    public enum TimeoutPolicy {
        WAIT,   // Wait indefinitely for approval
        FAIL    // Mark workflow as FAILED after timeout
    }
    
    private final String stepId;
    private final StepType type;
    private final JsonObject inputMapping;  // Maps workflow context to step input
    private final RetryPolicy retryPolicy;
    private final OnFailure onFailure;
    
    // Phase 5 Step 3: Human approval configuration (only for HUMAN_APPROVAL type)
    private final String approvalPrompt;  // Text shown to human
    private final java.util.List<String> allowedActions;  // e.g., ["APPROVE", "REJECT"]
    private final TimeoutPolicy timeoutPolicy;  // WAIT or FAIL
    private final Long timeoutMs;  // Timeout in milliseconds (null if WAIT)
    
    // Phase 5 Step 4: DAG dependencies
    private final java.util.List<String> dependsOn;  // Array of step_ids this step depends on
    
    /**
     * Construct a step definition.
     * 
     * @param stepId unique step identifier within workflow
     * @param type step type (PYTHON_TASK, GO_API_CALL, INTERNAL_OP, HUMAN_APPROVAL)
     * @param inputMapping input mapping (JSON object)
     * @param retryPolicy retry policy
     * @param onFailure failure handling strategy
     * @param approvalPrompt approval prompt text (for HUMAN_APPROVAL type)
     * @param allowedActions allowed approval actions (for HUMAN_APPROVAL type)
     * @param timeoutPolicy timeout policy (for HUMAN_APPROVAL type)
     * @param timeoutMs timeout in milliseconds (for HUMAN_APPROVAL type, null if WAIT)
     */
    public StepDefinition(String stepId, StepType type, JsonObject inputMapping,
                         RetryPolicy retryPolicy, OnFailure onFailure,
                         String approvalPrompt, java.util.List<String> allowedActions,
                         TimeoutPolicy timeoutPolicy, Long timeoutMs,
                         java.util.List<String> dependsOn) {
        this.stepId = stepId;
        this.type = type;
        this.inputMapping = inputMapping != null ? inputMapping : new JsonObject();
        this.retryPolicy = retryPolicy != null ? retryPolicy : RetryPolicy.defaultPolicy();
        this.onFailure = onFailure != null ? onFailure : OnFailure.FAIL;
        this.approvalPrompt = approvalPrompt;
        this.allowedActions = allowedActions != null ? new java.util.ArrayList<>(allowedActions) : null;
        this.timeoutPolicy = timeoutPolicy;
        this.timeoutMs = timeoutMs;
        this.dependsOn = dependsOn != null ? new java.util.ArrayList<>(dependsOn) : new java.util.ArrayList<>();
    }
    
    /**
     * Construct a step definition (non-approval step).
     */
    public StepDefinition(String stepId, StepType type, JsonObject inputMapping,
                         RetryPolicy retryPolicy, OnFailure onFailure) {
        this(stepId, type, inputMapping, retryPolicy, onFailure, null, null, null, null, null);
    }
    
    public String getStepId() {
        return stepId;
    }
    
    public StepType getType() {
        return type;
    }
    
    public JsonObject getInputMapping() {
        return inputMapping;
    }
    
    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }
    
    public OnFailure getOnFailure() {
        return onFailure;
    }
    
    // Phase 5 Step 3: Human approval configuration
    public String getApprovalPrompt() {
        return approvalPrompt;
    }
    
    public java.util.List<String> getAllowedActions() {
        return allowedActions != null ? new java.util.ArrayList<>(allowedActions) : null;
    }
    
    public TimeoutPolicy getTimeoutPolicy() {
        return timeoutPolicy;
    }
    
    public Long getTimeoutMs() {
        return timeoutMs;
    }
    
    public boolean isApprovalStep() {
        return type == StepType.HUMAN_APPROVAL;
    }
    
    // Phase 5 Step 4: DAG dependencies
    public java.util.List<String> getDependsOn() {
        return new java.util.ArrayList<>(dependsOn);
    }
    
    public boolean hasDependencies() {
        return dependsOn != null && !dependsOn.isEmpty();
    }
    
    /**
     * Parse a step definition from JSON.
     * 
     * @param json the JSON object
     * @return parsed step definition
     * @throws JsonParseException if JSON is invalid
     */
    public static StepDefinition fromJson(JsonObject json) throws JsonParseException {
        if (!json.has("step_id")) {
            throw new JsonParseException("Missing required field: step_id");
        }
        if (!json.has("type")) {
            throw new JsonParseException("Missing required field: type");
        }
        
        String stepId = json.get("step_id").getAsString();
        
        String typeStr = json.get("type").getAsString();
        StepType type;
        try {
            type = StepType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("Invalid step type: " + typeStr);
        }
        
        JsonObject inputMapping = json.has("input_mapping") && json.get("input_mapping").isJsonObject()
            ? json.getAsJsonObject("input_mapping")
            : new JsonObject();
        
        RetryPolicy retryPolicy = json.has("retry_policy") && json.get("retry_policy").isJsonObject()
            ? RetryPolicy.fromJson(json.getAsJsonObject("retry_policy"))
            : RetryPolicy.defaultPolicy();
        
        OnFailure onFailure = OnFailure.FAIL;
        if (json.has("on_failure")) {
            String onFailureStr = json.get("on_failure").getAsString();
            try {
                onFailure = OnFailure.valueOf(onFailureStr);
            } catch (IllegalArgumentException e) {
                throw new JsonParseException("Invalid on_failure value: " + onFailureStr);
            }
        }
        
        // Phase 5 Step 3: Parse approval configuration for HUMAN_APPROVAL steps
        String approvalPrompt = null;
        java.util.List<String> allowedActions = null;
        TimeoutPolicy timeoutPolicy = TimeoutPolicy.WAIT;
        Long timeoutMs = null;
        
        if (type == StepType.HUMAN_APPROVAL) {
            if (!json.has("prompt")) {
                throw new JsonParseException("HUMAN_APPROVAL step requires 'prompt' field");
            }
            approvalPrompt = json.get("prompt").getAsString();
            
            if (json.has("allowed_actions") && json.get("allowed_actions").isJsonArray()) {
                allowedActions = new java.util.ArrayList<>();
                com.google.gson.JsonArray actionsArray = json.getAsJsonArray("allowed_actions");
                for (com.google.gson.JsonElement elem : actionsArray) {
                    allowedActions.add(elem.getAsString());
                }
            } else {
                // Default: APPROVE and REJECT
                allowedActions = java.util.Arrays.asList("APPROVE", "REJECT");
            }
            
            if (json.has("timeout_policy")) {
                String timeoutPolicyStr = json.get("timeout_policy").getAsString();
                try {
                    timeoutPolicy = TimeoutPolicy.valueOf(timeoutPolicyStr);
                } catch (IllegalArgumentException e) {
                    throw new JsonParseException("Invalid timeout_policy value: " + timeoutPolicyStr);
                }
            }
            
            if (json.has("timeout_ms")) {
                timeoutMs = json.get("timeout_ms").getAsLong();
            }
        }
        
        // Phase 5 Step 4: Parse DAG dependencies
        java.util.List<String> dependsOn = new java.util.ArrayList<>();
        if (json.has("depends_on") && json.get("depends_on").isJsonArray()) {
            com.google.gson.JsonArray dependsArray = json.getAsJsonArray("depends_on");
            for (com.google.gson.JsonElement elem : dependsArray) {
                dependsOn.add(elem.getAsString());
            }
        }
        
        return new StepDefinition(stepId, type, inputMapping, retryPolicy, onFailure,
            approvalPrompt, allowedActions, timeoutPolicy, timeoutMs, dependsOn);
    }
    
    /**
     * Convert to JSON for serialization.
     * 
     * @return JSON object
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("step_id", stepId);
        json.addProperty("type", type.name());
        json.add("input_mapping", inputMapping);
        json.add("retry_policy", retryPolicy.toJson());
        json.addProperty("on_failure", onFailure.name());
        
        // Phase 5 Step 3: Include approval configuration if HUMAN_APPROVAL
        if (type == StepType.HUMAN_APPROVAL) {
            json.addProperty("prompt", approvalPrompt);
            if (allowedActions != null) {
                com.google.gson.JsonArray actionsArray = new com.google.gson.JsonArray();
                for (String action : allowedActions) {
                    actionsArray.add(action);
                }
                json.add("allowed_actions", actionsArray);
            }
            if (timeoutPolicy != null) {
                json.addProperty("timeout_policy", timeoutPolicy.name());
            }
            if (timeoutMs != null) {
                json.addProperty("timeout_ms", timeoutMs);
            }
        }
        
        // Phase 5 Step 4: Include dependencies if present
        if (dependsOn != null && !dependsOn.isEmpty()) {
            com.google.gson.JsonArray dependsArray = new com.google.gson.JsonArray();
            for (String dep : dependsOn) {
                dependsArray.add(dep);
            }
            json.add("depends_on", dependsArray);
        }
        
        return json;
    }
}

