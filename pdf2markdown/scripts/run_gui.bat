@echo off
chcp 65001 >nul
title PDF to Markdown Converter
echo ========================================
echo   PDF to Markdown Converter GUI
echo ========================================
echo.
echo Đang khởi động giao diện...
echo.

cd /d "%~dp0\..\.."
python -m pdf2markdown.gui.main_window

if errorlevel 1 (
    echo.
    echo ========================================
    echo   LỖI: Không thể chạy ứng dụng
    echo ========================================
    echo.
    echo Có thể do:
    echo - Chưa cài Python 3.13
    echo - Chưa cài dependencies: pip install -r requirements.txt
    echo.
    pause
)

