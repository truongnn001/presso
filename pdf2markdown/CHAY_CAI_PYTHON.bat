@echo off
REM Batch file to install Python with admin privileges
echo ========================================
echo Python Installation
echo ========================================
echo.
echo This will request Administrator privileges to install Python.
echo Please click 'Yes' when you see the UAC prompt.
echo.
pause

REM Run PowerShell script with admin privileges
powershell -ExecutionPolicy Bypass -File "%~dp0install_python_admin.ps1"

pause

