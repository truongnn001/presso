/*
 * PressO Desktop - Go API Hub Engine
 * ====================================
 *
 * RESPONSIBILITY:
 * - Act as the ONLY gateway for external network calls
 * - Encapsulate third-party API integrations
 * - Protect Kernel and Python Engine from network access
 * - Stateless message processing
 *
 * ARCHITECTURAL ROLE:
 * - Subprocess spawned by Java Kernel
 * - Communicates via stdin/stdout (JSON)
 * - NO direct database access
 * - NO business logic
 * - NO persistence (no DB)
 *
 * COMMUNICATION PROTOCOL:
 * - Read JSON messages from stdin (one per line)
 * - Write JSON responses to stdout (one per line)
 * - stderr for logging only
 *
 * Reference: PROJECT_DOCUMENTATION.md Section 4.5
 */

package main

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"presso/go-api-hub/api"
	"presso/go-api-hub/http"
	"presso/go-api-hub/mock"
	"presso/go-api-hub/observability"
	"presso/go-api-hub/provider"
	"presso/go-api-hub/secure"
)

// Engine version
const Version = "0.3.0"

// Engine represents the Go API Hub Engine
type Engine struct {
	running          bool
	startTime        time.Time
	config           *Config
	apiClient        *api.APIClient
	// Phase 4 Step 2: Mock providers
	mockRegistry     *mock.ProviderRegistry
	mockCredStore    *mock.CredentialStore
	// Phase 4 Step 3: Secure providers
	httpClient       *http.HTTPClient
	secureCredStore  *secure.SecureCredentialStore
	rateLimiter      *secure.RateLimiter
	providerRegistry *provider.Registry
	// Phase 4 Step 4: Observability
	logger           *observability.StructuredLogger
	metrics          *observability.Metrics
	retryConfig      *observability.RetryConfig
}

// Message represents an incoming IPC message
type Message struct {
	ID      string                 `json:"id"`
	Type    string                 `json:"type"`
	Method  string                 `json:"method"`  // Alternative field name
	Params  map[string]interface{} `json:"params"`
	Payload map[string]interface{} `json:"payload"` // Alternative field name
}

// Response represents an outgoing IPC response
type Response struct {
	ID      string                 `json:"id"`
	Success bool                   `json:"success"`
	Result  map[string]interface{} `json:"result,omitempty"`
	Error   *ErrorResponse         `json:"error,omitempty"`
}

// ErrorResponse represents an error in a response
type ErrorResponse struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

// Config represents engine configuration
type Config struct {
	TimeoutSeconds    int    `json:"timeout_seconds"`
	MaxRetries        int    `json:"max_retries"`
	LogLevel          string `json:"log_level"`
	UseMockProviders  bool   `json:"use_mock_providers"`  // Use mock instead of real HTTP
	RateLimitRPM      int    `json:"rate_limit_rpm"`      // Default requests per minute
	// Phase 4 Step 4: Feature flags
	EnableRetries     bool   `json:"enable_retries"`       // Enable retry logic
	EnableRateLimit   bool   `json:"enable_rate_limit"`    // Enable rate limiting
	EnableProviderJSONPlaceholder bool `json:"enable_provider_jsonplaceholder"` // Enable JSONPlaceholder provider
}

// DefaultConfig returns default configuration
func DefaultConfig() *Config {
	return &Config{
		TimeoutSeconds:   30,
		MaxRetries:       3,
		LogLevel:         "INFO",
		UseMockProviders: false, // Phase 4 Step 3: Default to real HTTP
		RateLimitRPM:     60,    // 1 request per second average
		// Phase 4 Step 4: Feature flags (all enabled by default)
		EnableRetries:     true,
		EnableRateLimit:   true,
		EnableProviderJSONPlaceholder: true,
	}
}

// LoadConfig loads configuration from file or returns defaults
func LoadConfig() (*Config, error) {
	// TODO: Load from config file
	// For now, return defaults
	return DefaultConfig(), nil
}


// NewEngine creates a new engine instance
func NewEngine() (*Engine, error) {
	config, err := LoadConfig()
	if err != nil {
		return nil, fmt.Errorf("failed to load config: %w", err)
	}

	// Phase 4 Step 4: Initialize observability
	logger := observability.NewStructuredLogger(observability.ParseLogLevel(config.LogLevel))
	metrics := observability.NewMetrics()
	retryConfig := observability.DefaultRetryConfig()
	retryConfig.Enabled = config.EnableRetries
	retryConfig.MaxAttempts = config.MaxRetries

	apiClient := api.NewAPIClient(time.Duration(config.TimeoutSeconds) * time.Second)
	
	// Phase 4 Step 2: Initialize mock provider registry (fallback)
	mockRegistry := mock.NewProviderRegistry()
	mockCredStore := mock.NewCredentialStore(mock.GetDefaultConfigPath())
	if err := mockCredStore.LoadFromConfig(); err != nil {
		logger.Warn("Failed to load mock credentials", map[string]interface{}{"error": err.Error()})
	}
	
	// Phase 4 Step 3: Initialize secure components
	httpClient := http.NewHTTPClient(http.DefaultClientConfig())
	
	// Initialize secure credential store with DPAPI encryption
	secureCredStore := secure.NewSecureCredentialStore("")
	if err := secureCredStore.Load(); err != nil {
		logger.Warn("Failed to load secure credentials", map[string]interface{}{"error": err.Error()})
	}
	
	// Initialize rate limiter (if enabled)
	var rateLimiter *secure.RateLimiter
	if config.EnableRateLimit {
		rateLimiter = secure.NewRateLimiter()
	} else {
		rateLimiter = nil
	}
	
	// Initialize provider registry with real HTTP providers
	providerRegistry := provider.NewRegistry()
	
	// Register JSONPlaceholder test provider (if enabled)
	if config.EnableProviderJSONPlaceholder {
		jsonPlaceholder := provider.NewJSONPlaceholderProvider(httpClient, rateLimiter)
		providerRegistry.Register(jsonPlaceholder)
		if rateLimiter != nil {
			rateLimiter.Configure(&secure.RateLimitConfig{
				Provider:          "jsonplaceholder",
				RequestsPerMinute: config.RateLimitRPM,
				BurstSize:         10,
				Enabled:           true,
			})
		}
	}

	return &Engine{
		running:          true,
		startTime:        time.Now(),
		config:           config,
		apiClient:        apiClient,
		mockRegistry:     mockRegistry,
		mockCredStore:    mockCredStore,
		httpClient:       httpClient,
		secureCredStore:  secureCredStore,
		rateLimiter:      rateLimiter,
		providerRegistry: providerRegistry,
		logger:           logger,
		metrics:          metrics,
		retryConfig:     retryConfig,
	}, nil
}

// Run starts the main message processing loop
func (e *Engine) Run() {
	// Phase 4 Step 4: Use structured logging
	e.logger.Info("Engine starting", map[string]interface{}{
		"version": Version,
		"features": map[string]interface{}{
			"retries_enabled":     e.config.EnableRetries,
			"rate_limit_enabled":  e.config.EnableRateLimit,
			"mock_providers":      e.config.UseMockProviders,
		},
	})

	// Signal ready to kernel
	e.sendReady()

	// Set up signal handlers for graceful shutdown
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM)

	// Start message reader in goroutine
	done := make(chan bool)
	go e.readLoop(done)

	// Wait for shutdown signal or EOF
	select {
	case <-sigChan:
		e.logger.Info("Shutdown signal received", nil)
		e.running = false
	case <-done:
		e.logger.Info("stdin closed, shutting down", nil)
		e.running = false
	}

	uptime := time.Since(e.startTime).Seconds()
	e.logger.Info("Engine stopped", map[string]interface{}{
		"uptime_seconds": uptime,
	})
}

// readLoop reads messages from stdin
func (e *Engine) readLoop(done chan bool) {
	scanner := bufio.NewScanner(os.Stdin)
	
	for e.running {
		if !scanner.Scan() {
			// EOF or error
			if err := scanner.Err(); err != nil && err != io.EOF {
				log.Printf("Error reading stdin: %v", err)
			}
			done <- true
			return
		}

		line := scanner.Text()
		if line == "" {
			continue
		}

		// Process message
		e.processMessage(line)
	}
}

// processMessage processes a single message line
func (e *Engine) processMessage(line string) {
	var msg Message
	if err := json.Unmarshal([]byte(line), &msg); err != nil {
		e.logger.Error("Invalid JSON message", err, map[string]interface{}{
			"message_preview": truncateString(line, 100),
		})
		e.sendError("", "PARSE_ERROR", fmt.Sprintf("Invalid JSON: %v", err))
		return
	}
	
	// Phase 4 Step 4: Structured logging for message received
	e.logger.Debug("Message received", map[string]interface{}{
		"request_id": msg.ID,
		"type":       msg.Type,
	})

	// Normalize message type
	msgType := msg.Type
	if msgType == "" {
		msgType = msg.Method
	}
	if msgType == "" {
		e.sendError(msg.ID, "MISSING_TYPE", "Message type not specified")
		return
	}

	// Normalize params
	params := msg.Params
	if params == nil {
		params = msg.Payload
	}
	if params == nil {
		params = make(map[string]interface{})
	}

	// Dispatch to handler
	response := e.dispatch(msgType, params, msg.ID)
	if response != nil {
		e.sendResponse(response)
	}
}

// dispatch routes message to appropriate handler
func (e *Engine) dispatch(msgType string, params map[string]interface{}, msgID string) *Response {
	msgType = normalizeCommand(msgType)

	// Built-in handlers
	switch msgType {
	case "PING":
		return e.handlePing(msgID, params)
	case "SHUTDOWN":
		return e.handleShutdown(msgID, params)
	case "HEALTH_CHECK":
		return e.handleHealthCheck(msgID, params)
	case "GET_STATUS":
		return e.handleStatus(msgID, params)
	
	// Phase 4 Step 2: External API integration (MOCK)
	case "EXTERNAL_API_CALL":
		return e.handleExternalAPICall(msgID, params)
	case "LIST_PROVIDERS":
		return e.handleListProviders(msgID, params)
	case "GET_PROVIDER_INFO":
		return e.handleGetProviderInfo(msgID, params)
	
	// Phase 4 Step 3: Credential management
	case "SAVE_CREDENTIAL":
		return e.handleSaveCredential(msgID, params)
	case "DELETE_CREDENTIAL":
		return e.handleDeleteCredential(msgID, params)
	case "GET_RATE_LIMIT_STATUS":
		return e.handleGetRateLimitStatus(msgID, params)
	case "GET_METRICS":
		return e.handleGetMetrics(msgID, params)
	}

	// Unknown message type
	e.logger.Warn("Unknown message type", map[string]interface{}{
		"request_id": msgID,
		"type":       msgType,
	})
	return e.makeErrorResponse(msgID, "UNKNOWN_TYPE", fmt.Sprintf("Unknown message type: %s", msgType))
}

// handlePing handles PING message
func (e *Engine) handlePing(msgID string, params map[string]interface{}) *Response {
	e.logger.Debug("PING received", map[string]interface{}{
		"request_id": msgID,
	})

	return &Response{
		ID:      msgID,
		Success: true,
		Result: map[string]interface{}{
			"message":   "PONG",
			"engine":    "Go API Hub Engine",
			"version":   Version,
			"timestamp": time.Now().UnixMilli(),
		},
	}
}

// handleShutdown handles SHUTDOWN message
func (e *Engine) handleShutdown(msgID string, params map[string]interface{}) *Response {
	log.Println("SHUTDOWN received, stopping engine")
	
	// Clear credentials from memory (security requirement)
	e.secureCredStore.Clear()
	log.Println("Credentials cleared from memory")
	
	e.running = false

	return &Response{
		ID:      msgID,
		Success: true,
		Result: map[string]interface{}{
			"message":   "Shutdown initiated",
			"timestamp": time.Now().UnixMilli(),
		},
	}
}

// handleHealthCheck handles HEALTH_CHECK message (Phase 4 Step 4: Enhanced)
func (e *Engine) handleHealthCheck(msgID string, params map[string]interface{}) *Response {
	uptime := time.Since(e.startTime).Seconds()
	metricsSnapshot := e.metrics.GetSnapshot()

	return &Response{
		ID:      msgID,
		Success: true,
		Result: map[string]interface{}{
			"healthy":        true,
			"uptime_seconds": uptime,
			"version":        Version,
			"metrics": map[string]interface{}{
				"total_requests": metricsSnapshot["total_requests"],
				"success_count":   metricsSnapshot["success_count"],
				"error_count":     metricsSnapshot["error_count"],
			},
			"last_error_at": metricsSnapshot["last_error_at"],
		},
	}
}

// handleStatus handles GET_STATUS message (Phase 4 Step 4: Enhanced with diagnostics)
func (e *Engine) handleStatus(msgID string, params map[string]interface{}) *Response {
	uptime := time.Since(e.startTime).Seconds()
	metricsSnapshot := e.metrics.GetSnapshot()

	return &Response{
		ID:      msgID,
		Success: true,
		Result: map[string]interface{}{
			"status":        "running",
			"engine":        "go",
			"version":       Version,
			"uptime_seconds": uptime,
			"capabilities": map[string]interface{}{
				"external_api_call":    true,
				"mock_providers":       e.mockRegistry.ListProviders(),
				"http_providers":       e.providerRegistry.List(),
				"secure_credentials":   true,  // Phase 4 Step 3: DPAPI encryption
				"rate_limiting":        e.config.EnableRateLimit,  // Phase 4 Step 3: Token bucket
				"real_http_calls":      true,  // Phase 4 Step 3: Real HTTP
				"retries":              e.config.EnableRetries,    // Phase 4 Step 4: Retry logic
				"structured_logging":   true,  // Phase 4 Step 4: Structured logs
				"metrics":              true,  // Phase 4 Step 4: Metrics collection
				"auth_refresh":         false, // TODO: Future phase
			},
			"secure_storage": map[string]interface{}{
				"path":     e.secureCredStore.GetStoragePath(),
				"loaded":   e.secureCredStore.IsLoaded(),
				"providers": e.secureCredStore.ListProviders(),
			},
			"diagnostics": map[string]interface{}{
				"request_counters": map[string]interface{}{
					"total_requests": metricsSnapshot["total_requests"],
					"success_count":  metricsSnapshot["success_count"],
					"error_count":    metricsSnapshot["error_count"],
					"rate_limit_hits": metricsSnapshot["rate_limit_hits"],
				},
				"latency": map[string]interface{}{
					"avg_latency_ms": metricsSnapshot["avg_latency_ms"],
				},
				"timestamps": map[string]interface{}{
					"first_request_at": metricsSnapshot["first_request_at"],
					"last_request_at":  metricsSnapshot["last_request_at"],
					"last_error_at":    metricsSnapshot["last_error_at"],
				},
			},
			"timestamp": time.Now().UnixMilli(),
		},
	}
}

// handleExternalAPICall handles EXTERNAL_API_CALL command (Phase 4 Step 2, 3 & 4)
func (e *Engine) handleExternalAPICall(msgID string, params map[string]interface{}) *Response {
	// Phase 4 Step 4: Structured logging (no sensitive data)
	e.logger.Debug("EXTERNAL_API_CALL received", map[string]interface{}{
		"request_id": msgID,
	})
	
	// Extract required parameters
	providerName, _ := params["provider"].(string)
	operation, _ := params["operation"].(string)
	requestID, _ := params["request_id"].(string)
	apiParams, _ := params["params"].(map[string]interface{})
	useMock, _ := params["use_mock"].(bool)
	
	// Use message ID as request_id if not provided
	if requestID == "" {
		requestID = msgID
	}
	
	// Validate required fields
	if providerName == "" {
		e.logger.Warn("EXTERNAL_API_CALL missing provider", map[string]interface{}{
			"request_id": msgID,
		})
		return &Response{
			ID:      msgID,
			Success: false,
			Error: &ErrorResponse{
				Code:    "INVALID_REQUEST",
				Message: "Missing required parameter: provider",
			},
		}
	}
	
	if operation == "" {
		e.logger.Warn("EXTERNAL_API_CALL missing operation", map[string]interface{}{
			"request_id": msgID,
			"provider":  providerName,
		})
		return &Response{
			ID:      msgID,
			Success: false,
			Error: &ErrorResponse{
				Code:    "INVALID_REQUEST",
				Message: "Missing required parameter: operation",
			},
		}
	}
	
	if apiParams == nil {
		apiParams = make(map[string]interface{})
	}
	
	// Determine whether to use mock or real provider
	// Priority: explicit param > config > check if mock provider exists
	shouldUseMock := useMock || e.config.UseMockProviders || strings.HasPrefix(providerName, "mock_")
	
	if shouldUseMock {
		return e.executeWithMockProvider(msgID, providerName, operation, requestID, apiParams)
	}
	
	return e.executeWithHTTPProvider(msgID, providerName, operation, requestID, apiParams)
}

// executeWithMockProvider executes using mock provider (Phase 4 Step 2 & 4)
func (e *Engine) executeWithMockProvider(msgID, providerName, operation, requestID string, apiParams map[string]interface{}) *Response {
	startTime := time.Now()
	
	// Phase 4 Step 4: Structured logging
	e.logger.LogAPIRequest(requestID, providerName, operation, map[string]interface{}{
		"provider_type": "mock",
	})
	
	// Get mock provider
	providerImpl, ok := e.mockRegistry.GetProvider(providerName)
	if !ok {
		e.logger.Error("Unknown mock provider", nil, map[string]interface{}{
			"request_id": requestID,
			"provider":   providerName,
		})
		return &Response{
			ID:      msgID,
			Success: false,
			Error: &ErrorResponse{
				Code:    "UNKNOWN_PROVIDER",
				Message: fmt.Sprintf("Mock provider '%s' not found", providerName),
			},
		}
	}
	
	// Validate mock credentials
	if err := e.mockCredStore.ValidateCredential(providerName); err != nil {
		e.logger.Error("Mock credential validation failed", err, map[string]interface{}{
			"request_id": requestID,
			"provider":   providerName,
		})
		return &Response{
			ID:      msgID,
			Success: false,
			Error: &ErrorResponse{
				Code:    "CREDENTIAL_ERROR",
				Message: fmt.Sprintf("Credential validation failed: %v", err),
			},
		}
	}
	
	// Execute mock API call
	apiResponse, err := providerImpl.Execute(operation, apiParams)
	durationMs := time.Since(startTime).Milliseconds()
	
	// Phase 4 Step 4: Record metrics
	success := err == nil && apiResponse != nil && apiResponse.Success
	e.metrics.RecordRequest(providerName, success, durationMs)
	
	if err != nil {
		e.logger.LogAPIResponse(requestID, providerName, operation, false, durationMs, map[string]interface{}{
			"error": err.Error(),
		})
		return &Response{
			ID:      msgID,
			Success: false,
			Error: &ErrorResponse{
				Code:    "EXECUTION_ERROR",
				Message: fmt.Sprintf("Mock API call failed: %v", err),
			},
		}
	}
	
	apiResponse.Metadata.RequestID = requestID
	
	// Phase 4 Step 4: Structured logging
	e.logger.LogAPIResponse(requestID, providerName, operation, apiResponse.Success, durationMs, map[string]interface{}{
		"provider_type": "mock",
	})
	
	if apiResponse.Success {
		return &Response{
			ID:      msgID,
			Success: true,
			Result: map[string]interface{}{
				"success":  true,
				"data":     apiResponse.Data,
				"metadata": apiResponse.Metadata,
			},
		}
	}
	
	return &Response{
		ID:      msgID,
		Success: false,
		Error: &ErrorResponse{
			Code:    apiResponse.Error.Code,
			Message: apiResponse.Error.Message,
		},
	}
}

// executeWithHTTPProvider executes using real HTTP provider (Phase 4 Step 3 & 4)
func (e *Engine) executeWithHTTPProvider(msgID, providerName, operation, requestID string, apiParams map[string]interface{}) *Response {
	startTime := time.Now()
	
	// Phase 4 Step 4: Structured logging for request start
	e.logger.LogAPIRequest(requestID, providerName, operation, nil)
	
	// Get HTTP provider
	providerImpl, ok := e.providerRegistry.Get(providerName)
	if !ok {
		e.logger.Error("Unknown HTTP provider", nil, map[string]interface{}{
			"request_id": requestID,
			"provider":   providerName,
		})
		return &Response{
			ID:      msgID,
			Success: false,
			Error: &ErrorResponse{
				Code:    "UNKNOWN_PROVIDER",
				Message: fmt.Sprintf("HTTP provider '%s' not found", providerName),
			},
		}
	}
	
	// Validate credentials if required
	if providerImpl.RequiresCredentials() {
		if err := e.secureCredStore.ValidateCredential(providerName); err != nil {
			e.logger.Error("Credential validation failed", err, map[string]interface{}{
				"request_id": requestID,
				"provider":   providerName,
			})
			return &Response{
				ID:      msgID,
				Success: false,
				Error: &ErrorResponse{
					Code:    "CREDENTIAL_ERROR",
					Message: fmt.Sprintf("Credential validation failed: %v", err),
				},
			}
		}
	}
	
	// Create context with timeout
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(e.config.TimeoutSeconds)*time.Second)
	defer cancel()
	
	// Phase 4 Step 4: Execute with retry logic
	var apiResponse *provider.APIResponse
	var err error
	
	if e.config.EnableRetries {
		// Use retry wrapper
		result, retryErr := observability.RetryWithBackoff(
			ctx,
			e.retryConfig,
			e.logger,
			requestID,
			providerName,
			operation,
			func(attempt int) (interface{}, error, int) {
				resp, execErr := providerImpl.Execute(ctx, operation, apiParams)
				if execErr != nil {
					return nil, execErr, 0
				}
				// Extract HTTP status from response
				httpStatus := 0
				if resp != nil {
					httpStatus = resp.Metadata.HTTPStatus
				}
				return resp, execErr, httpStatus
			},
		)
		
		if retryErr != nil {
			err = retryErr
		} else if result != nil {
			apiResponse = result.(*provider.APIResponse)
		}
	} else {
		// Execute without retry
		apiResponse, err = providerImpl.Execute(ctx, operation, apiParams)
	}
	
	durationMs := time.Since(startTime).Milliseconds()
	
	// Phase 4 Step 4: Record metrics and handle rate limits
	success := err == nil && apiResponse != nil && apiResponse.Success
	if apiResponse != nil && apiResponse.Error != nil && apiResponse.Error.Code == "RATE_LIMITED" {
		// Record rate limit hit
		e.metrics.RecordRateLimit(providerName)
		e.logger.LogRateLimit(providerName, 0) // Retry after not available in response
		success = false
	} else {
		e.metrics.RecordRequest(providerName, success, durationMs)
	}
	
	if err != nil {
		e.logger.LogAPIResponse(requestID, providerName, operation, false, durationMs, map[string]interface{}{
			"error": err.Error(),
		})
		return &Response{
			ID:      msgID,
			Success: false,
			Error: &ErrorResponse{
				Code:    "EXECUTION_ERROR",
				Message: fmt.Sprintf("HTTP API call failed: %v", err),
			},
		}
	}
	
	apiResponse.Metadata.RequestID = requestID
	
	// Phase 4 Step 4: Structured logging for response
	e.logger.LogAPIResponse(requestID, providerName, operation, apiResponse.Success, durationMs, map[string]interface{}{
		"http_status": apiResponse.Metadata.HTTPStatus,
	})
	
	if apiResponse.Success {
		return &Response{
			ID:      msgID,
			Success: true,
			Result: map[string]interface{}{
				"success":  true,
				"data":     apiResponse.Data,
				"metadata": apiResponse.Metadata,
			},
		}
	}
	
	return &Response{
		ID:      msgID,
		Success: false,
		Error: &ErrorResponse{
			Code:    apiResponse.Error.Code,
			Message: apiResponse.Error.Message,
		},
	}
}

// handleListProviders returns all available providers (mock and HTTP)
func (e *Engine) handleListProviders(msgID string, params map[string]interface{}) *Response {
	providerInfo := make([]map[string]interface{}, 0)
	
	// Add mock providers
	for _, name := range e.mockRegistry.ListProviders() {
		prov, _ := e.mockRegistry.GetProvider(name)
		credMeta := e.mockCredStore.GetCredentialMetadata(name)
		
		providerInfo = append(providerInfo, map[string]interface{}{
			"name":        name,
			"type":        "mock",
			"operations":  prov.SupportedOperations(),
			"credentials": credMeta,
		})
	}
	
	// Add HTTP providers
	for _, name := range e.providerRegistry.List() {
		prov, _ := e.providerRegistry.Get(name)
		var credMeta interface{}
		if prov.RequiresCredentials() {
			credMeta = e.secureCredStore.GetCredentialMetadata(name)
		} else {
			credMeta = map[string]interface{}{"required": false}
		}
		rateLimitStatus := e.rateLimiter.GetStatus(name)
		
		providerInfo = append(providerInfo, map[string]interface{}{
			"name":        name,
			"type":        "http",
			"operations":  prov.SupportedOperations(),
			"credentials": credMeta,
			"rate_limit":  rateLimitStatus,
		})
	}
	
	return &Response{
		ID:      msgID,
		Success: true,
		Result: map[string]interface{}{
			"providers": providerInfo,
			"total":     len(providerInfo),
		},
	}
}

// handleGetProviderInfo returns info about a specific provider
func (e *Engine) handleGetProviderInfo(msgID string, params map[string]interface{}) *Response {
	providerName, _ := params["provider"].(string)
	if providerName == "" {
		return &Response{
			ID:      msgID,
			Success: false,
			Error: &ErrorResponse{
				Code:    "INVALID_REQUEST",
				Message: "Missing required parameter: provider",
			},
		}
	}
	
	// Check mock providers first
	if mockProv, ok := e.mockRegistry.GetProvider(providerName); ok {
		credMeta := e.mockCredStore.GetCredentialMetadata(providerName)
		return &Response{
			ID:      msgID,
			Success: true,
			Result: map[string]interface{}{
				"name":        providerName,
				"type":        "mock",
				"operations":  mockProv.SupportedOperations(),
				"credentials": credMeta,
			},
		}
	}
	
	// Check HTTP providers
	if httpProv, ok := e.providerRegistry.Get(providerName); ok {
		var credMeta interface{}
		if httpProv.RequiresCredentials() {
			credMeta = e.secureCredStore.GetCredentialMetadata(providerName)
		} else {
			credMeta = map[string]interface{}{"required": false}
		}
		rateLimitStatus := e.rateLimiter.GetStatus(providerName)
		
		return &Response{
			ID:      msgID,
			Success: true,
			Result: map[string]interface{}{
				"name":        providerName,
				"type":        "http",
				"operations":  httpProv.SupportedOperations(),
				"credentials": credMeta,
				"rate_limit":  rateLimitStatus,
			},
		}
	}
	
	return &Response{
		ID:      msgID,
		Success: false,
		Error: &ErrorResponse{
			Code:    "UNKNOWN_PROVIDER",
			Message: fmt.Sprintf("Provider '%s' not found", providerName),
		},
	}
}

// handleSaveCredential saves encrypted credentials (Phase 4 Step 3)
func (e *Engine) handleSaveCredential(msgID string, params map[string]interface{}) *Response {
	providerName, _ := params["provider"].(string)
	if providerName == "" {
		return &Response{
			ID:      msgID,
			Success: false,
			Error: &ErrorResponse{
				Code:    "INVALID_REQUEST",
				Message: "Missing required parameter: provider",
			},
		}
	}
	
	// Build credential from params (NEVER log these values)
	cred := &secure.Credential{
		Provider: providerName,
	}
	
	if apiKey, ok := params["api_key"].(string); ok {
		cred.APIKey = apiKey
	}
	if apiSecret, ok := params["api_secret"].(string); ok {
		cred.APISecret = apiSecret
	}
	if accessToken, ok := params["access_token"].(string); ok {
		cred.AccessToken = accessToken
	}
	if baseURL, ok := params["base_url"].(string); ok {
		cred.BaseURL = baseURL
	}
	if expiresAt, ok := params["expires_at"].(float64); ok {
		cred.ExpiresAt = int64(expiresAt)
	}
	
	// Save encrypted credential
	if err := e.secureCredStore.SaveCredential(providerName, cred); err != nil {
		log.Printf("Failed to save credential for provider %s: %v", providerName, err)
		return &Response{
			ID:      msgID,
			Success: false,
			Error: &ErrorResponse{
				Code:    "SAVE_ERROR",
				Message: fmt.Sprintf("Failed to save credential: %v", err),
			},
		}
	}
	
	// Log only that credential was saved (NO values)
	log.Printf("Credential saved for provider: %s", providerName)
	
	return &Response{
		ID:      msgID,
		Success: true,
		Result: map[string]interface{}{
			"message":  "Credential saved successfully",
			"provider": providerName,
		},
	}
}

// handleDeleteCredential removes credentials (Phase 4 Step 3)
func (e *Engine) handleDeleteCredential(msgID string, params map[string]interface{}) *Response {
	providerName, _ := params["provider"].(string)
	if providerName == "" {
		return &Response{
			ID:      msgID,
			Success: false,
			Error: &ErrorResponse{
				Code:    "INVALID_REQUEST",
				Message: "Missing required parameter: provider",
			},
		}
	}
	
	if err := e.secureCredStore.DeleteCredential(providerName); err != nil {
		return &Response{
			ID:      msgID,
			Success: false,
			Error: &ErrorResponse{
				Code:    "DELETE_ERROR",
				Message: fmt.Sprintf("Failed to delete credential: %v", err),
			},
		}
	}
	
	log.Printf("Credential deleted for provider: %s", providerName)
	
	return &Response{
		ID:      msgID,
		Success: true,
		Result: map[string]interface{}{
			"message":  "Credential deleted successfully",
			"provider": providerName,
		},
	}
}

// handleGetRateLimitStatus returns rate limit status (Phase 4 Step 3)
func (e *Engine) handleGetRateLimitStatus(msgID string, params map[string]interface{}) *Response {
	if e.rateLimiter == nil {
		return &Response{
			ID:      msgID,
			Success: false,
			Error: &ErrorResponse{
				Code:    "FEATURE_DISABLED",
				Message: "Rate limiting is disabled",
			},
		}
	}
	
	providerName, _ := params["provider"].(string)
	
	if providerName != "" {
		status := e.rateLimiter.GetStatus(providerName)
		return &Response{
			ID:      msgID,
			Success: true,
			Result:  status,
		}
	}
	
	// Return all rate limit statuses
	statuses := e.rateLimiter.GetAllStatus()
	return &Response{
		ID:      msgID,
		Success: true,
		Result: map[string]interface{}{
			"rate_limits": statuses,
		},
	}
}

// handleGetMetrics returns metrics snapshot (Phase 4 Step 4)
func (e *Engine) handleGetMetrics(msgID string, params map[string]interface{}) *Response {
	snapshot := e.metrics.GetSnapshot()
	
	return &Response{
		ID:      msgID,
		Success: true,
		Result:  snapshot,
	}
}

// truncateString truncates a string to max length
func truncateString(s string, maxLen int) string {
	if len(s) <= maxLen {
		return s
	}
	return s[:maxLen] + "..."
}

// sendReady sends READY signal to kernel
func (e *Engine) sendReady() {
	capabilities := []string{
		"PING", "SHUTDOWN", "HEALTH_CHECK", "GET_STATUS",
		// Phase 4 Step 2: Mock external API capabilities
		"EXTERNAL_API_CALL", "LIST_PROVIDERS", "GET_PROVIDER_INFO",
		// Phase 4 Step 3: Secure credential management
		"SAVE_CREDENTIAL", "DELETE_CREDENTIAL", "GET_RATE_LIMIT_STATUS",
	}

	readyMsg := map[string]interface{}{
		"type":       "READY",
		"engine":     "go",
		"version":    Version,
		"capabilities": capabilities,
		"features": map[string]interface{}{
			"secure_credentials": true,
			"rate_limiting":      true,
			"real_http":          true,
		},
		"timestamp": time.Now().UnixMilli(),
	}

	e.sendJSON(readyMsg)
	log.Printf("READY signal sent, capabilities: %v", capabilities)
}

// sendResponse sends a response to stdout
func (e *Engine) sendResponse(resp *Response) {
	e.sendJSON(resp)
}

// sendError sends an error response
func (e *Engine) sendError(msgID, code, message string) {
	resp := e.makeErrorResponse(msgID, code, message)
	e.sendResponse(resp)
}

// makeErrorResponse creates an error response
func (e *Engine) makeErrorResponse(msgID, code, message string) *Response {
	return &Response{
		ID:      msgID,
		Success: false,
		Error: &ErrorResponse{
			Code:    code,
			Message: message,
		},
	}
}

// sendJSON sends a JSON object to stdout
func (e *Engine) sendJSON(obj interface{}) {
	data, err := json.Marshal(obj)
	if err != nil {
		e.logger.Error("Error marshaling JSON", err, nil)
		return
	}

	fmt.Println(string(data))
	// Phase 4 Step 4: Only log in debug mode to avoid noise
	e.logger.Debug("Sent IPC response", map[string]interface{}{
		"response_preview": truncateString(string(data), 200),
	})
}

// normalizeCommand normalizes command name to uppercase
func normalizeCommand(cmd string) string {
	// Simple uppercase normalization
	result := ""
	for _, r := range cmd {
		if r >= 'a' && r <= 'z' {
			result += string(r - 32)
		} else {
			result += string(r)
		}
	}
	return result
}

func main() {
	engine, err := NewEngine()
	if err != nil {
		log.Fatalf("Failed to initialize engine: %v", err)
		os.Exit(1)
	}

	engine.Run()
	os.Exit(0)
}

