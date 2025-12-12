# Déploiement du Backend sur Railway

## Étapes Rapides

### 1. Préparer le Backend pour Railway

Le fichier `railway.json` est déjà configuré pour indiquer à Railway de déployer depuis le dossier `backend`.

### 2. Créer un Projet Railway

1. Allez sur https://railway.app
2. Connectez-vous avec GitHub
3. Cliquez sur "New Project"
4. Sélectionnez "Deploy from GitHub repo"
5. Choisissez votre repository `BF4INVESTapp`
6. **Important** : Dans les paramètres du service, configurez :
   - **Root Directory** : `backend`
   - Ou Railway utilisera automatiquement le fichier `railway.json`

### 3. Configuration dans Railway

**IMPORTANT** : Configurez le Root Directory dans Railway :

1. Dans votre service Railway, allez dans **Settings**
2. Trouvez **Root Directory**
3. Entrez : `backend`
4. Sauvegardez

Cela indiquera à Railway de déployer uniquement le dossier backend.

#### Variables d'Environnement Requises

Dans Railway, allez dans votre service > Variables et ajoutez :

**⚠️ IMPORTANT : Remplacez les valeurs par les vôtres !**

1. **MONGODB_URI** : 
   - Créez un compte sur MongoDB Atlas : https://www.mongodb.com/cloud/atlas
   - Créez un cluster gratuit
   - Obtenez la connection string (remplacez `<password>` par votre mot de passe)
   - Exemple : `mongodb+srv://admin:VotreMotDePasse@cluster0.xxxxx.mongodb.net/bf4invest?retryWrites=true&w=majority`

2. **JWT_SECRET** :
   - Générez un secret fort (voir ci-dessous)
   - Ne partagez JAMAIS ce secret

3. **SERVER_PORT** :
   - Gardez : `8080`

4. **CORS_ALLOWED_ORIGINS** :
   - Pour tester : laissez vide ``
   - Après déploiement Vercel : `https://votre-app.vercel.app`

**Exemple de configuration** :
```
MONGODB_URI=mongodb+srv://admin:MonMotDePasse123@cluster0.abc123.mongodb.net/bf4invest?retryWrites=true&w=majority
JWT_SECRET=Xk9pL2mN4qR6sT8vW0xY2zA4bC6dE8fG0hI2jK4lM6nO8pQ0rS2tU4vW6xY8zA
SERVER_PORT=8080
CORS_ALLOWED_ORIGINS=
```

#### Générer un JWT_SECRET fort

**Windows PowerShell** :
```powershell
[Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes([System.Guid]::NewGuid().ToString() + [System.Guid]::NewGuid().ToString()))
```

**Linux/Mac** :
```bash
openssl rand -base64 32
```

**En ligne** (alternative) : https://www.random.org/strings/
- Générez une chaîne de 32+ caractères aléatoires

⚠️ Copiez le résultat et utilisez-le comme valeur de `JWT_SECRET`

### 4. Déploiement

Railway déploiera automatiquement :
- Détecte le `pom.xml` dans le dossier `backend` et utilise Maven
- Build le projet avec `mvn clean package -DskipTests`
- Lance le JAR avec `java -jar target/bf4-invest-backend-1.0.0.jar`

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

