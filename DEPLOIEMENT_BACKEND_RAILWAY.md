# Déploiement du Backend sur Railway

## Étapes Rapides

### 1. Préparer le Backend pour Railway

Railway peut déployer directement depuis GitHub. Aucune modification n'est nécessaire si votre structure est correcte.

### 2. Créer un Projet Railway

1. Allez sur https://railway.app
2. Connectez-vous avec GitHub
3. Cliquez sur "New Project"
4. Sélectionnez "Deploy from GitHub repo"
5. Choisissez votre repository
6. Railway détectera automatiquement le dossier `backend`

### 3. Configuration

#### Variables d'Environnement Requises

Dans Railway, allez dans votre service > Variables :

```
MONGODB_URI=mongodb+srv://user:password@cluster.mongodb.net/bf4invest
JWT_SECRET=votre-secret-jwt-fort-genere-avec-openssl-rand-base64-32
SERVER_PORT=8080
CORS_ALLOWED_ORIGINS=https://votre-frontend.vercel.app
```

#### Générer un JWT_SECRET fort

```bash
openssl rand -base64 32
```

### 4. Déploiement

Railway déploiera automatiquement :
- Détecte le `pom.xml` et utilise Maven
- Build le projet avec `mvn clean package`
- Lance le JAR avec `java -jar`

### 5. Obtenir l'URL

Railway vous donnera une URL publique comme :
```
https://votre-app.railway.app
```

L'API sera accessible à :
```
https://votre-app.railway.app/api
```

### 6. MongoDB Atlas (Si vous n'utilisez pas Railway MongoDB)

1. Créez un compte sur https://www.mongodb.com/cloud/atlas
2. Créez un cluster gratuit (M0)
3. Créez un utilisateur de base de données
4. Whitelist l'IP 0.0.0.0/0 (pour permettre Railway)
5. Obtenez la connection string
6. Utilisez-la dans `MONGODB_URI`

### 7. Test

Testez que votre backend répond :
```bash
curl https://votre-app.railway.app/api/auth/login
```

---

**Note** : Railway offre 5$ de crédit gratuit par mois, ce qui est généralement suffisant pour une petite application.

