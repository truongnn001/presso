# Runtime Verification Script for Phase 4 Step 1
# Tests Go API Hub Engine integration with Kernel

Write-Host "=== PHASE 4 STEP 1 RUNTIME VERIFICATION ===" -ForegroundColor Cyan
Write-Host ""

# Check prerequisites
$goExe = "E:\PressO\engines\go\api-hub.exe"
if (-not (Test-Path $goExe)) {
    Write-Host "ERROR: api-hub.exe not found at $goExe" -ForegroundColor Red
    exit 1
}
Write-Host "✓ Go executable found: $goExe" -ForegroundColor Green

# Test 1: Verify Go engine can be spawned and sends READY
Write-Host ""
Write-Host "TEST 1: Go Engine READY Signal" -ForegroundColor Yellow
Write-Host "----------------------------------------"

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $goExe
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true
$psi.WorkingDirectory = Split-Path $goExe

$process = [System.Diagnostics.Process]::Start($psi)
Start-Sleep -Milliseconds 500

# Read READY signal
$readyLine = $process.StandardOutput.ReadLine()
Write-Host "READY Signal: $readyLine" -ForegroundColor Cyan

if ($readyLine -match '"type"\s*:\s*"READY"') {
    Write-Host "✓ READY signal received" -ForegroundColor Green
    $readyReceived = $true
} else {
    Write-Host "✗ READY signal NOT received or invalid format" -ForegroundColor Red
    $readyReceived = $false
}

# Test 2: PING/PONG round-trip
Write-Host ""
Write-Host "TEST 2: PING/PONG Round-Trip" -ForegroundColor Yellow
Write-Host "----------------------------------------"

$pingMsg = '{"id":"test-ping-1","type":"PING","params":{}}'
Write-Host "Sending: $pingMsg" -ForegroundColor Cyan
$process.StandardInput.WriteLine($pingMsg)
$process.StandardInput.Flush()

Start-Sleep -Milliseconds 200
$pongLine = $process.StandardOutput.ReadLine()
Write-Host "Response: $pongLine" -ForegroundColor Cyan

if ($pongLine -match '"message"\s*:\s*"PONG"' -and $pongLine -match '"success"\s*:\s*true') {
    Write-Host "✓ PING/PONG round-trip successful" -ForegroundColor Green
    $pingPongWorks = $true
} else {
    Write-Host "✗ PING/PONG round-trip FAILED" -ForegroundColor Red
    $pingPongWorks = $false
}

# Cleanup
$process.Kill()
$process.WaitForExit()

# Final verdict
Write-Host ""
Write-Host "=== VERIFICATION RESULTS ===" -ForegroundColor Cyan
Write-Host ""

if ($readyReceived -and $pingPongWorks) {
    Write-Host "✓ Go engine standalone test: PASSED" -ForegroundColor Green
    Write-Host ""
    Write-Host "NOTE: Kernel integration requires Kernel JAR to be built." -ForegroundColor Yellow
    Write-Host "      Standalone verification shows Go engine is functional." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "VERDICT: Phase 4 Step 1 - PARTIALLY VERIFIED" -ForegroundColor Yellow
    Write-Host "  - Go engine executable: ✓" -ForegroundColor Green
    Write-Host "  - READY signal: ✓" -ForegroundColor Green
    Write-Host "  - PING/PONG: ✓" -ForegroundColor Green
    Write-Host "  - Kernel spawn: ⚠ NOT TESTED (requires Kernel build)" -ForegroundColor Yellow
} else {
    Write-Host "✗ Go engine test: FAILED" -ForegroundColor Red
    Write-Host ""
    Write-Host "VERDICT: Phase 4 Step 1 - NOT COMPLETED" -ForegroundColor Red
}

