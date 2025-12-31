# Phase 4 Step 1 - Go API Hub Engine Verification

## Overview

This document describes how to verify that Phase 4 Step 1 is complete and the Go API Hub Engine is properly integrated with the Kernel.

## Prerequisites

1. **Go installed** (version 1.21 or later)
   - Download from: https://go.dev/dl/
   - Verify: `go version`

2. **Java Kernel built**
   - Kernel JAR should be available
   - All dependencies resolved

## Build Steps

### 1. Build Go Engine Executable

```bash
cd engines/go
go build -o api-hub.exe main.go
```

Or use the provided build scripts:
- Windows CMD: `build.bat`
- PowerShell: `.\build.ps1`

**Expected Output:**
- `api-hub.exe` created in `engines/go/` directory
- No compilation errors

### 2. Verify Executable Location

The executable should be at:
```
E:\PressO\engines\go\api-hub.exe
```

This matches the path configured in `StateManager`:
- Config key: `engine.go.path`
- Default value: `${APP}/engines/go/api-hub.exe`

## Integration Verification

### 1. Kernel Startup

When the Kernel starts, it should:
1. Load configuration from StateManager
2. Check if Go engine is enabled (`engine.go.enabled = true`)
3. Attempt to spawn `engines/go/api-hub.exe`
4. Wait for READY signal from Go engine (10 second timeout)
5. Log engine startup success

**Expected Logs:**
```
[INFO] Starting all engines...
[INFO] Starting engine: go
[DEBUG] Engine command: engines\go\api-hub.exe
[DEBUG] Waiting for engine go to be ready...
[INFO] [go] Engine is ready
[INFO] Engine go started and ready (PID: <process_id>)
[INFO] Engine startup complete, 2 engines running
```

### 2. READY Signal Verification

The Go engine should send a READY signal immediately on startup:

**Expected READY Message (stdout):**
```json
{
  "type": "READY",
  "engine": "go",
  "version": "0.1.0",
  "capabilities": ["PING", "SHUTDOWN", "HEALTH_CHECK", "GET_STATUS"],
  "timestamp": 1234567890123
}
```

**Kernel Behavior:**
- Receives READY signal via stdout
- Sets engine health status to `true`
- Logs: `[go] Engine is ready`

### 3. PING/PONG Round-Trip

Test IPC communication by sending PING to Go engine:

**Via Kernel IPC (from UI or test script):**
```json
{
  "id": "test-ping-1",
  "type": "GO_PING",
  "payload": {}
}
```

**Expected Flow:**
1. Kernel receives `GO_PING` message
2. ModuleRouter routes to `GO_API_HUB` engine
3. Maps `GO_PING` → `PING` for engine
4. Sends to Go engine via stdin
5. Go engine responds with PONG via stdout
6. Kernel receives response and forwards to caller

**Expected Response:**
```json
{
  "id": "test-ping-1",
  "success": true,
  "result": {
    "message": "PONG",
    "engine": "Go API Hub Engine",
    "version": "0.1.0",
    "timestamp": 1234567890123
  }
}
```

### 4. Health Check

Test engine health status:

**Request:**
```json
{
  "id": "health-1",
  "type": "GET_ENGINE_STATUS",
  "payload": {}
}
```

**Expected Response:**
```json
{
  "id": "health-1",
  "success": true,
  "result": {
    "python": { "status": "running", "healthy": true, ... },
    "rust": { "status": "not_running", ... },
    "go": { "status": "running", "healthy": true, ... }
  }
}
```

### 5. Graceful Shutdown

When Kernel shuts down, it should:
1. Send SHUTDOWN command to Go engine
2. Wait for graceful termination (10 second timeout)
3. Force kill if timeout exceeded
4. Log shutdown completion

**Expected Logs:**
```
[INFO] Stopping all engines...
[INFO] Stopping engine: go
[INFO] [go] SHUTDOWN received, stopping engine
[INFO] Engine go stopped
[INFO] All engines stopped
```

**Go Engine Behavior:**
- Receives SHUTDOWN message
- Sets `running = false`
- Sends shutdown response
- Exits cleanly

## Manual Testing

### Test 1: Standalone Go Engine

Run Go engine manually to verify basic functionality:

```bash
cd engines/go
./api-hub.exe
```

**Manual Input (stdin):**
```json
{"id":"test-1","type":"PING","params":{}}
```

**Expected Output (stdout):**
```json
{"id":"test-1","success":true,"result":{"message":"PONG","engine":"Go API Hub Engine","version":"0.1.0","timestamp":1234567890123}}
```

**Expected Logs (stderr):**
```
[Go API Hub] 2024-01-01 12:00:00 main.go:123: PING received, sending PONG
```

### Test 2: Kernel Integration

1. Start Kernel (via Electron UI or directly)
2. Check logs for Go engine startup
3. Send GO_PING via IPC
4. Verify PONG response
5. Check engine status

## Troubleshooting

### Issue: Go engine not starting

**Symptoms:**
- `Engine go executable not found: <path>`
- `Engine go did not become ready within timeout`

**Solutions:**
1. Verify `api-hub.exe` exists at expected path
2. Check file permissions (executable)
3. Verify Go engine sends READY signal immediately
4. Check stderr for Go engine errors

### Issue: PING not working

**Symptoms:**
- `Engine not available: go`
- `Engine not healthy: go`
- Timeout waiting for response

**Solutions:**
1. Verify Go engine is running: `GET_ENGINE_STATUS`
2. Check Go engine logs (stderr)
3. Verify message format matches protocol
4. Check for JSON parsing errors

### Issue: Shutdown not graceful

**Symptoms:**
- `Engine go did not terminate gracefully, forcing`
- Process remains after shutdown

**Solutions:**
1. Verify SHUTDOWN handler sets `running = false`
2. Check Go engine main loop exits on EOF
3. Verify signal handlers work correctly

## Success Criteria Checklist

- [ ] Go engine binary exists at `engines/go/api-hub.exe`
- [ ] Kernel spawns Go engine on startup
- [ ] READY signal is received and logged
- [ ] PING/PONG round-trip works via `GO_PING`
- [ ] Health check shows Go engine as healthy
- [ ] Graceful shutdown works correctly
- [ ] No architectural violations
- [ ] All logs show proper engine lifecycle

## Next Steps (Phase 4 Step 2+)

Once Phase 4 Step 1 is verified:
- Implement real HTTP client in `api/client.go`
- Add API call handlers (`API_CALL`, `TAX_CODE_LOOKUP`, `AUTH_REFRESH`)
- Implement config file loading
- Add credential management
- Implement rate limiting and retry logic

