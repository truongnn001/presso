/*
 * PressO Desktop - Go API Hub Engine
 * ====================================
 *
 * FILE: observability/metrics.go
 * RESPONSIBILITY: In-memory metrics collection
 *
 * ARCHITECTURAL ROLE:
 * - Collect metrics for requests, success, errors, rate limits, latency
 * - Per-provider metrics
 * - Metrics reset on engine restart (no persistence)
 *
 * Reference: PROJECT_DOCUMENTATION.md Phase 4 Step 4
 */

package observability

import (
	"sync"
	"time"
)

// Metrics collects in-memory metrics
type Metrics struct {
	mu sync.RWMutex
	
	// Global counters
	TotalRequests   int64 `json:"total_requests"`
	SuccessCount    int64 `json:"success_count"`
	ErrorCount      int64 `json:"error_count"`
	RateLimitHits   int64 `json:"rate_limit_hits"`
	
	// Per-provider metrics
	ProviderMetrics map[string]*ProviderMetrics `json:"provider_metrics"`
	
	// Latency tracking
	TotalLatencyMs int64 `json:"total_latency_ms"`
	
	// Timestamps
	FirstRequestAt int64 `json:"first_request_at"`
	LastRequestAt  int64 `json:"last_request_at"`
	LastErrorAt    int64 `json:"last_error_at"`
}

// ProviderMetrics tracks metrics per provider
type ProviderMetrics struct {
	Provider       string  `json:"provider"`
	TotalRequests  int64   `json:"total_requests"`
	SuccessCount   int64   `json:"success_count"`
	ErrorCount     int64   `json:"error_count"`
	RateLimitHits  int64   `json:"rate_limit_hits"`
	TotalLatencyMs int64   `json:"total_latency_ms"`
	AvgLatencyMs   float64 `json:"avg_latency_ms"`
}

// NewMetrics creates a new metrics collector
func NewMetrics() *Metrics {
	return &Metrics{
		ProviderMetrics: make(map[string]*ProviderMetrics),
	}
}

// RecordRequest records a request metric
func (m *Metrics) RecordRequest(provider string, success bool, latencyMs int64) {
	m.mu.Lock()
	defer m.mu.Unlock()
	
	now := time.Now().UnixMilli()
	
	// Global metrics
	m.TotalRequests++
	if success {
		m.SuccessCount++
	} else {
		m.ErrorCount++
		m.LastErrorAt = now
	}
	m.TotalLatencyMs += latencyMs
	
	if m.FirstRequestAt == 0 {
		m.FirstRequestAt = now
	}
	m.LastRequestAt = now
	
	// Provider metrics
	pm, exists := m.ProviderMetrics[provider]
	if !exists {
		pm = &ProviderMetrics{
			Provider: provider,
		}
		m.ProviderMetrics[provider] = pm
	}
	
	pm.TotalRequests++
	if success {
		pm.SuccessCount++
	} else {
		pm.ErrorCount++
	}
	pm.TotalLatencyMs += latencyMs
	if pm.TotalRequests > 0 {
		pm.AvgLatencyMs = float64(pm.TotalLatencyMs) / float64(pm.TotalRequests)
	}
}

// RecordRateLimit records a rate limit hit
func (m *Metrics) RecordRateLimit(provider string) {
	m.mu.Lock()
	defer m.mu.Unlock()
	
	m.RateLimitHits++
	
	pm, exists := m.ProviderMetrics[provider]
	if !exists {
		pm = &ProviderMetrics{
			Provider: provider,
		}
		m.ProviderMetrics[provider] = pm
	}
	pm.RateLimitHits++
}

// GetSnapshot returns a snapshot of current metrics
func (m *Metrics) GetSnapshot() map[string]interface{} {
	m.mu.RLock()
	defer m.mu.RUnlock()
	
	avgLatencyMs := float64(0)
	if m.TotalRequests > 0 {
		avgLatencyMs = float64(m.TotalLatencyMs) / float64(m.TotalRequests)
	}
	
	providerMetrics := make(map[string]interface{})
	for name, pm := range m.ProviderMetrics {
		providerMetrics[name] = map[string]interface{}{
			"provider":        pm.Provider,
			"total_requests": pm.TotalRequests,
			"success_count":  pm.SuccessCount,
			"error_count":    pm.ErrorCount,
			"rate_limit_hits": pm.RateLimitHits,
			"avg_latency_ms": pm.AvgLatencyMs,
		}
	}
	
	return map[string]interface{}{
		"total_requests":   m.TotalRequests,
		"success_count":   m.SuccessCount,
		"error_count":     m.ErrorCount,
		"rate_limit_hits": m.RateLimitHits,
		"avg_latency_ms":  avgLatencyMs,
		"first_request_at": m.FirstRequestAt,
		"last_request_at":  m.LastRequestAt,
		"last_error_at":    m.LastErrorAt,
		"provider_metrics": providerMetrics,
	}
}

// Reset clears all metrics (for testing or restart)
func (m *Metrics) Reset() {
	m.mu.Lock()
	defer m.mu.Unlock()
	
	m.TotalRequests = 0
	m.SuccessCount = 0
	m.ErrorCount = 0
	m.RateLimitHits = 0
	m.TotalLatencyMs = 0
	m.FirstRequestAt = 0
	m.LastRequestAt = 0
	m.LastErrorAt = 0
	m.ProviderMetrics = make(map[string]*ProviderMetrics)
}

