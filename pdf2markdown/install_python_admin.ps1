# Script to request admin privileges and run Python installation
param(
    [switch]$Force
)

# Check if running as Administrator
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host "Requesting Administrator privileges..." -ForegroundColor Yellow
    Write-Host "You will see a UAC prompt. Please click 'Yes'." -ForegroundColor Yellow
    Write-Host ""
    
    # Get the script path
    $scriptPath = Join-Path $PSScriptRoot "install_python.ps1"
    
    # Relaunch with admin privileges
    Start-Process powershell.exe -Verb RunAs -ArgumentList "-ExecutionPolicy Bypass -File `"$scriptPath`"" -Wait
    
    exit
}

# If already admin, run the installation script
& "$PSScriptRoot\install_python.ps1"

