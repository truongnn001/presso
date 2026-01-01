/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: WorkflowTriggerService.java
 * RESPONSIBILITY: Workflow trigger management (manual + event-based)
 * 
 * ARCHITECTURAL ROLE:
 * - Manages workflow trigger configurations
 * - Subscribes to EventBus events
 * - Triggers workflows based on internal events
 * - NO external triggers (webhooks, APIs)
 * 
 * Reference: PROJECT_DOCUMENTATION.md Phase 5 Step 2
 */
package com.presso.kernel.workflow;

import com.presso.kernel.event.EventBus;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages workflow triggers (manual and event-based).
 */
public final class WorkflowTriggerService {
    
    private static final Logger logger = LoggerFactory.getLogger(WorkflowTriggerService.class);
    
    private final WorkflowEngine workflowEngine;
    private final EventBus eventBus;
    
    // Trigger configurations: eventType -> workflowId
    private final Map<String, String> eventTriggers = new ConcurrentHashMap<>();
    
    // EventBus subscriptions
    private EventBus.Subscription eventSubscription;
    
    /**
     * Construct a WorkflowTriggerService.
     * 
     * @param workflowEngine the workflow engine
     * @param eventBus the event bus
     */
    public WorkflowTriggerService(WorkflowEngine workflowEngine, EventBus eventBus) {
        this.workflowEngine = workflowEngine;
        this.eventBus = eventBus;
        logger.info("WorkflowTriggerService created");
    }
    
    /**
     * Start the trigger service (subscribe to events).
     */
    public void start() {
        // Subscribe to all events to check for triggers
        eventSubscription = eventBus.subscribeAll(this::handleEvent);
        logger.info("WorkflowTriggerService started, subscribed to EventBus");
    }
    
    /**
     * Stop the trigger service (unsubscribe from events).
     */
    public void stop() {
        if (eventSubscription != null) {
            eventSubscription.unsubscribe();
            eventSubscription = null;
        }
        logger.info("WorkflowTriggerService stopped");
    }
    
    /**
     * Register an event-based trigger.
     * 
     * @param eventType the event type to trigger on (e.g., "contract.created")
     * @param workflowId the workflow to trigger
     */
    public void registerEventTrigger(String eventType, String workflowId) {
        eventTriggers.put(eventType, workflowId);
        logger.info("Registered event trigger: eventType={}, workflowId={}", eventType, workflowId);
    }
    
    /**
     * Unregister an event-based trigger.
     * 
     * @param eventType the event type
     */
    public void unregisterEventTrigger(String eventType) {
        eventTriggers.remove(eventType);
        logger.info("Unregistered event trigger: eventType={}", eventType);
    }
    
    /**
     * Handle an event from EventBus.
     * 
     * @param event the event
     */
    private void handleEvent(EventBus.Event event) {
        String eventType = event.type();
        String workflowId = eventTriggers.get(eventType);
        
        if (workflowId != null) {
            logger.info("Event trigger fired: eventType={}, workflowId={}", eventType, workflowId);
            
            // Build initial context from event payload
            JsonObject initialContext = new JsonObject();
            if (event.payload() != null) {
                // Try to extract useful data from event payload
                if (event.payload() instanceof String) {
                    initialContext.addProperty("event_data", (String) event.payload());
                } else if (event.payload() instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payloadMap = (Map<String, Object>) event.payload();
                    for (Map.Entry<String, Object> entry : payloadMap.entrySet()) {
                        if (entry.getValue() instanceof String) {
                            initialContext.addProperty(entry.getKey(), (String) entry.getValue());
                        } else if (entry.getValue() instanceof Number) {
                            initialContext.addProperty(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                        }
                    }
                }
            }
            initialContext.addProperty("trigger_event", eventType);
            initialContext.addProperty("trigger_timestamp", event.timestamp());
            
            // Trigger workflow
            try {
                String executionId = workflowEngine.startWorkflow(workflowId, initialContext);
                logger.info("Workflow triggered: executionId={}, workflowId={}, eventType={}", 
                    executionId, workflowId, eventType);
            } catch (Exception e) {
                logger.error("Failed to trigger workflow: workflowId={}, eventType={}, error={}", 
                    workflowId, eventType, e.getMessage());
            }
        }
    }
    
    /**
     * Get all registered event triggers.
     * 
     * @return map of event type -> workflow ID
     */
    public Map<String, String> getEventTriggers() {
        return new ConcurrentHashMap<>(eventTriggers);
    }
}

