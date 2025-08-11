@echo off
echo "Creating command line tools: ""xtc.exe"", ""xec.exe"", ""xam.exe"""
REM Find architecture-specific launcher (Windows is typically amd64)
set LAUNCHER="%~dp0windows_launcher_amd64.exe"
if not exist %LAUNCHER% set LAUNCHER="%~dp0windows_launcher.exe"
copy /v /y /b %LAUNCHER% "%~dp0xtc.exe"
copy /v /y /b %LAUNCHER% "%~dp0xec.exe"
copy /v /y /b %LAUNCHER% "%~dp0xam.exe"

echo "Adding ""%~dp0"" to path"
echo %path%|find /i "%~dp0">nul  || set path=%path%;%~dp0

