/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: DagExecutor.java
 * RESPONSIBILITY: Execute DAG workflows with parallel execution
 * 
 * ARCHITECTURAL ROLE:
 * - Performs topological sort to determine execution order
 * - Identifies runnable steps (dependencies satisfied)
 * - Executes independent steps in parallel
 * - Handles failure propagation
 * 
 * Reference: PROJECT_DOCUMENTATION.md Phase 5 Step 4
 */
package com.presso.kernel.workflow;

import com.presso.kernel.workflow.persistence.WorkflowPersistenceService;
import com.presso.kernel.event.EventBus;
import com.presso.kernel.routing.ModuleRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Executes DAG workflows with parallel execution support.
 */
public final class DagExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(DagExecutor.class);
    
    private final WorkflowPersistenceService persistenceService;
    private final EventBus eventBus;
    private final ModuleRouter moduleRouter;
    private final int maxParallelism;
    
    // Step state tracking: stepId -> StepState
    private final Map<String, StepState> stepStates = new ConcurrentHashMap<>();
    private final ReentrantLock stateLock = new ReentrantLock();
    
    // Step definitions indexed by stepId
    private final Map<String, StepDefinition> stepMap = new HashMap<>();
    
    // Dependency graph: stepId -> set of dependent step IDs
    private final Map<String, Set<String>> dependents = new HashMap<>();
    
    // In-degree count: stepId -> number of unsatisfied dependencies
    private final Map<String, Integer> inDegree = new HashMap<>();
    
    /**
     * Step execution state.
     */
    public enum StepState {
        PENDING,    // Not yet started
        RUNNING,    // Currently executing
        COMPLETED, // Successfully completed
        FAILED      // Failed (will not retry)
    }
    
    /**
     * Construct a DAG executor.
     * 
     * @param persistenceService persistence service
     * @param eventBus event bus
     * @param moduleRouter module router for step execution
     * @param maxParallelism maximum number of parallel steps
     */
    public DagExecutor(WorkflowPersistenceService persistenceService, EventBus eventBus,
                      ModuleRouter moduleRouter, int maxParallelism) {
        this.persistenceService = persistenceService;
        this.eventBus = eventBus;
        this.moduleRouter = moduleRouter;
        this.maxParallelism = maxParallelism > 0 ? maxParallelism : Integer.MAX_VALUE;
    }
    
    /**
     * Initialize DAG structure from workflow definition.
     * 
     * @param definition workflow definition
     */
    public void initializeDag(WorkflowDefinition definition) {
        stepMap.clear();
        dependents.clear();
        inDegree.clear();
        stepStates.clear();
        
        // Build step map
        for (StepDefinition step : definition.getSteps()) {
            stepMap.put(step.getStepId(), step);
            stepStates.put(step.getStepId(), StepState.PENDING);
            dependents.put(step.getStepId(), new HashSet<>());
            inDegree.put(step.getStepId(), 0);
        }
        
        // Build dependency graph
        for (StepDefinition step : definition.getSteps()) {
            for (String depId : step.getDependsOn()) {
                dependents.get(depId).add(step.getStepId());
                inDegree.put(step.getStepId(), inDegree.get(step.getStepId()) + 1);
            }
        }
        
        logger.debug("DAG initialized: {} steps, max parallelism: {}", stepMap.size(), maxParallelism);
    }
    
    /**
     * Get runnable steps (dependencies satisfied, not yet started).
     * 
     * @return set of runnable step IDs
     */
    public Set<String> getRunnableSteps() {
        stateLock.lock();
        try {
            Set<String> runnable = new HashSet<>();
            for (Map.Entry<String, StepState> entry : stepStates.entrySet()) {
                String stepId = entry.getKey();
                StepState state = entry.getValue();
                
                if (state == StepState.PENDING && inDegree.get(stepId) == 0) {
                    runnable.add(stepId);
                }
            }
            return runnable;
        } finally {
            stateLock.unlock();
        }
    }
    
    /**
     * Mark step as completed and update dependent steps.
     * 
     * @param stepId completed step ID
     */
    public void markStepCompleted(String stepId) {
        stateLock.lock();
        try {
            stepStates.put(stepId, StepState.COMPLETED);
            
            // Decrease in-degree of dependent steps
            for (String dependentId : dependents.get(stepId)) {
                int newDegree = inDegree.get(dependentId) - 1;
                inDegree.put(dependentId, newDegree);
                logger.debug("Step {} dependency satisfied, in-degree: {}", dependentId, newDegree);
            }
        } finally {
            stateLock.unlock();
        }
    }
    
    /**
     * Mark step as failed.
     * 
     * @param stepId failed step ID
     */
    public void markStepFailed(String stepId) {
        stateLock.lock();
        try {
            stepStates.put(stepId, StepState.FAILED);
            
            // Mark all dependent steps as failed (failure propagation)
            for (String dependentId : dependents.get(stepId)) {
                if (stepStates.get(dependentId) == StepState.PENDING) {
                    stepStates.put(dependentId, StepState.FAILED);
                    logger.debug("Step {} marked as failed due to dependency failure: {}", dependentId, stepId);
                }
            }
        } finally {
            stateLock.unlock();
        }
    }
    
    /**
     * Mark step as running.
     * 
     * @param stepId step ID
     */
    public void markStepRunning(String stepId) {
        stateLock.lock();
        try {
            if (stepStates.get(stepId) == StepState.PENDING) {
                stepStates.put(stepId, StepState.RUNNING);
            }
        } finally {
            stateLock.unlock();
        }
    }
    
    /**
     * Get step state.
     * 
     * @param stepId step ID
     * @return step state
     */
    public StepState getStepState(String stepId) {
        return stepStates.getOrDefault(stepId, StepState.PENDING);
    }
    
    /**
     * Check if all steps are completed.
     * 
     * @return true if all steps completed
     */
    public boolean allStepsCompleted() {
        for (StepState state : stepStates.values()) {
            if (state != StepState.COMPLETED && state != StepState.FAILED) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Check if any step failed.
     * 
     * @return true if any step failed
     */
    public boolean hasFailedSteps() {
        for (StepState state : stepStates.values()) {
            if (state == StepState.FAILED) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get maximum parallelism.
     * 
     * @return max parallelism
     */
    public int getMaxParallelism() {
        return maxParallelism;
    }
    
    /**
     * Get step definition.
     * 
     * @param stepId step ID
     * @return step definition, or null if not found
     */
    public StepDefinition getStep(String stepId) {
        return stepMap.get(stepId);
    }
    
    /**
     * Restore step states from persistence (for resumption).
     * 
     * @param executionId execution identifier
     */
    public void restoreStepStates(String executionId) {
        // Load step states from database
        for (String stepId : stepMap.keySet()) {
            String status = persistenceService.getStepStatus(executionId, stepId);
            if ("completed".equals(status)) {
                stepStates.put(stepId, StepState.COMPLETED);
                // Recalculate in-degree for dependent steps
                markStepCompleted(stepId);
            } else if ("failed".equals(status)) {
                stepStates.put(stepId, StepState.FAILED);
            } else if ("running".equals(status)) {
                // Step was running - mark as pending to retry
                stepStates.put(stepId, StepState.PENDING);
            } else {
                stepStates.put(stepId, StepState.PENDING);
            }
        }
        
        // Recalculate in-degree based on completed steps
        for (StepDefinition step : stepMap.values()) {
            int degree = step.getDependsOn().size();
            for (String depId : step.getDependsOn()) {
                if (stepStates.get(depId) == StepState.COMPLETED) {
                    degree--;
                }
            }
            inDegree.put(step.getStepId(), degree);
        }
        
        logger.debug("Step states restored for execution: {}", executionId);
    }
}

