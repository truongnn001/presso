/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: GuardrailEnforcer.java
 * RESPONSIBILITY: System-enforced guardrails for AI suggestions
 * 
 * ARCHITECTURAL ROLE:
 * - Enforces policy rules on AI suggestions BEFORE they are returned
 * - Makes deterministic policy decisions (ALLOW, FLAG, BLOCK)
 * - Audits all policy decisions
 * - AI is NOT aware of policy decisions
 * 
 * NON-NEGOTIABLE PRINCIPLES:
 * - Policy is SYSTEM-ENFORCED, not AI-enforced
 * - Policy decisions are deterministic and auditable
 * - AI suggestions are filtered/flagged, not modified by AI
 * 
 * ============================================================================
 * PHASE 6 SCOPE FREEZE — POLICY ENFORCEMENT
 * ============================================================================
 * Guardrail enforcement is FROZEN at Phase 6 completion.
 * 
 * FORBIDDEN EXPANSIONS:
 * - AI-modified policies
 * - Dynamic policy learning
 * - Self-adjusting guardrails
 * - AI awareness of policy decisions
 * 
 * POLICY ENFORCEMENT GUARANTEES:
 * - Policy is SYSTEM-ENFORCED (not AI)
 * - Policy is DECLARATIVE (JSON config)
 * - Policy decisions are DETERMINISTIC
 * - All decisions are AUDITED
 * 
 * Any policy expansion requires new Phase approval.
 * ============================================================================
 * 
 * Reference: PROJECT_DOCUMENTATION.md Phase 6 Step 3
 * Reference: AI_GOVERNANCE_SUMMARY.md (Phase 6 Freeze)
 */
package com.presso.kernel.ai;

import com.presso.kernel.persistence.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Policy decision outcomes.
 */
enum PolicyDecision {
    ALLOW,   // Suggestion is allowed and returned normally
    FLAG,    // Suggestion is returned but marked as requiring human review
    BLOCK    // Suggestion is blocked (not returned, but audited)
}

/**
 * Enforces guardrail policies on AI suggestions.
 * All enforcement is deterministic and system-controlled.
 */
public final class GuardrailEnforcer {
    
    private static final Logger logger = LoggerFactory.getLogger(GuardrailEnforcer.class);
    
    private final DatabaseManager databaseManager;
    private GuardrailPolicy policy;
    
    /**
     * Construct a guardrail enforcer.
     * 
     * @param databaseManager database manager for audit logging
     * @param policy guardrail policy to enforce
     */
    public GuardrailEnforcer(DatabaseManager databaseManager, GuardrailPolicy policy) {
        this.databaseManager = databaseManager;
        this.policy = policy != null ? policy : GuardrailPolicy.createDefault();
        logger.info("GuardrailEnforcer created with policy: minConfidence={}, requireReview={}, maxSuggestions={}", 
            this.policy.getMinConfidenceThreshold(), 
            this.policy.isRequireHumanReviewBelowThreshold(),
            this.policy.getMaxSuggestionsPerRequest());
    }
    
    /**
     * Update policy (for config reload).
     * 
     * @param newPolicy new policy to enforce
     */
    public void updatePolicy(GuardrailPolicy newPolicy) {
        if (newPolicy != null) {
            this.policy = newPolicy;
            logger.info("Guardrail policy updated");
        }
    }
    
    /**
     * Enforce guardrails on a list of AI suggestions.
     * Filters, flags, and audits suggestions according to policy.
     * 
     * @param suggestions raw AI suggestions
     * @param analysisType analysis type (definition, history, state)
     * @param executionId execution ID (for audit linking, may be null)
     * @return filtered and flagged suggestions
     */
    public List<AISuggestion> enforce(List<AISuggestion> suggestions, String analysisType, String executionId) {
        if (suggestions == null || suggestions.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Check if analysis type is allowed
        Set<String> allowedTypes = policy.getAllowedAnalysisTypes();
        if (!allowedTypes.isEmpty() && !allowedTypes.contains(analysisType)) {
            logger.warn("Analysis type '{}' is not allowed by policy. Blocking all suggestions.", analysisType);
            // Audit all as blocked
            for (AISuggestion suggestion : suggestions) {
                auditPolicyDecision(suggestion, PolicyDecision.BLOCK, 
                    "Analysis type '" + analysisType + "' not allowed by policy", executionId);
            }
            return new ArrayList<>();
        }
        
        List<AISuggestion> result = new ArrayList<>();
        int allowedCount = 0;
        
        for (AISuggestion suggestion : suggestions) {
            PolicyDecision decision = evaluateSuggestion(suggestion);
            String reason = getPolicyReason(suggestion, decision);
            
            // Audit the decision
            auditPolicyDecision(suggestion, decision, reason, executionId);
            
            if (decision == PolicyDecision.BLOCK) {
                // Don't add to result, but already audited
                continue;
            }
            
            if (decision == PolicyDecision.FLAG) {
                // Add with human review flag
                result.add(suggestion.withRequiresHumanReview(true));
                allowedCount++;
            } else {
                // ALLOW - add normally
                result.add(suggestion);
                allowedCount++;
            }
            
            // Enforce max suggestions limit
            if (allowedCount >= policy.getMaxSuggestionsPerRequest()) {
                logger.debug("Reached max suggestions limit ({}). Truncating remaining suggestions.", 
                    policy.getMaxSuggestionsPerRequest());
                // Audit remaining as blocked
                for (int i = result.size(); i < suggestions.size(); i++) {
                    auditPolicyDecision(suggestions.get(i), PolicyDecision.BLOCK, 
                        "Exceeded max_suggestions_per_request limit", executionId);
                }
                break;
            }
        }
        
        logger.debug("Guardrail enforcement: {} suggestions -> {} allowed/flagged, {} blocked", 
            suggestions.size(), result.size(), suggestions.size() - result.size());
        
        return result;
    }
    
    /**
     * Enforce guardrails on a draft artifact (Phase 6 Step 4).
     * Evaluates draft against policy and returns flagged version if needed.
     * 
     * @param draft raw draft artifact
     * @param executionId execution ID (for audit linking, may be null)
     * @return draft with guardrail flags applied
     */
    public DraftArtifact enforceDraft(DraftArtifact draft, String executionId) {
        if (draft == null) {
            return null;
        }
        
        // Evaluate draft against policy (similar to suggestions)
        PolicyDecision decision = evaluateDraft(draft);
        String reason = getDraftPolicyReason(draft, decision);
        
        // Audit the decision
        auditDraftPolicyDecision(draft, decision, reason, executionId);
        
        if (decision == PolicyDecision.BLOCK) {
            // Draft is blocked - return null (caller should not return it)
            return null;
        }
        
        if (decision == PolicyDecision.FLAG) {
            // Add human review flag
            return draft.withRequiresHumanReview(true);
        }
        
        // ALLOW - return normally
        return draft;
    }
    
    /**
     * Evaluate a draft against policy.
     */
    private PolicyDecision evaluateDraft(DraftArtifact draft) {
        // Get confidence score
        double confidence = draft.getConfidence();
        if (draft.getConfidenceDetails() != null) {
            confidence = draft.getConfidenceDetails().getScore();
        }
        
        // Check confidence threshold
        if (confidence < policy.getMinConfidenceThreshold()) {
            if (policy.isRequireHumanReviewBelowThreshold()) {
                return PolicyDecision.FLAG;
            } else {
                return PolicyDecision.BLOCK;
            }
        }
        
        // All checks passed
        return PolicyDecision.ALLOW;
    }
    
    /**
     * Get human-readable reason for draft policy decision.
     */
    private String getDraftPolicyReason(DraftArtifact draft, PolicyDecision decision) {
        switch (decision) {
            case BLOCK:
                double confidence = draft.getConfidence();
                if (draft.getConfidenceDetails() != null) {
                    confidence = draft.getConfidenceDetails().getScore();
                }
                return "Confidence " + String.format("%.2f", confidence) + 
                    " below threshold " + String.format("%.2f", policy.getMinConfidenceThreshold());
                
            case FLAG:
                double conf = draft.getConfidence();
                if (draft.getConfidenceDetails() != null) {
                    conf = draft.getConfidenceDetails().getScore();
                }
                return "Confidence " + String.format("%.2f", conf) + 
                    " below threshold " + String.format("%.2f", policy.getMinConfidenceThreshold()) + 
                    " - requires human review";
                
            case ALLOW:
                return "Draft meets all policy requirements";
                
            default:
                return "Unknown decision";
        }
    }
    
    /**
     * Audit a draft policy decision to the database.
     */
    private void auditDraftPolicyDecision(DraftArtifact draft, PolicyDecision decision, 
                                          String reason, String executionId) {
        try (Connection conn = databaseManager.getConnection()) {
            double confidence = draft.getConfidence();
            if (draft.getConfidenceDetails() != null) {
                confidence = draft.getConfidenceDetails().getScore();
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO ai_guardrail_audit (suggestion_id, policy_decision, policy_reason, " +
                "confidence_score, execution_id, created_at) " +
                "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)"
            )) {
                stmt.setString(1, draft.getDraftId());  // Use draft_id as suggestion_id for audit
                stmt.setString(2, decision.name());
                stmt.setString(3, "DRAFT: " + reason);
                stmt.setDouble(4, confidence);
                stmt.setString(5, executionId);
                stmt.executeUpdate();
                conn.commit();
            }
        } catch (SQLException e) {
            logger.error("Failed to audit draft policy decision: draftId={}, decision={}, error={}", 
                draft.getDraftId(), decision, e.getMessage());
        }
    }
    
    /**
     * Evaluate a single suggestion against policy.
     * 
     * @param suggestion suggestion to evaluate
     * @return policy decision
     */
    private PolicyDecision evaluateSuggestion(AISuggestion suggestion) {
        // Check if suggestion type is blocked
        if (policy.getBlockedSuggestionTypes().contains(suggestion.getType())) {
            return PolicyDecision.BLOCK;
        }
        
        // Get confidence score
        double confidence = suggestion.getConfidence();
        if (suggestion.getConfidenceDetails() != null) {
            confidence = suggestion.getConfidenceDetails().getScore();
        }
        
        // Check confidence threshold
        if (confidence < policy.getMinConfidenceThreshold()) {
            if (policy.isRequireHumanReviewBelowThreshold()) {
                return PolicyDecision.FLAG;
            } else {
                return PolicyDecision.BLOCK;
            }
        }
        
        // All checks passed
        return PolicyDecision.ALLOW;
    }
    
    /**
     * Get human-readable reason for policy decision.
     */
    private String getPolicyReason(AISuggestion suggestion, PolicyDecision decision) {
        switch (decision) {
            case BLOCK:
                if (policy.getBlockedSuggestionTypes().contains(suggestion.getType())) {
                    return "Suggestion type '" + suggestion.getType() + "' is blocked by policy";
                }
                double confidence = suggestion.getConfidence();
                if (suggestion.getConfidenceDetails() != null) {
                    confidence = suggestion.getConfidenceDetails().getScore();
                }
                if (confidence < policy.getMinConfidenceThreshold()) {
                    return "Confidence " + String.format("%.2f", confidence) + 
                        " below threshold " + String.format("%.2f", policy.getMinConfidenceThreshold());
                }
                return "Blocked by policy";
                
            case FLAG:
                double conf = suggestion.getConfidence();
                if (suggestion.getConfidenceDetails() != null) {
                    conf = suggestion.getConfidenceDetails().getScore();
                }
                return "Confidence " + String.format("%.2f", conf) + 
                    " below threshold " + String.format("%.2f", policy.getMinConfidenceThreshold()) + 
                    " - requires human review";
                
            case ALLOW:
                return "Suggestion meets all policy requirements";
                
            default:
                return "Unknown decision";
        }
    }
    
    /**
     * Audit a policy decision to the database.
     */
    private void auditPolicyDecision(AISuggestion suggestion, PolicyDecision decision, 
                                     String reason, String executionId) {
        try (Connection conn = databaseManager.getConnection()) {
            double confidence = suggestion.getConfidence();
            if (suggestion.getConfidenceDetails() != null) {
                confidence = suggestion.getConfidenceDetails().getScore();
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO ai_guardrail_audit (suggestion_id, policy_decision, policy_reason, " +
                "confidence_score, execution_id, created_at) " +
                "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)"
            )) {
                stmt.setString(1, suggestion.getSuggestionId());
                stmt.setString(2, decision.name());
                stmt.setString(3, reason);
                stmt.setDouble(4, confidence);
                stmt.setString(5, executionId);
                stmt.executeUpdate();
                conn.commit();
            }
        } catch (SQLException e) {
            logger.error("Failed to audit policy decision: suggestionId={}, decision={}, error={}", 
                suggestion.getSuggestionId(), decision, e.getMessage());
        }
    }
}

