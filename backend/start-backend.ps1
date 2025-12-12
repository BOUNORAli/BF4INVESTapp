# Script de demarrage backend
Write-Host "=== Demarrage Backend Spring Boot ===" -ForegroundColor Green
cd C:\Users\PC\Documents\BF4INVESTapp\backend

Write-Host "Compilation en cours..." -ForegroundColor Yellow
mvn clean package -DskipTests

if ($LASTEXITCODE -eq 0) {
    Write-Host "`nCompilation reussie! Demarrage du serveur..." -ForegroundColor Green
    mvn spring-boot:run
} else {
    Write-Host "`nErreur de compilation. Verifiez les logs ci-dessus." -ForegroundColor Red
    Write-Host "Appuyez sur une touche pour continuer..." -ForegroundColor Yellow
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
}



