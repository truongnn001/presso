# Manual test script for Go API Hub Engine
# Tests READY signal and PING/PONG

$exePath = Join-Path $PSScriptRoot "api-hub.exe"

if (-not (Test-Path $exePath)) {
    Write-Host "ERROR: api-hub.exe not found at $exePath" -ForegroundColor Red
    exit 1
}

Write-Host "Starting Go engine for manual test..." -ForegroundColor Cyan

# Create process start info
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $exePath
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true

# Start process
$process = [System.Diagnostics.Process]::Start($psi)

# Wait a bit for READY signal
Start-Sleep -Milliseconds 500

# Read READY signal
$readyLine = $process.StandardOutput.ReadLine()
Write-Host "READY Signal: $readyLine" -ForegroundColor Green

# Send PING
$pingMsg = '{"id":"test-1","type":"PING","params":{}}'
Write-Host "Sending PING: $pingMsg" -ForegroundColor Cyan
$process.StandardInput.WriteLine($pingMsg)
$process.StandardInput.Flush()

# Wait for response
Start-Sleep -Milliseconds 200
$response = $process.StandardOutput.ReadLine()
Write-Host "PONG Response: $response" -ForegroundColor Green

# Check stderr for logs
$errorOutput = $process.StandardError.ReadToEnd()
if ($errorOutput) {
    Write-Host "Stderr logs:" -ForegroundColor Yellow
    Write-Host $errorOutput
}

# Cleanup
$process.Kill()
$process.WaitForExit()

Write-Host "`nTest completed." -ForegroundColor Cyan

