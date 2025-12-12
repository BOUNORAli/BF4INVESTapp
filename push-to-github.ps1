# Script PowerShell pour enregistrer tous les changements sur GitHub

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  Enregistrement sur GitHub" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "1. Verification de l'etat Git..." -ForegroundColor Yellow
git status
Write-Host ""

Write-Host "2. Ajout de tous les fichiers..." -ForegroundColor Yellow
git add .
Write-Host ""

Write-Host "3. Creation du commit..." -ForegroundColor Yellow
$commitMessage = @"
feat: Preparation production - Configuration Docker Compose, Vercel et documentation complete

- Configuration frontend avec variables d'environnement pour production
- Architecture Docker Compose avec nginx pour servir le frontend
- Configuration CORS configurable via variables d'environnement
- Scripts de demarrage pour Windows et Linux
- Documentation utilisateur complete (GUIDE_UTILISATEUR.md)
- Guides de deploiement Vercel et Railway
- Configuration pour deploiement sur Vercel
- Support des historiques de notifications et d'imports
- Correction des problemes de compilation et d'encodage
- Fichier .env.example avec toutes les variables necessaires
"@

git commit -m $commitMessage
Write-Host ""

Write-Host "4. Push sur GitHub..." -ForegroundColor Yellow
git push

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "==========================================" -ForegroundColor Green
    Write-Host "  Enregistrement reussi !" -ForegroundColor Green
    Write-Host "==========================================" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "==========================================" -ForegroundColor Red
    Write-Host "  Erreur lors de l'enregistrement" -ForegroundColor Red
    Write-Host "==========================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "Verifiez:" -ForegroundColor Yellow
    Write-Host "- Que vous etes connecte a GitHub"
    Write-Host "- Que le remote est configure: git remote -v"
    Write-Host "- Que vous avez les permissions"
}

