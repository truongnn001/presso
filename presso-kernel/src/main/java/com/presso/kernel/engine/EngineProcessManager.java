/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: EngineProcessManager.java
 * RESPONSIBILITY: Engine subprocess lifecycle management
 * 
 * ARCHITECTURAL ROLE:
 * - Spawns engine processes (Python, Rust, Go)
 * - Monitors engine health via heartbeat
 * - Restarts crashed engines
 * - Manages stdin/stdout communication pipes
 * - Graceful shutdown with timeout
 * 
 * COMMUNICATION PROTOCOL:
 * - JSON-RPC 2.0 style messages via stdin/stdout
 * - Reference: PROJECT_DOCUMENTATION.md Section 3.3
 * 
 * ENGINE PATHS (per PROJECT_DOCUMENTATION.md Section 8.1):
 * - Python: engines/python/engine_main.py
 * - Rust: engines/rust/presso-rust.exe
 * - Go: engines/go/api-hub.exe
 * 
 * BOUNDARIES:
 * - Does NOT contain business logic
 * - Does NOT directly process work items
 * - Manages process lifecycle only
 */
package com.presso.kernel.engine;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.presso.kernel.event.EventBus;
import com.presso.kernel.state.StateManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Manages the lifecycle of engine subprocesses.
 * <p>
 * Handles spawning, monitoring, and termination of Python, Rust,
 * and Go engine processes. Provides stdin/stdout communication
 * channels for message passing.
 * </p>
 */
public final class EngineProcessManager {
    
    private static final Logger logger = LoggerFactory.getLogger(EngineProcessManager.class);
    private static final Gson GSON = new Gson();
    
    /**
     * Engine process wrapper containing process and I/O handles.
     */
    public static final class EngineProcess {
        private final String engineName;
        private final Process process;
        private final BufferedReader stdout;
        private final BufferedReader stderr;
        private final PrintWriter stdin;
        private final Map<String, CompletableFuture<JsonObject>> pendingRequests;
        private final Thread readerThread;
        private final Thread errorThread;
        private volatile boolean healthy = false;
        private volatile boolean running = true;
        
        EngineProcess(String engineName, Process process, EventBus eventBus) {
            this.engineName = engineName;
            this.process = process;
            this.stdout = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            );
            this.stderr = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)
            );
            this.stdin = new PrintWriter(process.getOutputStream(), true, StandardCharsets.UTF_8);
            this.pendingRequests = new ConcurrentHashMap<>();
            
            // Start reader thread for stdout
            this.readerThread = Thread.ofVirtual()
                .name("engine-" + engineName + "-reader")
                .start(this::readLoop);
            
            // Start error reader thread for stderr (logging)
            this.errorThread = Thread.ofVirtual()
                .name("engine-" + engineName + "-error")
                .start(this::errorLoop);
        }
        
        /**
         * Read responses from engine stdout.
         */
        private void readLoop() {
            logger.debug("[{}] Reader thread started", engineName);
            
            while (running && process.isAlive()) {
                try {
                    String line = stdout.readLine();
                    
                    if (line == null) {
                        logger.info("[{}] Engine stdout closed", engineName);
                        break;
                    }
                    
                    if (line.isBlank()) {
                        continue;
                    }
                    
                    logger.debug("[{}] Received: {}", engineName, line);
                    handleResponse(line);
                    
                } catch (Exception e) {
                    if (running) {
                        logger.error("[{}] Error reading from engine: {}", engineName, e.getMessage());
                    }
                }
            }
            
            logger.debug("[{}] Reader thread ended", engineName);
        }
        
        /**
         * Read and log stderr output.
         */
        private void errorLoop() {
            while (running && process.isAlive()) {
                try {
                    String line = stderr.readLine();
                    
                    if (line == null) {
                        break;
                    }
                    
                    if (!line.isBlank()) {
                        logger.info("[{}] {}", engineName, line);
                    }
                    
                } catch (Exception e) {
                    if (running) {
                        logger.debug("[{}] Error reader exception: {}", engineName, e.getMessage());
                    }
                }
            }
        }
        
        /**
         * Handle a response line from the engine.
         */
        private void handleResponse(String line) {
            try {
                JsonObject response = GSON.fromJson(line, JsonObject.class);
                
                // Check for READY signal
                if (response.has("type") && "READY".equals(response.get("type").getAsString())) {
                    healthy = true;
                    logger.info("[{}] Engine is ready", engineName);
                    return;
                }
                
                // Look for request ID to match pending request
                if (response.has("id")) {
                    String requestId = response.get("id").getAsString();
                    CompletableFuture<JsonObject> future = pendingRequests.remove(requestId);
                    
                    if (future != null) {
                        future.complete(response);
                    } else {
                        logger.debug("[{}] Unsolicited response: {}", engineName, requestId);
                    }
                }
                
            } catch (Exception e) {
                logger.error("[{}] Failed to parse response: {}", engineName, e.getMessage());
            }
        }
        
        /**
         * Send a message and wait for response.
         */
        public JsonObject sendAndReceive(JsonObject message, long timeoutMs) throws Exception {
            String requestId = message.has("id") 
                ? message.get("id").getAsString() 
                : UUID.randomUUID().toString();
            
            if (!message.has("id")) {
                message.addProperty("id", requestId);
            }
            
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            pendingRequests.put(requestId, future);
            
            // Send message
            String json = GSON.toJson(message);
            logger.debug("[{}] Sending: {}", engineName, json);
            stdin.println(json);
            stdin.flush();
            
            // Wait for response
            try {
                return future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                pendingRequests.remove(requestId);
                throw new TimeoutException("Engine " + engineName + " did not respond within " + timeoutMs + "ms");
            }
        }
        
        /**
         * Send a message without waiting for response.
         */
        public void send(JsonObject message) {
            String json = GSON.toJson(message);
            logger.debug("[{}] Sending (no-wait): {}", engineName, json);
            stdin.println(json);
            stdin.flush();
        }
        
        public String getName() { return engineName; }
        public Process getProcess() { return process; }
        public boolean isHealthy() { return healthy; }
        public void setHealthy(boolean healthy) { this.healthy = healthy; }
        
        public boolean isAlive() {
            return process != null && process.isAlive();
        }
        
        /**
         * Stop the reader threads.
         */
        public void stopReaders() {
            running = false;
            readerThread.interrupt();
            errorThread.interrupt();
        }
    }
    
    /**
     * Engine types managed by this class.
     */
    public enum EngineType {
        PYTHON("python", "python", "engines/python/engine_main.py"),
        RUST("rust", "engines/rust/presso-rust.exe", null),
        GO("go", "engines/go/api-hub.exe", null);
        
        private final String name;
        private final String executable;
        private final String scriptPath;
        
        EngineType(String name, String executable, String scriptPath) {
            this.name = name;
            this.executable = executable;
            this.scriptPath = scriptPath;
        }
        
        public String getName() { return name; }
        public String getExecutable() { return executable; }
        public String getScriptPath() { return scriptPath; }
    }
    
    // Timeouts
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;
    private static final long MESSAGE_TIMEOUT_MS = 30000;
    private static final long READY_TIMEOUT_MS = 10000;
    
    private final EventBus eventBus;
    private final StateManager stateManager;
    
    // Active engine processes
    private final Map<String, EngineProcess> engines = new ConcurrentHashMap<>();
    
    // Application base path for engine scripts
    private Path appBasePath;
    
    /**
     * Construct an EngineProcessManager.
     * 
     * @param eventBus the event bus for engine events
     * @param stateManager the state manager for configuration
     */
    public EngineProcessManager(EventBus eventBus, StateManager stateManager) {
        this.eventBus = eventBus;
        this.stateManager = stateManager;
        
        // Get app base path - for development, use project root
        this.appBasePath = Path.of(System.getProperty("user.dir"));
        
        // If running from presso-kernel, go up one level
        if (appBasePath.endsWith("presso-kernel")) {
            appBasePath = appBasePath.getParent();
        }
        
        logger.debug("EngineProcessManager created, appBasePath={}", appBasePath);
    }
    
    /**
     * Start all configured engines.
     * 
     * @throws Exception if any required engine fails to start
     */
    public void startAllEngines() throws Exception {
        logger.info("Starting all engines...");
        
        // Start Python engine
        boolean pythonEnabled = stateManager.getConfig("engine.python.enabled", true);
        
        if (pythonEnabled) {
            try {
                startEngine(EngineType.PYTHON);
            } catch (Exception e) {
                logger.error("Failed to start Python engine: {}", e.getMessage());
                // Log but don't fail startup - allow testing without Python
            }
        } else {
            logger.info("Python engine is disabled");
        }
        
        // Start Go API Hub engine (Phase 4 Step 1)
        boolean goEnabled = stateManager.getConfig("engine.go.enabled", true);
        
        if (goEnabled) {
            try {
                startEngine(EngineType.GO);
            } catch (Exception e) {
                logger.error("Failed to start Go API Hub engine: {}", e.getMessage());
                // Log but don't fail startup - allow testing without Go engine binary
            }
        } else {
            logger.info("Go API Hub engine is disabled");
        }
        
        // TODO (Phase 4 Step 2+): Start Rust engine
        
        logger.info("Engine startup complete, {} engines running", engines.size());
    }
    
    /**
     * Start a specific engine.
     * 
     * @param engineType the engine type to start
     * @throws Exception if the engine fails to start
     */
    public void startEngine(EngineType engineType) throws Exception {
        String engineName = engineType.getName();
        
        if (engines.containsKey(engineName)) {
            logger.warn("Engine {} already running", engineName);
            return;
        }
        
        logger.info("Starting engine: {}", engineName);
        
        // Build command based on engine type
        List<String> command = buildEngineCommand(engineType);
        
        if (command == null || command.isEmpty()) {
            throw new IllegalStateException("Cannot build command for engine: " + engineName);
        }
        
        logger.debug("Engine command: {}", String.join(" ", command));
        
        // Create process builder
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(appBasePath.toFile());
        
        // Don't redirect error stream - we read it separately
        pb.redirectErrorStream(false);
        
        // Start the process
        Process process = pb.start();
        
        // Create engine wrapper
        EngineProcess engineProcess = new EngineProcess(engineName, process, eventBus);
        engines.put(engineName, engineProcess);
        
        // Wait for READY signal
        logger.debug("Waiting for engine {} to be ready...", engineName);
        long startTime = System.currentTimeMillis();
        
        while (!engineProcess.isHealthy() && engineProcess.isAlive()) {
            if (System.currentTimeMillis() - startTime > READY_TIMEOUT_MS) {
                logger.error("Engine {} did not become ready within timeout", engineName);
                stopEngine(engineName);
                throw new TimeoutException("Engine " + engineName + " startup timeout");
            }
            Thread.sleep(100);
        }
        
        if (!engineProcess.isAlive()) {
            engines.remove(engineName);
            throw new IllegalStateException("Engine " + engineName + " died during startup");
        }
        
        eventBus.publish("engine.started", engineName);
        logger.info("Engine {} started and ready (PID: {})", engineName, process.pid());
    }
    
    /**
     * Build the command to start an engine.
     */
    private List<String> buildEngineCommand(EngineType engineType) {
        List<String> command = new ArrayList<>();
        
        switch (engineType) {
            case PYTHON:
                // Use system Python or configured path
                String pythonPath = stateManager.getConfig(
                    "engine.python.executable", 
                    "python"  // Default to system Python
                );
                command.add(pythonPath);
                command.add("-u"); // Unbuffered output
                
                // Add script path
                Path scriptPath = appBasePath.resolve(engineType.getScriptPath());
                if (!Files.exists(scriptPath)) {
                    logger.error("Python engine script not found: {}", scriptPath);
                    return null;
                }
                command.add(scriptPath.toString());
                break;
                
            case RUST:
                // TODO (Phase 4 Step 2+): Implement Rust engine spawning
                logger.warn("Engine {} not yet implemented", engineType.getName());
                return null;
                
            case GO:
                // Go API Hub engine (Phase 4 Step 1)
                Path goExecutable = appBasePath.resolve(engineType.getExecutable());
                if (!Files.exists(goExecutable)) {
                    logger.error("Go engine executable not found: {}", goExecutable);
                    return null;
                }
                command.add(goExecutable.toString());
                break;
                
            default:
                return null;
        }
        
        return command;
    }
    
    /**
     * Stop all running engines.
     */
    public void stopAllEngines() {
        logger.info("Stopping all engines...");
        
        for (String engineName : engines.keySet()) {
            try {
                stopEngine(engineName);
            } catch (Exception e) {
                logger.error("Error stopping engine {}: {}", engineName, e.getMessage());
            }
        }
        
        logger.info("All engines stopped");
    }
    
    /**
     * Stop a specific engine gracefully.
     * 
     * @param engineName the engine to stop
     */
    public void stopEngine(String engineName) {
        EngineProcess engineProcess = engines.remove(engineName);
        
        if (engineProcess == null) {
            logger.debug("Engine {} not running", engineName);
            return;
        }
        
        logger.info("Stopping engine: {}", engineName);
        
        try {
            // Send shutdown command
            JsonObject shutdownMsg = new JsonObject();
            shutdownMsg.addProperty("id", "shutdown_" + System.currentTimeMillis());
            shutdownMsg.addProperty("type", "SHUTDOWN");
            engineProcess.send(shutdownMsg);
            
            // Stop reader threads
            engineProcess.stopReaders();
            
            // Wait for graceful shutdown
            boolean terminated = engineProcess.getProcess()
                .waitFor(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            if (!terminated) {
                logger.warn("Engine {} did not terminate gracefully, forcing", engineName);
                engineProcess.getProcess().destroyForcibly();
            }
            
            eventBus.publish("engine.stopped", engineName);
            logger.info("Engine {} stopped", engineName);
            
        } catch (Exception e) {
            logger.error("Error stopping engine {}: {}", engineName, e.getMessage());
            engineProcess.getProcess().destroyForcibly();
        }
    }
    
    /**
     * Check if an engine is available and healthy.
     * 
     * @param engineName the engine name
     * @return true if the engine is running and healthy
     */
    public boolean isEngineAvailable(String engineName) {
        EngineProcess engine = engines.get(engineName.toLowerCase());
        return engine != null && engine.isAlive() && engine.isHealthy();
    }
    
    /**
     * Send a message to an engine and get the response.
     * 
     * @param engineName the target engine
     * @param message the JSON message to send
     * @return the response JSON object
     * @throws Exception if communication fails
     */
    public JsonObject sendMessage(String engineName, JsonObject message) throws Exception {
        EngineProcess engineProcess = engines.get(engineName.toLowerCase());
        
        if (engineProcess == null || !engineProcess.isAlive()) {
            throw new IllegalStateException("Engine not available: " + engineName);
        }
        
        if (!engineProcess.isHealthy()) {
            throw new IllegalStateException("Engine not healthy: " + engineName);
        }
        
        return engineProcess.sendAndReceive(message, MESSAGE_TIMEOUT_MS);
    }
    
    /**
     * Send a message to an engine and get the response as string.
     * 
     * @param engineName the target engine
     * @param message the JSON message string
     * @return the response JSON string
     * @throws Exception if communication fails
     */
    public String sendMessage(String engineName, String message) throws Exception {
        JsonObject msgObj = GSON.fromJson(message, JsonObject.class);
        JsonObject response = sendMessage(engineName, msgObj);
        return GSON.toJson(response);
    }
    
    /**
     * Get the number of running engines.
     * 
     * @return the count of running engines
     */
    public int getRunningEngineCount() {
        return (int) engines.values().stream()
            .filter(EngineProcess::isAlive)
            .count();
    }
    
    /**
     * Get engine process info for debugging.
     * 
     * @param engineName the engine name
     * @return map with engine info
     */
    public Map<String, Object> getEngineInfo(String engineName) {
        EngineProcess engine = engines.get(engineName.toLowerCase());
        
        if (engine == null) {
            return Map.of("status", "NOT_RUNNING");
        }
        
        return Map.of(
            "status", engine.isAlive() ? "RUNNING" : "DEAD",
            "healthy", engine.isHealthy(),
            "pid", engine.getProcess().pid()
        );
    }
}

