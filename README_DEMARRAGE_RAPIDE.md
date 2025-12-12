# Démarrage Rapide - BF4 Invest

## Windows

Si vous obtenez une erreur de politique d'exécution PowerShell:

**Option 1 (Recommandée)**: Utilisez le fichier `.bat`
```cmd
start-production.bat
```

**Option 2**: Exécutez PowerShell avec bypass
```powershell
powershell -ExecutionPolicy Bypass -File .\start-production.ps1
```

**Option 3**: Modifiez temporairement la politique (une seule fois)
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
.\start-production.ps1
```

**Option 4**: Lancez directement Docker Compose
```cmd
docker-compose up -d --build
```

## Linux / Mac

```bash
chmod +x start-production.sh
./start-production.sh
```

Ou directement:
```bash
docker-compose up -d --build
```

## Vérification

Une fois démarré, vérifiez que tout fonctionne:

```bash
docker-compose ps
```

L'application sera accessible sur: **http://localhost**

## Identifiants par défaut

- **Email**: admin@bf4invest.ma
- **Mot de passe**: admin123

⚠️ **Changez le mot de passe après la première connexion !**

