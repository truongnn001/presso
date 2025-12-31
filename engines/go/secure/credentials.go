/*
 * PressO Desktop - Go API Hub Engine
 * ====================================
 *
 * FILE: secure/credentials.go
 * RESPONSIBILITY: Secure credential storage with DPAPI encryption
 *
 * ARCHITECTURAL ROLE:
 * - Encrypt credentials at rest using Windows DPAPI
 * - Decrypt credentials only in memory when needed
 * - Clear credentials from memory on shutdown
 * - NEVER log plaintext credential values
 *
 * STORAGE LOCATION:
 * - %APPDATA%\PressO\secure\credentials.enc
 *
 * SECURITY RULES (NON-NEGOTIABLE):
 * - Plaintext credentials MUST NEVER be logged
 * - Credentials exist in memory only during active use
 * - Fail-safe on missing or invalid credentials
 *
 * Reference: PROJECT_DOCUMENTATION.md Section 6.4
 */

package secure

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"sync"
	"time"
)

// Credential represents encrypted API credentials
type Credential struct {
	Provider     string `json:"provider"`
	APIKey       string `json:"api_key,omitempty"`
	APISecret    string `json:"api_secret,omitempty"`
	AccessToken  string `json:"access_token,omitempty"`
	RefreshToken string `json:"refresh_token,omitempty"`
	ExpiresAt    int64  `json:"expires_at,omitempty"`
	BaseURL      string `json:"base_url,omitempty"`
	// Additional metadata (not sensitive)
	CreatedAt    int64  `json:"created_at,omitempty"`
	UpdatedAt    int64  `json:"updated_at,omitempty"`
}

// CredentialMetadata contains non-sensitive info about credentials
type CredentialMetadata struct {
	Provider       string `json:"provider"`
	HasAPIKey      bool   `json:"has_api_key"`
	HasAPISecret   bool   `json:"has_api_secret"`
	HasAccessToken bool   `json:"has_access_token"`
	HasRefreshToken bool  `json:"has_refresh_token"`
	ExpiresAt      int64  `json:"expires_at,omitempty"`
	BaseURL        string `json:"base_url,omitempty"`
	CreatedAt      int64  `json:"created_at,omitempty"`
	UpdatedAt      int64  `json:"updated_at,omitempty"`
}

// SecureCredentialStore manages encrypted credentials
type SecureCredentialStore struct {
	mu           sync.RWMutex
	credentials  map[string]*Credential
	storagePath  string
	loaded       bool
}

// NewSecureCredentialStore creates a new secure credential store
func NewSecureCredentialStore(storagePath string) *SecureCredentialStore {
	if storagePath == "" {
		storagePath = GetDefaultStoragePath()
	}
	
	return &SecureCredentialStore{
		credentials: make(map[string]*Credential),
		storagePath: storagePath,
		loaded:      false,
	}
}

// GetDefaultStoragePath returns the default encrypted credentials file path
func GetDefaultStoragePath() string {
	appData := os.Getenv("APPDATA")
	if appData == "" {
		// Fallback for non-Windows or missing APPDATA
		homeDir, err := os.UserHomeDir()
		if err != nil {
			return "credentials.enc"
		}
		return filepath.Join(homeDir, ".presso", "secure", "credentials.enc")
	}
	return filepath.Join(appData, "PressO", "secure", "credentials.enc")
}

// Load loads and decrypts credentials from storage
func (s *SecureCredentialStore) Load() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	
	// Check if file exists
	if _, err := os.Stat(s.storagePath); os.IsNotExist(err) {
		log.Printf("Credentials file not found at %s, starting with empty store", s.storagePath)
		s.loaded = true
		return nil
	}
	
	// Read encrypted file
	encryptedData, err := os.ReadFile(s.storagePath)
	if err != nil {
		return fmt.Errorf("failed to read credentials file: %w", err)
	}
	
	if len(encryptedData) == 0 {
		log.Println("Credentials file is empty")
		s.loaded = true
		return nil
	}
	
	// Decrypt using DPAPI
	decryptedData, err := Decrypt(encryptedData)
	if err != nil {
		return fmt.Errorf("failed to decrypt credentials: %w", err)
	}
	
	// Parse JSON
	var creds struct {
		Credentials []Credential `json:"credentials"`
	}
	if err := json.Unmarshal(decryptedData, &creds); err != nil {
		// Clear decrypted data from memory
		for i := range decryptedData {
			decryptedData[i] = 0
		}
		return fmt.Errorf("failed to parse credentials: %w", err)
	}
	
	// Clear decrypted JSON from memory
	for i := range decryptedData {
		decryptedData[i] = 0
	}
	
	// Store in memory
	for _, cred := range creds.Credentials {
		credCopy := cred // Create copy to avoid reference issues
		s.credentials[cred.Provider] = &credCopy
		// Log only that credentials were loaded (NO VALUES)
		log.Printf("Loaded credentials for provider: %s (key present: %v)", 
			cred.Provider, cred.APIKey != "")
	}
	
	s.loaded = true
	log.Printf("Loaded %d credential(s) from encrypted storage", len(s.credentials))
	return nil
}

// Save encrypts and saves credentials to storage
func (s *SecureCredentialStore) Save() error {
	s.mu.RLock()
	defer s.mu.RUnlock()
	
	// Build credentials list
	creds := struct {
		Credentials []Credential `json:"credentials"`
	}{
		Credentials: make([]Credential, 0, len(s.credentials)),
	}
	
	for _, cred := range s.credentials {
		creds.Credentials = append(creds.Credentials, *cred)
	}
	
	// Marshal to JSON
	jsonData, err := json.MarshalIndent(creds, "", "  ")
	if err != nil {
		return fmt.Errorf("failed to marshal credentials: %w", err)
	}
	
	// Encrypt using DPAPI
	encryptedData, err := Encrypt(jsonData)
	if err != nil {
		// Clear JSON from memory
		for i := range jsonData {
			jsonData[i] = 0
		}
		return fmt.Errorf("failed to encrypt credentials: %w", err)
	}
	
	// Clear JSON from memory
	for i := range jsonData {
		jsonData[i] = 0
	}
	
	// Ensure directory exists
	dir := filepath.Dir(s.storagePath)
	if err := os.MkdirAll(dir, 0700); err != nil {
		return fmt.Errorf("failed to create secure directory: %w", err)
	}
	
	// Write encrypted file with restricted permissions
	if err := os.WriteFile(s.storagePath, encryptedData, 0600); err != nil {
		return fmt.Errorf("failed to write credentials file: %w", err)
	}
	
	log.Printf("Saved %d credential(s) to encrypted storage", len(s.credentials))
	return nil
}

// SaveCredential saves a single credential (encrypts and persists)
func (s *SecureCredentialStore) SaveCredential(provider string, cred *Credential) error {
	s.mu.Lock()
	cred.Provider = provider
	cred.UpdatedAt = time.Now().Unix()
	if cred.CreatedAt == 0 {
		cred.CreatedAt = cred.UpdatedAt
	}
	s.credentials[provider] = cred
	s.mu.Unlock()
	
	// Persist to disk
	if err := s.Save(); err != nil {
		return err
	}
	
	// Log only metadata (NO VALUES)
	log.Printf("Saved credential for provider: %s", provider)
	return nil
}

// LoadCredential retrieves a credential (decrypted in memory)
func (s *SecureCredentialStore) LoadCredential(provider string) (*Credential, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	
	if !s.loaded {
		return nil, fmt.Errorf("credential store not loaded")
	}
	
	cred, ok := s.credentials[provider]
	if !ok {
		return nil, fmt.Errorf("no credentials found for provider: %s", provider)
	}
	
	// Return a copy to prevent external modification
	credCopy := *cred
	return &credCopy, nil
}

// GetCredentialMetadata returns non-sensitive metadata about credentials
func (s *SecureCredentialStore) GetCredentialMetadata(provider string) *CredentialMetadata {
	s.mu.RLock()
	defer s.mu.RUnlock()
	
	cred, ok := s.credentials[provider]
	if !ok {
		return nil
	}
	
	return &CredentialMetadata{
		Provider:        provider,
		HasAPIKey:       cred.APIKey != "",
		HasAPISecret:    cred.APISecret != "",
		HasAccessToken:  cred.AccessToken != "",
		HasRefreshToken: cred.RefreshToken != "",
		ExpiresAt:       cred.ExpiresAt,
		BaseURL:         cred.BaseURL,
		CreatedAt:       cred.CreatedAt,
		UpdatedAt:       cred.UpdatedAt,
	}
}

// HasCredential checks if credentials exist for a provider
func (s *SecureCredentialStore) HasCredential(provider string) bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.credentials[provider] != nil
}

// ValidateCredential checks if credentials are valid for a provider
func (s *SecureCredentialStore) ValidateCredential(provider string) error {
	s.mu.RLock()
	defer s.mu.RUnlock()
	
	cred, ok := s.credentials[provider]
	if !ok {
		return fmt.Errorf("no credentials for provider: %s", provider)
	}
	
	// Check if at least one auth method is present
	if cred.APIKey == "" && cred.AccessToken == "" {
		return fmt.Errorf("credentials incomplete for provider: %s", provider)
	}
	
	// Check token expiration if applicable
	if cred.ExpiresAt > 0 && cred.ExpiresAt < time.Now().Unix() {
		return fmt.Errorf("credentials expired for provider: %s", provider)
	}
	
	return nil
}

// DeleteCredential removes credentials for a provider
func (s *SecureCredentialStore) DeleteCredential(provider string) error {
	s.mu.Lock()
	cred, ok := s.credentials[provider]
	if ok {
		// Clear sensitive data from memory
		s.clearCredential(cred)
		delete(s.credentials, provider)
	}
	s.mu.Unlock()
	
	if !ok {
		return fmt.Errorf("no credentials found for provider: %s", provider)
	}
	
	// Persist changes
	return s.Save()
}

// ListProviders returns all providers with stored credentials
func (s *SecureCredentialStore) ListProviders() []string {
	s.mu.RLock()
	defer s.mu.RUnlock()
	
	providers := make([]string, 0, len(s.credentials))
	for p := range s.credentials {
		providers = append(providers, p)
	}
	return providers
}

// Clear removes all credentials from memory (call on shutdown)
func (s *SecureCredentialStore) Clear() {
	s.mu.Lock()
	defer s.mu.Unlock()
	
	for _, cred := range s.credentials {
		s.clearCredential(cred)
	}
	s.credentials = make(map[string]*Credential)
	s.loaded = false
	
	log.Println("Cleared all credentials from memory")
}

// clearCredential zeroes out sensitive fields in a credential
func (s *SecureCredentialStore) clearCredential(cred *Credential) {
	if cred == nil {
		return
	}
	
	// Zero out sensitive strings
	clearString(&cred.APIKey)
	clearString(&cred.APISecret)
	clearString(&cred.AccessToken)
	clearString(&cred.RefreshToken)
}

// clearString overwrites a string with zeros
func clearString(s *string) {
	if s == nil || *s == "" {
		return
	}
	
	// Note: Go strings are immutable, so we can only replace the reference
	// The original string data may remain in memory until GC
	// For true secure clearing, use []byte instead
	*s = ""
}

// IsLoaded returns whether credentials have been loaded
func (s *SecureCredentialStore) IsLoaded() bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.loaded
}

// GetStoragePath returns the storage path
func (s *SecureCredentialStore) GetStoragePath() string {
	return s.storagePath
}

