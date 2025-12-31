/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * FILE: SecurityGateway.java
 * RESPONSIBILITY: Access control validation and security policy enforcement
 * 
 * ARCHITECTURAL ROLE:
 * - Validates all incoming IPC messages
 * - Sanitizes file paths against traversal attacks
 * - Enforces operation whitelist
 * - Manages sensitive data handling policies
 * - Controls credential access for API Hub
 * 
 * SECURITY PRINCIPLES (per PROJECT_DOCUMENTATION.md Section 6.1):
 * - Least Privilege: Minimum required permissions per operation
 * - Defense in Depth: Multiple validation layers
 * - Fail Secure: Errors result in denied access
 * - Secure Defaults: All security enabled by default
 * 
 * BOUNDARIES:
 * - Does NOT contain business logic
 * - Does NOT make external network calls
 * - Does NOT store credentials (only validates access)
 */
package com.presso.kernel.security;

import com.presso.kernel.state.StateManager;
import com.presso.kernel.ipc.IpcMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Security gateway for validating operations and enforcing policies.
 * <p>
 * All incoming messages pass through this gateway for validation
 * before being processed by the kernel.
 * </p>
 */
public final class SecurityGateway {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityGateway.class);
    
    // Path traversal patterns to block
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
        "\\.\\.[\\\\/]|[\\\\/]\\.\\."
    );
    
    // Maximum message size (1MB)
    private static final int MAX_MESSAGE_SIZE = 1024 * 1024;
    
    // Maximum file path length
    private static final int MAX_PATH_LENGTH = 260; // Windows MAX_PATH
    
    // Allowed file extensions for processing
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        ".pdf", ".xlsx", ".xls", ".doc", ".docx",
        ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp",
        ".txt", ".csv", ".json", ".xml"
    );
    
    // Blocked file paths (system directories)
    private static final Set<String> BLOCKED_PATHS = Set.of(
        "C:\\Windows",
        "C:\\Program Files",
        "C:\\Program Files (x86)",
        "C:\\ProgramData"
    );
    
    private final StateManager stateManager;
    
    /**
     * Construct a SecurityGateway with the state manager.
     * 
     * @param stateManager the state manager for configuration access
     */
    public SecurityGateway(StateManager stateManager) {
        this.stateManager = stateManager;
        logger.debug("SecurityGateway created");
    }
    
    /**
     * Validate an incoming IPC message.
     * 
     * @param message the message to validate
     * @return true if the message passes all security checks
     */
    public boolean validateMessage(IpcMessage message) {
        if (message == null) {
            logger.warn("Null message rejected");
            return false;
        }
        
        // Check message ID
        if (message.getId() == null || message.getId().isBlank()) {
            logger.warn("Message rejected: missing ID");
            return false;
        }
        
        // Check message type
        if (message.getType() == null || message.getType().isBlank()) {
            logger.warn("Message rejected: missing type");
            return false;
        }
        
        // Check message size (if raw available)
        // TODO: Implement size check when raw message is tracked
        
        // Validate payload if present
        if (message.getPayload() != null) {
            if (!validatePayload(message.getPayload())) {
                logger.warn("Message rejected: invalid payload");
                return false;
            }
        }
        
        logger.debug("Message validated: id={}, type={}", message.getId(), message.getType());
        return true;
    }
    
    /**
     * Validate a message payload.
     * 
     * @param payload the payload to validate
     * @return true if valid
     */
    private boolean validatePayload(Object payload) {
        // TODO (Phase 2): Implement deep payload validation
        // For now, accept non-null payloads
        return true;
    }
    
    /**
     * Sanitize and validate a file path.
     * 
     * @param pathString the path to validate
     * @return the sanitized path, or null if invalid
     */
    public Path sanitizePath(String pathString) {
        if (pathString == null || pathString.isBlank()) {
            logger.warn("Empty path rejected");
            return null;
        }
        
        // Check length
        if (pathString.length() > MAX_PATH_LENGTH) {
            logger.warn("Path too long: {} chars", pathString.length());
            return null;
        }
        
        // Check for path traversal
        if (PATH_TRAVERSAL_PATTERN.matcher(pathString).find()) {
            logger.warn("Path traversal detected: {}", pathString);
            return null;
        }
        
        // Normalize path
        Path path;
        try {
            path = Paths.get(pathString).normalize().toAbsolutePath();
        } catch (Exception e) {
            logger.warn("Invalid path: {}", pathString);
            return null;
        }
        
        // Check against blocked paths
        String pathStr = path.toString();
        for (String blocked : BLOCKED_PATHS) {
            if (pathStr.startsWith(blocked)) {
                logger.warn("Blocked path access: {}", pathString);
                return null;
            }
        }
        
        logger.debug("Path sanitized: {}", path);
        return path;
    }
    
    /**
     * Validate a file extension is allowed.
     * 
     * @param path the file path to check
     * @return true if the extension is allowed
     */
    public boolean isAllowedExtension(Path path) {
        if (path == null) {
            return false;
        }
        
        String fileName = path.getFileName().toString().toLowerCase();
        int dotIndex = fileName.lastIndexOf('.');
        
        if (dotIndex < 0) {
            // No extension
            return false;
        }
        
        String extension = fileName.substring(dotIndex);
        return ALLOWED_EXTENSIONS.contains(extension);
    }
    
    /**
     * Check if an operation is allowed for the current context.
     * 
     * @param operationType the operation type
     * @return true if allowed
     */
    public boolean isOperationAllowed(String operationType) {
        // TODO (Phase 2): Implement role-based access control
        // For now, all known operations are allowed
        return operationType != null && !operationType.isBlank();
    }
    
    /**
     * Validate input data structure.
     * 
     * @param data the data to validate
     * @param expectedType the expected type description
     * @return true if valid
     */
    public boolean validateInputStructure(Object data, String expectedType) {
        // TODO (Phase 2): Implement schema-based validation
        return data != null;
    }
    
    /**
     * Log a security event.
     * 
     * @param eventType the type of security event
     * @param details the event details
     */
    public void logSecurityEvent(String eventType, String details) {
        logger.warn("SECURITY EVENT [{}]: {}", eventType, details);
        // TODO (Phase 3): Write to security audit log in database
    }
}

