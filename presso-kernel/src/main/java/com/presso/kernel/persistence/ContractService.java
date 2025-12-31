/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: ContractService.java
 * RESPONSIBILITY: Contract data persistence (CRUD operations)
 * 
 * ARCHITECTURAL ROLE:
 * - Provides CRUD operations for contracts and payment stages
 * - Stores raw contract data (no business logic)
 * - Links contracts to execution history
 * - Fail-safe: persistence failures don't crash kernel
 * 
 * BOUNDARIES:
 * - Does NOT contain business logic (VAT calculation, pricing rules)
 * - Does NOT perform calculations
 * - Stores only raw data as provided
 * 
 * Reference: PROJECT_DOCUMENTATION.md Section 5.2
 */
package com.presso.kernel.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for contract data persistence.
 */
public final class ContractService {
    
    private static final Logger logger = LoggerFactory.getLogger(ContractService.class);
    
    private final DatabaseManager databaseManager;
    
    /**
     * Contract data model.
     */
    public static class Contract {
        private Long id;
        private String contractNumber;
        private String name;
        private LocalDate signedDate;
        private String buyerCompany;
        private String buyerTaxCode;
        private Timestamp createdAt;
        private Timestamp updatedAt;
        private List<PaymentStage> paymentStages;
        
        public Contract() {
            this.paymentStages = new ArrayList<>();
        }
        
        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        
        public String getContractNumber() { return contractNumber; }
        public void setContractNumber(String contractNumber) { this.contractNumber = contractNumber; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public LocalDate getSignedDate() { return signedDate; }
        public void setSignedDate(LocalDate signedDate) { this.signedDate = signedDate; }
        
        public String getBuyerCompany() { return buyerCompany; }
        public void setBuyerCompany(String buyerCompany) { this.buyerCompany = buyerCompany; }
        
        public String getBuyerTaxCode() { return buyerTaxCode; }
        public void setBuyerTaxCode(String buyerTaxCode) { this.buyerTaxCode = buyerTaxCode; }
        
        public Timestamp getCreatedAt() { return createdAt; }
        public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
        
        public Timestamp getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
        
        public List<PaymentStage> getPaymentStages() { return paymentStages; }
        public void setPaymentStages(List<PaymentStage> paymentStages) { this.paymentStages = paymentStages; }
    }
    
    /**
     * Payment stage data model.
     */
    public static class PaymentStage {
        private Long id;
        private Long contractId;
        private String stageName;
        private Double priceBeforeVat;
        private Double vatRate;
        private Double vatAmount;
        private Double priceAfterVat;
        private Integer sequenceOrder;
        
        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        
        public Long getContractId() { return contractId; }
        public void setContractId(Long contractId) { this.contractId = contractId; }
        
        public String getStageName() { return stageName; }
        public void setStageName(String stageName) { this.stageName = stageName; }
        
        public Double getPriceBeforeVat() { return priceBeforeVat; }
        public void setPriceBeforeVat(Double priceBeforeVat) { this.priceBeforeVat = priceBeforeVat; }
        
        public Double getVatRate() { return vatRate; }
        public void setVatRate(Double vatRate) { this.vatRate = vatRate; }
        
        public Double getVatAmount() { return vatAmount; }
        public void setVatAmount(Double vatAmount) { this.vatAmount = vatAmount; }
        
        public Double getPriceAfterVat() { return priceAfterVat; }
        public void setPriceAfterVat(Double priceAfterVat) { this.priceAfterVat = priceAfterVat; }
        
        public Integer getSequenceOrder() { return sequenceOrder; }
        public void setSequenceOrder(Integer sequenceOrder) { this.sequenceOrder = sequenceOrder; }
    }
    
    /**
     * Construct a ContractService.
     * 
     * @param databaseManager the database manager
     */
    public ContractService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        logger.debug("ContractService created");
    }
    
    /**
     * Create a new contract with payment stages.
     * 
     * @param contract the contract to create
     * @return the created contract with ID assigned
     * @throws SQLException if creation fails
     */
    public Contract createContract(Contract contract) throws SQLException {
        if (!databaseManager.isInitialized()) {
            throw new SQLException("Database not initialized");
        }
        
        Connection conn = databaseManager.getConnection();
        try {
            conn.setAutoCommit(false);
            
            // Insert contract
            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO contracts (contract_number, name, signed_date, buyer_company, buyer_tax_code, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
                
                Timestamp now = Timestamp.from(Instant.now());
                stmt.setString(1, contract.getContractNumber());
                stmt.setString(2, contract.getName());
                stmt.setObject(3, contract.getSignedDate());
                stmt.setString(4, contract.getBuyerCompany());
                stmt.setString(5, contract.getBuyerTaxCode());
                stmt.setTimestamp(6, now);
                stmt.setTimestamp(7, now);
                
                int rows = stmt.executeUpdate();
                if (rows == 0) {
                    throw new SQLException("Failed to create contract");
                }
                
                // Get generated ID
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        contract.setId(keys.getLong(1));
                        contract.setCreatedAt(now);
                        contract.setUpdatedAt(now);
                    }
                }
            }
            
            // Insert payment stages
            if (contract.getPaymentStages() != null && !contract.getPaymentStages().isEmpty()) {
                insertPaymentStages(conn, contract.getId(), contract.getPaymentStages());
            }
            
            conn.commit();
            logger.debug("Created contract: id={}, contractNumber={}", contract.getId(), contract.getContractNumber());
            
            return contract;
            
        } catch (SQLException e) {
            conn.rollback();
            logger.error("Failed to create contract: {}", e.getMessage());
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }
    
    /**
     * Get contract by ID with payment stages.
     * 
     * @param contractId the contract ID
     * @return the contract, or null if not found
     */
    public Contract getContractById(Long contractId) {
        if (!databaseManager.isInitialized() || contractId == null) {
            return null;
        }
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT id, contract_number, name, signed_date, buyer_company, buyer_tax_code, " +
                 "created_at, updated_at FROM contracts WHERE id = ?")) {
            
            stmt.setLong(1, contractId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Contract contract = mapContract(rs);
                    
                    // Load payment stages
                    contract.setPaymentStages(getPaymentStagesByContractId(conn, contractId));
                    
                    return contract;
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to get contract by ID: id={}, error={}", contractId, e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Update an existing contract.
     * 
     * @param contract the contract to update
     * @return true if update succeeded
     */
    public boolean updateContract(Contract contract) {
        if (!databaseManager.isInitialized() || contract.getId() == null) {
            return false;
        }
        
        Connection conn = null;
        try {
            conn = databaseManager.getConnection();
            conn.setAutoCommit(false);
            
            // Update contract
            try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE contracts SET contract_number = ?, name = ?, signed_date = ?, " +
                "buyer_company = ?, buyer_tax_code = ?, updated_at = ? WHERE id = ?")) {
                
                Timestamp now = Timestamp.from(Instant.now());
                stmt.setString(1, contract.getContractNumber());
                stmt.setString(2, contract.getName());
                stmt.setObject(3, contract.getSignedDate());
                stmt.setString(4, contract.getBuyerCompany());
                stmt.setString(5, contract.getBuyerTaxCode());
                stmt.setTimestamp(6, now);
                stmt.setLong(7, contract.getId());
                
                int rows = stmt.executeUpdate();
                if (rows == 0) {
                    conn.rollback();
                    return false;
                }
                
                contract.setUpdatedAt(now);
            }
            
            // Delete existing payment stages
            try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM payment_stages WHERE contract_id = ?")) {
                stmt.setLong(1, contract.getId());
                stmt.executeUpdate();
            }
            
            // Insert updated payment stages
            if (contract.getPaymentStages() != null && !contract.getPaymentStages().isEmpty()) {
                insertPaymentStages(conn, contract.getId(), contract.getPaymentStages());
            }
            
            conn.commit();
            logger.debug("Updated contract: id={}", contract.getId());
            return true;
            
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    logger.error("Error rolling back transaction", ex);
                }
            }
            logger.error("Failed to update contract: id={}, error={}", contract.getId(), e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    logger.error("Error resetting auto-commit", e);
                }
            }
        }
    }
    
    /**
     * Delete a contract and its payment stages.
     * 
     * @param contractId the contract ID to delete
     * @return true if deletion succeeded
     */
    public boolean deleteContract(Long contractId) {
        if (!databaseManager.isInitialized() || contractId == null) {
            return false;
        }
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "DELETE FROM contracts WHERE id = ?")) {
            
            stmt.setLong(1, contractId);
            int rows = stmt.executeUpdate();
            conn.commit();
            
            if (rows > 0) {
                logger.debug("Deleted contract: id={}", contractId);
                return true;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to delete contract: id={}, error={}", contractId, e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Get all contracts (without payment stages for performance).
     * 
     * @return list of contracts
     */
    public List<Contract> getAllContracts() {
        List<Contract> contracts = new ArrayList<>();
        
        if (!databaseManager.isInitialized()) {
            return contracts;
        }
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT id, contract_number, name, signed_date, buyer_company, buyer_tax_code, " +
                 "created_at, updated_at FROM contracts ORDER BY created_at DESC")) {
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    contracts.add(mapContract(rs));
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to get all contracts: {}", e.getMessage());
        }
        
        return contracts;
    }
    
    /**
     * Query parameters for contract search.
     */
    public static class ContractQueryParams {
        private String contractNumber;
        private String buyerCompany;
        private java.sql.Date signedDateFrom;
        private java.sql.Date signedDateTo;
        private Timestamp createdFrom;
        private Timestamp createdTo;
        private Integer limit = 100;
        private Integer offset = 0;
        
        public ContractQueryParams contractNumber(String contractNumber) {
            this.contractNumber = contractNumber;
            return this;
        }
        
        public ContractQueryParams buyerCompany(String buyerCompany) {
            this.buyerCompany = buyerCompany;
            return this;
        }
        
        public ContractQueryParams signedDateFrom(java.sql.Date from) {
            this.signedDateFrom = from;
            return this;
        }
        
        public ContractQueryParams signedDateTo(java.sql.Date to) {
            this.signedDateTo = to;
            return this;
        }
        
        public ContractQueryParams createdFrom(Timestamp from) {
            this.createdFrom = from;
            return this;
        }
        
        public ContractQueryParams createdTo(Timestamp to) {
            this.createdTo = to;
            return this;
        }
        
        public ContractQueryParams limit(Integer limit) {
            this.limit = limit;
            return this;
        }
        
        public ContractQueryParams offset(Integer offset) {
            this.offset = offset;
            return this;
        }
        
        // Getters
        public String getContractNumber() { return contractNumber; }
        public String getBuyerCompany() { return buyerCompany; }
        public java.sql.Date getSignedDateFrom() { return signedDateFrom; }
        public java.sql.Date getSignedDateTo() { return signedDateTo; }
        public Timestamp getCreatedFrom() { return createdFrom; }
        public Timestamp getCreatedTo() { return createdTo; }
        public Integer getLimit() { return limit; }
        public Integer getOffset() { return offset; }
    }
    
    /**
     * Search contracts with filters.
     * 
     * @param params query parameters
     * @return list of matching contracts
     */
    public List<Contract> searchContracts(ContractQueryParams params) {
        List<Contract> contracts = new ArrayList<>();
        
        if (!databaseManager.isInitialized()) {
            return contracts;
        }
        
        try (Connection conn = databaseManager.getConnection()) {
            StringBuilder sql = new StringBuilder(
                "SELECT id, contract_number, name, signed_date, buyer_company, buyer_tax_code, " +
                "created_at, updated_at FROM contracts WHERE 1=1"
            );
            
            List<Object> queryParams = new ArrayList<>();
            
            if (params.getContractNumber() != null && !params.getContractNumber().isEmpty()) {
                sql.append(" AND contract_number LIKE ?");
                queryParams.add("%" + params.getContractNumber() + "%");
            }
            
            if (params.getBuyerCompany() != null && !params.getBuyerCompany().isEmpty()) {
                sql.append(" AND buyer_company LIKE ?");
                queryParams.add("%" + params.getBuyerCompany() + "%");
            }
            
            if (params.getSignedDateFrom() != null) {
                sql.append(" AND signed_date >= ?");
                queryParams.add(params.getSignedDateFrom());
            }
            
            if (params.getSignedDateTo() != null) {
                sql.append(" AND signed_date <= ?");
                queryParams.add(params.getSignedDateTo());
            }
            
            if (params.getCreatedFrom() != null) {
                sql.append(" AND created_at >= ?");
                queryParams.add(params.getCreatedFrom());
            }
            
            if (params.getCreatedTo() != null) {
                sql.append(" AND created_at <= ?");
                queryParams.add(params.getCreatedTo());
            }
            
            sql.append(" ORDER BY created_at DESC");
            
            if (params.getLimit() != null && params.getLimit() > 0) {
                sql.append(" LIMIT ?");
                queryParams.add(params.getLimit());
            }
            
            if (params.getOffset() != null && params.getOffset() > 0) {
                sql.append(" OFFSET ?");
                queryParams.add(params.getOffset());
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < queryParams.size(); i++) {
                    stmt.setObject(i + 1, queryParams.get(i));
                }
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        contracts.add(mapContract(rs));
                    }
                }
            }
            
            logger.debug("Contract search returned {} results", contracts.size());
            
        } catch (SQLException e) {
            logger.error("Failed to search contracts: {}", e.getMessage());
        }
        
        return contracts;
    }
    
    /**
     * Count contracts matching query parameters.
     * 
     * @param params query parameters (limit/offset ignored)
     * @return count of matching contracts
     */
    public int countContracts(ContractQueryParams params) {
        if (!databaseManager.isInitialized()) {
            return 0;
        }
        
        try (Connection conn = databaseManager.getConnection()) {
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM contracts WHERE 1=1");
            List<Object> queryParams = new ArrayList<>();
            
            if (params.getContractNumber() != null && !params.getContractNumber().isEmpty()) {
                sql.append(" AND contract_number LIKE ?");
                queryParams.add("%" + params.getContractNumber() + "%");
            }
            
            if (params.getBuyerCompany() != null && !params.getBuyerCompany().isEmpty()) {
                sql.append(" AND buyer_company LIKE ?");
                queryParams.add("%" + params.getBuyerCompany() + "%");
            }
            
            if (params.getSignedDateFrom() != null) {
                sql.append(" AND signed_date >= ?");
                queryParams.add(params.getSignedDateFrom());
            }
            
            if (params.getSignedDateTo() != null) {
                sql.append(" AND signed_date <= ?");
                queryParams.add(params.getSignedDateTo());
            }
            
            if (params.getCreatedFrom() != null) {
                sql.append(" AND created_at >= ?");
                queryParams.add(params.getCreatedFrom());
            }
            
            if (params.getCreatedTo() != null) {
                sql.append(" AND created_at <= ?");
                queryParams.add(params.getCreatedTo());
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < queryParams.size(); i++) {
                    stmt.setObject(i + 1, queryParams.get(i));
                }
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to count contracts: {}", e.getMessage());
        }
        
        return 0;
    }
    
    /**
     * Link a contract to an execution history record.
     * 
     * @param executionId the execution history ID
     * @param contractId the contract ID
     * @return true if link succeeded
     */
    public boolean linkContractToExecution(Long executionId, Long contractId) {
        if (!databaseManager.isInitialized() || executionId == null || contractId == null) {
            return false;
        }
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE execution_history SET contract_id = ? WHERE id = ?")) {
            
            stmt.setLong(1, contractId);
            stmt.setLong(2, executionId);
            
            int rows = stmt.executeUpdate();
            conn.commit();
            
            if (rows > 0) {
                logger.debug("Linked contract to execution: contractId={}, executionId={}", contractId, executionId);
                return true;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to link contract to execution: executionId={}, contractId={}, error={}",
                executionId, contractId, e.getMessage());
        }
        
        return false;
    }
    
    // =========================================================================
    // Helper Methods
    // =========================================================================
    
    /**
     * Map ResultSet to Contract object.
     */
    private Contract mapContract(ResultSet rs) throws SQLException {
        Contract contract = new Contract();
        contract.setId(rs.getLong("id"));
        contract.setContractNumber(rs.getString("contract_number"));
        contract.setName(rs.getString("name"));
        
        java.sql.Date signedDate = rs.getDate("signed_date");
        if (signedDate != null) {
            contract.setSignedDate(signedDate.toLocalDate());
        }
        
        contract.setBuyerCompany(rs.getString("buyer_company"));
        contract.setBuyerTaxCode(rs.getString("buyer_tax_code"));
        contract.setCreatedAt(rs.getTimestamp("created_at"));
        contract.setUpdatedAt(rs.getTimestamp("updated_at"));
        
        return contract;
    }
    
    /**
     * Get payment stages for a contract.
     */
    private List<PaymentStage> getPaymentStagesByContractId(Connection conn, Long contractId) throws SQLException {
        List<PaymentStage> stages = new ArrayList<>();
        
        try (PreparedStatement stmt = conn.prepareStatement(
            "SELECT id, contract_id, stage_name, price_before_vat, vat_rate, vat_amount, " +
            "price_after_vat, sequence_order FROM payment_stages WHERE contract_id = ? ORDER BY sequence_order")) {
            
            stmt.setLong(1, contractId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    PaymentStage stage = new PaymentStage();
                    stage.setId(rs.getLong("id"));
                    stage.setContractId(rs.getLong("contract_id"));
                    stage.setStageName(rs.getString("stage_name"));
                    stage.setPriceBeforeVat(rs.getObject("price_before_vat", Double.class));
                    stage.setVatRate(rs.getObject("vat_rate", Double.class));
                    stage.setVatAmount(rs.getObject("vat_amount", Double.class));
                    stage.setPriceAfterVat(rs.getObject("price_after_vat", Double.class));
                    stage.setSequenceOrder(rs.getObject("sequence_order", Integer.class));
                    stages.add(stage);
                }
            }
        }
        
        return stages;
    }
    
    /**
     * Insert payment stages for a contract.
     */
    private void insertPaymentStages(Connection conn, Long contractId, List<PaymentStage> stages) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO payment_stages (contract_id, stage_name, price_before_vat, vat_rate, " +
            "vat_amount, price_after_vat, sequence_order) VALUES (?, ?, ?, ?, ?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS)) {
            
            for (PaymentStage stage : stages) {
                stmt.setLong(1, contractId);
                stmt.setString(2, stage.getStageName());
                stmt.setObject(3, stage.getPriceBeforeVat());
                stmt.setObject(4, stage.getVatRate());
                stmt.setObject(5, stage.getVatAmount());
                stmt.setObject(6, stage.getPriceAfterVat());
                stmt.setObject(7, stage.getSequenceOrder());
                
                stmt.addBatch();
            }
            
            stmt.executeBatch();
            
            // Get generated IDs
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                int index = 0;
                while (keys.next() && index < stages.size()) {
                    stages.get(index).setId(keys.getLong(1));
                    stages.get(index).setContractId(contractId);
                    index++;
                }
            }
        }
    }
}

