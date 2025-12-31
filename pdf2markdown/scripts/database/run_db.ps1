# PowerShell script to run database conversion
Write-Host "Starting PDF to Markdown conversion..." -ForegroundColor Cyan
Write-Host ""

# Find Python
$pythonCmd = $null
$commands = @("python", "python3", "py")

foreach ($cmd in $commands) {
    try {
        $result = Get-Command $cmd -ErrorAction SilentlyContinue
        if ($result) {
            $testVersion = & $cmd --version 2>&1
            if ($testVersion -notmatch "was not found" -and $testVersion -notmatch "install from the Microsoft Store") {
                $pythonCmd = $cmd
                Write-Host "Found Python: $cmd" -ForegroundColor Green
                break
            }
        }
    } catch {
        continue
    }
}

# Try common Python locations
if (-not $pythonCmd) {
    $pythonPaths = @(
        "$env:LOCALAPPDATA\Programs\Python\*\python.exe",
        "C:\Python*\python.exe",
        "$env:ProgramFiles\Python*\python.exe"
    )
    
    foreach ($path in $pythonPaths) {
        $found = Get-ChildItem -Path $path -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) {
            $testVersion = & $found.FullName --version 2>&1
            if ($testVersion -notmatch "was not found") {
                $pythonCmd = $found.FullName
                Write-Host "Found Python at: $pythonCmd" -ForegroundColor Green
                break
            }
        }
    }
}

# Check and install libraries if needed
if ($pythonCmd) {
    Write-Host ""
    Write-Host "Checking required libraries..." -ForegroundColor Yellow
    
    # Check PyMuPDF
    $checkFitz = & $pythonCmd -c "import fitz; print('OK')" 2>&1
    if ($LASTEXITCODE -ne 0 -or $checkFitz -notmatch "OK") {
        Write-Host "Installing PyMuPDF..." -ForegroundColor Yellow
        & $pythonCmd -m pip install pymupdf --quiet
    } else {
        Write-Host "PyMuPDF OK" -ForegroundColor Green
    }
    
    # Check regex
    $checkRegex = & $pythonCmd -c "import regex; print('OK')" 2>&1
    if ($LASTEXITCODE -ne 0 -or $checkRegex -notmatch "OK") {
        Write-Host "Installing regex..." -ForegroundColor Yellow
        & $pythonCmd -m pip install regex --quiet
    }
    
    Write-Host ""
    Write-Host "Running conversion..." -ForegroundColor Yellow
    Write-Host ""
    
    # Chuyển về thư mục root của project
    $scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
    $rootPath = Join-Path $scriptPath "..\..\.."
    Set-Location $rootPath
    
    # Chạy script với đường dẫn đầy đủ
    $scriptFile = Join-Path $scriptPath "convert_database.py"
    & $pythonCmd $scriptFile
    
    Write-Host ""
    Write-Host "Done!" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "ERROR: Python not found!" -ForegroundColor Red
    Write-Host "Please install Python from https://www.python.org/downloads/" -ForegroundColor Yellow
    Write-Host "Make sure to add Python to PATH during installation" -ForegroundColor Yellow
    Write-Host ""
    Read-Host "Press Enter to exit"
}

