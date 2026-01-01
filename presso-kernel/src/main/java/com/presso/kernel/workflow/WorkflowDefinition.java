/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: WorkflowDefinition.java
 * RESPONSIBILITY: Workflow definition model (data structure)
 * 
 * ARCHITECTURAL ROLE:
 * - Represents a workflow definition loaded from JSON
 * - Contains NO business logic (pure data)
 * - Validates structure only
 * 
 * Reference: PROJECT_DOCUMENTATION.md Phase 5 Step 1
 */
package com.presso.kernel.workflow;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a workflow definition.
 * <p>
 * Workflow definitions are DATA, not code. They describe what steps to execute,
 * not how to execute them (that's the engine's job).
 * </p>
 */
public final class WorkflowDefinition {
    
    private final String workflowId;
    private final String name;
    private final String version;
    private final List<StepDefinition> steps;
    private final Integer maxParallelism;  // Phase 5 Step 4: Maximum parallel steps
    
    /**
     * Construct a workflow definition.
     * 
     * @param workflowId unique workflow identifier
     * @param name human-readable name
     * @param version version string
     * @param steps list of step definitions
     * @param maxParallelism maximum parallel steps (Phase 5 Step 4)
     */
    public WorkflowDefinition(String workflowId, String name, String version, List<StepDefinition> steps, Integer maxParallelism) {
        this.workflowId = workflowId;
        this.name = name;
        this.version = version;
        this.steps = new ArrayList<>(steps);
        this.maxParallelism = maxParallelism;
    }
    
    /**
     * Construct a workflow definition (backward compatibility).
     */
    public WorkflowDefinition(String workflowId, String name, String version, List<StepDefinition> steps) {
        this(workflowId, name, version, steps, null);
    }
    
    public String getWorkflowId() {
        return workflowId;
    }
    
    public String getName() {
        return name;
    }
    
    public String getVersion() {
        return version;
    }
    
    public List<StepDefinition> getSteps() {
        return new ArrayList<>(steps);
    }
    
    /**
     * Get maximum parallelism (Phase 5 Step 4).
     * 
     * @return max parallelism, or null if not specified (unlimited)
     */
    public Integer getMaxParallelism() {
        return maxParallelism;
    }
    
    /**
     * Check if workflow has dependencies (DAG workflow).
     * 
     * @return true if any step has dependencies
     */
    public boolean isDagWorkflow() {
        for (StepDefinition step : steps) {
            if (step.hasDependencies()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Parse a workflow definition from JSON.
     * 
     * @param json the JSON object
     * @return parsed workflow definition
     * @throws JsonParseException if JSON is invalid
     */
    public static WorkflowDefinition fromJson(JsonObject json) throws JsonParseException {
        if (!json.has("workflow_id")) {
            throw new JsonParseException("Missing required field: workflow_id");
        }
        if (!json.has("name")) {
            throw new JsonParseException("Missing required field: name");
        }
        if (!json.has("version")) {
            throw new JsonParseException("Missing required field: version");
        }
        if (!json.has("steps") || !json.get("steps").isJsonArray()) {
            throw new JsonParseException("Missing or invalid field: steps");
        }
        
        String workflowId = json.get("workflow_id").getAsString();
        String name = json.get("name").getAsString();
        String version = json.get("version").getAsString();
        
        JsonArray stepsArray = json.getAsJsonArray("steps");
        List<StepDefinition> steps = new ArrayList<>();
        
        for (JsonElement stepElem : stepsArray) {
            if (!stepElem.isJsonObject()) {
                throw new JsonParseException("Invalid step: must be an object");
            }
            steps.add(StepDefinition.fromJson(stepElem.getAsJsonObject()));
        }
        
        if (steps.isEmpty()) {
            throw new JsonParseException("Workflow must have at least one step");
        }
        
        // Phase 5 Step 4: Parse max_parallelism
        Integer maxParallelism = null;
        if (json.has("max_parallelism")) {
            maxParallelism = json.get("max_parallelism").getAsInt();
            if (maxParallelism < 1) {
                throw new JsonParseException("max_parallelism must be >= 1");
            }
        }
        
        WorkflowDefinition definition = new WorkflowDefinition(workflowId, name, version, steps, maxParallelism);
        
        // Phase 5 Step 4: Validate DAG if dependencies are present
        if (definition.isDagWorkflow()) {
            DagValidator.validateDag(definition);
        }
        
        return definition;
    }
    
    /**
     * Convert to JSON for serialization.
     * 
     * @return JSON object
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("workflow_id", workflowId);
        json.addProperty("name", name);
        json.addProperty("version", version);
        
        JsonArray stepsArray = new JsonArray();
        for (StepDefinition step : steps) {
            stepsArray.add(step.toJson());
        }
        json.add("steps", stepsArray);
        
        // Phase 5 Step 4: Include max_parallelism if specified
        if (maxParallelism != null) {
            json.addProperty("max_parallelism", maxParallelism);
        }
        
        return json;
    }
}

