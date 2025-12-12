# Script de demarrage pour BF4 Invest - Production (Windows PowerShell)
# Usage: .\start-production.ps1
# 
# Si vous obtenez une erreur de politique d'execution:
# 1. Utilisez start-production.bat a la place
# 2. Ou executez: powershell -ExecutionPolicy Bypass -File .\start-production.ps1

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  BF4 Invest - Demarrage Production" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# Verifier si Docker est installe
try {
    docker --version | Out-Null
} catch {
    Write-Host "ERREUR: Docker n'est pas installe. Veuillez l'installer d'abord." -ForegroundColor Red
    exit 1
}

# Verifier si Docker Compose est installe
try {
    docker compose version | Out-Null
    $COMPOSE_CMD = "docker compose"
} catch {
    try {
        docker-compose --version | Out-Null
        $COMPOSE_CMD = "docker-compose"
    } catch {
        Write-Host "ERREUR: Docker Compose n'est pas installe. Veuillez l'installer d'abord." -ForegroundColor Red
        exit 1
    }
}

# Verifier si le fichier .env existe
if (-not (Test-Path .env)) {
    Write-Host "Creation du fichier .env..." -ForegroundColor Yellow
    if (Test-Path .env.example) {
        Copy-Item .env.example .env
        Write-Host "Fichier .env cree depuis .env.example." -ForegroundColor Green
    } else {
        # Creer un fichier .env avec les valeurs par defaut
        @"
MONGODB_URI=mongodb://mongodb:27017/bf4invest
JWT_SECRET=your-production-secret-key-minimum-256-bits-change-this-in-production
JWT_EXPIRATION=86400000
SERVER_PORT=8080
FRONTEND_PORT=80
CORS_ALLOWED_ORIGINS=
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=
SMTP_PASSWORD=
"@ | Out-File -FilePath .env -Encoding utf8
        Write-Host "Fichier .env cree avec les valeurs par defaut." -ForegroundColor Green
    }
    Write-Host ""
    Write-Host "IMPORTANT: Veuillez modifier le fichier .env avec vos parametres de production !" -ForegroundColor Yellow
    Write-Host "   - Generer un JWT_SECRET fort: openssl rand -base64 32" -ForegroundColor Yellow
    Write-Host "   - Configurer les parametres MongoDB si necessaire" -ForegroundColor Yellow
    Write-Host ""
    $continue = Read-Host "Continuer avec les valeurs par defaut ? (oui/non)"
    if ($continue -ne "oui") {
        Write-Host "Arret du demarrage. Modifiez .env puis relancez." -ForegroundColor Red
        exit 1
    }
}

# Verifier le JWT_SECRET
$envContent = Get-Content .env -Raw
if ($envContent -match "your-production-secret-key" -or $envContent -match "change-in-production") {
    Write-Host "ATTENTION: Le JWT_SECRET par defaut est toujours utilise !" -ForegroundColor Yellow
    Write-Host "   Veuillez generer un secret fort pour la production." -ForegroundColor Yellow
    Write-Host "   Commande: openssl rand -base64 32" -ForegroundColor Yellow
    Write-Host ""
    $continue = Read-Host "Continuer quand meme ? (oui/non)"
    if ($continue -ne "oui") {
        Write-Host "Arret du demarrage." -ForegroundColor Red
        exit 1
    }
}

Write-Host "Construction et demarrage des conteneurs..." -ForegroundColor Cyan
Write-Host ""

# Construire et demarrer
Invoke-Expression "$COMPOSE_CMD up -d --build"

Write-Host ""
Write-Host "Attente du demarrage des services..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

# Verifier que les services sont demarres
Write-Host ""
Write-Host "Etat des services:" -ForegroundColor Cyan
Invoke-Expression "$COMPOSE_CMD ps"

Write-Host ""
Write-Host "==========================================" -ForegroundColor Green
Write-Host "Application demarree avec succes !" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Frontend: http://localhost" -ForegroundColor Cyan
Write-Host "Backend API: http://localhost/api" -ForegroundColor Cyan
Write-Host ""
Write-Host "Identifiants par defaut:" -ForegroundColor Yellow
Write-Host "   Email: admin@bf4invest.ma"
Write-Host "   Mot de passe: admin123"
Write-Host ""
Write-Host "IMPORTANT: Changez le mot de passe admin apres la premiere connexion !" -ForegroundColor Yellow
Write-Host ""
Write-Host "Voir les logs: $COMPOSE_CMD logs -f" -ForegroundColor Cyan
Write-Host "Arreter: $COMPOSE_CMD down" -ForegroundColor Cyan
Write-Host ""
