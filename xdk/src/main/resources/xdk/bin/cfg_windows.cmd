@echo off
echo "Creating command line tools: ""xtc.exe"", ""xec.exe"", ""xtest.exe"", ""xam.exe"""
copy /v /y /b "%~dp0windows_launcher.exe" "%~dp0xtc.exe"
copy /v /y /b "%~dp0windows_launcher.exe" "%~dp0xtest.exe"
copy /v /y /b "%~dp0windows_launcher.exe" "%~dp0xec.exe"
copy /v /y /b "%~dp0windows_launcher.exe" "%~dp0xam.exe"

echo "Adding ""%~dp0"" to path"
echo %path%|find /i "%~dp0">nul  || set path=%path%;%~dp0

