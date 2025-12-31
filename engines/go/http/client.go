/*
 * PressO Desktop - Go API Hub Engine
 * ====================================
 *
 * FILE: http/client.go
 * RESPONSIBILITY: Real HTTP client for external API calls
 *
 * ARCHITECTURAL ROLE:
 * - Execute real HTTP requests to external APIs
 * - TLS 1.2+ enforcement
 * - Timeout and context cancellation support
 * - Response normalization
 *
 * SECURITY REQUIREMENTS:
 * - TLS enforcement (no plain HTTP for sensitive APIs)
 * - Certificate validation (no self-signed bypass)
 * - NEVER log request headers containing secrets
 * - Respect system proxy settings
 *
 * Reference: PROJECT_DOCUMENTATION.md Section 6.5
 */

package http

import (
	"bytes"
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// HTTPClient wraps the standard http.Client with additional features
type HTTPClient struct {
	client        *http.Client
	defaultTimeout time.Duration
	userAgent     string
}

// ClientConfig holds HTTP client configuration
type ClientConfig struct {
	Timeout          time.Duration
	MaxIdleConns     int
	IdleConnTimeout  time.Duration
	TLSMinVersion    uint16
	UserAgent        string
	ProxyURL         string // Optional proxy URL
}

// DefaultClientConfig returns default HTTP client configuration
func DefaultClientConfig() *ClientConfig {
	return &ClientConfig{
		Timeout:         30 * time.Second,
		MaxIdleConns:    100,
		IdleConnTimeout: 90 * time.Second,
		TLSMinVersion:   tls.VersionTLS12,
		UserAgent:       "PressO-APIHub/0.1.0",
		ProxyURL:        "", // Use system proxy by default
	}
}

// NewHTTPClient creates a new HTTP client with the given configuration
func NewHTTPClient(config *ClientConfig) *HTTPClient {
	if config == nil {
		config = DefaultClientConfig()
	}
	
	// Configure TLS
	tlsConfig := &tls.Config{
		MinVersion: config.TLSMinVersion,
		// Use system CA certificates
		// InsecureSkipVerify: false (default, never skip verification)
	}
	
	// Configure transport
	transport := &http.Transport{
		TLSClientConfig: tlsConfig,
		MaxIdleConns:    config.MaxIdleConns,
		IdleConnTimeout: config.IdleConnTimeout,
		DialContext: (&net.Dialer{
			Timeout:   10 * time.Second,
			KeepAlive: 30 * time.Second,
		}).DialContext,
		ForceAttemptHTTP2:     true,
		MaxIdleConnsPerHost:   10,
		ExpectContinueTimeout: 1 * time.Second,
	}
	
	// Configure proxy if specified
	if config.ProxyURL != "" {
		proxyURL, err := url.Parse(config.ProxyURL)
		if err == nil {
			transport.Proxy = http.ProxyURL(proxyURL)
		} else {
			log.Printf("Invalid proxy URL, using system proxy: %v", err)
			transport.Proxy = http.ProxyFromEnvironment
		}
	} else {
		// Use system proxy settings
		transport.Proxy = http.ProxyFromEnvironment
	}
	
	return &HTTPClient{
		client: &http.Client{
			Transport: transport,
			Timeout:   config.Timeout,
		},
		defaultTimeout: config.Timeout,
		userAgent:      config.UserAgent,
	}
}

// Request represents an HTTP request
type Request struct {
	Method      string
	URL         string
	Headers     map[string]string
	Body        interface{} // Will be JSON-encoded if not nil
	Timeout     time.Duration
	QueryParams map[string]string
}

// Response represents an HTTP response
type Response struct {
	StatusCode  int                    `json:"status_code"`
	Headers     map[string]string      `json:"headers,omitempty"`
	Body        interface{}            `json:"body,omitempty"`
	RawBody     []byte                 `json:"-"` // Raw response body
	LatencyMs   int64                  `json:"latency_ms"`
	Error       string                 `json:"error,omitempty"`
}

// Do executes an HTTP request
func (c *HTTPClient) Do(ctx context.Context, req *Request) (*Response, error) {
	startTime := time.Now()
	
	// Build URL with query params
	reqURL := req.URL
	if len(req.QueryParams) > 0 {
		parsedURL, err := url.Parse(reqURL)
		if err != nil {
			return nil, fmt.Errorf("invalid URL: %w", err)
		}
		q := parsedURL.Query()
		for k, v := range req.QueryParams {
			q.Set(k, v)
		}
		parsedURL.RawQuery = q.Encode()
		reqURL = parsedURL.String()
	}
	
	// Prepare body
	var bodyReader io.Reader
	if req.Body != nil {
		bodyBytes, err := json.Marshal(req.Body)
		if err != nil {
			return nil, fmt.Errorf("failed to marshal request body: %w", err)
		}
		bodyReader = bytes.NewReader(bodyBytes)
	}
	
	// Create HTTP request
	httpReq, err := http.NewRequestWithContext(ctx, req.Method, reqURL, bodyReader)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}
	
	// Set headers
	httpReq.Header.Set("User-Agent", c.userAgent)
	if req.Body != nil {
		httpReq.Header.Set("Content-Type", "application/json")
	}
	httpReq.Header.Set("Accept", "application/json")
	
	for k, v := range req.Headers {
		httpReq.Header.Set(k, v)
	}
	
	// Log request (WITHOUT sensitive headers)
	c.logRequest(req)
	
	// Execute request
	httpResp, err := c.client.Do(httpReq)
	if err != nil {
		latency := time.Since(startTime).Milliseconds()
		return &Response{
			StatusCode: 0,
			LatencyMs:  latency,
			Error:      err.Error(),
		}, err
	}
	defer httpResp.Body.Close()
	
	// Read response body
	respBody, err := io.ReadAll(httpResp.Body)
	if err != nil {
		latency := time.Since(startTime).Milliseconds()
		return &Response{
			StatusCode: httpResp.StatusCode,
			LatencyMs:  latency,
			Error:      fmt.Sprintf("failed to read response: %v", err),
		}, err
	}
	
	latency := time.Since(startTime).Milliseconds()
	
	// Build response
	resp := &Response{
		StatusCode: httpResp.StatusCode,
		Headers:    make(map[string]string),
		RawBody:    respBody,
		LatencyMs:  latency,
	}
	
	// Copy relevant headers
	for k, v := range httpResp.Header {
		if len(v) > 0 {
			resp.Headers[k] = v[0]
		}
	}
	
	// Try to parse JSON body
	if len(respBody) > 0 {
		var jsonBody interface{}
		if err := json.Unmarshal(respBody, &jsonBody); err == nil {
			resp.Body = jsonBody
		} else {
			// If not JSON, store as string
			resp.Body = string(respBody)
		}
	}
	
	// Log response (status only, no body)
	log.Printf("HTTP Response: status=%d, latency=%dms", resp.StatusCode, resp.LatencyMs)
	
	return resp, nil
}

// Get performs an HTTP GET request
func (c *HTTPClient) Get(ctx context.Context, url string, headers map[string]string) (*Response, error) {
	return c.Do(ctx, &Request{
		Method:  "GET",
		URL:     url,
		Headers: headers,
	})
}

// Post performs an HTTP POST request
func (c *HTTPClient) Post(ctx context.Context, url string, body interface{}, headers map[string]string) (*Response, error) {
	return c.Do(ctx, &Request{
		Method:  "POST",
		URL:     url,
		Body:    body,
		Headers: headers,
	})
}

// logRequest logs request info WITHOUT sensitive headers
func (c *HTTPClient) logRequest(req *Request) {
	// List of sensitive header names (case-insensitive)
	sensitiveHeaders := map[string]bool{
		"authorization":   true,
		"x-api-key":       true,
		"api-key":         true,
		"x-auth-token":    true,
		"cookie":          true,
		"x-access-token":  true,
		"bearer":          true,
	}
	
	// Count headers, marking sensitive ones
	headerInfo := make([]string, 0)
	for k := range req.Headers {
		lowerKey := strings.ToLower(k)
		if sensitiveHeaders[lowerKey] {
			headerInfo = append(headerInfo, fmt.Sprintf("%s=[REDACTED]", k))
		} else {
			headerInfo = append(headerInfo, k)
		}
	}
	
	log.Printf("HTTP Request: method=%s, url=%s, headers=[%s]",
		req.Method, req.URL, strings.Join(headerInfo, ", "))
}

// IsSuccess returns true if the response indicates success (2xx)
func (r *Response) IsSuccess() bool {
	return r.StatusCode >= 200 && r.StatusCode < 300
}

// IsClientError returns true if the response is a client error (4xx)
func (r *Response) IsClientError() bool {
	return r.StatusCode >= 400 && r.StatusCode < 500
}

// IsServerError returns true if the response is a server error (5xx)
func (r *Response) IsServerError() bool {
	return r.StatusCode >= 500
}

// GetErrorCode returns an error code based on status
func (r *Response) GetErrorCode() string {
	switch {
	case r.StatusCode == 0:
		return "NETWORK_ERROR"
	case r.StatusCode == 401:
		return "AUTHENTICATION_FAILED"
	case r.StatusCode == 403:
		return "FORBIDDEN"
	case r.StatusCode == 404:
		return "NOT_FOUND"
	case r.StatusCode == 429:
		return "RATE_LIMITED"
	case r.StatusCode >= 400 && r.StatusCode < 500:
		return "CLIENT_ERROR"
	case r.StatusCode >= 500:
		return "SERVER_ERROR"
	default:
		return "UNKNOWN_ERROR"
	}
}

