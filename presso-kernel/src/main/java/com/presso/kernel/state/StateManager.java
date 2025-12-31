/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: StateManager.java
 * RESPONSIBILITY: Application state persistence and configuration management
 * 
 * ARCHITECTURAL ROLE:
 * - Loads and saves configuration from JSON files
 * - Manages global application state
 * - Provides configuration to other components
 * - Coordinates SQLite database access (future)
 * 
 * CONFIGURATION FILES (per PROJECT_DOCUMENTATION.md Section 5.1):
 * - settings.json: User preferences
 * - modules.json: Engine configuration
 * - ui-state.json: UI layout persistence (TODO: Phase 2)
 * 
 * STORAGE PATHS:
 * Windows: %APPDATA%\PressO\config\
 * 
 * BOUNDARIES:
 * - Does NOT contain business logic
 * - Does NOT make external network calls
 * - Owns all database access (delegated from Kernel)
 */
package com.presso.kernel.state;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.presso.kernel.event.EventBus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages application state and configuration for the Kernel.
 * <p>
 * Provides a centralized store for configuration values and
 * coordinates persistence to JSON files and SQLite database.
 * </p>
 */
public final class StateManager {
    
    private static final Logger logger = LoggerFactory.getLogger(StateManager.class);
    
    // Configuration file names
    private static final String SETTINGS_FILE = "settings.json";
    private static final String MODULES_FILE = "modules.json";
    
    // Default configuration paths (Windows)
    // Reference: PROJECT_DOCUMENTATION.md Section 5.1
    private static final String APP_DATA_DIR = System.getenv("APPDATA");
    private static final Path BASE_PATH = Paths.get(
        APP_DATA_DIR != null ? APP_DATA_DIR : System.getProperty("user.home"), 
        "PressO"
    );
    private static final Path CONFIG_PATH = BASE_PATH.resolve("config");
    private static final Path DATA_PATH = BASE_PATH.resolve("data");
    
    private final EventBus eventBus;
    
    // In-memory configuration store (flat key-value)
    private final Map<String, Object> configStore = new ConcurrentHashMap<>();
    
    // Configuration loaded flag
    private volatile boolean configLoaded = false;
    
    /**
     * Construct a StateManager with the event bus.
     * 
     * @param eventBus the event bus for state change events
     */
    public StateManager(EventBus eventBus) {
        this.eventBus = eventBus;
        
        // Initialize default configuration
        initializeDefaults();
        
        logger.debug("StateManager created, basePath={}", BASE_PATH);
    }
    
    /**
     * Initialize default configuration values.
     * Based on PROJECT_DOCUMENTATION.md Section 5.3.
     */
    private void initializeDefaults() {
        // General settings (settings.json)
        configStore.put("general.language", "vi-VN");
        configStore.put("general.theme", "dark");
        configStore.put("general.startMinimized", false);
        configStore.put("general.launchOnStartup", false);
        
        // Export settings (settings.json)
        configStore.put("export.defaultPath", "");
        configStore.put("export.imageQuality", 95);
        configStore.put("export.pdfCompression", "medium");
        
        // VAT settings (settings.json)
        configStore.put("vat.defaultRate", 0.10);
        configStore.put("vat.calculationMode", "before-vat");
        
        // Engine settings (modules.json)
        configStore.put("engine.python.enabled", true);
        configStore.put("engine.python.path", "${APP}/engines/python/python.exe");
        configStore.put("engine.python.maxConcurrent", 2);
        configStore.put("engine.rust.enabled", true);
        configStore.put("engine.rust.path", "${APP}/engines/rust/presso-rust.exe");
        configStore.put("engine.go.enabled", true);
        configStore.put("engine.go.path", "${APP}/engines/go/api-hub.exe");
        configStore.put("engine.go.port", 0);
        
        logger.debug("Default configuration initialized");
    }
    
    /**
     * Load configuration from files.
     * Loads settings.json and modules.json, creating defaults if missing.
     * 
     * @throws Exception if critical configuration loading fails
     */
    public void loadConfiguration() throws Exception {
        logger.info("Loading configuration from {}", CONFIG_PATH);
        
        // Ensure directories exist first
        ensureDirectoriesExist();
        
        // Load settings.json
        loadSettingsFile();
        
        // Load modules.json
        loadModulesFile();
        
        configLoaded = true;
        eventBus.publish("state.config.loaded", null);
        
        logger.info("Configuration loaded successfully");
    }
    
    /**
     * Load settings.json file.
     * Creates default file if missing.
     */
    private void loadSettingsFile() {
        Path settingsPath = CONFIG_PATH.resolve(SETTINGS_FILE);
        JsonObject defaults = buildDefaultSettingsJson();
        
        JsonObject settings = ConfigFileHandler.readOrCreateDefault(settingsPath, defaults);
        
        // Parse settings into flat config store
        parseSettingsJson(settings);
        
        logger.debug("Settings loaded from {}", settingsPath);
    }
    
    /**
     * Load modules.json file.
     * Creates default file if missing.
     */
    private void loadModulesFile() {
        Path modulesPath = CONFIG_PATH.resolve(MODULES_FILE);
        JsonObject defaults = buildDefaultModulesJson();
        
        JsonObject modules = ConfigFileHandler.readOrCreateDefault(modulesPath, defaults);
        
        // Parse modules into flat config store
        parseModulesJson(modules);
        
        logger.debug("Modules loaded from {}", modulesPath);
    }
    
    /**
     * Build the default settings.json structure.
     * Based on PROJECT_DOCUMENTATION.md Section 5.3.
     * 
     * @return the default settings JSON
     */
    private JsonObject buildDefaultSettingsJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", "1.0");
        
        // General section
        JsonObject general = new JsonObject();
        general.addProperty("language", "vi-VN");
        general.addProperty("theme", "dark");
        general.addProperty("startMinimized", false);
        general.addProperty("launchOnStartup", false);
        root.add("general", general);
        
        // Export section
        JsonObject export = new JsonObject();
        export.addProperty("defaultPath", "");
        export.addProperty("imageQuality", 95);
        export.addProperty("pdfCompression", "medium");
        root.add("export", export);
        
        // VAT section
        JsonObject vat = new JsonObject();
        vat.addProperty("defaultRate", 0.10);
        vat.addProperty("calculationMode", "before-vat");
        root.add("vat", vat);
        
        return root;
    }
    
    /**
     * Build the default modules.json structure.
     * Based on PROJECT_DOCUMENTATION.md Section 5.3.
     * 
     * @return the default modules JSON
     */
    private JsonObject buildDefaultModulesJson() {
        JsonObject root = new JsonObject();
        
        // Python engine
        JsonObject python = new JsonObject();
        python.addProperty("enabled", true);
        python.addProperty("path", "${APP}/engines/python/python.exe");
        python.addProperty("maxConcurrent", 2);
        root.add("python", python);
        
        // Rust engine
        JsonObject rust = new JsonObject();
        rust.addProperty("enabled", true);
        rust.addProperty("path", "${APP}/engines/rust/presso-rust.exe");
        root.add("rust", rust);
        
        // Go API hub
        JsonObject go = new JsonObject();
        go.addProperty("enabled", true);
        go.addProperty("path", "${APP}/engines/go/api-hub.exe");
        go.addProperty("port", 0);
        root.add("go", go);
        
        return root;
    }
    
    /**
     * Parse settings.json into flat config store.
     * 
     * @param settings the parsed settings JSON
     */
    private void parseSettingsJson(JsonObject settings) {
        // Parse general section
        if (settings.has("general") && settings.get("general").isJsonObject()) {
            JsonObject general = settings.getAsJsonObject("general");
            parseSection("general", general);
        }
        
        // Parse export section
        if (settings.has("export") && settings.get("export").isJsonObject()) {
            JsonObject export = settings.getAsJsonObject("export");
            parseSection("export", export);
        }
        
        // Parse vat section
        if (settings.has("vat") && settings.get("vat").isJsonObject()) {
            JsonObject vat = settings.getAsJsonObject("vat");
            parseSection("vat", vat);
        }
        
        // TODO (Phase 2): Parse additional settings sections as needed
    }
    
    /**
     * Parse modules.json into flat config store.
     * 
     * @param modules the parsed modules JSON
     */
    private void parseModulesJson(JsonObject modules) {
        // Parse python engine config
        if (modules.has("python") && modules.get("python").isJsonObject()) {
            JsonObject python = modules.getAsJsonObject("python");
            parseSection("engine.python", python);
        }
        
        // Parse rust engine config
        if (modules.has("rust") && modules.get("rust").isJsonObject()) {
            JsonObject rust = modules.getAsJsonObject("rust");
            parseSection("engine.rust", rust);
        }
        
        // Parse go engine config
        if (modules.has("go") && modules.get("go").isJsonObject()) {
            JsonObject go = modules.getAsJsonObject("go");
            parseSection("engine.go", go);
        }
        
        // TODO (Phase 2): Support dynamic engine configuration
    }
    
    /**
     * Parse a JSON section into the flat config store.
     * 
     * @param prefix the key prefix for this section
     * @param section the JSON section to parse
     */
    private void parseSection(String prefix, JsonObject section) {
        for (String key : section.keySet()) {
            String fullKey = prefix + "." + key;
            
            var element = section.get(key);
            if (element.isJsonPrimitive()) {
                JsonPrimitive primitive = element.getAsJsonPrimitive();
                Object value = extractPrimitiveValue(primitive);
                configStore.put(fullKey, value);
                logger.trace("Loaded config: {}={}", fullKey, value);
            }
            // TODO (Phase 2): Support nested sections if needed
        }
    }
    
    /**
     * Extract a Java value from a JsonPrimitive.
     * 
     * @param primitive the JSON primitive
     * @return the Java value
     */
    private Object extractPrimitiveValue(JsonPrimitive primitive) {
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        } else if (primitive.isNumber()) {
            Number number = primitive.getAsNumber();
            // Preserve integer vs double distinction
            if (number.toString().contains(".")) {
                return number.doubleValue();
            } else {
                return number.intValue();
            }
        } else {
            return primitive.getAsString();
        }
    }
    
    /**
     * Save configuration to files.
     * Saves both settings.json and modules.json.
     */
    public void saveConfiguration() {
        logger.info("Saving configuration to {}", CONFIG_PATH);
        
        // Backup existing files before saving
        ConfigFileHandler.createBackup(CONFIG_PATH.resolve(SETTINGS_FILE));
        ConfigFileHandler.createBackup(CONFIG_PATH.resolve(MODULES_FILE));
        
        // Save settings.json
        saveSettingsFile();
        
        // Save modules.json
        saveModulesFile();
        
        eventBus.publish("state.config.saved", null);
        logger.info("Configuration saved successfully");
    }
    
    /**
     * Save settings.json from current config store.
     */
    private void saveSettingsFile() {
        Path settingsPath = CONFIG_PATH.resolve(SETTINGS_FILE);
        JsonObject root = new JsonObject();
        root.addProperty("version", "1.0");
        
        // Build general section
        JsonObject general = new JsonObject();
        general.addProperty("language", getConfig("general.language", "vi-VN"));
        general.addProperty("theme", getConfig("general.theme", "dark"));
        general.addProperty("startMinimized", getConfig("general.startMinimized", false));
        general.addProperty("launchOnStartup", getConfig("general.launchOnStartup", false));
        root.add("general", general);
        
        // Build export section
        JsonObject export = new JsonObject();
        export.addProperty("defaultPath", getConfig("export.defaultPath", ""));
        export.addProperty("imageQuality", getConfig("export.imageQuality", 95));
        export.addProperty("pdfCompression", getConfig("export.pdfCompression", "medium"));
        root.add("export", export);
        
        // Build vat section
        JsonObject vat = new JsonObject();
        vat.addProperty("defaultRate", getConfig("vat.defaultRate", 0.10));
        vat.addProperty("calculationMode", getConfig("vat.calculationMode", "before-vat"));
        root.add("vat", vat);
        
        if (ConfigFileHandler.writeJsonFile(settingsPath, root)) {
            logger.debug("Saved settings to {}", settingsPath);
        } else {
            logger.error("Failed to save settings to {}", settingsPath);
        }
    }
    
    /**
     * Save modules.json from current config store.
     */
    private void saveModulesFile() {
        Path modulesPath = CONFIG_PATH.resolve(MODULES_FILE);
        JsonObject root = new JsonObject();
        
        // Build python section
        JsonObject python = new JsonObject();
        python.addProperty("enabled", getConfig("engine.python.enabled", true));
        python.addProperty("path", getConfig("engine.python.path", "${APP}/engines/python/python.exe"));
        python.addProperty("maxConcurrent", getConfig("engine.python.maxConcurrent", 2));
        root.add("python", python);
        
        // Build rust section
        JsonObject rust = new JsonObject();
        rust.addProperty("enabled", getConfig("engine.rust.enabled", true));
        rust.addProperty("path", getConfig("engine.rust.path", "${APP}/engines/rust/presso-rust.exe"));
        root.add("rust", rust);
        
        // Build go section
        JsonObject go = new JsonObject();
        go.addProperty("enabled", getConfig("engine.go.enabled", true));
        go.addProperty("path", getConfig("engine.go.path", "${APP}/engines/go/api-hub.exe"));
        go.addProperty("port", getConfig("engine.go.port", 0));
        root.add("go", go);
        
        if (ConfigFileHandler.writeJsonFile(modulesPath, root)) {
            logger.debug("Saved modules to {}", modulesPath);
        } else {
            logger.error("Failed to save modules to {}", modulesPath);
        }
    }
    
    /**
     * Ensure required directories exist.
     */
    private void ensureDirectoriesExist() {
        try {
            java.nio.file.Files.createDirectories(CONFIG_PATH);
            java.nio.file.Files.createDirectories(DATA_PATH);
            java.nio.file.Files.createDirectories(BASE_PATH.resolve("templates"));
            java.nio.file.Files.createDirectories(BASE_PATH.resolve("cache"));
            java.nio.file.Files.createDirectories(BASE_PATH.resolve("logs"));
            java.nio.file.Files.createDirectories(BASE_PATH.resolve("secure"));
            
            logger.debug("Application directories verified");
        } catch (Exception e) {
            logger.error("Failed to create directories: {}", e.getMessage());
        }
    }
    
    /**
     * Get a configuration value.
     * 
     * @param key the configuration key (dot-separated path)
     * @param <T> the expected type
     * @return the value, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String key) {
        return (T) configStore.get(key);
    }
    
    /**
     * Get a configuration value with a default.
     * 
     * @param key the configuration key
     * @param defaultValue the default value if not found
     * @param <T> the expected type
     * @return the value, or the default if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String key, T defaultValue) {
        Object value = configStore.get(key);
        return value != null ? (T) value : defaultValue;
    }
    
    /**
     * Set a configuration value.
     * Note: Changes are not persisted until saveConfiguration() is called.
     * 
     * @param key the configuration key
     * @param value the value to set
     */
    public void setConfig(String key, Object value) {
        Object oldValue = configStore.put(key, value);
        
        if (oldValue == null || !oldValue.equals(value)) {
            eventBus.publish("state.config.changed", Map.of("key", key, "value", value));
            logger.debug("Config changed: {}={}", key, value);
        }
    }
    
    /**
     * Reload configuration from files.
     * Discards any unsaved in-memory changes.
     * 
     * @throws Exception if reload fails
     */
    public void reloadConfiguration() throws Exception {
        logger.info("Reloading configuration...");
        
        // Reset to defaults first
        initializeDefaults();
        
        // Then load from files
        loadSettingsFile();
        loadModulesFile();
        
        eventBus.publish("state.config.reloaded", null);
        logger.info("Configuration reloaded");
    }
    
    /**
     * Check if configuration has been loaded.
     * 
     * @return true if loaded
     */
    public boolean isConfigLoaded() {
        return configLoaded;
    }
    
    /**
     * Get the base application data path.
     * 
     * @return the base path (%APPDATA%/PressO)
     */
    public Path getBasePath() {
        return BASE_PATH;
    }
    
    /**
     * Get the configuration directory path.
     * 
     * @return the config path (%APPDATA%/PressO/config)
     */
    public Path getConfigPath() {
        return CONFIG_PATH;
    }
    
    /**
     * Get the database path.
     * 
     * @return the path to presso.db
     */
    public Path getDatabasePath() {
        return DATA_PATH.resolve("presso.db");
    }
    
    /**
     * Resolve an engine path, replacing ${APP} placeholder.
     * 
     * @param enginePathConfig the engine path from configuration
     * @return the resolved absolute path
     */
    public Path resolveEnginePath(String enginePathConfig) {
        if (enginePathConfig == null) {
            return null;
        }
        
        // Replace ${APP} with actual application directory
        // TODO (Phase 2): Determine actual app installation directory
        String appDir = System.getProperty("user.dir");
        String resolved = enginePathConfig.replace("${APP}", appDir);
        
        return Paths.get(resolved);
    }
}
