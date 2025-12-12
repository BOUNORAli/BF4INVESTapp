# Guide de Déploiement Production - BF4 Invest

## Vue d'Ensemble

Ce guide vous accompagne dans le déploiement de l'application BF4 Invest en production.

## Architecture

L'application est composée de trois services Docker:

- **MongoDB**: Base de données
- **Backend**: API Spring Boot (port 8080 interne)
- **Frontend**: Nginx servant l'application Angular (port 80)

## Prérequis

- Docker Engine 20.10+
- Docker Compose 2.0+
- Au moins 2GB de RAM disponible
- Port 80 disponible (ou modifier FRONTEND_PORT dans .env)

## Installation Rapide

### 1. Configuration

```bash
# Copier le fichier d'exemple
cp .env.example .env

# Éditer .env avec vos paramètres
# IMPORTANT: Générer un JWT_SECRET fort !
```

Générer un JWT_SECRET:
```bash
openssl rand -base64 32
```

### 2. Démarrage

**Windows:**
```cmd
# Recommandé: Utiliser le fichier .bat
start-production.bat

# Ou si vous préférez PowerShell (peut nécessiter de contourner la politique)
powershell -ExecutionPolicy Bypass -File .\start-production.ps1
```

**Linux/Mac:**
```bash
chmod +x start-production.sh
./start-production.sh
```

**Ou manuellement (tous systèmes):**
```bash
docker-compose up -d --build
```

> 💡 Si vous rencontrez des erreurs avec les scripts, consultez [README_DEMARRAGE_RAPIDE.md](README_DEMARRAGE_RAPIDE.md)

### 3. Vérification

Vérifier que tous les services sont démarrés:
```bash
docker-compose ps
```

Voir les logs:
```bash
docker-compose logs -f
```

## Configuration Production

### Variables d'Environnement Importantes

#### JWT_SECRET (OBLIGATOIRE)
Générez un secret fort et unique:
```bash
openssl rand -base64 32
```

#### CORS_ALLOWED_ORIGINS
Pour la production, spécifiez votre domaine:
```env
CORS_ALLOWED_ORIGINS=https://votre-domaine.com,https://www.votre-domaine.com
```

Ou laisser vide pour autoriser toutes les origines (non recommandé).

#### MongoDB
Si vous utilisez MongoDB Atlas ou un MongoDB externe:
```env
MONGODB_URI=mongodb+srv://username:password@cluster.mongodb.net/bf4invest
```

### Sécurité

1. **Changer le mot de passe admin**: Immédiatement après la première connexion
2. **HTTPS**: Configurez un reverse proxy (nginx/traefik) avec certificat SSL
3. **Firewall**: Ouvrez uniquement les ports nécessaires (80/443)
4. **Sauvegardes**: Configurez des sauvegardes automatiques de MongoDB

## Déploiement avec HTTPS

### Option 1: Nginx Reverse Proxy (Recommandé)

1. Installer nginx sur le serveur
2. Configurer un reverse proxy pointant vers `localhost:80`
3. Utiliser Let's Encrypt pour le certificat SSL

Exemple configuration nginx:
```nginx
server {
    listen 443 ssl http2;
    server_name votre-domaine.com;
    
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
    
    location / {
        proxy_pass http://localhost:80;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### Option 2: Traefik

Utilisez Traefik comme reverse proxy avec auto-certificats Let's Encrypt.

## Sauvegardes

### Sauvegarder MongoDB

```bash
docker-compose exec mongodb mongodump --out /data/backup
docker-compose cp mongodb:/data/backup ./backup-$(date +%Y%m%d)
```

### Restaurer MongoDB

```bash
docker-compose cp ./backup-YYYYMMDD mongodb:/data/backup
docker-compose exec mongodb mongorestore /data/backup
```

## Maintenance

### Mise à Jour

```bash
git pull
docker-compose down
docker-compose up -d --build
```

### Logs

Voir les logs de tous les services:
```bash
docker-compose logs -f
```

Logs d'un service spécifique:
```bash
docker-compose logs -f backend
docker-compose logs -f frontend
docker-compose logs -f mongodb
```

### Arrêter l'Application

```bash
docker-compose down
```

Pour supprimer aussi les volumes (⚠️ supprime les données):
```bash
docker-compose down -v
```

## Monitoring

### Santé des Services

Vérifier l'état:
```bash
docker-compose ps
```

Health check frontend:
```bash
curl http://localhost/health
```

### Performance

Surveillez l'utilisation des ressources:
```bash
docker stats
```

## Dépannage

### Le backend ne démarre pas

1. Vérifier les logs: `docker-compose logs backend`
2. Vérifier la connexion MongoDB
3. Vérifier que le port 8080 n'est pas utilisé

### Le frontend ne charge pas

1. Vérifier les logs: `docker-compose logs frontend`
2. Vérifier que le port 80 est accessible
3. Vérifier que le backend répond: `curl http://localhost/api/auth/login`

### Erreurs de connexion MongoDB

1. Vérifier MONGODB_URI dans .env
2. Vérifier que MongoDB est démarré: `docker-compose ps mongodb`
3. Vérifier les logs MongoDB: `docker-compose logs mongodb`

### Problèmes de CORS

1. Vérifier CORS_ALLOWED_ORIGINS dans .env
2. Vérifier que les origines sont correctement formatées (séparées par des virgules)

## Support

Pour toute question:
1. Consultez `GUIDE_UTILISATEUR.md` pour l'utilisation
2. Vérifiez les logs avec `docker-compose logs -f`
3. Consultez la documentation dans le code

---

**Bon déploiement !**

