# Chạy GUI cho PDF to Markdown Converter
# PowerShell Script

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  PDF to Markdown Converter GUI" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Đang khởi động giao diện..." -ForegroundColor Green
Write-Host ""

# Chuyển về thư mục root
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootPath = Join-Path $scriptPath "..\.."
Set-Location $rootPath

try {
    python -m pdf2markdown.gui.main_window
} catch {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "  LỖI: Không thể chạy ứng dụng" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "Có thể do:" -ForegroundColor Yellow
    Write-Host "- Chưa cài Python 3.13" -ForegroundColor Yellow
    Write-Host "- Chưa cài dependencies: pip install -r requirements.txt" -ForegroundColor Yellow
    Write-Host ""
    Read-Host "Nhấn Enter để thoát"
}

