/*
 * PressO Desktop - Go API Hub Engine
 * ====================================
 *
 * FILE: mock/credentials.go
 * RESPONSIBILITY: Mock credential handling for Phase 4 Step 2
 *
 * ARCHITECTURAL ROLE:
 * - Load mock credentials from config file
 * - Store credentials in memory ONLY
 * - NO encryption (deferred to future steps)
 * - NO persistence (credentials exist only during runtime)
 *
 * SECURITY NOTES:
 * - NEVER log credential values
 * - Credentials are mock values for testing only
 * - Real credential encryption: TODO Phase 4 Step 3+
 *
 * Reference: PROJECT_DOCUMENTATION.md Section 6.4
 */

package mock

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"sync"
)

// Credential represents API credentials for a provider
type Credential struct {
	Provider    string `json:"provider"`
	APIKey      string `json:"api_key,omitempty"`
	APISecret   string `json:"api_secret,omitempty"`
	AccessToken string `json:"access_token,omitempty"`
	ExpiresAt   int64  `json:"expires_at,omitempty"`
	// Additional fields for OAuth (future)
	// RefreshToken string `json:"refresh_token,omitempty"`
	// ClientID     string `json:"client_id,omitempty"`
	// ClientSecret string `json:"client_secret,omitempty"`
}

// CredentialStore manages in-memory credentials
type CredentialStore struct {
	mu          sync.RWMutex
	credentials map[string]*Credential
	configPath  string
}

// NewCredentialStore creates a new credential store
func NewCredentialStore(configPath string) *CredentialStore {
	return &CredentialStore{
		credentials: make(map[string]*Credential),
		configPath:  configPath,
	}
}

// LoadFromConfig loads mock credentials from a config file
func (s *CredentialStore) LoadFromConfig() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	
	// If no config path specified, use defaults
	if s.configPath == "" {
		s.loadDefaults()
		return nil
	}
	
	// Check if config file exists
	if _, err := os.Stat(s.configPath); os.IsNotExist(err) {
		log.Printf("Credentials config not found at %s, using defaults", s.configPath)
		s.loadDefaults()
		return nil
	}
	
	// Read config file
	data, err := os.ReadFile(s.configPath)
	if err != nil {
		log.Printf("Error reading credentials config: %v, using defaults", err)
		s.loadDefaults()
		return nil
	}
	
	// Parse JSON
	var config struct {
		Credentials []Credential `json:"credentials"`
	}
	if err := json.Unmarshal(data, &config); err != nil {
		log.Printf("Error parsing credentials config: %v, using defaults", err)
		s.loadDefaults()
		return nil
	}
	
	// Store credentials (DO NOT log actual values)
	for _, cred := range config.Credentials {
		s.credentials[cred.Provider] = &Credential{
			Provider:    cred.Provider,
			APIKey:      cred.APIKey,
			APISecret:   cred.APISecret,
			AccessToken: cred.AccessToken,
			ExpiresAt:   cred.ExpiresAt,
		}
		log.Printf("Loaded credentials for provider: %s (key present: %v)", 
			cred.Provider, cred.APIKey != "")
	}
	
	return nil
}

// loadDefaults loads default mock credentials
func (s *CredentialStore) loadDefaults() {
	// Default mock credentials for testing
	// These are NOT real credentials
	s.credentials["mock_tax"] = &Credential{
		Provider: "mock_tax",
		APIKey:   "MOCK_TAX_API_KEY_12345",
	}
	s.credentials["mock_crm"] = &Credential{
		Provider:    "mock_crm",
		APIKey:      "MOCK_CRM_API_KEY_67890",
		AccessToken: "MOCK_CRM_ACCESS_TOKEN",
		ExpiresAt:   9999999999999, // Far future
	}
	s.credentials["mock_generic"] = &Credential{
		Provider: "mock_generic",
		APIKey:   "MOCK_GENERIC_KEY",
	}
	
	log.Println("Loaded default mock credentials for testing")
}

// GetCredential retrieves credentials for a provider
func (s *CredentialStore) GetCredential(provider string) (*Credential, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	
	cred, ok := s.credentials[provider]
	return cred, ok
}

// HasCredential checks if credentials exist for a provider
func (s *CredentialStore) HasCredential(provider string) bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	
	_, ok := s.credentials[provider]
	return ok
}

// ValidateCredential checks if credentials are valid (mock validation)
func (s *CredentialStore) ValidateCredential(provider string) error {
	s.mu.RLock()
	defer s.mu.RUnlock()
	
	cred, ok := s.credentials[provider]
	if !ok {
		return fmt.Errorf("no credentials found for provider: %s", provider)
	}
	
	// Mock validation: just check if API key exists
	if cred.APIKey == "" && cred.AccessToken == "" {
		return fmt.Errorf("credentials incomplete for provider: %s", provider)
	}
	
	return nil
}

// ListProviders returns all providers with stored credentials
func (s *CredentialStore) ListProviders() []string {
	s.mu.RLock()
	defer s.mu.RUnlock()
	
	providers := make([]string, 0, len(s.credentials))
	for p := range s.credentials {
		providers = append(providers, p)
	}
	return providers
}

// GetCredentialMetadata returns non-sensitive credential info
func (s *CredentialStore) GetCredentialMetadata(provider string) map[string]interface{} {
	s.mu.RLock()
	defer s.mu.RUnlock()
	
	cred, ok := s.credentials[provider]
	if !ok {
		return nil
	}
	
	// Return metadata WITHOUT sensitive values
	return map[string]interface{}{
		"provider":          provider,
		"has_api_key":       cred.APIKey != "",
		"has_api_secret":    cred.APISecret != "",
		"has_access_token":  cred.AccessToken != "",
		"expires_at":        cred.ExpiresAt,
	}
}

// GetDefaultConfigPath returns the default config file path
func GetDefaultConfigPath() string {
	// Try to find config in standard locations
	// 1. Current directory
	if _, err := os.Stat("mock_credentials.json"); err == nil {
		return "mock_credentials.json"
	}
	
	// 2. User config directory
	configDir, err := os.UserConfigDir()
	if err == nil {
		path := filepath.Join(configDir, "PressO", "mock_credentials.json")
		if _, err := os.Stat(path); err == nil {
			return path
		}
	}
	
	// 3. AppData/Roaming on Windows
	appData := os.Getenv("APPDATA")
	if appData != "" {
		path := filepath.Join(appData, "PressO", "config", "mock_credentials.json")
		if _, err := os.Stat(path); err == nil {
			return path
		}
	}
	
	// Return empty string to use defaults
	return ""
}

