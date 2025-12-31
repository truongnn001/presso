# PressO Go API Hub Engine

## Overview

The Go API Hub Engine is the **ONLY gateway for external network calls** in the PressO Desktop architecture. It encapsulates all third-party API integrations and protects the Kernel and Python Engine from direct network access.

## Architecture

- **Subprocess**: Spawned by Java Kernel
- **Communication**: stdin/stdout JSON messages
- **Stateless**: No persistent state or database
- **Network Gateway**: All external API calls route through this engine

## Building

```bash
cd engines/go
go build -o api-hub.exe main.go
```

## Running

The engine is automatically spawned by the Java Kernel. For manual testing:

```bash
./api-hub.exe
```

## Message Protocol

### Incoming Messages (stdin)

```json
{
  "id": "request-123",
  "type": "PING",
  "params": {}
}
```

### Outgoing Responses (stdout)

```json
{
  "id": "request-123",
  "success": true,
  "result": {
    "message": "PONG",
    "engine": "Go API Hub Engine",
    "version": "0.1.0"
  }
}
```

## Supported Commands (Phase 4 Step 1)

- `PING` - Health check ping
- `HEALTH_CHECK` - Engine health status
- `GET_STATUS` - Detailed engine status
- `SHUTDOWN` - Graceful shutdown

## Future Commands (Phase 4 Step 2+)

- `API_CALL` - Generic API call
- `TAX_CODE_LOOKUP` - Tax code lookup API
- `AUTH_REFRESH` - OAuth token refresh

## Configuration

Configuration is loaded from JSON file (Phase 4 Step 2+). For now, defaults are used:
- Timeout: 30 seconds
- Max Retries: 3
- Log Level: INFO

## Logging

All logs go to stderr (stdout is reserved for IPC). Log format:
```
[Go API Hub] 2024-01-01 12:00:00 main.go:123: message
```

