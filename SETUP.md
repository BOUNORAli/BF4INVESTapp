# Guide de Configuration et Démarrage - BF4 Invest

## Prérequis

### Backend
- Java 17 ou supérieur
- Maven 3.9+
- MongoDB (local ou distant)

### Frontend
- Node.js 18+
- npm ou yarn

## Installation Rapide

### 1. Backend

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

Le backend démarre sur `http://localhost:8080/api`

### 2. Frontend

```bash
cd frontend
npm install
npm run dev
```

Le frontend démarre sur `http://localhost:4200` (ou le port configuré)

### 3. MongoDB

Si MongoDB n'est pas installé localement, vous pouvez utiliser Docker:

```bash
docker run -d -p 27017:27017 --name mongodb mongo:7.0
```

## Configuration

### Variables d'environnement Backend

Créer un fichier `.env` à la racine du backend avec:

```properties
MONGODB_URI=mongodb://localhost:27017/bf4invest
JWT_SECRET=votre-secret-jwt-minimum-256-bits
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=votre-email@gmail.com
SMTP_PASSWORD=votre-mot-de-passe-app
```

### Configuration Frontend

L'URL de l'API est définie dans `frontend/src/services/api.service.ts`:
```typescript
const API_BASE_URL = 'http://localhost:8080/api';
```

Ajustez selon votre environnement.

## Utilisateur par Défaut

Au premier démarrage, un utilisateur admin est créé automatiquement:
- **Email**: admin@bf4invest.ma
- **Mot de passe**: admin123

⚠️ **IMPORTANT**: Changez ce mot de passe immédiatement en production!

## Avec Docker Compose

Pour démarrer l'ensemble avec Docker Compose:

```bash
docker-compose up -d
```

Cela démarre:
- MongoDB sur le port 27017
- Backend Spring Boot sur le port 8080

Le frontend doit toujours être lancé manuellement pour le développement.

## Vérification

1. Backend: Accéder à `http://localhost:8080/api/swagger-ui.html` pour voir la documentation API
2. Frontend: Accéder à `http://localhost:4200` et se connecter avec les identifiants admin
3. MongoDB: Vérifier la connexion avec `mongosh` ou un client MongoDB

## Résolution de Problèmes

### Backend ne démarre pas
- Vérifier que MongoDB est bien démarré
- Vérifier le port 8080 n'est pas utilisé
- Consulter les logs dans `backend/logs/`

### Frontend ne se connecte pas au backend
- Vérifier que le backend est démarré
- Vérifier l'URL dans `api.service.ts`
- Vérifier CORS dans la configuration backend

### Erreurs MongoDB
- Vérifier que MongoDB est démarré: `mongosh`
- Vérifier l'URI dans `application.yml`

## Prochaines Étapes

1. Configurer SMTP pour les emails de rappel
2. Changer le JWT_SECRET en production
3. Configurer les rôles utilisateurs
4. Importer vos données Excel initiales




