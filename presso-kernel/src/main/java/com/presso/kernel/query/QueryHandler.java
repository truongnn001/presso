/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: QueryHandler.java
 * RESPONSIBILITY: Handle read-only IPC queries for persisted data
 * 
 * ARCHITECTURAL ROLE:
 * - Exposes query capabilities via IPC
 * - Validates query parameters
 * - Returns paginated results
 * - Fail-safe: query failures don't crash kernel
 * 
 * BOUNDARIES:
 * - Read-only operations (no data mutation)
 * - NO business logic
 * - NO UI rendering
 * - Validates all parameters strictly
 * 
 * Reference: PROJECT_DOCUMENTATION.md Section 4.2
 */
package com.presso.kernel.query;

import com.presso.kernel.persistence.ContractService;
import com.presso.kernel.persistence.ExecutionHistoryService;
import com.presso.kernel.persistence.ActivityLogService;
import com.presso.kernel.persistence.ContractService.Contract;
import com.presso.kernel.persistence.ContractService.ContractQueryParams;
import com.presso.kernel.persistence.ExecutionHistoryService.ExecutionHistoryEntry;
import com.presso.kernel.persistence.ExecutionHistoryService.ExecutionQueryParams;
import com.presso.kernel.persistence.ActivityLogService.ActivityLogEntry;
import com.presso.kernel.persistence.ActivityLogService.QueryParams;
import com.presso.kernel.persistence.ActivityLogService.ActivityAction;
import com.presso.kernel.persistence.ActivityLogService.Severity;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Handles read-only IPC queries for persisted data.
 */
public final class QueryHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(QueryHandler.class);
    
    private final ContractService contractService;
    private final ExecutionHistoryService executionHistory;
    private final ActivityLogService activityLog;
    
    /**
     * Construct a QueryHandler.
     * 
     * @param contractService the contract service
     * @param executionHistory the execution history service
     * @param activityLog the activity log service
     */
    public QueryHandler(ContractService contractService, ExecutionHistoryService executionHistory, 
                       ActivityLogService activityLog) {
        this.contractService = contractService;
        this.executionHistory = executionHistory;
        this.activityLog = activityLog;
        logger.debug("QueryHandler created");
    }
    
    /**
     * Handle QUERY_CONTRACTS request.
     * 
     * @param params query parameters from IPC
     * @return query results
     */
    public Map<String, Object> handleQueryContracts(JsonObject params) {
        try {
            ContractQueryParams queryParams = new ContractQueryParams();
            
            // Extract query parameters
            if (params.has("contract_number")) {
                queryParams.contractNumber(params.get("contract_number").getAsString());
            }
            
            if (params.has("buyer_company")) {
                queryParams.buyerCompany(params.get("buyer_company").getAsString());
            }
            
            if (params.has("signed_date_from")) {
                String dateStr = params.get("signed_date_from").getAsString();
                queryParams.signedDateFrom(java.sql.Date.valueOf(LocalDate.parse(dateStr)));
            }
            
            if (params.has("signed_date_to")) {
                String dateStr = params.get("signed_date_to").getAsString();
                queryParams.signedDateTo(java.sql.Date.valueOf(LocalDate.parse(dateStr)));
            }
            
            if (params.has("created_from")) {
                long timestamp = params.get("created_from").getAsLong();
                queryParams.createdFrom(new Timestamp(timestamp));
            }
            
            if (params.has("created_to")) {
                long timestamp = params.get("created_to").getAsLong();
                queryParams.createdTo(new Timestamp(timestamp));
            }
            
            if (params.has("limit")) {
                queryParams.limit(params.get("limit").getAsInt());
            }
            
            if (params.has("offset")) {
                queryParams.offset(params.get("offset").getAsInt());
            }
            
            // Execute query
            List<Contract> contracts = contractService.searchContracts(queryParams);
            int totalCount = contractService.countContracts(queryParams);
            
            // Convert to JSON-serializable format
            JsonArray contractsArray = new JsonArray();
            for (Contract contract : contracts) {
                contractsArray.add(contractToJson(contract));
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("contracts", contractsArray);
            result.put("count", contracts.size());
            result.put("total", totalCount);
            result.put("limit", queryParams.getLimit());
            result.put("offset", queryParams.getOffset());
            
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to query contracts: {}", e.getMessage());
            throw new RuntimeException("Query failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handle GET_CONTRACT_BY_ID request.
     * 
     * @param params query parameters (must contain "contract_id")
     * @return contract data or null
     */
    public Map<String, Object> handleGetContractById(JsonObject params) {
        try {
            if (!params.has("contract_id")) {
                throw new IllegalArgumentException("contract_id is required");
            }
            
            long contractId = params.get("contract_id").getAsLong();
            Contract contract = contractService.getContractById(contractId);
            
            if (contract == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("contract", null);
                result.put("found", false);
                return result;
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("contract", contractToJson(contract));
            result.put("found", true);
            
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to get contract by ID: {}", e.getMessage());
            throw new RuntimeException("Query failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handle QUERY_EXECUTION_HISTORY request.
     * 
     * @param params query parameters
     * @return query results
     */
    public Map<String, Object> handleQueryExecutionHistory(JsonObject params) {
        try {
            ExecutionQueryParams queryParams = new ExecutionQueryParams();
            
            // Extract query parameters
            if (params.has("operation_type")) {
                queryParams.operationType(params.get("operation_type").getAsString());
            }
            
            if (params.has("module")) {
                queryParams.module(params.get("module").getAsString());
            }
            
            if (params.has("status")) {
                String statusStr = params.get("status").getAsString().toUpperCase();
                try {
                    queryParams.status(ExecutionHistoryService.ExecutionStatus.valueOf(statusStr));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid status: " + statusStr);
                }
            }
            
            if (params.has("from_time")) {
                long timestamp = params.get("from_time").getAsLong();
                queryParams.fromTime(new Timestamp(timestamp));
            }
            
            if (params.has("to_time")) {
                long timestamp = params.get("to_time").getAsLong();
                queryParams.toTime(new Timestamp(timestamp));
            }
            
            if (params.has("contract_id")) {
                queryParams.contractId(params.get("contract_id").getAsLong());
            }
            
            if (params.has("limit")) {
                queryParams.limit(params.get("limit").getAsInt());
            }
            
            if (params.has("offset")) {
                queryParams.offset(params.get("offset").getAsInt());
            }
            
            // Execute query
            List<ExecutionHistoryEntry> entries = executionHistory.queryExecutionHistory(queryParams);
            int totalCount = executionHistory.countExecutionHistory(queryParams);
            
            // Convert to JSON-serializable format
            JsonArray entriesArray = new JsonArray();
            for (ExecutionHistoryEntry entry : entries) {
                entriesArray.add(executionHistoryToJson(entry));
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("executions", entriesArray);
            result.put("count", entries.size());
            result.put("total", totalCount);
            result.put("limit", queryParams.getLimit());
            result.put("offset", queryParams.getOffset());
            
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to query execution history: {}", e.getMessage());
            throw new RuntimeException("Query failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handle QUERY_ACTIVITY_LOGS request.
     * 
     * @param params query parameters
     * @return query results
     */
    public Map<String, Object> handleQueryActivityLogs(JsonObject params) {
        try {
            QueryParams queryParams = new QueryParams();
            
            // Extract query parameters
            if (params.has("from_time")) {
                long timestamp = params.get("from_time").getAsLong();
                queryParams.fromTime(new Timestamp(timestamp));
            }
            
            if (params.has("to_time")) {
                long timestamp = params.get("to_time").getAsLong();
                queryParams.toTime(new Timestamp(timestamp));
            }
            
            if (params.has("action")) {
                String actionStr = params.get("action").getAsString().toUpperCase();
                try {
                    queryParams.action(ActivityAction.valueOf(actionStr));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid action: " + actionStr);
                }
            }
            
            if (params.has("severity")) {
                String severityStr = params.get("severity").getAsString().toUpperCase();
                try {
                    queryParams.severity(Severity.valueOf(severityStr));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid severity: " + severityStr);
                }
            }
            
            if (params.has("module")) {
                queryParams.module(params.get("module").getAsString());
            }
            
            if (params.has("related_execution_id")) {
                queryParams.relatedExecutionId(params.get("related_execution_id").getAsLong());
            }
            
            if (params.has("entity_type")) {
                queryParams.entity(params.get("entity_type").getAsString(), 
                    params.has("entity_id") ? params.get("entity_id").getAsInt() : null);
            }
            
            if (params.has("limit")) {
                queryParams.limit(params.get("limit").getAsInt());
            }
            
            if (params.has("offset")) {
                queryParams.offset(params.get("offset").getAsInt());
            }
            
            // Execute query
            List<ActivityLogEntry> entries = activityLog.queryActivityLogs(queryParams);
            int totalCount = activityLog.countActivityLogs(queryParams);
            
            // Convert to JSON-serializable format
            JsonArray entriesArray = new JsonArray();
            for (ActivityLogEntry entry : entries) {
                entriesArray.add(activityLogToJson(entry));
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("activities", entriesArray);
            result.put("count", entries.size());
            result.put("total", totalCount);
            result.put("limit", queryParams.getLimit());
            result.put("offset", queryParams.getOffset());
            
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to query activity logs: {}", e.getMessage());
            throw new RuntimeException("Query failed: " + e.getMessage(), e);
        }
    }
    
    // =========================================================================
    // Helper Methods - Convert to JSON
    // =========================================================================
    
    private JsonObject contractToJson(Contract contract) {
        JsonObject json = new JsonObject();
        json.addProperty("id", contract.getId());
        json.addProperty("contract_number", contract.getContractNumber());
        json.addProperty("name", contract.getName());
        
        if (contract.getSignedDate() != null) {
            json.addProperty("signed_date", contract.getSignedDate().toString());
        }
        
        json.addProperty("buyer_company", contract.getBuyerCompany());
        json.addProperty("buyer_tax_code", contract.getBuyerTaxCode());
        
        if (contract.getCreatedAt() != null) {
            json.addProperty("created_at", contract.getCreatedAt().getTime());
        }
        
        if (contract.getUpdatedAt() != null) {
            json.addProperty("updated_at", contract.getUpdatedAt().getTime());
        }
        
        // Include payment stages if loaded
        if (contract.getPaymentStages() != null && !contract.getPaymentStages().isEmpty()) {
            JsonArray stagesArray = new JsonArray();
            for (ContractService.PaymentStage stage : contract.getPaymentStages()) {
                JsonObject stageJson = new JsonObject();
                stageJson.addProperty("id", stage.getId());
                stageJson.addProperty("contract_id", stage.getContractId());
                stageJson.addProperty("stage_name", stage.getStageName());
                if (stage.getPriceBeforeVat() != null) {
                    stageJson.addProperty("price_before_vat", stage.getPriceBeforeVat());
                }
                if (stage.getVatRate() != null) {
                    stageJson.addProperty("vat_rate", stage.getVatRate());
                }
                if (stage.getVatAmount() != null) {
                    stageJson.addProperty("vat_amount", stage.getVatAmount());
                }
                if (stage.getPriceAfterVat() != null) {
                    stageJson.addProperty("price_after_vat", stage.getPriceAfterVat());
                }
                if (stage.getSequenceOrder() != null) {
                    stageJson.addProperty("sequence_order", stage.getSequenceOrder());
                }
                stagesArray.add(stageJson);
            }
            json.add("payment_stages", stagesArray);
        }
        
        return json;
    }
    
    private JsonObject executionHistoryToJson(ExecutionHistoryEntry entry) {
        JsonObject json = new JsonObject();
        json.addProperty("id", entry.getId());
        json.addProperty("operation_type", entry.getOperationType());
        json.addProperty("module", entry.getModule());
        
        if (entry.getStartedAt() != null) {
            json.addProperty("started_at", entry.getStartedAt().getTime());
        }
        
        if (entry.getCompletedAt() != null) {
            json.addProperty("completed_at", entry.getCompletedAt().getTime());
        }
        
        json.addProperty("status", entry.getStatus());
        json.addProperty("input_summary", entry.getInputSummary());
        json.addProperty("output_summary", entry.getOutputSummary());
        json.addProperty("error_message", entry.getErrorMessage());
        
        if (entry.getContractId() != null) {
            json.addProperty("contract_id", entry.getContractId());
        }
        
        return json;
    }
    
    private JsonObject activityLogToJson(ActivityLogEntry entry) {
        JsonObject json = new JsonObject();
        json.addProperty("id", entry.getId());
        
        if (entry.getTimestamp() != null) {
            json.addProperty("timestamp", entry.getTimestamp().getTime());
        }
        
        json.addProperty("action", entry.getAction());
        json.addProperty("severity", entry.getSeverity());
        json.addProperty("module", entry.getModule());
        json.addProperty("short_message", entry.getShortMessage());
        json.addProperty("entity_type", entry.getEntityType());
        
        if (entry.getEntityId() != null) {
            json.addProperty("entity_id", entry.getEntityId());
        }
        
        if (entry.getRelatedExecutionId() != null) {
            json.addProperty("related_execution_id", entry.getRelatedExecutionId());
        }
        
        json.addProperty("details", entry.getDetails());
        json.addProperty("metadata", entry.getMetadata());
        
        return json;
    }
}

