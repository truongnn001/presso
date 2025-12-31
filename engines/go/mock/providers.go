/*
 * PressO Desktop - Go API Hub Engine
 * ====================================
 *
 * FILE: mock/providers.go
 * RESPONSIBILITY: Mock API providers for Phase 4 Step 2
 *
 * ARCHITECTURAL ROLE:
 * - Simulate external API behavior WITHOUT real network calls
 * - Return deterministic responses based on provider/operation
 * - Support error simulation for testing
 *
 * MOCK-FIRST STRATEGY:
 * - NO real HTTP calls
 * - NO actual external services
 * - Deterministic, testable responses
 *
 * Reference: PROJECT_DOCUMENTATION.md Section 4.5
 */

package mock

import (
	"fmt"
	"time"
)

// Provider represents a mock API provider
type Provider interface {
	// Name returns the provider identifier
	Name() string
	// SupportedOperations returns list of operations this provider handles
	SupportedOperations() []string
	// Execute performs a mock operation
	Execute(operation string, params map[string]interface{}) (*APIResponse, error)
}

// APIResponse represents a normalized API response
type APIResponse struct {
	Success  bool                   `json:"success"`
	Data     map[string]interface{} `json:"data,omitempty"`
	Error    *APIError              `json:"error,omitempty"`
	Metadata ResponseMetadata       `json:"metadata"`
}

// APIError represents an error from the API
type APIError struct {
	Code    string `json:"code"`
	Message string `json:"message"`
	Details string `json:"details,omitempty"`
}

// ResponseMetadata contains metadata about the response
type ResponseMetadata struct {
	Provider    string `json:"provider"`
	Operation   string `json:"operation"`
	RequestID   string `json:"request_id"`
	Timestamp   int64  `json:"timestamp"`
	MockedData  bool   `json:"mocked_data"`
	LatencyMs   int64  `json:"latency_ms"`
}

// ProviderRegistry holds all registered mock providers
type ProviderRegistry struct {
	providers map[string]Provider
}

// NewProviderRegistry creates a new registry with default providers
func NewProviderRegistry() *ProviderRegistry {
	registry := &ProviderRegistry{
		providers: make(map[string]Provider),
	}
	
	// Register default mock providers
	registry.Register(NewMockTaxProvider())
	registry.Register(NewMockCRMProvider())
	registry.Register(NewMockGenericProvider())
	
	return registry
}

// Register adds a provider to the registry
func (r *ProviderRegistry) Register(provider Provider) {
	r.providers[provider.Name()] = provider
}

// GetProvider returns a provider by name
func (r *ProviderRegistry) GetProvider(name string) (Provider, bool) {
	provider, ok := r.providers[name]
	return provider, ok
}

// ListProviders returns all registered provider names
func (r *ProviderRegistry) ListProviders() []string {
	names := make([]string, 0, len(r.providers))
	for name := range r.providers {
		names = append(names, name)
	}
	return names
}

// =============================================================================
// Mock Tax Provider
// =============================================================================

// MockTaxProvider simulates a tax code lookup API
type MockTaxProvider struct {
	// Mock database of tax codes
	taxDatabase map[string]TaxCodeInfo
}

// TaxCodeInfo represents tax code information
type TaxCodeInfo struct {
	TaxCode     string `json:"tax_code"`
	CompanyName string `json:"company_name"`
	Address     string `json:"address"`
	Status      string `json:"status"`
}

// NewMockTaxProvider creates a new mock tax provider
func NewMockTaxProvider() *MockTaxProvider {
	return &MockTaxProvider{
		taxDatabase: map[string]TaxCodeInfo{
			"0100000000": {
				TaxCode:     "0100000000",
				CompanyName: "Công ty TNHH ABC",
				Address:     "123 Đường Nguyễn Huệ, Quận 1, TP.HCM",
				Status:      "active",
			},
			"0200000000": {
				TaxCode:     "0200000000",
				CompanyName: "Công ty Cổ phần XYZ",
				Address:     "456 Đường Lê Lợi, Quận 3, TP.HCM",
				Status:      "active",
			},
			"0300000000": {
				TaxCode:     "0300000000",
				CompanyName: "Công ty TNHH DEF",
				Address:     "789 Đường Hai Bà Trưng, Quận 1, TP.HCM",
				Status:      "suspended",
			},
			// Special test codes for error simulation
			"ERROR_TIMEOUT": {},
			"ERROR_AUTH":    {},
			"ERROR_INVALID": {},
		},
	}
}

func (p *MockTaxProvider) Name() string {
	return "mock_tax"
}

func (p *MockTaxProvider) SupportedOperations() []string {
	return []string{"lookup", "validate", "batch_lookup"}
}

func (p *MockTaxProvider) Execute(operation string, params map[string]interface{}) (*APIResponse, error) {
	startTime := time.Now()
	
	// Simulate network latency (50-150ms)
	time.Sleep(75 * time.Millisecond)
	
	switch operation {
	case "lookup":
		return p.handleLookup(params, startTime)
	case "validate":
		return p.handleValidate(params, startTime)
	case "batch_lookup":
		return p.handleBatchLookup(params, startTime)
	default:
		return &APIResponse{
			Success: false,
			Error: &APIError{
				Code:    "UNSUPPORTED_OPERATION",
				Message: fmt.Sprintf("Operation '%s' not supported by mock_tax provider", operation),
			},
			Metadata: ResponseMetadata{
				Provider:   p.Name(),
				Operation:  operation,
				Timestamp:  time.Now().UnixMilli(),
				MockedData: true,
				LatencyMs:  time.Since(startTime).Milliseconds(),
			},
		}, nil
	}
}

func (p *MockTaxProvider) handleLookup(params map[string]interface{}, startTime time.Time) (*APIResponse, error) {
	taxCode, ok := params["tax_code"].(string)
	if !ok || taxCode == "" {
		return &APIResponse{
			Success: false,
			Error: &APIError{
				Code:    "INVALID_REQUEST",
				Message: "Missing required parameter: tax_code",
			},
			Metadata: ResponseMetadata{
				Provider:   p.Name(),
				Operation:  "lookup",
				Timestamp:  time.Now().UnixMilli(),
				MockedData: true,
				LatencyMs:  time.Since(startTime).Milliseconds(),
			},
		}, nil
	}
	
	// Check for error simulation codes
	switch taxCode {
	case "ERROR_TIMEOUT":
		// Simulate timeout (5 seconds)
		time.Sleep(5 * time.Second)
		return &APIResponse{
			Success: false,
			Error: &APIError{
				Code:    "TIMEOUT",
				Message: "Request timed out",
				Details: "Simulated timeout for testing",
			},
			Metadata: ResponseMetadata{
				Provider:   p.Name(),
				Operation:  "lookup",
				Timestamp:  time.Now().UnixMilli(),
				MockedData: true,
				LatencyMs:  time.Since(startTime).Milliseconds(),
			},
		}, nil
		
	case "ERROR_AUTH":
		return &APIResponse{
			Success: false,
			Error: &APIError{
				Code:    "AUTHENTICATION_FAILED",
				Message: "Invalid or expired credentials",
				Details: "Simulated authentication failure for testing",
			},
			Metadata: ResponseMetadata{
				Provider:   p.Name(),
				Operation:  "lookup",
				Timestamp:  time.Now().UnixMilli(),
				MockedData: true,
				LatencyMs:  time.Since(startTime).Milliseconds(),
			},
		}, nil
		
	case "ERROR_INVALID":
		return &APIResponse{
			Success: false,
			Error: &APIError{
				Code:    "INVALID_TAX_CODE",
				Message: "Tax code format is invalid",
				Details: "Simulated validation error for testing",
			},
			Metadata: ResponseMetadata{
				Provider:   p.Name(),
				Operation:  "lookup",
				Timestamp:  time.Now().UnixMilli(),
				MockedData: true,
				LatencyMs:  time.Since(startTime).Milliseconds(),
			},
		}, nil
	}
	
	// Look up in mock database
	info, found := p.taxDatabase[taxCode]
	if !found {
		return &APIResponse{
			Success: false,
			Error: &APIError{
				Code:    "NOT_FOUND",
				Message: fmt.Sprintf("Tax code '%s' not found", taxCode),
			},
			Metadata: ResponseMetadata{
				Provider:   p.Name(),
				Operation:  "lookup",
				Timestamp:  time.Now().UnixMilli(),
				MockedData: true,
				LatencyMs:  time.Since(startTime).Milliseconds(),
			},
		}, nil
	}
	
	return &APIResponse{
		Success: true,
		Data: map[string]interface{}{
			"tax_code":     info.TaxCode,
			"company_name": info.CompanyName,
			"address":      info.Address,
			"status":       info.Status,
		},
		Metadata: ResponseMetadata{
			Provider:   p.Name(),
			Operation:  "lookup",
			Timestamp:  time.Now().UnixMilli(),
			MockedData: true,
			LatencyMs:  time.Since(startTime).Milliseconds(),
		},
	}, nil
}

func (p *MockTaxProvider) handleValidate(params map[string]interface{}, startTime time.Time) (*APIResponse, error) {
	taxCode, ok := params["tax_code"].(string)
	if !ok || taxCode == "" {
		return &APIResponse{
			Success: false,
			Error: &APIError{
				Code:    "INVALID_REQUEST",
				Message: "Missing required parameter: tax_code",
			},
			Metadata: ResponseMetadata{
				Provider:   p.Name(),
				Operation:  "validate",
				Timestamp:  time.Now().UnixMilli(),
				MockedData: true,
				LatencyMs:  time.Since(startTime).Milliseconds(),
			},
		}, nil
	}
	
	// Simple validation: 10 digits
	valid := len(taxCode) == 10
	for _, c := range taxCode {
		if c < '0' || c > '9' {
			valid = false
			break
		}
	}
	
	return &APIResponse{
		Success: true,
		Data: map[string]interface{}{
			"tax_code": taxCode,
			"valid":    valid,
			"format":   "10_digit_numeric",
		},
		Metadata: ResponseMetadata{
			Provider:   p.Name(),
			Operation:  "validate",
			Timestamp:  time.Now().UnixMilli(),
			MockedData: true,
			LatencyMs:  time.Since(startTime).Milliseconds(),
		},
	}, nil
}

func (p *MockTaxProvider) handleBatchLookup(params map[string]interface{}, startTime time.Time) (*APIResponse, error) {
	taxCodesRaw, ok := params["tax_codes"].([]interface{})
	if !ok || len(taxCodesRaw) == 0 {
		return &APIResponse{
			Success: false,
			Error: &APIError{
				Code:    "INVALID_REQUEST",
				Message: "Missing required parameter: tax_codes (array)",
			},
			Metadata: ResponseMetadata{
				Provider:   p.Name(),
				Operation:  "batch_lookup",
				Timestamp:  time.Now().UnixMilli(),
				MockedData: true,
				LatencyMs:  time.Since(startTime).Milliseconds(),
			},
		}, nil
	}
	
	results := make([]map[string]interface{}, 0, len(taxCodesRaw))
	for _, tc := range taxCodesRaw {
		taxCode, ok := tc.(string)
		if !ok {
			continue
		}
		
		info, found := p.taxDatabase[taxCode]
		if found {
			results = append(results, map[string]interface{}{
				"tax_code":     info.TaxCode,
				"company_name": info.CompanyName,
				"address":      info.Address,
				"status":       info.Status,
				"found":        true,
			})
		} else {
			results = append(results, map[string]interface{}{
				"tax_code": taxCode,
				"found":    false,
			})
		}
	}
	
	return &APIResponse{
		Success: true,
		Data: map[string]interface{}{
			"results": results,
			"total":   len(results),
		},
		Metadata: ResponseMetadata{
			Provider:   p.Name(),
			Operation:  "batch_lookup",
			Timestamp:  time.Now().UnixMilli(),
			MockedData: true,
			LatencyMs:  time.Since(startTime).Milliseconds(),
		},
	}, nil
}

// =============================================================================
// Mock CRM Provider
// =============================================================================

// MockCRMProvider simulates a CRM API
type MockCRMProvider struct {
	// Mock customer database
	customers map[string]CustomerInfo
}

// CustomerInfo represents customer information
type CustomerInfo struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	Email     string `json:"email"`
	Phone     string `json:"phone"`
	Company   string `json:"company"`
	Status    string `json:"status"`
	CreatedAt string `json:"created_at"`
}

// NewMockCRMProvider creates a new mock CRM provider
func NewMockCRMProvider() *MockCRMProvider {
	return &MockCRMProvider{
		customers: map[string]CustomerInfo{
			"CUST001": {
				ID:        "CUST001",
				Name:      "Nguyễn Văn A",
				Email:     "nguyenvana@example.com",
				Phone:     "0901234567",
				Company:   "Công ty ABC",
				Status:    "active",
				CreatedAt: "2024-01-15T10:30:00Z",
			},
			"CUST002": {
				ID:        "CUST002",
				Name:      "Trần Thị B",
				Email:     "tranthib@example.com",
				Phone:     "0912345678",
				Company:   "Công ty XYZ",
				Status:    "active",
				CreatedAt: "2024-02-20T14:45:00Z",
			},
			"CUST003": {
				ID:        "CUST003",
				Name:      "Lê Văn C",
				Email:     "levanc@example.com",
				Phone:     "0923456789",
				Company:   "Công ty DEF",
				Status:    "inactive",
				CreatedAt: "2024-03-10T09:15:00Z",
			},
		},
	}
}

func (p *MockCRMProvider) Name() string {
	return "mock_crm"
}

func (p *MockCRMProvider) SupportedOperations() []string {
	return []string{"get_customer", "list_customers", "search_customers"}
}

func (p *MockCRMProvider) Execute(operation string, params map[string]interface{}) (*APIResponse, error) {
	startTime := time.Now()
	
	// Simulate network latency
	time.Sleep(60 * time.Millisecond)
	
	switch operation {
	case "get_customer":
		return p.handleGetCustomer(params, startTime)
	case "list_customers":
		return p.handleListCustomers(params, startTime)
	case "search_customers":
		return p.handleSearchCustomers(params, startTime)
	default:
		return &APIResponse{
			Success: false,
			Error: &APIError{
				Code:    "UNSUPPORTED_OPERATION",
				Message: fmt.Sprintf("Operation '%s' not supported by mock_crm provider", operation),
			},
			Metadata: ResponseMetadata{
				Provider:   p.Name(),
				Operation:  operation,
				Timestamp:  time.Now().UnixMilli(),
				MockedData: true,
				LatencyMs:  time.Since(startTime).Milliseconds(),
			},
		}, nil
	}
}

func (p *MockCRMProvider) handleGetCustomer(params map[string]interface{}, startTime time.Time) (*APIResponse, error) {
	customerID, ok := params["customer_id"].(string)
	if !ok || customerID == "" {
		return &APIResponse{
			Success: false,
			Error: &APIError{
				Code:    "INVALID_REQUEST",
				Message: "Missing required parameter: customer_id",
			},
			Metadata: ResponseMetadata{
				Provider:   p.Name(),
				Operation:  "get_customer",
				Timestamp:  time.Now().UnixMilli(),
				MockedData: true,
				LatencyMs:  time.Since(startTime).Milliseconds(),
			},
		}, nil
	}
	
	customer, found := p.customers[customerID]
	if !found {
		return &APIResponse{
			Success: false,
			Error: &APIError{
				Code:    "NOT_FOUND",
				Message: fmt.Sprintf("Customer '%s' not found", customerID),
			},
			Metadata: ResponseMetadata{
				Provider:   p.Name(),
				Operation:  "get_customer",
				Timestamp:  time.Now().UnixMilli(),
				MockedData: true,
				LatencyMs:  time.Since(startTime).Milliseconds(),
			},
		}, nil
	}
	
	return &APIResponse{
		Success: true,
		Data: map[string]interface{}{
			"id":         customer.ID,
			"name":       customer.Name,
			"email":      customer.Email,
			"phone":      customer.Phone,
			"company":    customer.Company,
			"status":     customer.Status,
			"created_at": customer.CreatedAt,
		},
		Metadata: ResponseMetadata{
			Provider:   p.Name(),
			Operation:  "get_customer",
			Timestamp:  time.Now().UnixMilli(),
			MockedData: true,
			LatencyMs:  time.Since(startTime).Milliseconds(),
		},
	}, nil
}

func (p *MockCRMProvider) handleListCustomers(params map[string]interface{}, startTime time.Time) (*APIResponse, error) {
	customers := make([]map[string]interface{}, 0, len(p.customers))
	for _, c := range p.customers {
		customers = append(customers, map[string]interface{}{
			"id":         c.ID,
			"name":       c.Name,
			"email":      c.Email,
			"phone":      c.Phone,
			"company":    c.Company,
			"status":     c.Status,
			"created_at": c.CreatedAt,
		})
	}
	
	return &APIResponse{
		Success: true,
		Data: map[string]interface{}{
			"customers": customers,
			"total":     len(customers),
		},
		Metadata: ResponseMetadata{
			Provider:   p.Name(),
			Operation:  "list_customers",
			Timestamp:  time.Now().UnixMilli(),
			MockedData: true,
			LatencyMs:  time.Since(startTime).Milliseconds(),
		},
	}, nil
}

func (p *MockCRMProvider) handleSearchCustomers(params map[string]interface{}, startTime time.Time) (*APIResponse, error) {
	query, _ := params["query"].(string)
	if query == "" {
		return p.handleListCustomers(params, startTime)
	}
	
	// Simple search: match name or company (case-insensitive would need more code)
	results := make([]map[string]interface{}, 0)
	for _, c := range p.customers {
		// Simple substring match
		if contains(c.Name, query) || contains(c.Company, query) || contains(c.Email, query) {
			results = append(results, map[string]interface{}{
				"id":      c.ID,
				"name":    c.Name,
				"email":   c.Email,
				"company": c.Company,
				"status":  c.Status,
			})
		}
	}
	
	return &APIResponse{
		Success: true,
		Data: map[string]interface{}{
			"results": results,
			"total":   len(results),
			"query":   query,
		},
		Metadata: ResponseMetadata{
			Provider:   p.Name(),
			Operation:  "search_customers",
			Timestamp:  time.Now().UnixMilli(),
			MockedData: true,
			LatencyMs:  time.Since(startTime).Milliseconds(),
		},
	}, nil
}

// =============================================================================
// Mock Generic Provider (for testing arbitrary API calls)
// =============================================================================

// MockGenericProvider simulates a generic API for testing
type MockGenericProvider struct{}

// NewMockGenericProvider creates a new mock generic provider
func NewMockGenericProvider() *MockGenericProvider {
	return &MockGenericProvider{}
}

func (p *MockGenericProvider) Name() string {
	return "mock_generic"
}

func (p *MockGenericProvider) SupportedOperations() []string {
	return []string{"echo", "delay", "error"}
}

func (p *MockGenericProvider) Execute(operation string, params map[string]interface{}) (*APIResponse, error) {
	startTime := time.Now()
	
	switch operation {
	case "echo":
		// Echo back the params
		return &APIResponse{
			Success: true,
			Data:    params,
			Metadata: ResponseMetadata{
				Provider:   p.Name(),
				Operation:  "echo",
				Timestamp:  time.Now().UnixMilli(),
				MockedData: true,
				LatencyMs:  time.Since(startTime).Milliseconds(),
			},
		}, nil
		
	case "delay":
		// Simulate configurable delay
		delayMs, ok := params["delay_ms"].(float64)
		if !ok {
			delayMs = 100
		}
		time.Sleep(time.Duration(delayMs) * time.Millisecond)
		
		return &APIResponse{
			Success: true,
			Data: map[string]interface{}{
				"delayed_ms": delayMs,
			},
			Metadata: ResponseMetadata{
				Provider:   p.Name(),
				Operation:  "delay",
				Timestamp:  time.Now().UnixMilli(),
				MockedData: true,
				LatencyMs:  time.Since(startTime).Milliseconds(),
			},
		}, nil
		
	case "error":
		// Simulate a configurable error
		errorCode, _ := params["error_code"].(string)
		if errorCode == "" {
			errorCode = "SIMULATED_ERROR"
		}
		errorMessage, _ := params["error_message"].(string)
		if errorMessage == "" {
			errorMessage = "This is a simulated error for testing"
		}
		
		return &APIResponse{
			Success: false,
			Error: &APIError{
				Code:    errorCode,
				Message: errorMessage,
			},
			Metadata: ResponseMetadata{
				Provider:   p.Name(),
				Operation:  "error",
				Timestamp:  time.Now().UnixMilli(),
				MockedData: true,
				LatencyMs:  time.Since(startTime).Milliseconds(),
			},
		}, nil
		
	default:
		return &APIResponse{
			Success: false,
			Error: &APIError{
				Code:    "UNSUPPORTED_OPERATION",
				Message: fmt.Sprintf("Operation '%s' not supported", operation),
			},
			Metadata: ResponseMetadata{
				Provider:   p.Name(),
				Operation:  operation,
				Timestamp:  time.Now().UnixMilli(),
				MockedData: true,
				LatencyMs:  time.Since(startTime).Milliseconds(),
			},
		}, nil
	}
}

// Helper function for simple string contains check
func contains(s, substr string) bool {
	return len(s) >= len(substr) && (s == substr || len(substr) == 0 || 
		(len(s) > 0 && len(substr) > 0 && findSubstring(s, substr)))
}

func findSubstring(s, substr string) bool {
	for i := 0; i <= len(s)-len(substr); i++ {
		if s[i:i+len(substr)] == substr {
			return true
		}
	}
	return false
}

