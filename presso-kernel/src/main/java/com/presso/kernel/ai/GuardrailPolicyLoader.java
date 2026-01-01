/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: GuardrailPolicyLoader.java
 * RESPONSIBILITY: Load guardrail policy from configuration file
 * 
 * ARCHITECTURAL ROLE:
 * - Loads policy from ai_guardrails.json in config directory
 * - Creates default policy if file doesn't exist
 * - Provides policy reload capability
 * 
 * Reference: PROJECT_DOCUMENTATION.md Phase 6 Step 3
 */
package com.presso.kernel.ai;

import com.presso.kernel.state.ConfigFileHandler;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Loads guardrail policy from configuration file.
 */
public final class GuardrailPolicyLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(GuardrailPolicyLoader.class);
    
    private static final String POLICY_FILE = "ai_guardrails.json";
    
    /**
     * Load policy from configuration directory.
     * 
     * @param configPath path to config directory (%APPDATA%/PressO/config)
     * @return loaded policy (or default if file doesn't exist)
     */
    public static GuardrailPolicy loadPolicy(Path configPath) {
        Path policyPath = configPath.resolve(POLICY_FILE);
        
        JsonObject defaults = buildDefaultPolicyJson();
        JsonObject policyJson = ConfigFileHandler.readOrCreateDefault(policyPath, defaults);
        
        GuardrailPolicy policy = GuardrailPolicy.fromJson(policyJson);
        logger.info("Guardrail policy loaded from {}", policyPath);
        
        return policy;
    }
    
    /**
     * Build default policy JSON structure.
     * 
     * @return default policy JSON
     */
    private static JsonObject buildDefaultPolicyJson() {
        JsonObject json = new JsonObject();
        json.addProperty("min_confidence_threshold", 0.5);
        json.addProperty("require_human_review_below_threshold", true);
        json.addProperty("max_suggestions_per_request", 50);
        
        com.google.gson.JsonArray blockedArray = new com.google.gson.JsonArray();
        // Empty by default - no types blocked
        json.add("blocked_suggestion_types", blockedArray);
        
        com.google.gson.JsonArray allowedArray = new com.google.gson.JsonArray();
        // Empty by default - all analysis types allowed
        json.add("allowed_analysis_types", allowedArray);
        
        return json;
    }
}

