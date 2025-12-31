# PowerShell script để chạy PDF to Markdown conversion
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "PDF to Markdown Converter" -ForegroundColor Cyan
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
            $pythonCmd = $cmd
            Write-Host "Tìm thấy Python: $cmd" -ForegroundColor Green
            break
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
            $pythonCmd = $found.FullName
            Write-Host "Tìm thấy Python tại: $pythonCmd" -ForegroundColor Green
            break
        }
    }
}

# Kiểm tra xem Python có thể import fitz không (PyMuPDF)
if ($pythonCmd) {
    try {
        $testResult = & $pythonCmd -c "import fitz; print('OK')" 2>&1
        if ($LASTEXITCODE -ne 0 -or $testResult -notmatch "OK") {
            Write-Host "Cảnh báo: Python tìm thấy nhưng chưa có thư viện PyMuPDF (fitz)" -ForegroundColor Yellow
            Write-Host "Vui lòng cài đặt: pip install pymupdf" -ForegroundColor Yellow
        }
    } catch {
        # Bỏ qua lỗi kiểm tra
    }
}

# Chuyển về thư mục root
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootPath = Join-Path $scriptPath "..\.."
Set-Location $rootPath

# Chạy script
if ($pythonCmd) {
    Write-Host ""
    Write-Host "Đang chạy conversion..." -ForegroundColor Yellow
    Write-Host ""
    
    if ($pythonCmd -eq "python" -or $pythonCmd -eq "python3" -or $pythonCmd -eq "py") {
        & $pythonCmd main.py
    } else {
        & $pythonCmd main.py
    }
    
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
    Write-Host "2. Hoặc đảm bảo Python đã được thêm vào PATH" -ForegroundColor Yellow
    Write-Host "3. Sau đó chạy lại script này" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Nhấn phím bất kỳ để thoát..."
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
}

