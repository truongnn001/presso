/*
 * PressO Desktop - Go API Hub Engine
 * ====================================
 *
 * FILE: secure/ratelimit.go
 * RESPONSIBILITY: Per-provider rate limiting for external API calls
 *
 * ARCHITECTURAL ROLE:
 * - Prevent API abuse and respect provider rate limits
 * - In-memory token bucket algorithm
 * - Per-provider configuration
 * - Graceful degradation (return error, don't crash)
 *
 * ALGORITHM: Token Bucket
 * - Each provider has a bucket with max tokens
 * - Tokens refill at a constant rate
 * - Each request consumes one token
 * - If no tokens available, request is rejected
 *
 * Reference: PROJECT_DOCUMENTATION.md Section 4.5
 */

package secure

import (
	"fmt"
	"sync"
	"time"
)

// RateLimitConfig defines rate limiting parameters for a provider
type RateLimitConfig struct {
	Provider         string  `json:"provider"`
	RequestsPerMinute int    `json:"requests_per_minute"`
	BurstSize        int     `json:"burst_size"` // Max tokens in bucket
	Enabled          bool    `json:"enabled"`
}

// DefaultRateLimitConfig returns default rate limit configuration
func DefaultRateLimitConfig(provider string) *RateLimitConfig {
	return &RateLimitConfig{
		Provider:          provider,
		RequestsPerMinute: 60,  // 1 request per second average
		BurstSize:         10,  // Allow bursts of up to 10 requests
		Enabled:           true,
	}
}

// tokenBucket implements the token bucket algorithm
type tokenBucket struct {
	mu           sync.Mutex
	tokens       float64
	maxTokens    float64
	refillRate   float64 // tokens per second
	lastRefill   time.Time
}

// newTokenBucket creates a new token bucket
func newTokenBucket(maxTokens float64, refillRate float64) *tokenBucket {
	return &tokenBucket{
		tokens:     maxTokens, // Start full
		maxTokens:  maxTokens,
		refillRate: refillRate,
		lastRefill: time.Now(),
	}
}

// take attempts to take a token from the bucket
// Returns true if successful, false if rate limited
func (tb *tokenBucket) take() bool {
	tb.mu.Lock()
	defer tb.mu.Unlock()
	
	// Refill tokens based on elapsed time
	now := time.Now()
	elapsed := now.Sub(tb.lastRefill).Seconds()
	tb.tokens += elapsed * tb.refillRate
	if tb.tokens > tb.maxTokens {
		tb.tokens = tb.maxTokens
	}
	tb.lastRefill = now
	
	// Try to take a token
	if tb.tokens >= 1 {
		tb.tokens--
		return true
	}
	
	return false
}

// available returns the current number of available tokens
func (tb *tokenBucket) available() float64 {
	tb.mu.Lock()
	defer tb.mu.Unlock()
	
	// Refill tokens based on elapsed time
	now := time.Now()
	elapsed := now.Sub(tb.lastRefill).Seconds()
	tokens := tb.tokens + elapsed*tb.refillRate
	if tokens > tb.maxTokens {
		tokens = tb.maxTokens
	}
	
	return tokens
}

// RateLimitError represents a rate limit exceeded error
type RateLimitError struct {
	Provider      string
	RetryAfterMs  int64
	Message       string
}

func (e *RateLimitError) Error() string {
	return e.Message
}

// RateLimiter manages rate limiting for multiple providers
type RateLimiter struct {
	mu      sync.RWMutex
	buckets map[string]*tokenBucket
	configs map[string]*RateLimitConfig
}

// NewRateLimiter creates a new rate limiter
func NewRateLimiter() *RateLimiter {
	return &RateLimiter{
		buckets: make(map[string]*tokenBucket),
		configs: make(map[string]*RateLimitConfig),
	}
}

// Configure sets rate limit configuration for a provider
func (rl *RateLimiter) Configure(config *RateLimitConfig) {
	rl.mu.Lock()
	defer rl.mu.Unlock()
	
	rl.configs[config.Provider] = config
	
	if config.Enabled {
		// Convert requests per minute to tokens per second
		refillRate := float64(config.RequestsPerMinute) / 60.0
		rl.buckets[config.Provider] = newTokenBucket(
			float64(config.BurstSize),
			refillRate,
		)
	} else {
		delete(rl.buckets, config.Provider)
	}
}

// ConfigureDefault sets default rate limit for a provider
func (rl *RateLimiter) ConfigureDefault(provider string) {
	rl.Configure(DefaultRateLimitConfig(provider))
}

// Allow checks if a request is allowed for the given provider
// Returns nil if allowed, RateLimitError if rate limited
func (rl *RateLimiter) Allow(provider string) error {
	rl.mu.RLock()
	bucket, hasBucket := rl.buckets[provider]
	config, hasConfig := rl.configs[provider]
	rl.mu.RUnlock()
	
	// If no config, allow by default (but log warning)
	if !hasConfig {
		return nil
	}
	
	// If rate limiting disabled, allow
	if !config.Enabled {
		return nil
	}
	
	// If no bucket (shouldn't happen), allow
	if !hasBucket {
		return nil
	}
	
	// Try to take a token
	if bucket.take() {
		return nil
	}
	
	// Rate limited - calculate retry time
	// Estimate time until one token is available
	available := bucket.available()
	tokensNeeded := 1.0 - available
	if tokensNeeded < 0 {
		tokensNeeded = 0
	}
	
	refillRate := float64(config.RequestsPerMinute) / 60.0
	retryAfterSec := tokensNeeded / refillRate
	if retryAfterSec < 0.1 {
		retryAfterSec = 0.1
	}
	
	return &RateLimitError{
		Provider:     provider,
		RetryAfterMs: int64(retryAfterSec * 1000),
		Message: fmt.Sprintf(
			"Rate limit exceeded for provider '%s'. Retry after %d ms",
			provider, int64(retryAfterSec*1000),
		),
	}
}

// GetStatus returns rate limit status for a provider
func (rl *RateLimiter) GetStatus(provider string) map[string]interface{} {
	rl.mu.RLock()
	defer rl.mu.RUnlock()
	
	config, hasConfig := rl.configs[provider]
	bucket, hasBucket := rl.buckets[provider]
	
	status := map[string]interface{}{
		"provider": provider,
		"configured": hasConfig,
	}
	
	if hasConfig {
		status["enabled"] = config.Enabled
		status["requests_per_minute"] = config.RequestsPerMinute
		status["burst_size"] = config.BurstSize
	}
	
	if hasBucket {
		status["available_tokens"] = bucket.available()
	}
	
	return status
}

// GetAllStatus returns rate limit status for all configured providers
func (rl *RateLimiter) GetAllStatus() []map[string]interface{} {
	rl.mu.RLock()
	defer rl.mu.RUnlock()
	
	statuses := make([]map[string]interface{}, 0, len(rl.configs))
	for provider := range rl.configs {
		statuses = append(statuses, rl.GetStatus(provider))
	}
	return statuses
}

// Reset resets the rate limiter for a provider (refills bucket)
func (rl *RateLimiter) Reset(provider string) {
	rl.mu.Lock()
	defer rl.mu.Unlock()
	
	config, hasConfig := rl.configs[provider]
	if !hasConfig || !config.Enabled {
		return
	}
	
	// Recreate bucket with full tokens
	refillRate := float64(config.RequestsPerMinute) / 60.0
	rl.buckets[provider] = newTokenBucket(
		float64(config.BurstSize),
		refillRate,
	)
}

// ResetAll resets all rate limiters
func (rl *RateLimiter) ResetAll() {
	rl.mu.Lock()
	providers := make([]string, 0, len(rl.configs))
	for p := range rl.configs {
		providers = append(providers, p)
	}
	rl.mu.Unlock()
	
	for _, p := range providers {
		rl.Reset(p)
	}
}

