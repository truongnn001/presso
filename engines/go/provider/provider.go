/*
 * PressO Desktop - Go API Hub Engine
 * ====================================
 *
 * FILE: provider/provider.go
 * RESPONSIBILITY: Provider interface and registry for real HTTP providers
 *
 * ARCHITECTURAL ROLE:
 * - Define common interface for API providers
 * - Registry for provider lookup
 * - Support both mock and real HTTP providers
 *
 * Reference: PROJECT_DOCUMENTATION.md Section 4.5
 */

package provider

import (
	"context"
	"time"
)

// APIResponse represents a normalized API response
type APIResponse struct {
	Success  bool                   `json:"success"`
	Data     map[string]interface{} `json:"data,omitempty"`
	Error    *APIError              `json:"error,omitempty"`
	Metadata ResponseMetadata       `json:"metadata"`
}

// APIError represents an error from the API
type APIError struct {
	Code       string `json:"code"`
	Message    string `json:"message"`
	Details    string `json:"details,omitempty"`
	StatusCode int    `json:"status_code,omitempty"`
}

// ResponseMetadata contains metadata about the response
type ResponseMetadata struct {
	Provider   string `json:"provider"`
	Operation  string `json:"operation"`
	RequestID  string `json:"request_id"`
	Timestamp  int64  `json:"timestamp"`
	LatencyMs  int64  `json:"latency_ms"`
	MockedData bool   `json:"mocked_data"`
	HTTPStatus int    `json:"http_status,omitempty"`
}

// Provider represents an API provider interface
type Provider interface {
	// Name returns the provider identifier
	Name() string
	// SupportedOperations returns list of operations this provider handles
	SupportedOperations() []string
	// Execute performs an operation
	Execute(ctx context.Context, operation string, params map[string]interface{}) (*APIResponse, error)
	// IsMock returns true if this is a mock provider
	IsMock() bool
	// RequiresCredentials returns true if this provider needs credentials
	RequiresCredentials() bool
}

// ProviderConfig holds configuration for a provider
type ProviderConfig struct {
	Name              string            `json:"name"`
	BaseURL           string            `json:"base_url"`
	Timeout           time.Duration     `json:"timeout"`
	UseMock           bool              `json:"use_mock"`
	RateLimitRPM      int               `json:"rate_limit_rpm"` // Requests per minute
	Headers           map[string]string `json:"headers,omitempty"`
}

// DefaultProviderConfig returns default configuration
func DefaultProviderConfig(name string) *ProviderConfig {
	return &ProviderConfig{
		Name:         name,
		Timeout:      30 * time.Second,
		UseMock:      false,
		RateLimitRPM: 60,
	}
}

// Registry holds registered providers
type Registry struct {
	providers map[string]Provider
}

// NewRegistry creates a new provider registry
func NewRegistry() *Registry {
	return &Registry{
		providers: make(map[string]Provider),
	}
}

// Register adds a provider to the registry
func (r *Registry) Register(provider Provider) {
	r.providers[provider.Name()] = provider
}

// Get returns a provider by name
func (r *Registry) Get(name string) (Provider, bool) {
	provider, ok := r.providers[name]
	return provider, ok
}

// List returns all registered provider names
func (r *Registry) List() []string {
	names := make([]string, 0, len(r.providers))
	for name := range r.providers {
		names = append(names, name)
	}
	return names
}

// GetAll returns all registered providers
func (r *Registry) GetAll() map[string]Provider {
	return r.providers
}

// MakeSuccessResponse creates a success response
func MakeSuccessResponse(provider, operation, requestID string, data map[string]interface{}, latencyMs int64, mocked bool) *APIResponse {
	return &APIResponse{
		Success: true,
		Data:    data,
		Metadata: ResponseMetadata{
			Provider:   provider,
			Operation:  operation,
			RequestID:  requestID,
			Timestamp:  time.Now().UnixMilli(),
			LatencyMs:  latencyMs,
			MockedData: mocked,
		},
	}
}

// MakeErrorResponse creates an error response
func MakeErrorResponse(provider, operation, requestID, code, message string, latencyMs int64) *APIResponse {
	return &APIResponse{
		Success: false,
		Error: &APIError{
			Code:    code,
			Message: message,
		},
		Metadata: ResponseMetadata{
			Provider:   provider,
			Operation:  operation,
			RequestID:  requestID,
			Timestamp:  time.Now().UnixMilli(),
			LatencyMs:  latencyMs,
			MockedData: false,
		},
	}
}

