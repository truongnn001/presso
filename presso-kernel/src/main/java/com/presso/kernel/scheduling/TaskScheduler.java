/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: TaskScheduler.java
 * RESPONSIBILITY: Task queue management and execution scheduling
 * 
 * ARCHITECTURAL ROLE:
 * - Receives tasks from IPC messages
 * - Manages priority queue for pending tasks
 * - Controls concurrent execution limits
 * - Dispatches tasks to ModuleRouter
 * - Tracks task status (pending, running, completed, failed)
 * 
 * TASK LIFECYCLE:
 * 1. SUBMITTED: Task received from IPC
 * 2. QUEUED: Added to priority queue
 * 3. DISPATCHED: Sent to appropriate engine
 * 4. COMPLETED/FAILED: Result returned
 * 
 * BOUNDARIES:
 * - Does NOT contain business logic
 * - Does NOT directly communicate with engines
 * - Delegates routing to ModuleRouter
 * 
 * Reference: PROJECT_DOCUMENTATION.md Section 4.2 (TaskScheduler component)
 */
package com.presso.kernel.scheduling;

import com.presso.kernel.routing.ModuleRouter;
import com.presso.kernel.event.EventBus;
import com.presso.kernel.ipc.IpcMessage;
import com.presso.kernel.ipc.KernelResponse;
import com.presso.kernel.persistence.ExecutionHistoryService;
import com.presso.kernel.persistence.ContractService;
import com.presso.kernel.persistence.ContractService.Contract;
import com.presso.kernel.persistence.ContractService.PaymentStage;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages task scheduling and execution for the Kernel.
 * <p>
 * Tasks are queued and processed in FIFO order with priority support.
 * Concurrent execution is controlled per engine type.
 * </p>
 */
public final class TaskScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(TaskScheduler.class);
    
    /**
     * Task status enumeration.
     */
    public enum TaskStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
    
    /**
     * Internal task record.
     */
    private record QueuedTask(
        IpcMessage message,
        Consumer<KernelResponse> callback,
        long submittedAt
    ) {}
    
    private final ModuleRouter moduleRouter;
    private final EventBus eventBus;
    private final ExecutionHistoryService executionHistory;
    private final ContractService contractService;
    
    // Task queue - unbounded for Phase 1, will add capacity limits in Phase 2
    private final BlockingQueue<QueuedTask> taskQueue = new LinkedBlockingQueue<>();
    
    // Track execution IDs for tasks
    private final ConcurrentHashMap<String, Long> taskExecutionIds = new ConcurrentHashMap<>();
    
    // Scheduler state
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread workerThread;
    
    /**
     * Construct a TaskScheduler with required dependencies.
     * 
     * @param moduleRouter the router for dispatching tasks
     * @param eventBus the event bus for task events
     * @param executionHistory the execution history service (may be null)
     * @param contractService the contract service (may be null)
     */
    public TaskScheduler(ModuleRouter moduleRouter, EventBus eventBus, 
                        ExecutionHistoryService executionHistory, ContractService contractService) {
        this.moduleRouter = moduleRouter;
        this.eventBus = eventBus;
        this.executionHistory = executionHistory;
        this.contractService = contractService;
        logger.debug("TaskScheduler created");
    }
    
    /**
     * Start the task scheduler worker thread.
     */
    public void start() {
        if (running.get()) {
            logger.warn("TaskScheduler already running");
            return;
        }
        
        running.set(true);
        workerThread = Thread.ofVirtual().name("task-scheduler-worker").start(this::processQueue);
        logger.info("TaskScheduler started");
    }
    
    /**
     * Stop the task scheduler gracefully.
     */
    public void stop() {
        if (!running.get()) {
            return;
        }
        
        running.set(false);
        
        // Interrupt worker thread if waiting
        if (workerThread != null) {
            workerThread.interrupt();
        }
        
        logger.info("TaskScheduler stopped, {} tasks remaining in queue", taskQueue.size());
    }
    
    /**
     * Submit a task for execution.
     * 
     * @param message the IPC message containing the task
     * @param callback the callback for the response
     */
    public void submitTask(IpcMessage message, Consumer<KernelResponse> callback) {
        if (!running.get()) {
            callback.accept(KernelResponse.error(
                message.getId(),
                "SCHEDULER_STOPPED",
                "Task scheduler is not accepting tasks"
            ));
            return;
        }
        
        QueuedTask task = new QueuedTask(message, callback, System.currentTimeMillis());
        taskQueue.offer(task);
        
        logger.debug("Task queued: id={}, type={}, queueSize={}",
            message.getId(), message.getType(), taskQueue.size());
        
        eventBus.publish("task.queued", message.getId());
    }
    
    /**
     * Worker thread main loop - processes tasks from queue.
     */
    private void processQueue() {
        logger.debug("Task processing loop started");
        
        while (running.get()) {
            try {
                // Block waiting for next task
                QueuedTask task = taskQueue.take();
                processTask(task);
                
            } catch (InterruptedException e) {
                if (!running.get()) {
                    logger.debug("Task processing interrupted for shutdown");
                    break;
                }
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Error processing task: {}", e.getMessage());
            }
        }
        
        logger.debug("Task processing loop ended");
    }
    
    /**
     * Process a single task.
     * 
     * @param task the task to process
     */
    private void processTask(QueuedTask task) {
        IpcMessage message = task.message();
        String taskId = message.getId();
        String operationType = message.getType();
        
        logger.debug("Processing task: id={}", taskId);
        eventBus.publish("task.started", taskId);
        
        // Record task start
        long executionId = -1;
        if (executionHistory != null) {
            String module = extractModule(operationType);
            String inputSummary = createInputSummary(message);
            executionId = executionHistory.recordTaskStart(taskId, operationType, module, inputSummary);
            if (executionId >= 0) {
                taskExecutionIds.put(taskId, executionId);
            }
        }
        
        try {
            // Update status to running
            if (executionId >= 0 && executionHistory != null) {
                executionHistory.recordTaskRunning(executionId, taskId);
            }
            
            // Route and execute task
            KernelResponse response = moduleRouter.route(message);
            task.callback().accept(response);
            
            // Record completion
            if (executionId >= 0 && executionHistory != null) {
                String outputSummary = createOutputSummary(response);
                executionHistory.recordTaskCompleted(executionId, taskId, outputSummary);
            }
            
            // Persist contract data if present in task (Phase 3 Step 3)
            if (executionId >= 0 && contractService != null) {
                persistContractDataIfPresent(message, response, executionId);
            }
            
            eventBus.publish("task.completed", taskId);
            logger.debug("Task completed: id={}", taskId);
            
        } catch (Exception e) {
            logger.error("Task failed: id={}, error={}", taskId, e.getMessage());
            
            // Record failure
            if (executionId >= 0 && executionHistory != null) {
                executionHistory.recordTaskFailed(executionId, taskId, e.getMessage());
            }
            
            task.callback().accept(KernelResponse.error(
                taskId,
                "TASK_FAILED",
                e.getMessage()
            ));
            eventBus.publish("task.failed", taskId);
        } finally {
            // Clean up execution ID tracking
            taskExecutionIds.remove(taskId);
        }
    }
    
    /**
     * Extract module name from operation type.
     * 
     * @param operationType the operation type
     * @return module name (excel, pdf, image, etc.)
     */
    private String extractModule(String operationType) {
        if (operationType == null) {
            return "unknown";
        }
        
        String op = operationType.toUpperCase();
        
        // Excel operations
        if (op.startsWith("EXPORT_EXCEL") || op.contains("EXCEL")) {
            return "excel";
        }
        
        // PDF operations
        if (op.startsWith("PDF_")) {
            return "pdf";
        }
        
        // Image operations
        if (op.startsWith("IMAGE_")) {
            return "image";
        }
        
        // Template operations
        if (op.contains("TEMPLATE")) {
            return "template";
        }
        
        // Default
        return "unknown";
    }
    
    /**
     * Create input summary from message.
     * 
     * @param message the IPC message
     * @return input summary string
     */
    private String createInputSummary(IpcMessage message) {
        try {
            JsonObject payload = message.getPayload();
            if (payload != null) {
                // Create a brief summary (truncate if needed)
                return payload.toString();
            }
            return "No input data";
        } catch (Exception e) {
            return "Error creating input summary: " + e.getMessage();
        }
    }
    
    /**
     * Create output summary from response.
     * 
     * @param response the kernel response
     * @return output summary string
     */
    private String createOutputSummary(KernelResponse response) {
        try {
            if (response.isSuccess()) {
                Object result = response.getResult();
                if (result != null) {
                    return result.toString();
                }
                return "Success";
            } else {
                return "Error: " + response.getErrorCode() + " - " + response.getErrorMessage();
            }
        } catch (Exception e) {
            return "Error creating output summary: " + e.getMessage();
        }
    }
    
    /**
     * Persist contract data if present in task input/output.
     * This is called when a document generation task completes.
     * 
     * @param message the original IPC message
     * @param response the task response
     * @param executionId the execution history ID
     */
    private void persistContractDataIfPresent(IpcMessage message, KernelResponse response, long executionId) {
        try {
            // Check if contract data is in the message payload
            JsonObject payload = message.getPayload();
            if (payload == null || !payload.has("contract")) {
                return;
            }
            
            JsonObject contractJson = payload.getAsJsonObject("contract");
            if (contractJson == null) {
                return;
            }
            
            // Extract contract data
            Contract contract = extractContractFromJson(contractJson);
            if (contract == null) {
                logger.debug("No valid contract data found in task");
                return;
            }
            
            // Persist contract (create or update)
            Long contractId = null;
            if (contract.getId() != null) {
                // Update existing contract
                if (contractService.updateContract(contract)) {
                    contractId = contract.getId();
                }
            } else {
                // Create new contract
                try {
                    Contract created = contractService.createContract(contract);
                    contractId = created.getId();
                } catch (Exception e) {
                    logger.error("Failed to create contract: {}", e.getMessage());
                    return;
                }
            }
            
            // Link contract to execution history
            if (contractId != null && executionHistory != null) {
                executionHistory.linkContractToExecution(executionId, contractId);
                logger.debug("Persisted and linked contract: contractId={}, executionId={}", contractId, executionId);
            }
            
        } catch (Exception e) {
            // Fail-safe: log error but don't throw
            logger.error("Failed to persist contract data: executionId={}, error={}", executionId, e.getMessage());
        }
    }
    
    /**
     * Extract contract data from JSON.
     * 
     * @param contractJson the contract JSON object
     * @return Contract object, or null if invalid
     */
    private Contract extractContractFromJson(JsonObject contractJson) {
        try {
            Contract contract = new Contract();
            
            // Extract contract fields
            if (contractJson.has("id")) {
                JsonElement idElem = contractJson.get("id");
                if (!idElem.isJsonNull()) {
                    contract.setId(idElem.getAsLong());
                }
            }
            
            if (contractJson.has("contract_number")) {
                contract.setContractNumber(contractJson.get("contract_number").getAsString());
            }
            
            if (contractJson.has("name")) {
                contract.setName(contractJson.get("name").getAsString());
            } else {
                // Name is required
                return null;
            }
            
            if (contractJson.has("signed_date")) {
                String dateStr = contractJson.get("signed_date").getAsString();
                if (dateStr != null && !dateStr.isEmpty()) {
                    contract.setSignedDate(java.time.LocalDate.parse(dateStr));
                }
            }
            
            if (contractJson.has("buyer_company")) {
                contract.setBuyerCompany(contractJson.get("buyer_company").getAsString());
            }
            
            if (contractJson.has("buyer_tax_code")) {
                contract.setBuyerTaxCode(contractJson.get("buyer_tax_code").getAsString());
            }
            
            // Extract payment stages
            if (contractJson.has("payment_stages")) {
                JsonArray stagesArray = contractJson.getAsJsonArray("payment_stages");
                List<PaymentStage> stages = new ArrayList<>();
                
                for (JsonElement stageElem : stagesArray) {
                    if (stageElem.isJsonObject()) {
                        JsonObject stageJson = stageElem.getAsJsonObject();
                        PaymentStage stage = new PaymentStage();
                        
                        if (stageJson.has("stage_name")) {
                            stage.setStageName(stageJson.get("stage_name").getAsString());
                        } else {
                            continue; // Skip invalid stage
                        }
                        
                        if (stageJson.has("price_before_vat")) {
                            stage.setPriceBeforeVat(stageJson.get("price_before_vat").getAsDouble());
                        }
                        
                        if (stageJson.has("vat_rate")) {
                            stage.setVatRate(stageJson.get("vat_rate").getAsDouble());
                        }
                        
                        if (stageJson.has("vat_amount")) {
                            stage.setVatAmount(stageJson.get("vat_amount").getAsDouble());
                        }
                        
                        if (stageJson.has("price_after_vat")) {
                            stage.setPriceAfterVat(stageJson.get("price_after_vat").getAsDouble());
                        }
                        
                        if (stageJson.has("sequence_order")) {
                            stage.setSequenceOrder(stageJson.get("sequence_order").getAsInt());
                        }
                        
                        stages.add(stage);
                    }
                }
                
                contract.setPaymentStages(stages);
            }
            
            return contract;
            
        } catch (Exception e) {
            logger.error("Failed to extract contract from JSON: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Get the current queue size.
     * 
     * @return number of pending tasks
     */
    public int getQueueSize() {
        return taskQueue.size();
    }
    
    /**
     * Check if the scheduler is running.
     * 
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }
}

