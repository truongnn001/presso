@echo off
REM Script batch để chạy PDF to Markdown conversion
echo ========================================
echo PDF to Markdown Converter
echo ========================================
echo.

cd /d "%~dp0\..\.."

REM Thử các lệnh Python khác nhau
where python >nul 2>&1
if %errorlevel% == 0 (
    echo Tìm thấy Python, đang chạy...
    python main.py
    goto :end
)

where python3 >nul 2>&1
if %errorlevel% == 0 (
    echo Tìm thấy Python3, đang chạy...
    python3 main.py
    goto :end
)

where py >nul 2>&1
if %errorlevel% == 0 (
    echo Tìm thấy py launcher, đang chạy...
    py main.py
    goto :end
)

REM Thử tìm Python trong các vị trí thông thường
if exist "C:\Python*\python.exe" (
    for %%p in (C:\Python*\python.exe) do (
        echo Tìm thấy Python tại %%p, đang chạy...
        "%%p" main.py
        goto :end
    )
)

if exist "%LOCALAPPDATA%\Programs\Python\python.exe" (
    echo Tìm thấy Python tại %LOCALAPPDATA%\Programs\Python, đang chạy...
    "%LOCALAPPDATA%\Programs\Python\python.exe" main.py
    goto :end
)

echo.
echo ========================================
echo LỖI: Không tìm thấy Python!
echo ========================================
echo.
echo Vui lòng:
echo 1. Cài đặt Python từ https://www.python.org/downloads/
echo 2. Hoặc đảm bảo Python đã được thêm vào PATH
echo 3. Sau đó chạy lại script này
echo.
pause

:end
pause

