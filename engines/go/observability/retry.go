/*
 * PressO Desktop - Go API Hub Engine
 * ====================================
 *
 * FILE: observability/retry.go
 * RESPONSIBILITY: Retry & backoff logic for transient failures
 *
 * ARCHITECTURAL ROLE:
 * - Exponential backoff with jitter
 * - Retry for network timeouts and HTTP 5xx
 * - Do NOT retry auth failures or client errors (4xx except 429)
 *
 * Reference: PROJECT_DOCUMENTATION.md Phase 4 Step 4
 */

package observability

import (
	"context"
	"errors"
	"fmt"
	"math"
	"math/rand"
	"net"
	"net/url"
	"time"
)

// RetryConfig holds retry configuration
type RetryConfig struct {
	MaxAttempts      int           `json:"max_attempts"`
	InitialDelayMs   int64         `json:"initial_delay_ms"`
	MaxDelayMs       int64         `json:"max_delay_ms"`
	BackoffMultiplier float64      `json:"backoff_multiplier"`
	JitterPercent    float64      `json:"jitter_percent"`
	Enabled          bool          `json:"enabled"`
}

// DefaultRetryConfig returns default retry configuration
func DefaultRetryConfig() *RetryConfig {
	return &RetryConfig{
		MaxAttempts:      3,
		InitialDelayMs:   100,  // 100ms
		MaxDelayMs:       5000, // 5 seconds
		BackoffMultiplier: 2.0,
		JitterPercent:    0.1,  // 10% jitter
		Enabled:          true,
	}
}

// IsRetryableError checks if an error is retryable
func IsRetryableError(err error, httpStatus int) bool {
	// Network errors are retryable
	if err != nil {
		var netErr net.Error
		if errors.As(err, &netErr) {
			if netErr.Timeout() {
				return true
			}
		}
		
		var urlErr *url.Error
		if errors.As(err, &urlErr) {
			return true
		}
	}
	
	// HTTP 5xx are retryable (server errors)
	if httpStatus >= 500 && httpStatus < 600 {
		return true
	}
	
	// HTTP 429 (rate limit) is retryable
	if httpStatus == 429 {
		return true
	}
	
	// HTTP 4xx (except 429) are NOT retryable (client errors)
	if httpStatus >= 400 && httpStatus < 500 {
		return false
	}
	
	// Auth failures (401, 403) are NOT retryable
	if httpStatus == 401 || httpStatus == 403 {
		return false
	}
	
	return false
}

// CalculateBackoff calculates backoff delay with jitter
func CalculateBackoff(attempt int, config *RetryConfig) time.Duration {
	if attempt <= 0 {
		return 0
	}
	
	// Exponential backoff: delay = initial * (multiplier ^ (attempt - 1))
	delayMs := float64(config.InitialDelayMs) * math.Pow(config.BackoffMultiplier, float64(attempt-1))
	
	// Cap at max delay
	if delayMs > float64(config.MaxDelayMs) {
		delayMs = float64(config.MaxDelayMs)
	}
	
	// Add jitter (±jitterPercent)
	jitterRange := delayMs * config.JitterPercent
	jitter := (rand.Float64()*2 - 1) * jitterRange // -1 to +1
	delayMs += jitter
	
	// Ensure non-negative
	if delayMs < 0 {
		delayMs = float64(config.InitialDelayMs)
	}
	
	return time.Duration(delayMs) * time.Millisecond
}

// RetryFunc is a function that can be retried
type RetryFunc func(attempt int) (interface{}, error, int) // Returns: result, error, httpStatus

// RetryWithBackoff executes a function with retry and exponential backoff
func RetryWithBackoff(
	ctx context.Context,
	config *RetryConfig,
	logger *StructuredLogger,
	requestID string,
	provider string,
	operation string,
	fn RetryFunc,
) (interface{}, error) {
	if !config.Enabled {
		// Execute once without retry
		result, err, _ := fn(1)
		return result, err
	}
	
	var lastErr error
	
	for attempt := 1; attempt <= config.MaxAttempts; attempt++ {
		// Execute function
		result, err, httpStatus := fn(attempt)
		
		// Success
		if err == nil {
			return result, nil
		}
		
		lastErr = err
		
		// Check if error is retryable
		if !IsRetryableError(err, httpStatus) {
			return nil, fmt.Errorf("non-retryable error: %w", err)
		}
		
		// Last attempt, don't retry
		if attempt >= config.MaxAttempts {
			break
		}
		
		// Calculate backoff delay
		delay := CalculateBackoff(attempt, config)
		
		// Log retry
		if logger != nil {
			reason := "network error"
			if httpStatus > 0 {
				reason = fmt.Sprintf("HTTP %d", httpStatus)
			}
			logger.LogRetry(requestID, provider, operation, attempt, delay.Milliseconds(), reason)
		}
		
		// Wait with context cancellation support
		select {
		case <-ctx.Done():
			return nil, fmt.Errorf("context cancelled: %w", ctx.Err())
		case <-time.After(delay):
			// Continue to next attempt
		}
	}
	
	// All retries exhausted
	return nil, fmt.Errorf("max retries (%d) exceeded: %w", config.MaxAttempts, lastErr)
}

