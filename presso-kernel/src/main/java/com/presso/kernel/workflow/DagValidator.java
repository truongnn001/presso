/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: DagValidator.java
 * RESPONSIBILITY: Validate DAG structure and detect cycles
 * 
 * ARCHITECTURAL ROLE:
 * - Validates workflow DAG structure at load time
 * - Detects cycles (forbidden in DAG)
 * - Ensures all dependencies reference valid steps
 * 
 * Reference: PROJECT_DOCUMENTATION.md Phase 5 Step 4
 */
package com.presso.kernel.workflow;

import java.util.*;

/**
 * Validates Directed Acyclic Graph (DAG) structure for workflows.
 */
public final class DagValidator {
    
    /**
     * Validate that a workflow definition forms a valid DAG.
     * 
     * @param definition workflow definition
     * @throws IllegalArgumentException if DAG is invalid (cycle detected or invalid dependency)
     */
    public static void validateDag(WorkflowDefinition definition) {
        List<StepDefinition> steps = definition.getSteps();
        
        // Build step index
        Map<String, StepDefinition> stepMap = new HashMap<>();
        for (StepDefinition step : steps) {
            stepMap.put(step.getStepId(), step);
        }
        
        // Validate all dependencies reference existing steps
        for (StepDefinition step : steps) {
            for (String depId : step.getDependsOn()) {
                if (!stepMap.containsKey(depId)) {
                    throw new IllegalArgumentException(
                        "Step '" + step.getStepId() + "' depends on non-existent step '" + depId + "'");
                }
                if (depId.equals(step.getStepId())) {
                    throw new IllegalArgumentException(
                        "Step '" + step.getStepId() + "' cannot depend on itself");
                }
            }
        }
        
        // Detect cycles using DFS
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        
        for (StepDefinition step : steps) {
            if (!visited.contains(step.getStepId())) {
                if (hasCycle(step.getStepId(), stepMap, visited, recursionStack)) {
                    throw new IllegalArgumentException(
                        "Cycle detected in workflow DAG. Workflow must be acyclic.");
                }
            }
        }
    }
    
    /**
     * Check for cycles using DFS.
     * 
     * @param stepId current step ID
     * @param stepMap map of step ID to step definition
     * @param visited set of visited nodes
     * @param recursionStack set of nodes in current recursion path
     * @return true if cycle detected
     */
    private static boolean hasCycle(String stepId, Map<String, StepDefinition> stepMap,
                                   Set<String> visited, Set<String> recursionStack) {
        visited.add(stepId);
        recursionStack.add(stepId);
        
        StepDefinition step = stepMap.get(stepId);
        if (step != null) {
            for (String depId : step.getDependsOn()) {
                if (!visited.contains(depId)) {
                    if (hasCycle(depId, stepMap, visited, recursionStack)) {
                        return true;
                    }
                } else if (recursionStack.contains(depId)) {
                    // Back edge found - cycle detected
                    return true;
                }
            }
        }
        
        recursionStack.remove(stepId);
        return false;
    }
}

