@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0shove.ps1" pair %*
if errorlevel 1 pause
endlocal
