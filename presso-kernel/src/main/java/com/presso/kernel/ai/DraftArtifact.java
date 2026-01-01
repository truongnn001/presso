/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: DraftArtifact.java
 * RESPONSIBILITY: AI draft artifact data model
 * 
 * ARCHITECTURAL ROLE:
 * - Represents a read-only AI-generated draft
 * - Contains draft content, rationale, and metadata
 * - NO executable actions - drafts only
 * - Status is always DRAFT_ONLY (non-actionable)
 * 
 * NON-NEGOTIABLE PRINCIPLES:
 * - Drafts are READ-ONLY artifacts
 * - Drafts require explicit human action to apply
 * - Drafts never auto-apply or execute
 * 
 * Reference: PROJECT_DOCUMENTATION.md Phase 6 Step 4
 */
package com.presso.kernel.ai;

import com.google.gson.JsonObject;
import java.util.ArrayList;

/**
 * Represents an AI-generated draft artifact.
 * Drafts are NON-ACTIONABLE and require explicit human application.
 */
public final class DraftArtifact {
    
    /**
     * Draft type/category.
     */
    public enum DraftType {
        WORKFLOW_JSON,    // Workflow definition JSON skeleton
        STEP_PARAMS,       // Step parameter recommendations
        POLICY_CONFIG,     // Policy/configuration proposals
        DOC_SNIPPET       // Documentation snippets
    }
    
    /**
     * Draft status (always DRAFT_ONLY for Phase 6 Step 4).
     */
    public enum DraftStatus {
        DRAFT_ONLY  // Draft is non-actionable, requires human review
    }
    
    private final String draftId;
    private final DraftType draftType;
    private final JsonObject content;  // Draft payload (JSON)
    private final JsonObject sourceContext;  // What data informed the draft
    private final String rationale;  // Why this draft was generated
    private final double confidence;  // 0.0 to 1.0
    private final ConfidenceDetails confidenceDetails;  // Detailed confidence information
    private final Limitations limitations;  // Known assumptions and missing data
    private final DraftStatus status;  // Always DRAFT_ONLY
    private final long timestamp;
    private final boolean requiresHumanReview;  // Set by guardrails
    
    /**
     * Construct a draft artifact.
     * 
     * @param draftId unique draft identifier
     * @param draftType draft type
     * @param content draft payload (JSON)
     * @param sourceContext context that informed the draft
     * @param rationale why this draft was generated
     * @param confidence confidence score (0.0 to 1.0)
     * @param confidenceDetails detailed confidence information
     * @param limitations known assumptions and missing data
     * @param requiresHumanReview whether this draft requires human review (set by guardrails)
     */
    public DraftArtifact(String draftId, DraftType draftType, JsonObject content,
                        JsonObject sourceContext, String rationale, double confidence,
                        ConfidenceDetails confidenceDetails, Limitations limitations,
                        boolean requiresHumanReview) {
        this.draftId = draftId;
        this.draftType = draftType;
        this.content = content != null ? content : new JsonObject();
        this.sourceContext = sourceContext != null ? sourceContext : new JsonObject();
        this.rationale = rationale != null ? rationale : "";
        this.confidence = Math.max(0.0, Math.min(1.0, confidence));
        this.confidenceDetails = confidenceDetails;
        this.limitations = limitations;
        this.status = DraftStatus.DRAFT_ONLY;
        this.timestamp = System.currentTimeMillis();
        this.requiresHumanReview = requiresHumanReview;
    }
    
    public String getDraftId() { return draftId; }
    public DraftType getDraftType() { return draftType; }
    public JsonObject getContent() { return content; }
    public JsonObject getSourceContext() { return sourceContext; }
    public String getRationale() { return rationale; }
    public double getConfidence() { return confidence; }
    public ConfidenceDetails getConfidenceDetails() { return confidenceDetails; }
    public Limitations getLimitations() { return limitations; }
    public DraftStatus getStatus() { return status; }
    public long getTimestamp() { return timestamp; }
    public boolean isRequiresHumanReview() { return requiresHumanReview; }
    
    /**
     * Convert to JSON for IPC response.
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("draft_id", draftId);
        json.addProperty("draft_type", draftType.name());
        json.add("content", content);
        json.add("source_context", sourceContext);
        json.addProperty("rationale", rationale);
        json.addProperty("confidence", confidence);
        json.addProperty("status", status.name());
        json.addProperty("timestamp", timestamp);
        json.addProperty("requires_human_review", requiresHumanReview);
        
        if (confidenceDetails != null) {
            json.add("confidence_details", confidenceDetails.toJson());
        } else {
            // Fallback: create confidence details from legacy confidence score
            ConfidenceDetails fallback = new ConfidenceDetails(confidence, 
                "Confidence based on draft generation context");
            json.add("confidence_details", fallback.toJson());
        }
        
        if (limitations != null) {
            json.add("limitations", limitations.toJson());
        } else {
            // Default: no known limitations
            Limitations defaultLimits = new Limitations(new ArrayList<>(), new ArrayList<>());
            json.add("limitations", defaultLimits.toJson());
        }
        
        return json;
    }
    
    /**
     * Create a copy with requiresHumanReview flag set.
     */
    public DraftArtifact withRequiresHumanReview(boolean requiresHumanReview) {
        return new DraftArtifact(
            draftId, draftType, content, sourceContext, rationale, confidence,
            confidenceDetails, limitations, requiresHumanReview
        );
    }
    
    /**
     * Compute content hash for audit purposes.
     * 
     * @return SHA-256 hash of content JSON (as hex string)
     */
    public String computeContentHash() {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "hash_error";
        }
    }
}

