/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: AIAdvisorService.java
 * RESPONSIBILITY: AI advisor for read-only workflow analysis
 * 
 * ARCHITECTURAL ROLE:
 * - Analyzes workflow definitions, execution history, and states
 * - Provides READ-ONLY suggestions (never executes or modifies)
 * - Fully auditable (all suggestions logged)
 * 
 * NON-NEGOTIABLE PRINCIPLES:
 * - AI NEVER triggers workflows, executes steps, approves, or modifies state
 * - AI ALWAYS returns suggestions as plain data
 * - All AI activity is auditable
 * 
 * ============================================================================
 * PHASE 6 SCOPE FREEZE — DO NOT EXPAND
 * ============================================================================
 * AI capabilities are FROZEN at Phase 6 completion.
 * 
 * FORBIDDEN EXPANSIONS:
 * - Execution capabilities (workflow/step execution)
 * - Auto-application logic (auto-apply suggestions)
 * - State mutation (modify workflows/config)
 * - Approval resolution (bypass human approval)
 * - Policy modification (AI-modified policies)
 * 
 * BOUNDARY ENFORCEMENT:
 * This service MUST NOT have access to:
 * - TaskScheduler execution methods
 * - WorkflowEngine.startWorkflow()
 * - ApprovalService.resolveApproval()
 * - Any state mutation methods
 * 
 * Any AI capability expansion requires:
 * 1. New Phase approval
 * 2. Governance review
 * 3. Architecture review
 * 4. Security review
 * ============================================================================
 * 
 * Reference: PROJECT_DOCUMENTATION.md Phase 6 Step 1
 * Reference: AI_GOVERNANCE_SUMMARY.md (Phase 6 Freeze)
 */
package com.presso.kernel.ai;

import com.presso.kernel.persistence.DatabaseManager;
import com.presso.kernel.workflow.WorkflowDefinition;
import com.presso.kernel.workflow.StepDefinition;
import com.presso.kernel.workflow.persistence.WorkflowPersistenceService;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.UUID;

/**
 * AI Advisor service for read-only workflow analysis and suggestions.
 */
public final class AIAdvisorService {
    
    private static final Logger logger = LoggerFactory.getLogger(AIAdvisorService.class);
    
    private final DatabaseManager databaseManager;
    private final WorkflowPersistenceService workflowPersistence;
    
    /**
     * Construct an AI Advisor service.
     * 
     * @param databaseManager database manager
     * @param workflowPersistence workflow persistence service
     */
    public AIAdvisorService(DatabaseManager databaseManager, WorkflowPersistenceService workflowPersistence) {
        this.databaseManager = databaseManager;
        this.workflowPersistence = workflowPersistence;
        logger.info("AIAdvisorService created (read-only mode)");
    }
    
    /**
     * Analyze a workflow definition and provide suggestions.
     * 
     * @param workflowId workflow identifier
     * @param definition workflow definition
     * @return list of suggestions
     */
    public List<AISuggestion> analyzeWorkflowDefinition(String workflowId, WorkflowDefinition definition) {
        List<AISuggestion> suggestions = new ArrayList<>();
        
        // Analyze for parallelization opportunities
        suggestions.addAll(analyzeParallelizationOpportunities(workflowId, definition));
        
        // Analyze step configurations
        suggestions.addAll(analyzeStepConfigurations(workflowId, definition));
        
        // Analyze DAG structure
        if (definition.isDagWorkflow()) {
            suggestions.addAll(analyzeDagStructure(workflowId, definition));
        }
        
        // Log suggestions for audit
        for (AISuggestion suggestion : suggestions) {
            logSuggestion(suggestion);
        }
        
        return suggestions;
    }
    
    /**
     * Analyze workflow execution history and provide suggestions.
     * 
     * @param workflowId workflow identifier
     * @return list of suggestions based on execution history
     */
    public List<AISuggestion> analyzeExecutionHistory(String workflowId) {
        List<AISuggestion> suggestions = new ArrayList<>();
        
        // Analyze failure patterns
        suggestions.addAll(analyzeFailurePatterns(workflowId));
        
        // Analyze performance patterns
        suggestions.addAll(analyzePerformancePatterns(workflowId));
        
        // Analyze retry patterns
        suggestions.addAll(analyzeRetryPatterns(workflowId));
        
        // Log suggestions for audit
        for (AISuggestion suggestion : suggestions) {
            logSuggestion(suggestion);
        }
        
        return suggestions;
    }
    
    /**
     * Analyze current workflow execution state and provide suggestions.
     * 
     * @param executionId execution identifier
     * @return list of suggestions based on current state
     */
    public List<AISuggestion> analyzeExecutionState(String executionId) {
        List<AISuggestion> suggestions = new ArrayList<>();
        
        // Analyze pending approvals
        suggestions.addAll(analyzePendingApprovals(executionId));
        
        // Analyze stuck workflows
        suggestions.addAll(analyzeStuckWorkflows(executionId));
        
        // Log suggestions for audit
        for (AISuggestion suggestion : suggestions) {
            logSuggestion(suggestion);
        }
        
        return suggestions;
    }
    
    /**
     * Analyze parallelization opportunities in workflow definition (Phase 6 Step 2: with explainability).
     */
    private List<AISuggestion> analyzeParallelizationOpportunities(String workflowId, WorkflowDefinition definition) {
        List<AISuggestion> suggestions = new ArrayList<>();
        
        // Check if workflow has independent steps that could run in parallel
        if (!definition.isDagWorkflow()) {
            List<StepDefinition> steps = definition.getSteps();
            if (steps.size() >= 2) {
                // Check if steps are independent (no data dependencies)
                boolean hasIndependentSteps = true;
                int dependentSteps = 0;
                for (int i = 1; i < steps.size(); i++) {
                    StepDefinition step = steps.get(i);
                    // Check if step uses data from previous steps
                    if (step.getInputMapping() != null) {
                        String inputJson = step.getInputMapping().toString();
                        for (int j = 0; j < i; j++) {
                            if (inputJson.contains("${" + steps.get(j).getStepId())) {
                                hasIndependentSteps = false;
                                dependentSteps++;
                                break;
                            }
                        }
                    }
                }
                
                if (hasIndependentSteps) {
                    JsonObject metadata = new JsonObject();
                    metadata.addProperty("workflow_id", workflowId);
                    metadata.addProperty("step_count", steps.size());
                    
                    // Phase 6 Step 2: Generate explanation
                    List<String> reasoningSteps = new ArrayList<>();
                    reasoningSteps.add("Analyzed workflow definition for step dependencies");
                    reasoningSteps.add("Checked input mappings for variable references between steps");
                    reasoningSteps.add("Found " + steps.size() + " steps with no detected data dependencies");
                    reasoningSteps.add("Sequential execution may be unnecessary if steps are truly independent");
                    
                    JsonObject evidence = new JsonObject();
                    evidence.addProperty("workflow_id", workflowId);
                    evidence.addProperty("total_steps", steps.size());
                    evidence.addProperty("independent_steps", steps.size());
                    evidence.addProperty("analysis_method", "input_mapping_variable_analysis");
                    evidence.addProperty("data_source", "workflow_definition");
                    
                    Explanation explanation = new Explanation(
                        "Workflow has " + steps.size() + " steps that appear independent based on input mapping analysis. " +
                        "No variable references between steps detected, suggesting parallel execution may be possible.",
                        reasoningSteps,
                        evidence
                    );
                    
                    // Phase 6 Step 2: Compute confidence
                    double confidenceScore = 0.7;  // Medium confidence - static analysis may miss runtime dependencies
                    String confidenceExplanation = "Medium confidence: Based on static analysis of input mappings. " +
                        "Runtime data dependencies may exist that are not visible in the definition.";
                    ConfidenceDetails confidenceDetails = new ConfidenceDetails(confidenceScore, confidenceExplanation);
                    
                    // Phase 6 Step 2: Identify limitations
                    List<String> assumptions = new ArrayList<>();
                    assumptions.add("Input mapping analysis accurately reflects data dependencies");
                    assumptions.add("Steps do not have implicit dependencies not captured in input mappings");
                    List<String> missingData = new ArrayList<>();
                    missingData.add("Runtime execution data to verify actual dependencies");
                    Limitations limitations = new Limitations(assumptions, missingData);
                    
                    suggestions.add(new AISuggestion(
                        UUID.randomUUID().toString(),
                        AISuggestion.SuggestionType.OPTIMIZATION,
                        "Consider Parallel Execution",
                        "This workflow has " + steps.size() + " steps that appear to be independent. " +
                        "Consider converting to a DAG workflow with 'depends_on' fields to enable parallel execution " +
                        "and improve performance.",
                        "workflow:" + workflowId,
                        metadata,
                        confidenceScore,
                        explanation,
                        confidenceDetails,
                        limitations
                    ));
                }
            }
        } else {
            // DAG workflow - check if max_parallelism is set
            if (definition.getMaxParallelism() == null) {
                JsonObject metadata = new JsonObject();
                metadata.addProperty("workflow_id", workflowId);
                
                // Phase 6 Step 2: Generate explanation
                List<String> reasoningSteps = new ArrayList<>();
                reasoningSteps.add("Detected DAG workflow structure");
                reasoningSteps.add("Checked for max_parallelism configuration");
                reasoningSteps.add("No max_parallelism limit found");
                reasoningSteps.add("Unlimited parallelism may cause resource contention");
                
                JsonObject evidence = new JsonObject();
                evidence.addProperty("workflow_id", workflowId);
                evidence.addProperty("is_dag", true);
                evidence.addProperty("max_parallelism", "not_set");
                evidence.addProperty("data_source", "workflow_definition");
                
                Explanation explanation = new Explanation(
                    "DAG workflow does not specify max_parallelism, which means all independent steps could run " +
                    "simultaneously. This may cause resource contention or overwhelm system resources.",
                    reasoningSteps,
                    evidence
                );
                
                // Phase 6 Step 2: Compute confidence
                double confidenceScore = 0.6;  // Medium confidence
                String confidenceExplanation = "Medium confidence: Based on workflow definition analysis. " +
                    "Resource impact depends on step execution characteristics.";
                ConfidenceDetails confidenceDetails = new ConfidenceDetails(confidenceScore, confidenceExplanation);
                
                // Phase 6 Step 2: Identify limitations
                List<String> assumptions = new ArrayList<>();
                assumptions.add("Resource constraints exist that require parallelism limits");
                List<String> missingData = new ArrayList<>();
                missingData.add("System resource capacity information");
                missingData.add("Step execution resource requirements");
                Limitations limitations = new Limitations(assumptions, missingData);
                
                suggestions.add(new AISuggestion(
                    UUID.randomUUID().toString(),
                    AISuggestion.SuggestionType.CONFIGURATION,
                    "Set Max Parallelism",
                    "This DAG workflow does not specify 'max_parallelism'. Consider setting it to limit " +
                    "concurrent step execution and control resource usage.",
                    "workflow:" + workflowId,
                    metadata,
                    confidenceScore,
                    explanation,
                    confidenceDetails,
                    limitations
                ));
            }
        }
        
        return suggestions;
    }
    
    /**
     * Analyze step configurations for optimization opportunities (Phase 6 Step 2: with explainability).
     */
    private List<AISuggestion> analyzeStepConfigurations(String workflowId, WorkflowDefinition definition) {
        List<AISuggestion> suggestions = new ArrayList<>();
        
        for (StepDefinition step : definition.getSteps()) {
            // Check retry policy
            if (step.getRetryPolicy() != null) {
                if (step.getRetryPolicy().getMaxAttempts() == 1 && 
                    step.getOnFailure() == StepDefinition.OnFailure.FAIL) {
                    JsonObject metadata = new JsonObject();
                    metadata.addProperty("workflow_id", workflowId);
                    metadata.addProperty("step_id", step.getStepId());
                    
                    // Phase 6 Step 2: Generate explanation
                    List<String> reasoningSteps = new ArrayList<>();
                    reasoningSteps.add("Analyzed step configuration for workflow: " + workflowId);
                    reasoningSteps.add("Checked retry policy configuration for step: " + step.getStepId());
                    reasoningSteps.add("Step has retry policy with max_attempts = 1 (no retries)");
                    reasoningSteps.add("Step on_failure policy is set to FAIL");
                    reasoningSteps.add("No retry capability means transient failures will cause immediate failure");
                    
                    JsonObject evidence = new JsonObject();
                    evidence.addProperty("workflow_id", workflowId);
                    evidence.addProperty("step_id", step.getStepId());
                    evidence.addProperty("max_attempts", step.getRetryPolicy().getMaxAttempts());
                    evidence.addProperty("on_failure", step.getOnFailure().name());
                    evidence.addProperty("data_source", "workflow_definition");
                    
                    Explanation explanation = new Explanation(
                        "Step '" + step.getStepId() + "' has a retry policy configured with max_attempts = 1, " +
                        "meaning it will not retry on failure. Combined with on_failure = FAIL, this means " +
                        "any transient failure will cause the workflow to fail immediately. Adding retry capability " +
                        "could improve reliability for transient failures.",
                        reasoningSteps,
                        evidence
                    );
                    
                    // Phase 6 Step 2: Compute confidence
                    double confidenceScore = 0.8;  // High confidence - clear configuration issue
                    String confidenceExplanation = "High confidence: Based on explicit step configuration analysis. " +
                        "Retry policy configuration is clearly defined in the workflow definition.";
                    ConfidenceDetails confidenceDetails = new ConfidenceDetails(confidenceScore, confidenceExplanation);
                    
                    // Phase 6 Step 2: Identify limitations
                    List<String> assumptions = new ArrayList<>();
                    assumptions.add("Transient failures are possible for this step type");
                    assumptions.add("Retry logic would be beneficial for this step");
                    List<String> missingData = new ArrayList<>();
                    missingData.add("Historical failure data to verify transient failure frequency");
                    missingData.add("Step execution context to determine if retries are appropriate");
                    Limitations limitations = new Limitations(assumptions, missingData);
                    
                    suggestions.add(new AISuggestion(
                        UUID.randomUUID().toString(),
                        AISuggestion.SuggestionType.RELIABILITY,
                        "Consider Adding Retry Policy",
                        "Step '" + step.getStepId() + "' has no retry policy and fails immediately on error. " +
                        "Consider adding a retry policy for transient failures.",
                        "workflow:" + workflowId + ":step:" + step.getStepId(),
                        metadata,
                        confidenceScore,
                        explanation,
                        confidenceDetails,
                        limitations
                    ));
                }
            }
            
            // Check for approval steps without timeout
            if (step.isApprovalStep()) {
                if (step.getTimeoutPolicy() == StepDefinition.TimeoutPolicy.WAIT) {
                    JsonObject metadata = new JsonObject();
                    metadata.addProperty("workflow_id", workflowId);
                    metadata.addProperty("step_id", step.getStepId());
                    
                    // Phase 6 Step 2: Generate explanation
                    List<String> reasoningSteps = new ArrayList<>();
                    reasoningSteps.add("Analyzed step configuration for workflow: " + workflowId);
                    reasoningSteps.add("Detected approval step: " + step.getStepId());
                    reasoningSteps.add("Checked timeout policy configuration");
                    reasoningSteps.add("Timeout policy is set to WAIT (no timeout)");
                    reasoningSteps.add("Approval step may wait indefinitely without timeout");
                    
                    JsonObject evidence = new JsonObject();
                    evidence.addProperty("workflow_id", workflowId);
                    evidence.addProperty("step_id", step.getStepId());
                    evidence.addProperty("is_approval_step", true);
                    evidence.addProperty("timeout_policy", step.getTimeoutPolicy().name());
                    evidence.addProperty("data_source", "workflow_definition");
                    
                    Explanation explanation = new Explanation(
                        "Approval step '" + step.getStepId() + "' has timeout_policy set to WAIT, meaning it will " +
                        "wait indefinitely for approval. Without a timeout, the workflow may become stuck if the " +
                        "approver is unavailable. Setting timeout_policy to FAIL with a timeout_ms value would " +
                        "prevent indefinite blocking.",
                        reasoningSteps,
                        evidence
                    );
                    
                    // Phase 6 Step 2: Compute confidence
                    double confidenceScore = 0.7;  // Medium-high confidence - clear configuration issue
                    String confidenceExplanation = "Medium-high confidence: Based on explicit step configuration analysis. " +
                        "Timeout policy configuration is clearly defined, but impact depends on approval workflow context.";
                    ConfidenceDetails confidenceDetails = new ConfidenceDetails(confidenceScore, confidenceExplanation);
                    
                    // Phase 6 Step 2: Identify limitations
                    List<String> assumptions = new ArrayList<>();
                    assumptions.add("Indefinite waiting is undesirable for this workflow");
                    assumptions.add("Timeout would be beneficial for workflow reliability");
                    List<String> missingData = new ArrayList<>();
                    missingData.add("Approval workflow context and requirements");
                    missingData.add("Historical approval wait times");
                    Limitations limitations = new Limitations(assumptions, missingData);
                    
                    suggestions.add(new AISuggestion(
                        UUID.randomUUID().toString(),
                        AISuggestion.SuggestionType.CONFIGURATION,
                        "Consider Approval Timeout",
                        "Approval step '" + step.getStepId() + "' has no timeout policy. " +
                        "Consider setting 'timeout_policy: FAIL' with a 'timeout_ms' value to prevent " +
                        "workflows from waiting indefinitely.",
                        "workflow:" + workflowId + ":step:" + step.getStepId(),
                        metadata,
                        confidenceScore,
                        explanation,
                        confidenceDetails,
                        limitations
                    ));
                }
            }
        }
        
        return suggestions;
    }
    
    /**
     * Analyze DAG structure for optimization opportunities (Phase 6 Step 2: with explainability).
     */
    private List<AISuggestion> analyzeDagStructure(String workflowId, WorkflowDefinition definition) {
        List<AISuggestion> suggestions = new ArrayList<>();
        
        // Count steps with no dependencies (can run in parallel)
        int independentSteps = 0;
        int totalSteps = definition.getSteps().size();
        for (StepDefinition step : definition.getSteps()) {
            if (!step.hasDependencies()) {
                independentSteps++;
            }
        }
        
        if (independentSteps > 1) {
            JsonObject metadata = new JsonObject();
            metadata.addProperty("workflow_id", workflowId);
            metadata.addProperty("independent_steps", independentSteps);
            metadata.addProperty("total_steps", totalSteps);
            
            // Phase 6 Step 2: Generate explanation
            List<String> reasoningSteps = new ArrayList<>();
            reasoningSteps.add("Analyzed DAG workflow structure");
            reasoningSteps.add("Counted steps with no dependencies (depends_on field empty or missing)");
            reasoningSteps.add("Found " + independentSteps + " independent steps out of " + totalSteps + " total");
            reasoningSteps.add("Independent steps can execute in parallel");
            
            JsonObject evidence = new JsonObject();
            evidence.addProperty("workflow_id", workflowId);
            evidence.addProperty("total_steps", totalSteps);
            evidence.addProperty("independent_steps", independentSteps);
            evidence.addProperty("parallelism_opportunity", true);
            evidence.addProperty("data_source", "workflow_definition");
            
            Explanation explanation = new Explanation(
                "DAG workflow has " + independentSteps + " steps with no dependencies that can execute in parallel. " +
                "This represents a significant parallelism opportunity. Ensure max_parallelism is configured " +
                "appropriately to take advantage of this.",
                reasoningSteps,
                evidence
            );
            
            // Phase 6 Step 2: Compute confidence
            double confidenceScore = 0.9;  // High confidence - clear DAG structure
            String confidenceExplanation = "High confidence: Based on explicit DAG dependency analysis. " +
                "Independent steps are clearly identified by absence of depends_on fields.";
            ConfidenceDetails confidenceDetails = new ConfidenceDetails(confidenceScore, confidenceExplanation);
            
            // Phase 6 Step 2: Identify limitations
            List<String> assumptions = new ArrayList<>();
            assumptions.add("Steps are truly independent (no implicit dependencies)");
            assumptions.add("System has capacity for parallel execution");
            List<String> missingData = new ArrayList<>();
            missingData.add("Runtime execution data to verify actual independence");
            Limitations limitations = new Limitations(assumptions, missingData);
            
            suggestions.add(new AISuggestion(
                UUID.randomUUID().toString(),
                AISuggestion.SuggestionType.OPTIMIZATION,
                "Parallel Execution Available",
                "This DAG workflow has " + independentSteps + " independent steps that can run in parallel. " +
                "Ensure 'max_parallelism' is set appropriately to take advantage of this.",
                "workflow:" + workflowId,
                metadata,
                confidenceScore,
                explanation,
                confidenceDetails,
                limitations
            ));
        }
        
        return suggestions;
    }
    
    /**
     * Analyze failure patterns from execution history (Phase 6 Step 2: with explainability).
     */
    private List<AISuggestion> analyzeFailurePatterns(String workflowId) {
        List<AISuggestion> suggestions = new ArrayList<>();
        
        try (Connection conn = databaseManager.getConnection()) {
            // Find steps that fail frequently
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT step_id, COUNT(*) as failure_count, " +
                "GROUP_CONCAT(DISTINCT error_message) as error_messages, " +
                "COUNT(DISTINCT execution_id) as execution_count " +
                "FROM workflow_step_execution " +
                "WHERE execution_id IN (SELECT execution_id FROM workflow_execution WHERE workflow_id = ?) " +
                "AND status = 'failed' " +
                "GROUP BY step_id " +
                "HAVING failure_count >= 3 " +
                "ORDER BY failure_count DESC"
            )) {
                stmt.setString(1, workflowId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String stepId = rs.getString("step_id");
                        int failureCount = rs.getInt("failure_count");
                        int executionCount = rs.getInt("execution_count");
                        String errorMessages = rs.getString("error_messages");
                        String errorSummary = errorMessages != null ? 
                            errorMessages.substring(0, Math.min(100, errorMessages.length())) : "unknown";
                        
                        JsonObject metadata = new JsonObject();
                        metadata.addProperty("workflow_id", workflowId);
                        metadata.addProperty("step_id", stepId);
                        metadata.addProperty("failure_count", failureCount);
                        metadata.addProperty("execution_count", executionCount);
                        metadata.addProperty("error_messages", errorMessages);
                        
                        // Phase 6 Step 2: Generate explanation
                        List<String> reasoningSteps = new ArrayList<>();
                        reasoningSteps.add("Analyzed execution history for workflow: " + workflowId);
                        reasoningSteps.add("Queried workflow_step_execution table for failed steps");
                        reasoningSteps.add("Step '" + stepId + "' failed " + failureCount + " times across " + executionCount + " executions");
                        reasoningSteps.add("Common error patterns: " + errorSummary);
                        reasoningSteps.add("Failure rate suggests reliability issue");
                        
                        JsonObject evidence = new JsonObject();
                        evidence.addProperty("workflow_id", workflowId);
                        evidence.addProperty("step_id", stepId);
                        evidence.addProperty("failure_count", failureCount);
                        evidence.addProperty("execution_count", executionCount);
                        evidence.addProperty("error_messages", errorMessages);
                        evidence.addProperty("data_source", "execution_history");
                        evidence.addProperty("time_window", "all_available_history");
                        
                        Explanation explanation = new Explanation(
                            "Step '" + stepId + "' has failed " + failureCount + " times across " + executionCount + 
                            " workflow executions. Common errors include: " + errorSummary + ". " +
                            "This pattern suggests a reliability issue that may benefit from retry logic or " +
                            "configuration review.",
                            reasoningSteps,
                            evidence
                        );
                        
                        // Phase 6 Step 2: Compute confidence based on data volume
                        double confidenceScore = computeConfidenceFromDataVolume(executionCount, failureCount);
                        String confidenceExplanation = "Confidence based on " + executionCount + " executions with " + 
                            failureCount + " failures. " + 
                            (executionCount >= 10 ? "High data volume provides reliable pattern." : 
                             "Limited data volume - pattern may be less reliable.");
                        ConfidenceDetails confidenceDetails = new ConfidenceDetails(confidenceScore, confidenceExplanation);
                        
                        // Phase 6 Step 2: Identify limitations
                        List<String> assumptions = new ArrayList<>();
                        assumptions.add("Historical failure pattern will continue");
                        assumptions.add("Failures are due to step configuration, not external factors");
                        List<String> missingData = new ArrayList<>();
                        if (executionCount < 10) {
                            missingData.add("More execution history for statistical significance");
                        }
                        missingData.add("Step configuration details to identify root cause");
                        Limitations limitations = new Limitations(assumptions, missingData);
                        
                        suggestions.add(new AISuggestion(
                            UUID.randomUUID().toString(),
                            AISuggestion.SuggestionType.RELIABILITY,
                            "Frequent Step Failure",
                            "Step '" + stepId + "' has failed " + failureCount + " times in recent executions. " +
                            "Common errors: " + errorSummary +
                            ". Consider reviewing the step configuration or adding retry logic.",
                            "workflow:" + workflowId + ":step:" + stepId,
                            metadata,
                            confidenceScore,
                            explanation,
                            confidenceDetails,
                            limitations
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to analyze failure patterns: workflowId={}, error={}", workflowId, e.getMessage());
        }
        
        return suggestions;
    }
    
    /**
     * Compute confidence score based on data volume (Phase 6 Step 2).
     * 
     * @param executionCount number of executions analyzed
     * @param failureCount number of failures observed
     * @return confidence score (0.0 to 1.0)
     */
    private double computeConfidenceFromDataVolume(int executionCount, int failureCount) {
        // Base confidence on data volume
        double volumeConfidence = Math.min(1.0, executionCount / 20.0);  // Max confidence at 20+ executions
        
        // Adjust based on failure rate consistency
        double failureRate = failureCount / (double) Math.max(1, executionCount);
        double consistencyBonus = failureRate > 0.5 ? 0.1 : 0.0;  // Higher confidence if consistent failures
        
        return Math.min(1.0, volumeConfidence + consistencyBonus);
    }
    
    /**
     * Analyze performance patterns from execution history (Phase 6 Step 2: with explainability).
     */
    private List<AISuggestion> analyzePerformancePatterns(String workflowId) {
        List<AISuggestion> suggestions = new ArrayList<>();
        
        try (Connection conn = databaseManager.getConnection()) {
            // Find slow steps
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT step_id, " +
                "AVG((julianday(completed_at) - julianday(started_at)) * 86400) as avg_duration_seconds, " +
                "COUNT(*) as execution_count, " +
                "MIN((julianday(completed_at) - julianday(started_at)) * 86400) as min_duration_seconds, " +
                "MAX((julianday(completed_at) - julianday(started_at)) * 86400) as max_duration_seconds " +
                "FROM workflow_step_execution " +
                "WHERE execution_id IN (SELECT execution_id FROM workflow_execution WHERE workflow_id = ?) " +
                "AND status = 'completed' " +
                "AND completed_at IS NOT NULL AND started_at IS NOT NULL " +
                "GROUP BY step_id " +
                "HAVING avg_duration_seconds > 10 " +
                "ORDER BY avg_duration_seconds DESC"
            )) {
                stmt.setString(1, workflowId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String stepId = rs.getString("step_id");
                        double avgDuration = rs.getDouble("avg_duration_seconds");
                        int executionCount = rs.getInt("execution_count");
                        double minDuration = rs.getDouble("min_duration_seconds");
                        double maxDuration = rs.getDouble("max_duration_seconds");
                        
                        JsonObject metadata = new JsonObject();
                        metadata.addProperty("workflow_id", workflowId);
                        metadata.addProperty("step_id", stepId);
                        metadata.addProperty("avg_duration_seconds", avgDuration);
                        metadata.addProperty("execution_count", executionCount);
                        metadata.addProperty("min_duration_seconds", minDuration);
                        metadata.addProperty("max_duration_seconds", maxDuration);
                        
                        // Phase 6 Step 2: Generate explanation
                        List<String> reasoningSteps = new ArrayList<>();
                        reasoningSteps.add("Analyzed execution history for performance patterns");
                        reasoningSteps.add("Calculated average execution duration per step");
                        reasoningSteps.add("Step '" + stepId + "' average duration: " + String.format("%.1f", avgDuration) + " seconds");
                        reasoningSteps.add("Duration exceeds 10 seconds threshold");
                        reasoningSteps.add("Performance optimization may be beneficial");
                        
                        JsonObject evidence = new JsonObject();
                        evidence.addProperty("workflow_id", workflowId);
                        evidence.addProperty("step_id", stepId);
                        evidence.addProperty("avg_duration_seconds", avgDuration);
                        evidence.addProperty("min_duration_seconds", minDuration);
                        evidence.addProperty("max_duration_seconds", maxDuration);
                        evidence.addProperty("execution_count", executionCount);
                        evidence.addProperty("data_source", "execution_history");
                        evidence.addProperty("time_window", "all_available_history");
                        evidence.addProperty("threshold_seconds", 10);
                        
                        Explanation explanation = new Explanation(
                            "Step '" + stepId + "' has an average execution time of " + 
                            String.format("%.1f", avgDuration) + " seconds across " + executionCount + 
                            " executions (range: " + String.format("%.1f", minDuration) + " - " + 
                            String.format("%.1f", maxDuration) + " seconds). " +
                            "This exceeds the 10-second threshold and may indicate a performance bottleneck.",
                            reasoningSteps,
                            evidence
                        );
                        
                        // Phase 6 Step 2: Compute confidence
                        double confidenceScore = computeConfidenceFromDataVolume(executionCount, 0);
                        String confidenceExplanation = "Confidence based on " + executionCount + " completed executions. " +
                            (executionCount >= 5 ? "Sufficient data for reliable average." : 
                             "Limited data - average may vary with more executions.");
                        ConfidenceDetails confidenceDetails = new ConfidenceDetails(confidenceScore, confidenceExplanation);
                        
                        // Phase 6 Step 2: Identify limitations
                        List<String> assumptions = new ArrayList<>();
                        assumptions.add("10 seconds is an appropriate threshold for this workflow context");
                        assumptions.add("Performance is consistent across different execution contexts");
                        List<String> missingData = new ArrayList<>();
                        if (executionCount < 5) {
                            missingData.add("More execution history for reliable average");
                        }
                        missingData.add("Step resource usage details (CPU, memory, I/O)");
                        Limitations limitations = new Limitations(assumptions, missingData);
                        
                        suggestions.add(new AISuggestion(
                            UUID.randomUUID().toString(),
                            AISuggestion.SuggestionType.OPTIMIZATION,
                            "Slow Step Detected",
                            "Step '" + stepId + "' has an average execution time of " + 
                            String.format("%.1f", avgDuration) + " seconds. " +
                            "Consider optimizing this step or breaking it into smaller steps.",
                            "workflow:" + workflowId + ":step:" + stepId,
                            metadata,
                            confidenceScore,
                            explanation,
                            confidenceDetails,
                            limitations
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to analyze performance patterns: workflowId={}, error={}", workflowId, e.getMessage());
        }
        
        return suggestions;
    }
    
    /**
     * Analyze retry patterns from execution history (Phase 6 Step 2: with explainability).
     */
    private List<AISuggestion> analyzeRetryPatterns(String workflowId) {
        List<AISuggestion> suggestions = new ArrayList<>();
        
        try (Connection conn = databaseManager.getConnection()) {
            // Find steps that frequently require retries
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT step_id, AVG(retry_count) as avg_retries, MAX(retry_count) as max_retries, " +
                "COUNT(*) as execution_count " +
                "FROM workflow_step_execution " +
                "WHERE execution_id IN (SELECT execution_id FROM workflow_execution WHERE workflow_id = ?) " +
                "AND retry_count > 0 " +
                "GROUP BY step_id " +
                "HAVING avg_retries >= 1.5 " +
                "ORDER BY avg_retries DESC"
            )) {
                stmt.setString(1, workflowId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String stepId = rs.getString("step_id");
                        double avgRetries = rs.getDouble("avg_retries");
                        int maxRetries = rs.getInt("max_retries");
                        int executionCount = rs.getInt("execution_count");
                        
                        JsonObject metadata = new JsonObject();
                        metadata.addProperty("workflow_id", workflowId);
                        metadata.addProperty("step_id", stepId);
                        metadata.addProperty("avg_retries", avgRetries);
                        metadata.addProperty("max_retries", maxRetries);
                        metadata.addProperty("execution_count", executionCount);
                        
                        // Phase 6 Step 2: Generate explanation
                        List<String> reasoningSteps = new ArrayList<>();
                        reasoningSteps.add("Analyzed retry patterns from execution history");
                        reasoningSteps.add("Calculated average retry count per step");
                        reasoningSteps.add("Step '" + stepId + "' average retries: " + String.format("%.1f", avgRetries));
                        reasoningSteps.add("Maximum retries observed: " + maxRetries);
                        reasoningSteps.add("High retry rate suggests transient failures");
                        
                        JsonObject evidence = new JsonObject();
                        evidence.addProperty("workflow_id", workflowId);
                        evidence.addProperty("step_id", stepId);
                        evidence.addProperty("avg_retries", avgRetries);
                        evidence.addProperty("max_retries", maxRetries);
                        evidence.addProperty("execution_count", executionCount);
                        evidence.addProperty("data_source", "execution_history");
                        evidence.addProperty("time_window", "all_available_history");
                        
                        Explanation explanation = new Explanation(
                            "Step '" + stepId + "' requires an average of " + 
                            String.format("%.1f", avgRetries) + " retries per execution across " + executionCount +
                            " executions, with a maximum of " + maxRetries + " retries. " +
                            "This pattern suggests transient failures (network issues, temporary unavailability) " +
                            "that are eventually resolved through retries. Consider increasing retry attempts or " +
                            "backoff duration to reduce retry overhead.",
                            reasoningSteps,
                            evidence
                        );
                        
                        // Phase 6 Step 2: Compute confidence
                        double confidenceScore = computeConfidenceFromDataVolume(executionCount, 0);
                        String confidenceExplanation = "Confidence based on " + executionCount + " executions with retries. " +
                            (executionCount >= 5 ? "Sufficient data for reliable pattern." : 
                             "Limited data - pattern may be less reliable.");
                        ConfidenceDetails confidenceDetails = new ConfidenceDetails(confidenceScore, confidenceExplanation);
                        
                        // Phase 6 Step 2: Identify limitations
                        List<String> assumptions = new ArrayList<>();
                        assumptions.add("Retries indicate transient failures, not permanent issues");
                        assumptions.add("Current retry policy is insufficient");
                        List<String> missingData = new ArrayList<>();
                        if (executionCount < 5) {
                            missingData.add("More execution history for reliable retry pattern");
                        }
                        missingData.add("Error messages to identify failure types");
                        missingData.add("Current retry policy configuration");
                        Limitations limitations = new Limitations(assumptions, missingData);
                        
                        suggestions.add(new AISuggestion(
                            UUID.randomUUID().toString(),
                            AISuggestion.SuggestionType.RELIABILITY,
                            "High Retry Rate",
                            "Step '" + stepId + "' requires an average of " + 
                            String.format("%.1f", avgRetries) + " retries per execution (max: " + maxRetries + "). " +
                            "This suggests transient failures. Consider increasing retry attempts or backoff duration.",
                            "workflow:" + workflowId + ":step:" + stepId,
                            metadata,
                            confidenceScore,
                            explanation,
                            confidenceDetails,
                            limitations
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to analyze retry patterns: workflowId={}, error={}", workflowId, e.getMessage());
        }
        
        return suggestions;
    }
    
    /**
     * Analyze pending approvals (Phase 6 Step 2: with explainability).
     */
    private List<AISuggestion> analyzePendingApprovals(String executionId) {
        List<AISuggestion> suggestions = new ArrayList<>();
        
        try (Connection conn = databaseManager.getConnection()) {
            // Find approvals waiting for a long time
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT step_id, requested_at, " +
                "(julianday('now') - julianday(requested_at)) * 86400 as wait_seconds " +
                "FROM workflow_approval " +
                "WHERE execution_id = ? AND decision IS NULL " +
                "AND (julianday('now') - julianday(requested_at)) * 86400 > 3600"
            )) {
                stmt.setString(1, executionId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String stepId = rs.getString("step_id");
                        String requestedAt = rs.getString("requested_at");
                        double waitSeconds = rs.getDouble("wait_seconds");
                        double waitHours = waitSeconds / 3600.0;
                        
                        JsonObject metadata = new JsonObject();
                        metadata.addProperty("execution_id", executionId);
                        metadata.addProperty("step_id", stepId);
                        metadata.addProperty("wait_seconds", waitSeconds);
                        metadata.addProperty("requested_at", requestedAt);
                        
                        // Phase 6 Step 2: Generate explanation
                        List<String> reasoningSteps = new ArrayList<>();
                        reasoningSteps.add("Analyzed pending approvals for execution: " + executionId);
                        reasoningSteps.add("Queried workflow_approval table for unresolved approvals");
                        reasoningSteps.add("Step '" + stepId + "' approval requested at: " + requestedAt);
                        reasoningSteps.add("Current wait time: " + String.format("%.1f", waitHours) + " hours");
                        reasoningSteps.add("Wait time exceeds 1 hour threshold");
                        
                        JsonObject evidence = new JsonObject();
                        evidence.addProperty("execution_id", executionId);
                        evidence.addProperty("step_id", stepId);
                        evidence.addProperty("requested_at", requestedAt);
                        evidence.addProperty("wait_seconds", waitSeconds);
                        evidence.addProperty("wait_hours", waitHours);
                        evidence.addProperty("threshold_hours", 1.0);
                        evidence.addProperty("data_source", "workflow_approval");
                        evidence.addProperty("current_time", new java.sql.Timestamp(System.currentTimeMillis()).toString());
                        
                        Explanation explanation = new Explanation(
                            "Approval for step '" + stepId + "' has been pending for " + 
                            String.format("%.1f", waitHours) + " hours (requested at " + requestedAt + "). " +
                            "This exceeds the 1-hour threshold and may indicate approver unavailability or " +
                            "workflow blocking. Consider checking with the approver or setting a timeout policy.",
                            reasoningSteps,
                            evidence
                        );
                        
                        // Phase 6 Step 2: Compute confidence
                        double confidenceScore = 0.9;  // High confidence - clear time-based pattern
                        String confidenceExplanation = "High confidence: Based on explicit time calculation. " +
                            "Wait time is objectively measured from approval request timestamp.";
                        ConfidenceDetails confidenceDetails = new ConfidenceDetails(confidenceScore, confidenceExplanation);
                        
                        // Phase 6 Step 2: Identify limitations
                        List<String> assumptions = new ArrayList<>();
                        assumptions.add("1 hour is an appropriate threshold for approval wait time");
                        assumptions.add("Long wait indicates a problem (may be intentional)");
                        List<String> missingData = new ArrayList<>();
                        missingData.add("Approver availability information");
                        missingData.add("Approval workflow context");
                        Limitations limitations = new Limitations(assumptions, missingData);
                        
                        suggestions.add(new AISuggestion(
                            UUID.randomUUID().toString(),
                            AISuggestion.SuggestionType.PATTERN_DETECTION,
                            "Long-Pending Approval",
                            "Approval for step '" + stepId + "' has been pending for " + 
                            String.format("%.0f", waitHours) + " hours. " +
                            "Consider checking with the approver or setting a timeout policy.",
                            "execution:" + executionId + ":step:" + stepId,
                            metadata,
                            confidenceScore,
                            explanation,
                            confidenceDetails,
                            limitations
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to analyze pending approvals: executionId={}, error={}", executionId, e.getMessage());
        }
        
        return suggestions;
    }
    
    /**
     * Analyze stuck workflows (Phase 6 Step 2: with explainability).
     */
    private List<AISuggestion> analyzeStuckWorkflows(String executionId) {
        List<AISuggestion> suggestions = new ArrayList<>();
        
        try (Connection conn = databaseManager.getConnection()) {
            // Check if workflow has been running for a long time
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT workflow_id, (julianday('now') - julianday(started_at)) * 86400 as run_seconds " +
                "FROM workflow_execution " +
                "WHERE execution_id = ? AND status = 'running' " +
                "AND (julianday('now') - julianday(started_at)) * 86400 > 7200"
            )) {
                stmt.setString(1, executionId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String workflowId = rs.getString("workflow_id");
                        double runSeconds = rs.getDouble("run_seconds");
                        double runHours = runSeconds / 3600.0;
                        
                        JsonObject metadata = new JsonObject();
                        metadata.addProperty("execution_id", executionId);
                        metadata.addProperty("workflow_id", workflowId);
                        metadata.addProperty("run_seconds", runSeconds);
                        
                        // Phase 6 Step 2: Generate explanation
                        List<String> reasoningSteps = new ArrayList<>();
                        reasoningSteps.add("Analyzed workflow execution state for execution: " + executionId);
                        reasoningSteps.add("Queried workflow_execution table for running workflows");
                        reasoningSteps.add("Workflow execution has been running for " + String.format("%.1f", runHours) + " hours");
                        reasoningSteps.add("Run time exceeds 2 hour threshold (7200 seconds)");
                        reasoningSteps.add("Long-running execution may indicate stuck workflow or waiting step");
                        
                        JsonObject evidence = new JsonObject();
                        evidence.addProperty("execution_id", executionId);
                        evidence.addProperty("workflow_id", workflowId);
                        evidence.addProperty("run_seconds", runSeconds);
                        evidence.addProperty("run_hours", runHours);
                        evidence.addProperty("threshold_seconds", 7200);
                        evidence.addProperty("threshold_hours", 2.0);
                        evidence.addProperty("status", "running");
                        evidence.addProperty("data_source", "workflow_execution");
                        evidence.addProperty("current_time", new java.sql.Timestamp(System.currentTimeMillis()).toString());
                        
                        Explanation explanation = new Explanation(
                            "Workflow execution '" + executionId + "' has been running for " + 
                            String.format("%.1f", runHours) + " hours (exceeds 2-hour threshold). " +
                            "This may indicate a stuck workflow, a step waiting for external input, or a step " +
                            "that is taking longer than expected. Consider checking the current step status and " +
                            "whether any steps are waiting for approval or external resources.",
                            reasoningSteps,
                            evidence
                        );
                        
                        // Phase 6 Step 2: Compute confidence
                        double confidenceScore = 0.85;  // High confidence - clear time-based pattern
                        String confidenceExplanation = "High confidence: Based on explicit time calculation from execution " +
                            "start time. Run duration is objectively measured and exceeds threshold significantly.";
                        ConfidenceDetails confidenceDetails = new ConfidenceDetails(confidenceScore, confidenceExplanation);
                        
                        // Phase 6 Step 2: Identify limitations
                        List<String> assumptions = new ArrayList<>();
                        assumptions.add("2 hours is an appropriate threshold for detecting stuck workflows");
                        assumptions.add("Long run time indicates a problem (may be intentional for long-running workflows)");
                        List<String> missingData = new ArrayList<>();
                        missingData.add("Current step execution status to identify what step is running");
                        missingData.add("Step configuration to determine if long execution is expected");
                        missingData.add("External resource availability information");
                        Limitations limitations = new Limitations(assumptions, missingData);
                        
                        suggestions.add(new AISuggestion(
                            UUID.randomUUID().toString(),
                            AISuggestion.SuggestionType.PATTERN_DETECTION,
                            "Long-Running Workflow",
                            "Workflow execution has been running for " + 
                            String.format("%.1f", runHours) + " hours. " +
                            "This may indicate a stuck workflow or a step waiting for external input.",
                            "execution:" + executionId,
                            metadata,
                            confidenceScore,
                            explanation,
                            confidenceDetails,
                            limitations
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to analyze stuck workflows: executionId={}, error={}", executionId, e.getMessage());
        }
        
        return suggestions;
    }
    
    /**
     * Log suggestion for audit trail (Phase 6 Step 2: with explainability persistence).
     */
    private void logSuggestion(AISuggestion suggestion) {
        try (Connection conn = databaseManager.getConnection()) {
            Gson gson = new Gson();
            
            // Serialize explainability fields to JSON strings
            String explanationJson = null;
            String confidenceDetailsJson = null;
            String limitationsJson = null;
            String evidenceSummaryJson = null;
            
            if (suggestion.getExplanation() != null) {
                explanationJson = gson.toJson(suggestion.getExplanation().toJson());
                // Extract evidence from explanation for evidence_summary
                JsonObject evidence = suggestion.getExplanation().getEvidence();
                if (evidence != null) {
                    evidenceSummaryJson = gson.toJson(evidence);
                }
            }
            
            if (suggestion.getConfidenceDetails() != null) {
                confidenceDetailsJson = gson.toJson(suggestion.getConfidenceDetails().toJson());
            }
            
            if (suggestion.getLimitations() != null) {
                limitationsJson = gson.toJson(suggestion.getLimitations().toJson());
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO ai_suggestion_audit (suggestion_id, type, title, context, confidence, " +
                "explanation, confidence_details, limitations, evidence_summary, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)"
            )) {
                stmt.setString(1, suggestion.getSuggestionId());
                stmt.setString(2, suggestion.getType().name());
                stmt.setString(3, suggestion.getTitle());
                stmt.setString(4, suggestion.getContext());
                stmt.setDouble(5, suggestion.getConfidence());
                stmt.setString(6, explanationJson);
                stmt.setString(7, confidenceDetailsJson);
                stmt.setString(8, limitationsJson);
                stmt.setString(9, evidenceSummaryJson);
                stmt.executeUpdate();
                conn.commit();
            }
        } catch (SQLException e) {
            logger.error("Failed to log AI suggestion: suggestionId={}, error={}", 
                suggestion.getSuggestionId(), e.getMessage());
        }
    }
}

