/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: AISuggestion.java
 * RESPONSIBILITY: AI suggestion data model
 * 
 * ARCHITECTURAL ROLE:
 * - Represents a read-only AI suggestion
 * - Contains suggestion type, message, and optional data
 * - NO executable actions - suggestions only
 * 
 * Reference: PROJECT_DOCUMENTATION.md Phase 6 Step 1
 */
package com.presso.kernel.ai;

import com.google.gson.JsonObject;
import java.util.ArrayList;

/**
 * Represents an AI-generated suggestion.
 * Suggestions are READ-ONLY advisory data, never executable.
 */
public final class AISuggestion {
    
    /**
     * Suggestion type/category.
     */
    public enum SuggestionType {
        OPTIMIZATION,      // Performance optimization suggestions
        RELIABILITY,       // Reliability and error prevention
        CONFIGURATION,     // Configuration recommendations
        PATTERN_DETECTION  // Pattern-based insights
    }
    
    private final String suggestionId;
    private final SuggestionType type;
    private final String title;
    private final String message;
    private final String context;  // What the suggestion is about (workflow_id, step_id, etc.)
    private final JsonObject metadata;  // Optional structured data
    private final double confidence;  // 0.0 to 1.0 (deprecated - use confidenceDetails)
    private final long timestamp;
    
    // Phase 6 Step 2: Explainability & Confidence
    private final Explanation explanation;  // Why the suggestion exists
    private final ConfidenceDetails confidenceDetails;  // Detailed confidence information
    private final Limitations limitations;  // Known assumptions and missing data
    
    // Phase 6 Step 3: Guardrails & Policy
    private final boolean requiresHumanReview;  // Whether this suggestion requires human review (set by guardrails)
    
    /**
     * Construct an AI suggestion (Phase 6 Step 2: with explainability).
     * 
     * @param suggestionId unique suggestion identifier
     * @param type suggestion type
     * @param title short title
     * @param message detailed message
     * @param context context (workflow_id, step_id, etc.)
     * @param metadata optional structured data
     * @param confidence confidence score (0.0 to 1.0) - deprecated, use confidenceDetails
     * @param explanation explanation (why the suggestion exists)
     * @param confidenceDetails detailed confidence information
     * @param limitations known assumptions and missing data
     */
    public AISuggestion(String suggestionId, SuggestionType type, String title, String message,
                       String context, JsonObject metadata, double confidence,
                       Explanation explanation, ConfidenceDetails confidenceDetails, Limitations limitations) {
        this(suggestionId, type, title, message, context, metadata, confidence, 
            explanation, confidenceDetails, limitations, false);
    }
    
    /**
     * Construct an AI suggestion (Phase 6 Step 3: with guardrail flag).
     */
    public AISuggestion(String suggestionId, SuggestionType type, String title, String message,
                       String context, JsonObject metadata, double confidence,
                       Explanation explanation, ConfidenceDetails confidenceDetails, Limitations limitations,
                       boolean requiresHumanReview) {
        this.suggestionId = suggestionId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.context = context;
        this.metadata = metadata != null ? metadata : new JsonObject();
        this.confidence = Math.max(0.0, Math.min(1.0, confidence));
        this.timestamp = System.currentTimeMillis();
        this.explanation = explanation;
        this.confidenceDetails = confidenceDetails;
        this.limitations = limitations;
        this.requiresHumanReview = requiresHumanReview;
    }
    
    /**
     * Construct an AI suggestion (backward compatibility - Phase 6 Step 1).
     */
    public AISuggestion(String suggestionId, SuggestionType type, String title, String message,
                       String context, JsonObject metadata, double confidence) {
        this(suggestionId, type, title, message, context, metadata, confidence, 
            null, null, null);
    }
    
    public String getSuggestionId() { return suggestionId; }
    public SuggestionType getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getContext() { return context; }
    public JsonObject getMetadata() { return metadata; }
    public double getConfidence() { return confidence; }
    public long getTimestamp() { return timestamp; }
    
    // Phase 6 Step 2: Explainability & Confidence
    public Explanation getExplanation() { return explanation; }
    public ConfidenceDetails getConfidenceDetails() { return confidenceDetails; }
    public Limitations getLimitations() { return limitations; }
    
    // Phase 6 Step 3: Guardrails & Policy
    public boolean isRequiresHumanReview() { return requiresHumanReview; }
    
    /**
     * Convert to JSON for IPC response (Phase 6 Step 2: with explainability).
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("suggestion_id", suggestionId);
        json.addProperty("type", type.name());
        json.addProperty("title", title);
        json.addProperty("message", message);
        json.addProperty("context", context);
        json.add("metadata", metadata);
        json.addProperty("confidence", confidence);  // Deprecated but kept for backward compatibility
        json.addProperty("timestamp", timestamp);
        
        // Phase 6 Step 2: Add explainability fields
        if (explanation != null) {
            json.add("explanation", explanation.toJson());
        }
        if (confidenceDetails != null) {
            json.add("confidence_details", confidenceDetails.toJson());
        } else {
            // Fallback: create confidence details from legacy confidence score
            ConfidenceDetails fallback = new ConfidenceDetails(confidence, 
                "Confidence based on pattern frequency and data volume");
            json.add("confidence_details", fallback.toJson());
        }
        if (limitations != null) {
            json.add("limitations", limitations.toJson());
        } else {
            // Default: no known limitations
            Limitations defaultLimits = new Limitations(new ArrayList<>(), new ArrayList<>());
            json.add("limitations", defaultLimits.toJson());
        }
        
        // Phase 6 Step 3: Add guardrail flag
        json.addProperty("requires_human_review", requiresHumanReview);
        
        return json;
    }
    
    /**
     * Create a copy with requiresHumanReview flag set (Phase 6 Step 3).
     */
    public AISuggestion withRequiresHumanReview(boolean requiresHumanReview) {
        return new AISuggestion(
            suggestionId, type, title, message, context, metadata, confidence,
            explanation, confidenceDetails, limitations, requiresHumanReview
        );
    }
}

