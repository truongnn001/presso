@echo off
REM PressO Go API Hub Engine - Build Script
REM Builds the engine executable for Windows

echo Building Go API Hub Engine...

cd /d "%~dp0"

REM Check if Go is installed
go version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Go is not installed or not in PATH
    echo Please install Go from https://go.dev/dl/
    exit /b 1
)

REM Build the executable
echo Building api-hub.exe...
go build -o api-hub.exe main.go

if %errorlevel% equ 0 (
    echo.
    echo SUCCESS: api-hub.exe built successfully
    echo Location: %CD%\api-hub.exe
) else (
    echo.
    echo ERROR: Build failed
    exit /b 1
)

