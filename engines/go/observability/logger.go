/*
 * PressO Desktop - Go API Hub Engine
 * ====================================
 *
 * FILE: observability/logger.go
 * RESPONSIBILITY: Structured logging with correlation fields
 *
 * ARCHITECTURAL ROLE:
 * - Structured JSON logging to stderr
 * - Correlation fields (request_id, provider, operation, outcome, duration_ms)
 * - NEVER logs credentials or sensitive data
 *
 * SECURITY REQUIREMENTS:
 * - NEVER log credentials
 * - NEVER log auth headers
 * - NEVER log sensitive payloads
 *
 * Reference: PROJECT_DOCUMENTATION.md Phase 4 Step 4
 */

package observability

import (
	"encoding/json"
	"fmt"
	"os"
	"time"
)

// LogLevel represents log severity
type LogLevel string

const (
	LogLevelDebug LogLevel = "DEBUG"
	LogLevelInfo  LogLevel = "INFO"
	LogLevelWarn  LogLevel = "WARN"
	LogLevelError LogLevel = "ERROR"
)

// StructuredLogger provides structured JSON logging
type StructuredLogger struct {
	level LogLevel
}

// LogEntry represents a structured log entry
type LogEntry struct {
	Timestamp  int64                  `json:"timestamp"`
	Level      string                 `json:"level"`
	Message    string                 `json:"message"`
	RequestID  string                 `json:"request_id,omitempty"`
	Provider   string                 `json:"provider,omitempty"`
	Operation  string                 `json:"operation,omitempty"`
	Outcome    string                 `json:"outcome,omitempty"` // "success" or "failure"
	DurationMs int64                  `json:"duration_ms,omitempty"`
	Error      string                 `json:"error,omitempty"`
	Fields     map[string]interface{} `json:"fields,omitempty"`
}

// NewStructuredLogger creates a new structured logger
func NewStructuredLogger(level LogLevel) *StructuredLogger {
	return &StructuredLogger{
		level: level,
	}
}

// shouldLog checks if a log level should be logged
func (l *StructuredLogger) shouldLog(level LogLevel) bool {
	levels := map[LogLevel]int{
		LogLevelDebug: 0,
		LogLevelInfo:  1,
		LogLevelWarn:  2,
		LogLevelError: 3,
	}
	return levels[level] >= levels[l.level]
}

// log writes a structured log entry
func (l *StructuredLogger) log(level LogLevel, entry LogEntry) {
	if !l.shouldLog(level) {
		return
	}
	
	entry.Timestamp = time.Now().UnixMilli()
	entry.Level = string(level)
	
	data, err := json.Marshal(entry)
	if err != nil {
		// Fallback to simple log if JSON marshal fails
		fmt.Fprintf(os.Stderr, "[Go API Hub] %s: %s\n", level, entry.Message)
		return
	}
	
	fmt.Fprintf(os.Stderr, "%s\n", string(data))
}

// Debug logs a debug message
func (l *StructuredLogger) Debug(message string, fields map[string]interface{}) {
	l.log(LogLevelDebug, LogEntry{
		Message: message,
		Fields:  fields,
	})
}

// Info logs an info message
func (l *StructuredLogger) Info(message string, fields map[string]interface{}) {
	l.log(LogLevelInfo, LogEntry{
		Message: message,
		Fields:  fields,
	})
}

// Warn logs a warning message
func (l *StructuredLogger) Warn(message string, fields map[string]interface{}) {
	l.log(LogLevelWarn, LogEntry{
		Message: message,
		Fields:  fields,
	})
}

// Error logs an error message
func (l *StructuredLogger) Error(message string, err error, fields map[string]interface{}) {
	errorMsg := ""
	if err != nil {
		errorMsg = err.Error()
	}
	
	l.log(LogLevelError, LogEntry{
		Message: message,
		Error:   errorMsg,
		Fields:  fields,
	})
}

// LogAPIRequest logs an API request with correlation fields
func (l *StructuredLogger) LogAPIRequest(
	requestID string,
	provider string,
	operation string,
	fields map[string]interface{},
) {
	if fields == nil {
		fields = make(map[string]interface{})
	}
	fields["request_id"] = requestID
	fields["provider"] = provider
	fields["operation"] = operation
	
	l.Info("API request started", fields)
}

// LogAPIResponse logs an API response with outcome and duration
func (l *StructuredLogger) LogAPIResponse(
	requestID string,
	provider string,
	operation string,
	success bool,
	durationMs int64,
	fields map[string]interface{},
) {
	if fields == nil {
		fields = make(map[string]interface{})
	}
	
	outcome := "success"
	if !success {
		outcome = "failure"
	}
	
	fields["request_id"] = requestID
	fields["provider"] = provider
	fields["operation"] = operation
	fields["outcome"] = outcome
	fields["duration_ms"] = durationMs
	
	level := LogLevelInfo
	if !success {
		level = LogLevelError
	}
	
	l.log(level, LogEntry{
		Message:    "API request completed",
		RequestID:  requestID,
		Provider:   provider,
		Operation:  operation,
		Outcome:    outcome,
		DurationMs: durationMs,
		Fields:     fields,
	})
}

// LogRateLimit logs a rate limit event
func (l *StructuredLogger) LogRateLimit(
	provider string,
	retryAfterMs int64,
) {
	l.Warn("Rate limit exceeded", map[string]interface{}{
		"provider":       provider,
		"retry_after_ms": retryAfterMs,
	})
}

// LogRetry logs a retry attempt
func (l *StructuredLogger) LogRetry(
	requestID string,
	provider string,
	operation string,
	attempt int,
	delayMs int64,
	reason string,
) {
	l.Info("Retrying API request", map[string]interface{}{
		"request_id": requestID,
		"provider":   provider,
		"operation":  operation,
		"attempt":    attempt,
		"delay_ms":   delayMs,
		"reason":     reason,
	})
}

// ParseLogLevel parses a log level string
func ParseLogLevel(level string) LogLevel {
	switch level {
	case "DEBUG", "debug":
		return LogLevelDebug
	case "INFO", "info":
		return LogLevelInfo
	case "WARN", "warn", "WARNING", "warning":
		return LogLevelWarn
	case "ERROR", "error":
		return LogLevelError
	default:
		return LogLevelInfo
	}
}

