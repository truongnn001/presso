# PressO Go API Hub Engine - Build Script (PowerShell)
# Builds the engine executable for Windows

Write-Host "Building Go API Hub Engine..." -ForegroundColor Cyan

$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptPath

# Check if Go is installed
try {
    $goVersion = go version 2>&1
    Write-Host "Go version: $goVersion" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Go is not installed or not in PATH" -ForegroundColor Red
    Write-Host "Please install Go from https://go.dev/dl/" -ForegroundColor Yellow
    exit 1
}

# Build the executable
Write-Host "Building api-hub.exe..." -ForegroundColor Cyan
go build -o api-hub.exe main.go

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "SUCCESS: api-hub.exe built successfully" -ForegroundColor Green
    Write-Host "Location: $(Get-Location)\api-hub.exe" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "ERROR: Build failed" -ForegroundColor Red
    exit 1
}

