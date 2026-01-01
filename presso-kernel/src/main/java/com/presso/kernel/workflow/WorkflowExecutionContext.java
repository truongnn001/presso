/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: WorkflowExecutionContext.java
 * RESPONSIBILITY: Execution context for a running workflow
 * 
 * ARCHITECTURAL ROLE:
 * - Tracks execution state of a workflow instance
 * - Maintains step results and context variables
 * - Thread-safe for concurrent access
 * 
 * Reference: PROJECT_DOCUMENTATION.md Phase 5 Step 1
 */
package com.presso.kernel.workflow;

import com.google.gson.JsonObject;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Execution context for a running workflow instance.
 * <p>
 * Maintains state during workflow execution:
 * - Step results (keyed by step_id)
 * - Context variables (for input mapping)
 * - Execution metadata
 * </p>
 */
public final class WorkflowExecutionContext {
    
    private final String executionId;
    private final String workflowId;
    private final JsonObject initialContext;  // Initial input to workflow
    private final Map<String, JsonObject> stepResults;  // step_id -> result
    private final Map<String, Object> contextVariables;  // Variables for input mapping
    
    /**
     * Construct a workflow execution context.
     * 
     * @param executionId unique execution identifier
     * @param workflowId workflow definition ID
     * @param initialContext initial input context
     */
    public WorkflowExecutionContext(String executionId, String workflowId, JsonObject initialContext) {
        this.executionId = executionId;
        this.workflowId = workflowId;
        this.initialContext = initialContext != null ? initialContext : new JsonObject();
        this.stepResults = new ConcurrentHashMap<>();
        this.contextVariables = new ConcurrentHashMap<>();
    }
    
    public String getExecutionId() {
        return executionId;
    }
    
    public String getWorkflowId() {
        return workflowId;
    }
    
    public JsonObject getInitialContext() {
        return initialContext;
    }
    
    /**
     * Store a step result.
     * 
     * @param stepId step identifier
     * @param result step result (JSON object)
     */
    public void setStepResult(String stepId, JsonObject result) {
        stepResults.put(stepId, result);
    }
    
    /**
     * Get a step result.
     * 
     * @param stepId step identifier
     * @return step result, or null if not available
     */
    public JsonObject getStepResult(String stepId) {
        return stepResults.get(stepId);
    }
    
    /**
     * Set a context variable.
     * 
     * @param key variable name
     * @param value variable value
     */
    public void setVariable(String key, Object value) {
        contextVariables.put(key, value);
    }
    
    /**
     * Get a context variable.
     * 
     * @param key variable name
     * @return variable value, or null if not set
     */
    public Object getVariable(String key) {
        return contextVariables.get(key);
    }
    
    /**
     * Build input for a step based on input mapping.
     * <p>
     * Input mapping can reference:
     * - Initial context: ${initial.key}
     * - Previous step results: ${step_id.key}
     * - Context variables: ${var.key}
     * </p>
     * 
     * @param inputMapping input mapping JSON
     * @return resolved input JSON
     */
    public JsonObject resolveInput(JsonObject inputMapping) {
        JsonObject resolved = new JsonObject();
        
        for (String key : inputMapping.keySet()) {
            Object value = inputMapping.get(key);
            resolved.add(key, resolveValue(value));
        }
        
        return resolved;
    }
    
    /**
     * Resolve a single value (recursive for nested objects).
     */
    private com.google.gson.JsonElement resolveValue(Object value) {
        if (value instanceof com.google.gson.JsonPrimitive) {
            com.google.gson.JsonPrimitive primitive = (com.google.gson.JsonPrimitive) value;
            if (primitive.isString()) {
                String str = primitive.getAsString();
                // Check for variable references: ${...}
                if (str.startsWith("${") && str.endsWith("}")) {
                    String ref = str.substring(2, str.length() - 1);
                    return resolveReference(ref);
                }
            }
            return primitive;
        } else if (value instanceof JsonObject) {
            JsonObject obj = (JsonObject) value;
            JsonObject resolved = new JsonObject();
            for (String key : obj.keySet()) {
                resolved.add(key, resolveValue(obj.get(key)));
            }
            return resolved;
        } else if (value instanceof com.google.gson.JsonArray) {
            com.google.gson.JsonArray array = (com.google.gson.JsonArray) value;
            com.google.gson.JsonArray resolved = new com.google.gson.JsonArray();
            for (com.google.gson.JsonElement elem : array) {
                resolved.add(resolveValue(elem));
            }
            return resolved;
        }
        // Fallback: convert to JSON element
        return com.google.gson.JsonNull.INSTANCE;
    }
    
    /**
     * Resolve a reference like "initial.key", "step_id.key", or "var.key".
     */
    private com.google.gson.JsonElement resolveReference(String ref) {
        if (ref.startsWith("initial.")) {
            String key = ref.substring(8);
            if (initialContext.has(key)) {
                return initialContext.get(key);
            }
        } else if (ref.contains(".")) {
            // Format: step_id.key or var.key
            int dotIndex = ref.indexOf('.');
            String prefix = ref.substring(0, dotIndex);
            String key = ref.substring(dotIndex + 1);
            
            if (stepResults.containsKey(prefix)) {
                JsonObject stepResult = stepResults.get(prefix);
                if (stepResult.has(key)) {
                    return stepResult.get(key);
                }
            } else if (contextVariables.containsKey(prefix)) {
                Object varValue = contextVariables.get(prefix);
                // Convert to JSON element
                if (varValue instanceof String) {
                    return new com.google.gson.JsonPrimitive((String) varValue);
                } else if (varValue instanceof Number) {
                    return new com.google.gson.JsonPrimitive((Number) varValue);
                } else if (varValue instanceof Boolean) {
                    return new com.google.gson.JsonPrimitive((Boolean) varValue);
                }
            }
        }
        
        // Not found, return null
        return com.google.gson.JsonNull.INSTANCE;
    }
}

