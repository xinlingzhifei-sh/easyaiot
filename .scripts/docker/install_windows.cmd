@echo off
REM yFeiEye Windows deploy launcher (bypasses ExecutionPolicy for this script only)
setlocal
cd /d "%~dp0"
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install_windows.ps1" %*
exit /b %ERRORLEVEL%
