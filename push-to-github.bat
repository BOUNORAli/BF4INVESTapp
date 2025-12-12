@echo off
REM Script pour enregistrer tous les changements sur GitHub

echo ==========================================
echo   Enregistrement sur GitHub
echo ==========================================
echo.

echo 1. Verification de l'etat Git...
git status
echo.

echo 2. Ajout de tous les fichiers...
git add .
echo.

echo 3. Creation du commit...
git commit -m "feat: Preparation production - Configuration Docker Compose, Vercel et documentation complete

- Configuration frontend avec variables d'environnement pour production
- Architecture Docker Compose avec nginx pour servir le frontend
- Configuration CORS configurable via variables d'environnement
- Scripts de demarrage pour Windows et Linux
- Documentation utilisateur complete (GUIDE_UTILISATEUR.md)
- Guides de deploiement Vercel et Railway
- Configuration pour deploiement sur Vercel
- Support des historiques de notifications et d'imports
- Correction des problemes de compilation et d'encodage
- Fichier .env.example avec toutes les variables necessaires"
echo.

echo 4. Push sur GitHub...
git push
echo.

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ==========================================
    echo   Enregistrement reussi !
    echo ==========================================
) else (
    echo.
    echo ==========================================
    echo   Erreur lors de l'enregistrement
    echo ==========================================
    echo.
    echo Verifiez:
    echo - Que vous etes connecte a GitHub
    echo - Que le remote est configure: git remote -v
    echo - Que vous avez les permissions
)

pause

