@echo off
REM Script de demarrage pour BF4 Invest - Production (Windows Batch)
REM Ce script contourne les restrictions de politique d'execution PowerShell

chcp 65001 >nul
echo ==========================================
echo   BF4 Invest - Demarrage Production
echo ==========================================
echo.

REM Executer le script PowerShell avec bypass de la politique
powershell.exe -ExecutionPolicy Bypass -File "%~dp0start-production.ps1"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Erreur lors du demarrage. Verifiez les logs ci-dessus.
    pause
    exit /b %ERRORLEVEL%
)

pause

