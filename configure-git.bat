@echo off
REM Script pour configurer Git avec votre nom et email

echo ==========================================
echo   Configuration Git
echo ==========================================
echo.

echo Entrez votre nom (ex: BOUNORAli):
set /p GIT_NAME=

echo Entrez votre email (ex: votre@email.com):
set /p GIT_EMAIL=

echo.
echo Configuration de Git...
git config user.name "%GIT_NAME%"
git config user.email "%GIT_EMAIL%"

echo.
echo Configuration terminee !
echo Nom: %GIT_NAME%
echo Email: %GIT_EMAIL%
echo.
echo Pour configurer globalement (tous les projets):
echo   git config --global user.name "%GIT_NAME%"
echo   git config --global user.email "%GIT_EMAIL%"
echo.

pause

