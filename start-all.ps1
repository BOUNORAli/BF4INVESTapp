# Script de demarrage pour BF4 Invest
Write-Host "========================================" -ForegroundColor Green
Write-Host "   BF4 INVEST - Demarrage des Services" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

# 1. Demarrer MongoDB
Write-Host "[1/3] Verification de MongoDB..." -ForegroundColor Yellow
$mongoRunning = docker ps --filter "name=mongodb" --format "{{.Names}}"
if (-not $mongoRunning) {
    Write-Host "   Demarrage de MongoDB..." -ForegroundColor Cyan
    docker run -d -p 27017:27017 --name mongodb mongo:7.0 2>&1 | Out-Null
    Start-Sleep -Seconds 3
    Write-Host "   OK MongoDB demarre" -ForegroundColor Green
} else {
    Write-Host "   OK MongoDB deja en cours d'execution" -ForegroundColor Green
}

# 2. Demarrer le Backend
Write-Host "[2/3] Demarrage du Backend Spring Boot..." -ForegroundColor Yellow
$backendDir = Join-Path $PSScriptRoot "backend"
Set-Location $backendDir

# Compiler si necessaire
if (-not (Test-Path "target\*.jar")) {
    Write-Host "   Compilation en cours..." -ForegroundColor Cyan
    mvn clean package -DskipTests | Out-Null
}

Write-Host "   Lancement du backend dans une nouvelle fenetre..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$backendDir'; mvn spring-boot:run"
Write-Host "   OK Backend en cours de demarrage" -ForegroundColor Green
Start-Sleep -Seconds 2

# 3. Demarrer le Frontend
Write-Host "[3/3] Demarrage du Frontend Angular..." -ForegroundColor Yellow
$frontendDir = Join-Path $PSScriptRoot "frontend"
Set-Location $frontendDir

# Installer les dependances si necessaire
if (-not (Test-Path "node_modules")) {
    Write-Host "   Installation des dependances npm..." -ForegroundColor Cyan
    cmd /c "npm install"
}

Write-Host "   Lancement du frontend dans une nouvelle fenetre..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$frontendDir'; cmd /c 'npm run dev'"
Write-Host "   OK Frontend en cours de demarrage" -ForegroundColor Green

# Resume
Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "   Services demarres avec succes!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "URLs:" -ForegroundColor Cyan
Write-Host "   - MongoDB: localhost:27017" -ForegroundColor White
Write-Host "   - Backend API: http://localhost:8080/api" -ForegroundColor White
Write-Host "   - Swagger UI: http://localhost:8080/api/swagger-ui.html" -ForegroundColor White
Write-Host "   - Frontend: http://localhost:4200" -ForegroundColor White
Write-Host ""
Write-Host "Identifiants de connexion:" -ForegroundColor Cyan
Write-Host "   Email: admin@bf4invest.ma" -ForegroundColor White
Write-Host "   Mot de passe: admin123" -ForegroundColor White
Write-Host ""
Write-Host "Les logs sont affiches dans les fenetres PowerShell ouvertes" -ForegroundColor Yellow
Write-Host "Attendez quelques secondes que les services demarrent completement" -ForegroundColor Yellow
Write-Host ""
