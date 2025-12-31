# PowerShell script để kiểm tra và cài đặt thư viện, sau đó chạy conversion
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Setup và Chạy PDF to Markdown Converter" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Tìm Python
$pythonCmd = $null

# Thử các lệnh Python khác nhau
$commands = @("python", "python3", "py")

foreach ($cmd in $commands) {
    try {
        $result = Get-Command $cmd -ErrorAction SilentlyContinue
        if ($result) {
            # Kiểm tra xem có phải Windows Store stub không
            $testVersion = & $cmd --version 2>&1
            if ($testVersion -notmatch "was not found" -and $testVersion -notmatch "install from the Microsoft Store") {
                $pythonCmd = $cmd
                Write-Host "Tìm thấy Python: $cmd" -ForegroundColor Green
                Write-Host "  Version: $testVersion" -ForegroundColor Gray
                break
            }
        }
    } catch {
        continue
    }
}

# Nếu không tìm thấy, thử tìm trong các vị trí thông thường
if (-not $pythonCmd) {
    $pythonPaths = @(
        "$env:LOCALAPPDATA\Programs\Python\*\python.exe",
        "C:\Python*\python.exe",
        "$env:ProgramFiles\Python*\python.exe",
        "$env:USERPROFILE\AppData\Local\Programs\Python\*\python.exe"
    )
    
    foreach ($path in $pythonPaths) {
        $found = Get-ChildItem -Path $path -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) {
            $testVersion = & $found.FullName --version 2>&1
            if ($testVersion -notmatch "was not found") {
                $pythonCmd = $found.FullName
                Write-Host "Tìm thấy Python tại: $pythonCmd" -ForegroundColor Green
                Write-Host "  Version: $testVersion" -ForegroundColor Gray
                break
            }
        }
    }
}

# Kiểm tra và cài đặt thư viện nếu cần
if ($pythonCmd) {
    Write-Host ""
    Write-Host "Đang kiểm tra thư viện cần thiết..." -ForegroundColor Yellow
    
    # Kiểm tra PyMuPDF
    $checkFitz = & $pythonCmd -c "import fitz; print('OK')" 2>&1
    if ($LASTEXITCODE -ne 0 -or $checkFitz -notmatch "OK") {
        Write-Host "Chưa có PyMuPDF, đang cài đặt..." -ForegroundColor Yellow
        & $pythonCmd -m pip install pymupdf --quiet
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✓ Đã cài đặt PyMuPDF" -ForegroundColor Green
        } else {
            Write-Host "✗ Không thể cài đặt PyMuPDF" -ForegroundColor Red
        }
    } else {
        Write-Host "✓ PyMuPDF đã có sẵn" -ForegroundColor Green
    }
    
    # Kiểm tra regex
    $checkRegex = & $pythonCmd -c "import regex; print('OK')" 2>&1
    if ($LASTEXITCODE -ne 0 -or $checkRegex -notmatch "OK") {
        Write-Host "Chưa có regex, đang cài đặt..." -ForegroundColor Yellow
        & $pythonCmd -m pip install regex --quiet
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✓ Đã cài đặt regex" -ForegroundColor Green
        }
    }
    
    Write-Host ""
    Write-Host "Đang chạy conversion..." -ForegroundColor Yellow
    Write-Host ""
    
    # Chuyển về thư mục root của project
    $scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
    $rootPath = Join-Path $scriptPath "..\..\.."
    Set-Location $rootPath
    
    # Chạy script với đường dẫn đầy đủ
    $scriptFile = Join-Path $scriptPath "convert_database.py"
    & $pythonCmd $scriptFile
    
    Write-Host ""
    Write-Host "Hoàn tất!" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "LỖI: Không tìm thấy Python!" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "Vui lòng:" -ForegroundColor Yellow
    Write-Host "1. Cài đặt Python từ https://www.python.org/downloads/" -ForegroundColor Yellow
    Write-Host "2. Khi cài đặt, nhớ chọn 'Add Python to PATH'" -ForegroundColor Yellow
    Write-Host "3. Sau đó chạy lại script này" -ForegroundColor Yellow
    Write-Host ""
    Read-Host "Nhấn Enter để thoát"
}

