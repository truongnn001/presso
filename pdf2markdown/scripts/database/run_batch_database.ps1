# PowerShell script để chạy batch processing cho Database PDFs
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Batch Processing Database PDFs" -ForegroundColor Cyan
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
        "$env:ProgramFiles\Python*\python.exe"
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

# Chạy script
if ($pythonCmd) {
    Write-Host ""
    Write-Host "Đang chạy batch processing..." -ForegroundColor Yellow
    Write-Host ""
    
    # Chuyển về thư mục root của project
    $scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
    $rootPath = Join-Path $scriptPath "..\..\.."
    Set-Location $rootPath
    
    # Chạy script với đường dẫn đầy đủ
    $scriptFile = Join-Path $scriptPath "batch_process_database.py"
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
    Write-Host "2. Hoặc đảm bảo Python đã được thêm vào PATH" -ForegroundColor Yellow
    Write-Host "3. Sau đó chạy lại script này" -ForegroundColor Yellow
    Write-Host ""
    Read-Host "Nhấn Enter để thoát"
}

