/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: Explanation.java
 * RESPONSIBILITY: Explanation data model for AI suggestions
 * 
 * ARCHITECTURAL ROLE:
 * - Represents the explanation for why an AI suggestion exists
 * - Contains summary, reasoning steps, and evidence references
 * - Ensures AI suggestions are explainable and transparent
 * 
 * Reference: PROJECT_DOCUMENTATION.md Phase 6 Step 2
 */
package com.presso.kernel.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.util.List;
import java.util.ArrayList;

/**
 * Represents the explanation for an AI suggestion.
 */
public final class Explanation {
    
    private final String summary;  // Human-readable summary
    private final List<String> reasoningSteps;  // List of reasoning steps
    private final JsonObject evidence;  // References to data points used
    
    /**
     * Construct an explanation.
     * 
     * @param summary human-readable summary
     * @param reasoningSteps list of reasoning steps
     * @param evidence evidence references (data points used)
     */
    public Explanation(String summary, List<String> reasoningSteps, JsonObject evidence) {
        this.summary = summary != null ? summary : "";
        this.reasoningSteps = reasoningSteps != null ? new ArrayList<>(reasoningSteps) : new ArrayList<>();
        this.evidence = evidence != null ? evidence : new JsonObject();
    }
    
    public String getSummary() { return summary; }
    public List<String> getReasoningSteps() { return new ArrayList<>(reasoningSteps); }
    public JsonObject getEvidence() { return evidence; }
    
    /**
     * Convert to JSON.
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("summary", summary);
        
        JsonArray stepsArray = new JsonArray();
        for (String step : reasoningSteps) {
            stepsArray.add(step);
        }
        json.add("reasoning_steps", stepsArray);
        json.add("evidence", evidence);
        
        return json;
    }
}

