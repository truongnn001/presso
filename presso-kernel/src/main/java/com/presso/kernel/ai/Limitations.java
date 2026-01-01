/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: Limitations.java
 * RESPONSIBILITY: Limitations data model for AI suggestions
 * 
 * ARCHITECTURAL ROLE:
 * - Represents known assumptions and missing data
 * - Ensures transparency about suggestion limitations
 * - Helps users understand suggestion context
 * 
 * Reference: PROJECT_DOCUMENTATION.md Phase 6 Step 2
 */
package com.presso.kernel.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.util.List;
import java.util.ArrayList;

/**
 * Represents limitations of an AI suggestion.
 */
public final class Limitations {
    
    private final List<String> knownAssumptions;  // Known assumptions made
    private final List<String> missingData;  // Missing data (if any)
    
    /**
     * Construct limitations.
     * 
     * @param knownAssumptions known assumptions made
     * @param missingData missing data (if any)
     */
    public Limitations(List<String> knownAssumptions, List<String> missingData) {
        this.knownAssumptions = knownAssumptions != null ? new ArrayList<>(knownAssumptions) : new ArrayList<>();
        this.missingData = missingData != null ? new ArrayList<>(missingData) : new ArrayList<>();
    }
    
    public List<String> getKnownAssumptions() { return new ArrayList<>(knownAssumptions); }
    public List<String> getMissingData() { return new ArrayList<>(missingData); }
    
    /**
     * Convert to JSON.
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        
        JsonArray assumptionsArray = new JsonArray();
        for (String assumption : knownAssumptions) {
            assumptionsArray.add(assumption);
        }
        json.add("known_assumptions", assumptionsArray);
        
        JsonArray missingArray = new JsonArray();
        for (String missing : missingData) {
            missingArray.add(missing);
        }
        json.add("missing_data", missingArray);
        
        return json;
    }
}

