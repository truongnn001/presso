/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: GuardrailPolicy.java
 * RESPONSIBILITY: Guardrail policy model for AI suggestion governance
 * 
 * ARCHITECTURAL ROLE:
 * - Defines declarative policy rules for AI suggestions
 * - Loaded at Kernel startup from configuration
 * - Immutable at runtime (policy changes require config reload)
 * 
 * NON-NEGOTIABLE PRINCIPLES:
 * - Policy is SYSTEM-ENFORCED, not AI-enforced
 * - Policy decisions are deterministic
 * - All policy decisions are auditable
 * 
 * Reference: PROJECT_DOCUMENTATION.md Phase 6 Step 3
 */
package com.presso.kernel.ai;

import com.google.gson.JsonObject;
import java.util.Set;
import java.util.HashSet;

/**
 * Represents guardrail policy configuration for AI suggestions.
 * Policy is declarative and enforced by the system, not by AI.
 */
public final class GuardrailPolicy {
    
    /**
     * Minimum confidence threshold (0.0 to 1.0).
     * Suggestions below this threshold may be blocked or flagged.
     */
    private final double minConfidenceThreshold;
    
    /**
     * Whether to require human review for suggestions below threshold.
     * If true, low-confidence suggestions are FLAGGED (not blocked).
     * If false, low-confidence suggestions are BLOCKED.
     */
    private final boolean requireHumanReviewBelowThreshold;
    
    /**
     * Maximum number of suggestions to return per request.
     * Prevents overwhelming users with too many suggestions.
     */
    private final int maxSuggestionsPerRequest;
    
    /**
     * Blocked suggestion types (deny-list).
     * Suggestions of these types are BLOCKED regardless of confidence.
     */
    private final Set<AISuggestion.SuggestionType> blockedSuggestionTypes;
    
    /**
     * Allowed analysis types.
     * Only these analysis types are permitted.
     * Empty set means all types are allowed.
     */
    private final Set<String> allowedAnalysisTypes;
    
    /**
     * Construct a guardrail policy.
     * 
     * @param minConfidenceThreshold minimum confidence threshold (0.0 to 1.0)
     * @param requireHumanReviewBelowThreshold whether to flag (true) or block (false) low-confidence suggestions
     * @param maxSuggestionsPerRequest maximum suggestions per request
     * @param blockedSuggestionTypes blocked suggestion types (deny-list)
     * @param allowedAnalysisTypes allowed analysis types (empty = all allowed)
     */
    public GuardrailPolicy(
            double minConfidenceThreshold,
            boolean requireHumanReviewBelowThreshold,
            int maxSuggestionsPerRequest,
            Set<AISuggestion.SuggestionType> blockedSuggestionTypes,
            Set<String> allowedAnalysisTypes) {
        this.minConfidenceThreshold = Math.max(0.0, Math.min(1.0, minConfidenceThreshold));
        this.requireHumanReviewBelowThreshold = requireHumanReviewBelowThreshold;
        this.maxSuggestionsPerRequest = Math.max(1, maxSuggestionsPerRequest);
        this.blockedSuggestionTypes = blockedSuggestionTypes != null ? 
            new HashSet<>(blockedSuggestionTypes) : new HashSet<>();
        this.allowedAnalysisTypes = allowedAnalysisTypes != null ? 
            new HashSet<>(allowedAnalysisTypes) : new HashSet<>();
    }
    
    /**
     * Create default policy (permissive for development).
     * 
     * @return default policy
     */
    public static GuardrailPolicy createDefault() {
        Set<AISuggestion.SuggestionType> blocked = new HashSet<>();
        Set<String> allowed = new HashSet<>();  // Empty = all allowed
        return new GuardrailPolicy(
            0.5,  // 50% minimum confidence
            true,  // Flag low-confidence, don't block
            50,    // Max 50 suggestions per request
            blocked,
            allowed
        );
    }
    
    /**
     * Load policy from JSON configuration.
     * 
     * @param json JSON configuration object
     * @return loaded policy
     */
    public static GuardrailPolicy fromJson(JsonObject json) {
        if (json == null) {
            return createDefault();
        }
        
        double minConfidence = json.has("min_confidence_threshold") ? 
            json.get("min_confidence_threshold").getAsDouble() : 0.5;
        
        boolean requireReview = json.has("require_human_review_below_threshold") ? 
            json.get("require_human_review_below_threshold").getAsBoolean() : true;
        
        int maxSuggestions = json.has("max_suggestions_per_request") ? 
            json.get("max_suggestions_per_request").getAsInt() : 50;
        
        Set<AISuggestion.SuggestionType> blocked = new HashSet<>();
        if (json.has("blocked_suggestion_types")) {
            json.getAsJsonArray("blocked_suggestion_types").forEach(elem -> {
                try {
                    blocked.add(AISuggestion.SuggestionType.valueOf(elem.getAsString()));
                } catch (IllegalArgumentException e) {
                    // Skip invalid types
                }
            });
        }
        
        Set<String> allowed = new HashSet<>();
        if (json.has("allowed_analysis_types")) {
            json.getAsJsonArray("allowed_analysis_types").forEach(elem -> {
                allowed.add(elem.getAsString());
            });
        }
        
        return new GuardrailPolicy(minConfidence, requireReview, maxSuggestions, blocked, allowed);
    }
    
    public double getMinConfidenceThreshold() { return minConfidenceThreshold; }
    public boolean isRequireHumanReviewBelowThreshold() { return requireHumanReviewBelowThreshold; }
    public int getMaxSuggestionsPerRequest() { return maxSuggestionsPerRequest; }
    public Set<AISuggestion.SuggestionType> getBlockedSuggestionTypes() { 
        return new HashSet<>(blockedSuggestionTypes); 
    }
    public Set<String> getAllowedAnalysisTypes() { 
        return new HashSet<>(allowedAnalysisTypes); 
    }
    
    /**
     * Convert to JSON for persistence.
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("min_confidence_threshold", minConfidenceThreshold);
        json.addProperty("require_human_review_below_threshold", requireHumanReviewBelowThreshold);
        json.addProperty("max_suggestions_per_request", maxSuggestionsPerRequest);
        
        com.google.gson.JsonArray blockedArray = new com.google.gson.JsonArray();
        for (AISuggestion.SuggestionType type : blockedSuggestionTypes) {
            blockedArray.add(type.name());
        }
        json.add("blocked_suggestion_types", blockedArray);
        
        com.google.gson.JsonArray allowedArray = new com.google.gson.JsonArray();
        for (String type : allowedAnalysisTypes) {
            allowedArray.add(type);
        }
        json.add("allowed_analysis_types", allowedArray);
        
        return json;
    }
}

