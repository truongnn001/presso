/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: DatabaseManager.java
 * RESPONSIBILITY: SQLite database connection and initialization
 * 
 * ARCHITECTURAL ROLE:
 * - Manages SQLite database connection
 * - Creates database schema on first run
 * - Provides connection access to persistence services
 * - Handles database lifecycle (open, close)
 * 
 * BOUNDARIES:
 * - Does NOT contain business logic
 * - Does NOT perform queries (delegates to services)
 * - Fail-safe: initialization failures are logged but don't crash kernel
 * 
 * Reference: PROJECT_DOCUMENTATION.md Section 5.2
 */
package com.presso.kernel.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages SQLite database connection and schema initialization.
 */
public final class DatabaseManager {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    
    private static final String DB_FILENAME = "presso.db";
    private static final String DB_DIR = "data";
    
    private Connection connection;
    private final String dbPath;
    private volatile boolean initialized = false;
    
    /**
     * Construct a DatabaseManager.
     * Determines database path from %APPDATA%/PressO/data/presso.db
     */
    public DatabaseManager() {
        // Get %APPDATA% path
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isEmpty()) {
            // Fallback to user.home if APPDATA not available
            appData = System.getProperty("user.home");
        }
        
        Path dbDir = Paths.get(appData, "PressO", DB_DIR);
        this.dbPath = dbDir.resolve(DB_FILENAME).toString();
        
        logger.debug("DatabaseManager created, dbPath={}", dbPath);
    }
    
    /**
     * Initialize database connection and create schema if needed.
     * 
     * @throws SQLException if database initialization fails
     */
    public void initialize() throws SQLException {
        if (initialized) {
            logger.warn("Database already initialized");
            return;
        }
        
        try {
            // Ensure database directory exists
            File dbFile = new File(dbPath);
            File dbDir = dbFile.getParentFile();
            if (dbDir != null && !dbDir.exists()) {
                Files.createDirectories(dbDir.toPath());
                logger.info("Created database directory: {}", dbDir);
            }
            
            // Connect to SQLite database
            String jdbcUrl = "jdbc:sqlite:" + dbPath;
            connection = DriverManager.getConnection(jdbcUrl);
            connection.setAutoCommit(false);
            
            logger.info("Connected to SQLite database: {}", dbPath);
            
            // Create schema if tables don't exist
            createSchemaIfNeeded();
            
            initialized = true;
            logger.info("Database initialization complete");
            
        } catch (Exception e) {
            logger.error("Database initialization failed: {}", e.getMessage(), e);
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ex) {
                    logger.error("Error closing connection after init failure", ex);
                }
                connection = null;
            }
            throw new SQLException("Database initialization failed", e);
        }
    }
    
    /**
     * Create database schema if tables don't exist.
     */
    private void createSchemaIfNeeded() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            
            // Create execution_history table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS execution_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    operation_type TEXT NOT NULL,
                    module TEXT NOT NULL,
                    started_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    completed_at DATETIME,
                    status TEXT CHECK(status IN ('pending', 'running', 'completed', 'failed')),
                    input_summary TEXT,
                    output_summary TEXT,
                    error_message TEXT
                )
                """);
            
            // Create contracts table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS contracts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    contract_number TEXT UNIQUE,
                    name TEXT NOT NULL,
                    signed_date DATE,
                    buyer_company TEXT,
                    buyer_tax_code TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);
            
            // Create payment_stages table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS payment_stages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    contract_id INTEGER NOT NULL REFERENCES contracts(id) ON DELETE CASCADE,
                    stage_name TEXT NOT NULL,
                    price_before_vat REAL,
                    vat_rate REAL DEFAULT 0.10,
                    vat_amount REAL,
                    price_after_vat REAL,
                    sequence_order INTEGER
                )
                """);
            
            // Create activity_log table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS activity_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
                    user_action TEXT NOT NULL,
                    entity_type TEXT,
                    entity_id INTEGER,
                    details TEXT,
                    related_execution_id INTEGER,
                    module TEXT,
                    severity TEXT CHECK(severity IN ('INFO', 'WARN', 'ERROR')),
                    short_message TEXT,
                    metadata TEXT
                )
                """);
            
            // Add contract_id to execution_history for linking (Phase 3 Step 3)
            try {
                stmt.execute("ALTER TABLE execution_history ADD COLUMN contract_id INTEGER REFERENCES contracts(id)");
                logger.debug("Added contract_id column to execution_history");
            } catch (SQLException e) {
                // Column already exists, ignore
                logger.debug("Column contract_id already exists or migration not needed");
            }
            
            // Migrate existing activity_log table to add new columns (Phase 3 Step 2)
            // SQLite doesn't support IF NOT EXISTS for ALTER TABLE, so we check first
            try {
                stmt.execute("ALTER TABLE activity_log ADD COLUMN related_execution_id INTEGER");
                logger.debug("Added related_execution_id column to activity_log");
            } catch (SQLException e) {
                // Column already exists, ignore
                logger.debug("Column related_execution_id already exists or migration not needed");
            }
            try {
                stmt.execute("ALTER TABLE activity_log ADD COLUMN module TEXT");
                logger.debug("Added module column to activity_log");
            } catch (SQLException e) {
                // Column already exists, ignore
                logger.debug("Column module already exists or migration not needed");
            }
            try {
                stmt.execute("ALTER TABLE activity_log ADD COLUMN severity TEXT CHECK(severity IN ('INFO', 'WARN', 'ERROR'))");
                logger.debug("Added severity column to activity_log");
            } catch (SQLException e) {
                // Column already exists, ignore
                logger.debug("Column severity already exists or migration not needed");
            }
            try {
                stmt.execute("ALTER TABLE activity_log ADD COLUMN short_message TEXT");
                logger.debug("Added short_message column to activity_log");
            } catch (SQLException e) {
                // Column already exists, ignore
                logger.debug("Column short_message already exists or migration not needed");
            }
            try {
                stmt.execute("ALTER TABLE activity_log ADD COLUMN metadata TEXT");
                logger.debug("Added metadata column to activity_log");
            } catch (SQLException e) {
                // Column already exists, ignore
                logger.debug("Column metadata already exists or migration not needed");
            }
            
            // Create indexes for better query performance
            // TODO (Phase 3+): Add more indexes as needed for querying
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_execution_history_status ON execution_history(status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_execution_history_module ON execution_history(module)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_execution_history_started_at ON execution_history(started_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_activity_log_timestamp ON activity_log(timestamp)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_activity_log_action ON activity_log(user_action)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_activity_log_severity ON activity_log(severity)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_activity_log_module ON activity_log(module)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_activity_log_related_execution ON activity_log(related_execution_id)");
            
            // Indexes for contracts and payment_stages
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_contracts_contract_number ON contracts(contract_number)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_payment_stages_contract_id ON payment_stages(contract_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_execution_history_contract_id ON execution_history(contract_id)");
            
            connection.commit();
            logger.debug("Database schema created/verified");
            
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        }
    }
    
    /**
     * Get database connection.
     * 
     * @return the database connection
     * @throws SQLException if database is not initialized
     */
    public Connection getConnection() throws SQLException {
        if (!initialized || connection == null || connection.isClosed()) {
            throw new SQLException("Database not initialized or connection closed");
        }
        return connection;
    }
    
    /**
     * Close database connection.
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("Database connection closed");
            } catch (SQLException e) {
                logger.error("Error closing database connection: {}", e.getMessage());
            } finally {
                connection = null;
                initialized = false;
            }
        }
    }
    
    /**
     * Check if database is initialized.
     * 
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized && connection != null;
    }
    
    /**
     * Get database file path.
     * 
     * @return the database path
     */
    public String getDbPath() {
        return dbPath;
    }
}

