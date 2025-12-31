/*
 * PressO Desktop - Go API Hub Engine
 * ====================================
 *
 * FILE: provider/http_provider.go
 * RESPONSIBILITY: Real HTTP provider implementation
 *
 * ARCHITECTURAL ROLE:
 * - Execute real HTTP requests to external APIs
 * - Use secure credentials from credential store
 * - Normalize responses to internal format
 *
 * SECURITY REQUIREMENTS:
 * - NEVER log credentials or auth headers
 * - Use TLS for all requests
 * - Respect rate limits
 *
 * Reference: PROJECT_DOCUMENTATION.md Section 4.5
 */

package provider

import (
	"context"
	"fmt"
	"log"
	"time"

	"presso/go-api-hub/http"
	"presso/go-api-hub/secure"
)

// HTTPProvider implements Provider interface with real HTTP calls
type HTTPProvider struct {
	name            string
	baseURL         string
	operations      map[string]OperationConfig
	httpClient      *http.HTTPClient
	credentialStore *secure.SecureCredentialStore
	rateLimiter     *secure.RateLimiter
}

// OperationConfig defines how an operation maps to HTTP
type OperationConfig struct {
	Method      string
	Path        string
	AuthType    string // "api_key", "bearer", "basic", "none"
	AuthHeader  string // Header name for auth (e.g., "X-API-Key", "Authorization")
}

// NewHTTPProvider creates a new HTTP provider
func NewHTTPProvider(
	name string,
	baseURL string,
	operations map[string]OperationConfig,
	httpClient *http.HTTPClient,
	credentialStore *secure.SecureCredentialStore,
	rateLimiter *secure.RateLimiter,
) *HTTPProvider {
	return &HTTPProvider{
		name:            name,
		baseURL:         baseURL,
		operations:      operations,
		httpClient:      httpClient,
		credentialStore: credentialStore,
		rateLimiter:     rateLimiter,
	}
}

func (p *HTTPProvider) Name() string {
	return p.name
}

func (p *HTTPProvider) SupportedOperations() []string {
	ops := make([]string, 0, len(p.operations))
	for op := range p.operations {
		ops = append(ops, op)
	}
	return ops
}

func (p *HTTPProvider) IsMock() bool {
	return false
}

func (p *HTTPProvider) RequiresCredentials() bool {
	return true
}

func (p *HTTPProvider) Execute(ctx context.Context, operation string, params map[string]interface{}) (*APIResponse, error) {
	startTime := time.Now()
	
	// Check rate limit
	if p.rateLimiter != nil {
		if err := p.rateLimiter.Allow(p.name); err != nil {
			if rlErr, ok := err.(*secure.RateLimitError); ok {
				return &APIResponse{
					Success: false,
					Error: &APIError{
						Code:    "RATE_LIMITED",
						Message: rlErr.Message,
					},
					Metadata: ResponseMetadata{
						Provider:   p.name,
						Operation:  operation,
						Timestamp:  time.Now().UnixMilli(),
						LatencyMs:  time.Since(startTime).Milliseconds(),
					},
				}, nil
			}
			return nil, err
		}
	}
	
	// Get operation config
	opConfig, ok := p.operations[operation]
	if !ok {
		return &APIResponse{
			Success: false,
			Error: &APIError{
				Code:    "UNSUPPORTED_OPERATION",
				Message: fmt.Sprintf("Operation '%s' not supported by provider '%s'", operation, p.name),
			},
			Metadata: ResponseMetadata{
				Provider:   p.name,
				Operation:  operation,
				Timestamp:  time.Now().UnixMilli(),
				LatencyMs:  time.Since(startTime).Milliseconds(),
			},
		}, nil
	}
	
	// Build headers with auth
	headers := make(map[string]string)
	if err := p.addAuthHeaders(headers, opConfig); err != nil {
		return &APIResponse{
			Success: false,
			Error: &APIError{
				Code:    "CREDENTIAL_ERROR",
				Message: err.Error(),
			},
			Metadata: ResponseMetadata{
				Provider:   p.name,
				Operation:  operation,
				Timestamp:  time.Now().UnixMilli(),
				LatencyMs:  time.Since(startTime).Milliseconds(),
			},
		}, nil
	}
	
	// Build URL
	url := p.baseURL + opConfig.Path
	
	// Build request
	req := &http.Request{
		Method:  opConfig.Method,
		URL:     url,
		Headers: headers,
	}
	
	// Add body for POST/PUT
	if opConfig.Method == "POST" || opConfig.Method == "PUT" || opConfig.Method == "PATCH" {
		req.Body = params
	} else {
		// Add params as query string
		queryParams := make(map[string]string)
		for k, v := range params {
			if str, ok := v.(string); ok {
				queryParams[k] = str
			} else {
				queryParams[k] = fmt.Sprintf("%v", v)
			}
		}
		req.QueryParams = queryParams
	}
	
	// Log operation (NO credentials)
	log.Printf("Executing HTTP request: provider=%s, operation=%s, method=%s", p.name, operation, opConfig.Method)
	
	// Execute request
	resp, err := p.httpClient.Do(ctx, req)
	latencyMs := time.Since(startTime).Milliseconds()
	
	if err != nil {
		return &APIResponse{
			Success: false,
			Error: &APIError{
				Code:    "NETWORK_ERROR",
				Message: err.Error(),
			},
			Metadata: ResponseMetadata{
				Provider:   p.name,
				Operation:  operation,
				Timestamp:  time.Now().UnixMilli(),
				LatencyMs:  latencyMs,
			},
		}, nil
	}
	
	// Convert response
	return p.convertResponse(resp, operation, latencyMs), nil
}

// addAuthHeaders adds authentication headers based on credentials
// SECURITY: This function handles secrets - never log the values
func (p *HTTPProvider) addAuthHeaders(headers map[string]string, opConfig OperationConfig) error {
	if opConfig.AuthType == "none" || opConfig.AuthType == "" {
		return nil
	}
	
	// Get credentials
	cred, err := p.credentialStore.LoadCredential(p.name)
	if err != nil {
		return fmt.Errorf("failed to load credentials: %w", err)
	}
	
	switch opConfig.AuthType {
	case "api_key":
		if cred.APIKey == "" {
			return fmt.Errorf("API key not configured for provider: %s", p.name)
		}
		headerName := opConfig.AuthHeader
		if headerName == "" {
			headerName = "X-API-Key"
		}
		headers[headerName] = cred.APIKey
		
	case "bearer":
		token := cred.AccessToken
		if token == "" {
			token = cred.APIKey // Fallback to API key as bearer token
		}
		if token == "" {
			return fmt.Errorf("access token not configured for provider: %s", p.name)
		}
		headers["Authorization"] = "Bearer " + token
		
	case "basic":
		// Basic auth would use APIKey as username and APISecret as password
		// Not implemented in this phase
		return fmt.Errorf("basic auth not yet implemented")
		
	default:
		return fmt.Errorf("unknown auth type: %s", opConfig.AuthType)
	}
	
	return nil
}

// convertResponse converts HTTP response to APIResponse
func (p *HTTPProvider) convertResponse(resp *http.Response, operation string, latencyMs int64) *APIResponse {
	metadata := ResponseMetadata{
		Provider:   p.name,
		Operation:  operation,
		Timestamp:  time.Now().UnixMilli(),
		LatencyMs:  latencyMs,
		HTTPStatus: resp.StatusCode,
		MockedData: false,
	}
	
	if resp.IsSuccess() {
		// Success response
		data := make(map[string]interface{})
		if resp.Body != nil {
			switch v := resp.Body.(type) {
			case map[string]interface{}:
				data = v
			default:
				data["response"] = resp.Body
			}
		}
		
		return &APIResponse{
			Success:  true,
			Data:     data,
			Metadata: metadata,
		}
	}
	
	// Error response
	errorCode := resp.GetErrorCode()
	errorMessage := fmt.Sprintf("HTTP %d: %s", resp.StatusCode, errorCode)
	
	// Try to extract error message from response body
	if bodyMap, ok := resp.Body.(map[string]interface{}); ok {
		if msg, ok := bodyMap["message"].(string); ok {
			errorMessage = msg
		} else if msg, ok := bodyMap["error"].(string); ok {
			errorMessage = msg
		} else if errObj, ok := bodyMap["error"].(map[string]interface{}); ok {
			if msg, ok := errObj["message"].(string); ok {
				errorMessage = msg
			}
		}
	}
	
	return &APIResponse{
		Success: false,
		Error: &APIError{
			Code:       errorCode,
			Message:    errorMessage,
			StatusCode: resp.StatusCode,
		},
		Metadata: metadata,
	}
}

// =============================================================================
// Pre-configured providers
// =============================================================================

// NewGenericHTTPProvider creates a generic HTTP provider
func NewGenericHTTPProvider(
	name string,
	baseURL string,
	httpClient *http.HTTPClient,
	credentialStore *secure.SecureCredentialStore,
	rateLimiter *secure.RateLimiter,
) *HTTPProvider {
	operations := map[string]OperationConfig{
		"get": {
			Method:   "GET",
			Path:     "",
			AuthType: "api_key",
		},
		"post": {
			Method:   "POST",
			Path:     "",
			AuthType: "api_key",
		},
	}
	
	return NewHTTPProvider(name, baseURL, operations, httpClient, credentialStore, rateLimiter)
}

// NoCredentialHTTPProvider is an HTTP provider that doesn't require credentials
type NoCredentialHTTPProvider struct {
	*HTTPProvider
}

func (p *NoCredentialHTTPProvider) RequiresCredentials() bool {
	return false
}

// NewJSONPlaceholderProvider creates a test provider using JSONPlaceholder API
// This is a FREE, public API for testing - NO credentials required
func NewJSONPlaceholderProvider(
	httpClient *http.HTTPClient,
	rateLimiter *secure.RateLimiter,
) *NoCredentialHTTPProvider {
	operations := map[string]OperationConfig{
		"get_posts": {
			Method:   "GET",
			Path:     "/posts",
			AuthType: "none",
		},
		"get_post": {
			Method:   "GET",
			Path:     "/posts",
			AuthType: "none",
		},
		"get_users": {
			Method:   "GET",
			Path:     "/users",
			AuthType: "none",
		},
		"get_user": {
			Method:   "GET",
			Path:     "/users",
			AuthType: "none",
		},
		"create_post": {
			Method:   "POST",
			Path:     "/posts",
			AuthType: "none",
		},
	}
	
	baseProvider := NewHTTPProvider(
		"jsonplaceholder",
		"https://jsonplaceholder.typicode.com",
		operations,
		httpClient,
		nil, // No credentials needed
		rateLimiter,
	)
	
	return &NoCredentialHTTPProvider{HTTPProvider: baseProvider}
}

