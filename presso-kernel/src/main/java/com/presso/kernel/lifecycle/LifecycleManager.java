/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: LifecycleManager.java
 * RESPONSIBILITY: Application lifecycle management
 * 
 * ARCHITECTURAL ROLE:
 * - Manages startup, idle, and shutdown phases
 * - Supervises engine processes (health checks, restart)
 * - Coordinates graceful shutdown sequence
 * - Emits lifecycle events for other components
 * 
 * LIFECYCLE PHASES (per PROJECT_DOCUMENTATION.md Section 7):
 * 1. STARTUP: Initialize → Load config → Start engines → Ready
 * 2. IDLE: Monitor health → Handle events → Wait for work
 * 3. SHUTDOWN: Stop accepting → Drain queue → Stop engines → Cleanup
 * 
 * BOUNDARIES:
 * - Does NOT make external network calls
 * - Does NOT perform business logic
 * - Delegates actual work to specialized managers
 */
package com.presso.kernel.lifecycle;

import com.presso.kernel.event.EventBus;
import com.presso.kernel.state.StateManager;
import com.presso.kernel.engine.EngineProcessManager;
import com.presso.kernel.scheduling.TaskScheduler;
import com.presso.kernel.persistence.ActivityLogService;
import com.presso.kernel.persistence.ActivityLogService.ActivityAction;
import com.presso.kernel.persistence.ActivityLogService.ActivityEvent;
import com.presso.kernel.persistence.ActivityLogService.Severity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the overall lifecycle of the PressO Kernel.
 * <p>
 * Coordinates startup and shutdown sequences, ensuring proper
 * initialization order and graceful termination of all components.
 * </p>
 */
public final class LifecycleManager {
    
    private static final Logger logger = LoggerFactory.getLogger(LifecycleManager.class);
    
    /**
     * Lifecycle states for the kernel.
     */
    public enum State {
        CREATED,
        STARTING,
        RUNNING,
        SHUTTING_DOWN,
        TERMINATED
    }
    
    private final EventBus eventBus;
    private final StateManager stateManager;
    private final EngineProcessManager engineProcessManager;
    private final TaskScheduler taskScheduler;
    private final ActivityLogService activityLog;
    
    private volatile State currentState = State.CREATED;
    
    /**
     * Construct a LifecycleManager with required dependencies.
     * 
     * @param eventBus the event bus for lifecycle events
     * @param stateManager the state manager for configuration
     * @param engineProcessManager the engine process manager
     * @param taskScheduler the task scheduler
     * @param activityLog the activity log service (may be null)
     */
    public LifecycleManager(
            EventBus eventBus,
            StateManager stateManager,
            EngineProcessManager engineProcessManager,
            TaskScheduler taskScheduler,
            ActivityLogService activityLog) {
        this.eventBus = eventBus;
        this.stateManager = stateManager;
        this.engineProcessManager = engineProcessManager;
        this.taskScheduler = taskScheduler;
        this.activityLog = activityLog;
        
        // Subscribe to task events for activity logging
        if (activityLog != null) {
            eventBus.subscribe("task.started", (busEvent) -> {
                Object payload = busEvent.payload();
                if (payload instanceof String taskId) {
                    ActivityEvent event = new ActivityEvent.Builder(ActivityAction.TASK_STARTED)
                        .severity(Severity.INFO)
                        .entity("task", null)
                        .shortMessage("Task started: " + taskId)
                        .build();
                    activityLog.recordActivity(event);
                }
            });
            
            eventBus.subscribe("task.completed", (busEvent) -> {
                Object payload = busEvent.payload();
                if (payload instanceof String taskId) {
                    ActivityEvent event = new ActivityEvent.Builder(ActivityAction.TASK_COMPLETED)
                        .severity(Severity.INFO)
                        .entity("task", null)
                        .shortMessage("Task completed: " + taskId)
                        .build();
                    activityLog.recordActivity(event);
                }
            });
            
            eventBus.subscribe("task.failed", (busEvent) -> {
                Object payload = busEvent.payload();
                if (payload instanceof String taskId) {
                    ActivityEvent event = new ActivityEvent.Builder(ActivityAction.TASK_FAILED)
                        .severity(Severity.ERROR)
                        .entity("task", null)
                        .shortMessage("Task failed: " + taskId)
                        .build();
                    activityLog.recordActivity(event);
                }
            });
        }
        
        logger.debug("LifecycleManager created");
    }
    
    /**
     * Execute the startup sequence.
     * <p>
     * Startup order:
     * 1. Load configuration from StateManager
     * 2. Initialize database connection
     * 3. Start engine processes
     * 4. Start task scheduler
     * 5. Emit READY event
     * </p>
     * 
     * @throws Exception if startup fails
     */
    public void startup() throws Exception {
        if (currentState != State.CREATED) {
            throw new IllegalStateException("Cannot start from state: " + currentState);
        }
        
        currentState = State.STARTING;
        logger.info("Kernel startup sequence initiated");
        
        // Record startup activity
        if (activityLog != null) {
            ActivityEvent event = new ActivityEvent.Builder(ActivityAction.SYSTEM_STARTUP)
                .severity(Severity.INFO)
                .shortMessage("Kernel startup initiated")
                .build();
            activityLog.recordActivity(event);
        }
        
        try {
            // Step 1: Load configuration
            logger.debug("Loading configuration...");
            stateManager.loadConfiguration();
            
            // Step 2: Initialize database
            // Database initialization is handled by KernelMain before LifecycleManager
            logger.debug("Database should already be initialized");
            
            // Step 3: Start engine processes
            logger.debug("Starting engine processes...");
            engineProcessManager.startAllEngines();
            
            // Step 4: Start task scheduler
            logger.debug("Starting task scheduler...");
            taskScheduler.start();
            
            // Step 5: Transition to running state
            currentState = State.RUNNING;
            eventBus.publish("lifecycle.ready", null);
            
            logger.info("Kernel startup complete, state=RUNNING");
            
        } catch (Exception e) {
            logger.error("Startup failed: {}", e.getMessage());
            currentState = State.TERMINATED;
            throw e;
        }
    }
    
    /**
     * Execute the graceful shutdown sequence.
     * <p>
     * Shutdown order:
     * 1. Stop accepting new tasks
     * 2. Drain task queue (wait for completion)
     * 3. Stop engine processes
     * 4. Close database connection
     * 5. Final cleanup
     * </p>
     */
    public void shutdown() {
        if (currentState == State.TERMINATED || currentState == State.SHUTTING_DOWN) {
            logger.warn("Shutdown already in progress or completed");
            return;
        }
        
        currentState = State.SHUTTING_DOWN;
        logger.info("Kernel shutdown sequence initiated");
        
        // Record shutdown activity
        if (activityLog != null) {
            ActivityEvent event = new ActivityEvent.Builder(ActivityAction.SYSTEM_SHUTDOWN)
                .severity(Severity.INFO)
                .shortMessage("Kernel shutdown initiated")
                .build();
            activityLog.recordActivity(event);
        }
        
        try {
            // Step 1: Stop task scheduler (stops accepting new tasks)
            logger.debug("Stopping task scheduler...");
            taskScheduler.stop();
            
            // Step 2: Drain task queue
            // TODO (Phase 2): Implement queue draining with timeout
            logger.debug("Draining task queue placeholder");
            
            // Step 3: Stop engine processes
            logger.debug("Stopping engine processes...");
            engineProcessManager.stopAllEngines();
            
            // Step 4: Close database
            // Database shutdown is handled by KernelMain after LifecycleManager
            logger.debug("Database will be closed by KernelMain");
            
            // Step 5: Final cleanup
            stateManager.saveConfiguration();
            eventBus.publish("lifecycle.shutdown", null);
            
            currentState = State.TERMINATED;
            logger.info("Kernel shutdown complete, state=TERMINATED");
            
        } catch (Exception e) {
            logger.error("Error during shutdown: {}", e.getMessage());
            currentState = State.TERMINATED;
        }
    }
    
    /**
     * Get the current lifecycle state.
     * 
     * @return the current state
     */
    public State getCurrentState() {
        return currentState;
    }
    
    /**
     * Check if the kernel is in a running state.
     * 
     * @return true if running
     */
    public boolean isRunning() {
        return currentState == State.RUNNING;
    }
}

