# PowerShell script to install Python 3.7+ with PATH configuration
# Requires Administrator privileges

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Python Installation Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check if running as Administrator
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host "ERROR: This script requires Administrator privileges!" -ForegroundColor Red
    Write-Host "Please run PowerShell as Administrator and try again." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Right-click PowerShell and select 'Run as Administrator'" -ForegroundColor Yellow
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "Running as Administrator - OK" -ForegroundColor Green
Write-Host ""

# Check if Python already installed
$pythonPath = $null
$pythonVersions = @("python3.12", "python3.11", "python3.10", "python3.9", "python3.8", "python3.7", "python")

foreach ($pyCmd in $pythonVersions) {
    try {
        $result = Get-Command $pyCmd -ErrorAction SilentlyContinue
        if ($result) {
            $version = & $pyCmd --version 2>&1
            if ($version -notmatch "was not found" -and $version -notmatch "install from the Microsoft Store") {
                Write-Host "Python already installed: $version" -ForegroundColor Green
                Write-Host "Location: $($result.Source)" -ForegroundColor Gray
                $pythonPath = $result.Source
                break
            }
        }
    } catch {
        continue
    }
}

# Check common installation paths
if (-not $pythonPath) {
    $commonPaths = @(
        "$env:LOCALAPPDATA\Programs\Python\Python3*\python.exe",
        "C:\Python3*\python.exe",
        "$env:ProgramFiles\Python3*\python.exe",
        "$env:ProgramFiles(x86)\Python3*\python.exe"
    )
    
    foreach ($pathPattern in $commonPaths) {
        $found = Get-ChildItem -Path $pathPattern -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) {
            $version = & $found.FullName --version 2>&1
            if ($version -notmatch "was not found") {
                Write-Host "Found Python: $version" -ForegroundColor Green
                Write-Host "Location: $($found.FullName)" -ForegroundColor Gray
                $pythonPath = $found.FullName
                break
            }
        }
    }
}

if ($pythonPath) {
    Write-Host ""
    Write-Host "Python is already installed. Checking PATH..." -ForegroundColor Yellow
    
    # Check if Python is in PATH
    $pythonDir = Split-Path $pythonPath -Parent
    $scriptsDir = Join-Path $pythonDir "Scripts"
    
    $currentPath = [Environment]::GetEnvironmentVariable("Path", "Machine")
    $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
    
    $inPath = $false
    if ($currentPath -like "*$pythonDir*" -or $userPath -like "*$pythonDir*") {
        $inPath = $true
        Write-Host "Python is already in PATH" -ForegroundColor Green
    } else {
        Write-Host "Python is NOT in PATH. Adding..." -ForegroundColor Yellow
        
        # Add to User PATH
        if ($userPath) {
            $newUserPath = "$userPath;$pythonDir;$scriptsDir"
        } else {
            $newUserPath = "$pythonDir;$scriptsDir"
        }
        
        [Environment]::SetEnvironmentVariable("Path", $newUserPath, "User")
        $env:Path = "$env:Path;$pythonDir;$scriptsDir"
        
        Write-Host "Added to PATH: $pythonDir" -ForegroundColor Green
        Write-Host "Added to PATH: $scriptsDir" -ForegroundColor Green
    }
    
    Write-Host ""
    Write-Host "Verifying installation..." -ForegroundColor Yellow
    $testVersion = & $pythonPath --version 2>&1
    Write-Host "Python version: $testVersion" -ForegroundColor Green
    
    Write-Host ""
    Write-Host "Installation complete!" -ForegroundColor Green
    Write-Host "Please close and reopen your terminal for PATH changes to take effect." -ForegroundColor Yellow
    Read-Host "Press Enter to exit"
    exit 0
}

# Python not found, proceed with installation
Write-Host "Python not found. Starting installation..." -ForegroundColor Yellow
Write-Host ""

# Try using winget first (Windows Package Manager)
$useWinget = $false
try {
    $wingetCheck = Get-Command winget -ErrorAction SilentlyContinue
    if ($wingetCheck) {
        Write-Host "Found winget. Using winget to install Python..." -ForegroundColor Green
        $useWinget = $true
    }
} catch {
    # winget not available
}

if ($useWinget) {
    Write-Host "Installing Python 3.12 (latest stable) via winget..." -ForegroundColor Yellow
    Write-Host "This may take a few minutes..." -ForegroundColor Gray
    
    winget install Python.Python.3.12 --silent --accept-package-agreements --accept-source-agreements
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Python installed successfully via winget!" -ForegroundColor Green
        
        # Wait a moment for installation to complete
        Start-Sleep -Seconds 5
        
        # Refresh PATH
        $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
        
        # Verify installation
        $pythonPath = Get-Command python -ErrorAction SilentlyContinue
        if ($pythonPath) {
            $version = & python --version 2>&1
            Write-Host "Python version: $version" -ForegroundColor Green
            Write-Host "Installation location: $($pythonPath.Source)" -ForegroundColor Gray
        }
        
        Write-Host ""
        Write-Host "Installation complete!" -ForegroundColor Green
        Write-Host "Please close and reopen your terminal for PATH changes to take effect." -ForegroundColor Yellow
        Read-Host "Press Enter to exit"
        exit 0
    } else {
        Write-Host "winget installation failed. Trying manual download..." -ForegroundColor Yellow
    }
}

# Manual download and installation
Write-Host "Downloading Python 3.12 installer..." -ForegroundColor Yellow
Write-Host ""

$downloadUrl = "https://www.python.org/ftp/python/3.12.1/python-3.12.1-amd64.exe"
$installerPath = "$env:TEMP\python-installer.exe"

try {
    # Download Python installer
    Write-Host "Downloading from: $downloadUrl" -ForegroundColor Gray
    Invoke-WebRequest -Uri $downloadUrl -OutFile $installerPath -UseBasicParsing
    
    Write-Host "Download complete!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Installing Python..." -ForegroundColor Yellow
    Write-Host "This will install Python with the following options:" -ForegroundColor Gray
    Write-Host "  - Add Python to PATH" -ForegroundColor Gray
    Write-Host "  - Install for all users" -ForegroundColor Gray
    Write-Host "  - Install pip" -ForegroundColor Gray
    Write-Host ""
    
    # Install Python with silent options
    # /quiet = silent installation
    # InstallAllUsers = 1 = install for all users
    # PrependPath = 1 = add to PATH
    # Include_test = 0 = don't install test suite
    $installArgs = @(
        "/quiet",
        "InstallAllUsers=1",
        "PrependPath=1",
        "Include_test=0",
        "Include_pip=1",
        "Include_doc=0",
        "Include_tcltk=0"
    )
    
    $process = Start-Process -FilePath $installerPath -ArgumentList $installArgs -Wait -PassThru
    
    if ($process.ExitCode -eq 0) {
        Write-Host "Python installed successfully!" -ForegroundColor Green
        
        # Refresh PATH
        $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
        
        # Wait a moment
        Start-Sleep -Seconds 3
        
        # Verify installation
        $pythonPath = Get-Command python -ErrorAction SilentlyContinue
        if ($pythonPath) {
            $version = & python --version 2>&1
            Write-Host "Python version: $version" -ForegroundColor Green
            Write-Host "Installation location: $($pythonPath.Source)" -ForegroundColor Gray
        } else {
            Write-Host "Python installed but not found in PATH yet." -ForegroundColor Yellow
            Write-Host "Please close and reopen your terminal." -ForegroundColor Yellow
        }
        
        Write-Host ""
        Write-Host "Installation complete!" -ForegroundColor Green
    } else {
        Write-Host "Installation failed with exit code: $($process.ExitCode)" -ForegroundColor Red
        Write-Host "You may need to run the installer manually." -ForegroundColor Yellow
    }
    
    # Clean up
    if (Test-Path $installerPath) {
        Remove-Item $installerPath -Force
    }
    
} catch {
    Write-Host "Error during installation: $_" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please download and install Python manually from:" -ForegroundColor Yellow
    Write-Host "https://www.python.org/downloads/" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Make sure to:" -ForegroundColor Yellow
    Write-Host "1. Check 'Add Python to PATH' during installation" -ForegroundColor Yellow
    Write-Host "2. Choose 'Install for all users' if you have admin rights" -ForegroundColor Yellow
}

Write-Host ""
Read-Host "Press Enter to exit"

