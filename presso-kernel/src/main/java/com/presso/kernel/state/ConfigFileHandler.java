/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: ConfigFileHandler.java
 * RESPONSIBILITY: JSON configuration file I/O operations
 * 
 * ARCHITECTURAL ROLE:
 * - Reads and writes JSON configuration files
 * - Creates default configuration files if missing
 * - Handles file system errors gracefully
 * - Pure I/O utility - no business logic
 * 
 * BOUNDARIES:
 * - Does NOT contain business logic
 * - Does NOT make external network calls
 * - Does NOT interact with engines or UI
 * - Only handles local file system operations
 * 
 * Reference: PROJECT_DOCUMENTATION.md Section 5.3
 */
package com.presso.kernel.state;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Handles JSON configuration file read/write operations.
 * <p>
 * Provides safe file I/O with error handling and default file creation.
 * Uses Gson for JSON parsing (lightweight, no framework overhead).
 * </p>
 */
public final class ConfigFileHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigFileHandler.class);
    
    // Gson instance configured for pretty printing
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .serializeNulls()
        .create();
    
    // Private constructor - utility class
    private ConfigFileHandler() {}
    
    /**
     * Read a JSON file and parse it as a JsonObject.
     * 
     * @param filePath the path to the JSON file
     * @return Optional containing the parsed JsonObject, or empty if file doesn't exist or is invalid
     */
    public static Optional<JsonObject> readJsonFile(Path filePath) {
        if (filePath == null) {
            logger.warn("Null file path provided");
            return Optional.empty();
        }
        
        if (!Files.exists(filePath)) {
            logger.debug("Configuration file does not exist: {}", filePath);
            return Optional.empty();
        }
        
        if (!Files.isRegularFile(filePath)) {
            logger.warn("Path is not a regular file: {}", filePath);
            return Optional.empty();
        }
        
        try (Reader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            
            if (element == null || element.isJsonNull()) {
                logger.warn("Empty JSON file: {}", filePath);
                return Optional.empty();
            }
            
            if (!element.isJsonObject()) {
                logger.warn("JSON file root is not an object: {}", filePath);
                return Optional.empty();
            }
            
            logger.debug("Successfully read JSON file: {}", filePath);
            return Optional.of(element.getAsJsonObject());
            
        } catch (JsonSyntaxException e) {
            logger.error("Invalid JSON syntax in {}: {}", filePath, e.getMessage());
            return Optional.empty();
        } catch (IOException e) {
            logger.error("Failed to read file {}: {}", filePath, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Write a JsonObject to a file.
     * Creates parent directories if they don't exist.
     * 
     * @param filePath the path to write to
     * @param jsonObject the JSON object to write
     * @return true if successful, false otherwise
     */
    public static boolean writeJsonFile(Path filePath, JsonObject jsonObject) {
        if (filePath == null || jsonObject == null) {
            logger.warn("Null path or content provided for write");
            return false;
        }
        
        try {
            // Ensure parent directory exists
            Path parent = filePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
                logger.debug("Created directory: {}", parent);
            }
            
            // Write JSON with pretty printing
            try (Writer writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
                GSON.toJson(jsonObject, writer);
            }
            
            logger.debug("Successfully wrote JSON file: {}", filePath);
            return true;
            
        } catch (IOException e) {
            logger.error("Failed to write file {}: {}", filePath, e.getMessage());
            return false;
        }
    }
    
    /**
     * Read a JSON file, or create it with default content if it doesn't exist.
     * 
     * @param filePath the path to the JSON file
     * @param defaultContent the default content to write if file doesn't exist
     * @return the parsed JsonObject (either from file or defaults)
     */
    public static JsonObject readOrCreateDefault(Path filePath, JsonObject defaultContent) {
        Optional<JsonObject> existing = readJsonFile(filePath);
        
        if (existing.isPresent()) {
            return existing.get();
        }
        
        // File doesn't exist or is invalid - create with defaults
        logger.info("Creating default configuration file: {}", filePath);
        
        if (writeJsonFile(filePath, defaultContent)) {
            return defaultContent;
        }
        
        // Write failed but we can still use defaults in memory
        logger.warn("Could not write default file, using in-memory defaults");
        return defaultContent;
    }
    
    /**
     * Check if a configuration file exists and is valid JSON.
     * 
     * @param filePath the path to check
     * @return true if file exists and contains valid JSON
     */
    public static boolean isValidConfigFile(Path filePath) {
        return readJsonFile(filePath).isPresent();
    }
    
    /**
     * Create a backup of an existing configuration file.
     * 
     * @param filePath the file to backup
     * @return true if backup was created or file doesn't exist
     */
    public static boolean createBackup(Path filePath) {
        if (!Files.exists(filePath)) {
            return true; // Nothing to backup
        }
        
        try {
            Path backupPath = filePath.resolveSibling(filePath.getFileName() + ".bak");
            Files.copy(filePath, backupPath, 
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            logger.debug("Created backup: {}", backupPath);
            return true;
        } catch (IOException e) {
            logger.error("Failed to create backup of {}: {}", filePath, e.getMessage());
            return false;
        }
    }
    
    /**
     * Get the Gson instance for external use.
     * 
     * @return the configured Gson instance
     */
    public static Gson getGson() {
        return GSON;
    }
}

