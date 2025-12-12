# Guide de Déploiement - BF4 Invest

## Déploiement avec Docker Compose

### Prérequis
- Docker
- Docker Compose

### Étapes

1. **Cloner le projet**
   ```bash
   git clone <repository-url>
   cd BF4INVESTapp
   ```

2. **Configurer les variables d'environnement**
   
   Créer un fichier `.env` à la racine:
   ```env
   MONGODB_URI=mongodb://mongodb:27017/bf4invest
   JWT_SECRET=your-production-secret-key-minimum-256-bits
   SMTP_HOST=smtp.gmail.com
   SMTP_PORT=587
   SMTP_USERNAME=your-email@gmail.com
   SMTP_PASSWORD=your-app-password
   ```

3. **Construire et démarrer**
   ```bash
   docker-compose up -d --build
   ```

4. **Vérifier les logs**
   ```bash
   docker-compose logs -f backend
   ```

## Déploiement Production

### Backend

1. **Construire le JAR**
   ```bash
   cd backend
   mvn clean package -DskipTests
   ```

2. **Exécuter**
   ```bash
   java -jar target/bf4-invest-backend-1.0.0.jar --spring.profiles.active=prod
   ```

### Frontend

1. **Construire pour production**
   ```bash
   cd frontend
   npm install
   npm run build
   ```

2. **Servir les fichiers**
   
   Les fichiers sont dans `frontend/dist/`. Servir avec nginx, Apache, ou intégrer dans le backend Spring Boot.

### MongoDB Production

- Utiliser MongoDB Atlas ou une instance MongoDB gérée
- Configurer les backups automatiques
- Activer l'authentification

## Configuration Production

### Sécurité

1. **Changer le JWT_SECRET**
   - Utiliser un secret d'au moins 256 bits
   - Générer avec: `openssl rand -base64 32`

2. **Configurer HTTPS**
   - Utiliser un reverse proxy (nginx) avec certificat SSL
   - Configurer Spring Boot pour accepter les proxies

3. **Firewall**
   - Ouvrir uniquement les ports nécessaires
   - MongoDB: seulement depuis le backend
   - Backend: seulement depuis le frontend/nginx

### Monitoring

- Activer les logs
- Configurer un système de monitoring (Prometheus, Grafana)
- Configurer des alertes pour les erreurs

## Maintenance

### Backups

```bash
# Backup MongoDB
docker exec bf4invest-mongodb mongodump --out /data/backup

# Restore
docker exec -i bf4invest-mongodb mongorestore /data/backup
```

### Mises à jour

1. Arrêter les services
2. Backup de la base de données
3. Mettre à jour le code
4. Rebuild et redémarrer

```bash
docker-compose down
docker-compose up -d --build
```




