@echo off
REM Script pour initialiser Git et pousser sur GitHub

echo ==========================================
echo   Initialisation Git et Push sur GitHub
echo ==========================================
echo.

echo 1. Initialisation du repository Git...
git init
if %ERRORLEVEL% NEQ 0 (
    echo Erreur lors de l'initialisation Git
    pause
    exit /b 1
)
echo OK
echo.

echo 2. Ajout du remote GitHub...
git remote add origin https://github.com/BOUNORAli/BF4INVESTapp.git
if %ERRORLEVEL% NEQ 0 (
    echo Le remote existe peut-etre deja, on continue...
)
echo.

echo 3. Configuration Git (nom et email)...
git config user.name "BOUNORAli"
git config user.email "bounor.ali@example.com"
echo OK - Utilisez "git config user.email votre@email.com" pour changer
echo.

echo 4. Creation de la branche main...
git branch -M main
echo.

echo 5. Ajout de tous les fichiers...
git add .
echo.

echo 6. Creation du commit...
git commit -m "feat: Preparation production complete - Configuration Docker Compose, Vercel et documentation complete"
if %ERRORLEVEL% NEQ 0 (
    echo Erreur lors du commit
    pause
    exit /b 1
)
echo.

echo 7. Push sur GitHub...
git push -u origin main
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ==========================================
    echo   SUCCES ! Tous les fichiers sont sur GitHub
    echo ==========================================
) else (
    echo.
    echo ==========================================
    echo   Erreur lors du push
    echo ==========================================
    echo.
    echo Verifiez:
    echo - Que vous etes connecte a GitHub
    echo - Que le repository existe sur GitHub
    echo - Que vous avez les permissions d'ecriture
)

pause

