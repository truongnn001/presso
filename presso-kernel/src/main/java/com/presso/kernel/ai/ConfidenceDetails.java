/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: ConfidenceDetails.java
 * RESPONSIBILITY: Confidence details for AI suggestions
 * 
 * ARCHITECTURAL ROLE:
 * - Represents confidence score, level, and explanation
 * - Ensures confidence is explicit and justified
 * - Provides transparency in AI decision-making
 * 
 * Reference: PROJECT_DOCUMENTATION.md Phase 6 Step 2
 */
package com.presso.kernel.ai;

import com.google.gson.JsonObject;

/**
 * Represents confidence details for an AI suggestion.
 */
public final class ConfidenceDetails {
    
    /**
     * Confidence level.
     */
    public enum ConfidenceLevel {
        LOW,      // 0.0 - 0.4
        MEDIUM,   // 0.4 - 0.7
        HIGH      // 0.7 - 1.0
    }
    
    private final double score;  // 0.0 to 1.0
    private final ConfidenceLevel level;
    private final String explanation;  // Why confidence is at this level
    
    /**
     * Construct confidence details.
     * 
     * @param score confidence score (0.0 to 1.0)
     * @param explanation explanation of confidence level
     */
    public ConfidenceDetails(double score, String explanation) {
        this.score = Math.max(0.0, Math.min(1.0, score));
        this.level = computeLevel(score);
        this.explanation = explanation != null ? explanation : "";
    }
    
    /**
     * Compute confidence level from score.
     */
    private static ConfidenceLevel computeLevel(double score) {
        if (score < 0.4) {
            return ConfidenceLevel.LOW;
        } else if (score < 0.7) {
            return ConfidenceLevel.MEDIUM;
        } else {
            return ConfidenceLevel.HIGH;
        }
    }
    
    public double getScore() { return score; }
    public ConfidenceLevel getLevel() { return level; }
    public String getExplanation() { return explanation; }
    
    /**
     * Convert to JSON.
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("score", score);
        json.addProperty("level", level.name());
        json.addProperty("explanation", explanation);
        return json;
    }
}

