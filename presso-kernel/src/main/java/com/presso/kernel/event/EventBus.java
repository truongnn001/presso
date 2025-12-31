/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: EventBus.java
 * RESPONSIBILITY: Internal publish-subscribe messaging for decoupled components
 * 
 * ARCHITECTURAL ROLE:
 * - Enables loose coupling between kernel components
 * - Provides async event distribution
 * - Supports multiple subscribers per event type
 * - No business logic - pure infrastructure
 * 
 * EVENT NAMING CONVENTION:
 * - Dot-separated hierarchy: "category.action"
 * - Examples: "lifecycle.ready", "task.completed", "engine.crashed"
 * 
 * BOUNDARIES:
 * - Internal kernel use only
 * - Does NOT cross process boundaries
 * - Does NOT replace IPC for UI communication
 * 
 * Reference: PROJECT_DOCUMENTATION.md Section 4.2 (EventBus component)
 */
package com.presso.kernel.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Simple publish-subscribe event bus for internal kernel communication.
 * <p>
 * Provides loose coupling between kernel components through asynchronous
 * event delivery. Events are delivered to subscribers in the order of
 * subscription.
 * </p>
 */
public final class EventBus {
    
    private static final Logger logger = LoggerFactory.getLogger(EventBus.class);
    
    /**
     * Event wrapper containing event type and payload.
     * 
     * @param type the event type (dot-separated name)
     * @param payload the event payload (can be null)
     * @param timestamp when the event was published
     */
    public record Event(String type, Object payload, long timestamp) {
        public Event(String type, Object payload) {
            this(type, payload, System.currentTimeMillis());
        }
    }
    
    /**
     * Subscription handle for unsubscribing.
     */
    public interface Subscription {
        void unsubscribe();
    }
    
    // Subscriber storage: event type -> list of handlers
    private final Map<String, List<Consumer<Event>>> subscribers = new ConcurrentHashMap<>();
    
    // Wildcard subscribers (receive all events)
    private final List<Consumer<Event>> wildcardSubscribers = new CopyOnWriteArrayList<>();
    
    // Async event delivery executor
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    
    // Statistics
    private volatile long eventsPublished = 0;
    
    /**
     * Construct an EventBus.
     */
    public EventBus() {
        logger.debug("EventBus created");
    }
    
    /**
     * Subscribe to events of a specific type.
     * 
     * @param eventType the event type to subscribe to
     * @param handler the handler to invoke when events occur
     * @return a subscription handle for unsubscribing
     */
    public Subscription subscribe(String eventType, Consumer<Event> handler) {
        List<Consumer<Event>> handlers = subscribers.computeIfAbsent(
            eventType, k -> new CopyOnWriteArrayList<>()
        );
        handlers.add(handler);
        
        logger.debug("Subscriber added for event type: {}", eventType);
        
        // Return subscription handle
        return () -> {
            handlers.remove(handler);
            logger.debug("Subscriber removed for event type: {}", eventType);
        };
    }
    
    /**
     * Subscribe to all events (wildcard subscription).
     * 
     * @param handler the handler to invoke for all events
     * @return a subscription handle for unsubscribing
     */
    public Subscription subscribeAll(Consumer<Event> handler) {
        wildcardSubscribers.add(handler);
        logger.debug("Wildcard subscriber added");
        
        return () -> {
            wildcardSubscribers.remove(handler);
            logger.debug("Wildcard subscriber removed");
        };
    }
    
    /**
     * Publish an event asynchronously.
     * 
     * @param eventType the event type
     * @param payload the event payload (can be null)
     */
    public void publish(String eventType, Object payload) {
        Event event = new Event(eventType, payload);
        eventsPublished++;
        
        logger.debug("Publishing event: type={}", eventType);
        
        // Deliver to type-specific subscribers
        List<Consumer<Event>> handlers = subscribers.get(eventType);
        if (handlers != null) {
            for (Consumer<Event> handler : handlers) {
                deliverAsync(handler, event);
            }
        }
        
        // Deliver to wildcard subscribers
        for (Consumer<Event> handler : wildcardSubscribers) {
            deliverAsync(handler, event);
        }
    }
    
    /**
     * Publish an event synchronously (for testing or critical events).
     * 
     * @param eventType the event type
     * @param payload the event payload
     */
    public void publishSync(String eventType, Object payload) {
        Event event = new Event(eventType, payload);
        eventsPublished++;
        
        logger.debug("Publishing event (sync): type={}", eventType);
        
        // Deliver to type-specific subscribers
        List<Consumer<Event>> handlers = subscribers.get(eventType);
        if (handlers != null) {
            for (Consumer<Event> handler : handlers) {
                deliverSync(handler, event);
            }
        }
        
        // Deliver to wildcard subscribers
        for (Consumer<Event> handler : wildcardSubscribers) {
            deliverSync(handler, event);
        }
    }
    
    /**
     * Deliver event asynchronously to a handler.
     */
    private void deliverAsync(Consumer<Event> handler, Event event) {
        executor.submit(() -> deliverSync(handler, event));
    }
    
    /**
     * Deliver event synchronously to a handler.
     */
    private void deliverSync(Consumer<Event> handler, Event event) {
        try {
            handler.accept(event);
        } catch (Exception e) {
            logger.error("Error delivering event {}: {}", event.type(), e.getMessage());
        }
    }
    
    /**
     * Get the total number of events published.
     * 
     * @return the event count
     */
    public long getEventsPublished() {
        return eventsPublished;
    }
    
    /**
     * Shutdown the event bus executor.
     * Call during kernel shutdown.
     */
    public void shutdown() {
        executor.shutdown();
        logger.debug("EventBus shutdown");
    }
}

