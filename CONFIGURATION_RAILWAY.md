# Configuration des Variables d'Environnement Railway

## Variables à configurer dans Railway

### 1. MONGODB_URI

**Vous devez remplacer** `mongodb+srv://user:password@cluster.mongodb.net/bf4invest` par votre vraie connection string.

#### Option A : MongoDB Atlas (Recommandé - Gratuit)

1. Créez un compte sur https://www.mongodb.com/cloud/atlas
2. Créez un cluster gratuit (M0)
3. Créez un utilisateur de base de données :
   - Database Access > Add New Database User
   - Créez un utilisateur avec username et password
4. Whitelist l'IP :
   - Network Access > Add IP Address
   - Cliquez sur "Allow Access from Anywhere" (0.0.0.0/0)
5. Obtenez la connection string :
   - Clusters > Connect > Connect your application
   - Copiez la connection string
   - Remplacez `<password>` par votre mot de passe utilisateur
   - Remplacez `<database>` par `bf4invest` (ou gardez le nom par défaut)

**Exemple** :
```
MONGODB_URI=mongodb+srv://admin:MonMotDePasse123@cluster0.xxxxx.mongodb.net/bf4invest?retryWrites=true&w=majority
```

#### Option B : Railway MongoDB (Si vous utilisez le plugin MongoDB)

Si vous ajoutez le plugin MongoDB dans Railway, utilisez la variable fournie automatiquement :
```
MONGODB_URI=${{MongoDB.MONGODB_URL}}
```

### 2. JWT_SECRET

**Vous devez générer un secret fort** (ne gardez PAS `<généré avec: openssl rand -base64 32>`)

#### Méthode 1 : Windows PowerShell
```powershell
[Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes([System.Guid]::NewGuid().ToString() + [System.Guid]::NewGuid().ToString()))
```

#### Méthode 2 : En ligne (alternative)
Allez sur https://www.random.org/strings/ et générez une chaîne de 32 caractères aléatoires.

#### Méthode 3 : Si vous avez OpenSSL installé
```bash
openssl rand -base64 32
```

**Exemple** (utilisez votre propre secret) :
```
JWT_SECRET=a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6
```

⚠️ **IMPORTANT** : Gardez ce secret en sécurité ! Ne le partagez jamais.

### 3. SERVER_PORT

**Vous pouvez garder la valeur par défaut** :
```
SERVER_PORT=8080
```

Railway utilise généralement le port spécifié dans cette variable ou celui détecté automatiquement.

### 4. CORS_ALLOWED_ORIGINS

**Pour l'instant, vous pouvez :**

#### Option A : Laisser vide (temporairement)
```
CORS_ALLOWED_ORIGINS=
```
Cela autorisera toutes les origines (OK pour tester, moins sécurisé).

#### Option B : Mettre votre URL Vercel (une fois déployé)
Après avoir déployé le frontend sur Vercel, vous recevrez une URL comme :
`https://bf4-invest.vercel.app`

Alors mettez :
```
CORS_ALLOWED_ORIGINS=https://bf4-invest.vercel.app
```

#### Option C : Plusieurs origines (séparées par des virgules)
```
CORS_ALLOWED_ORIGINS=https://bf4-invest.vercel.app,https://www.votre-domaine.com
```

---

## Exemple de Configuration Complète

Voici un exemple avec des valeurs réelles (remplacez par les vôtres) :

```
MONGODB_URI=mongodb+srv://bf4admin:MyPassword123@cluster0.abc123.mongodb.net/bf4invest?retryWrites=true&w=majority
JWT_SECRET=Xk9pL2mN4qR6sT8vW0xY2zA4bC6dE8fG0hI2jK4lM6nO8pQ0rS2tU4vW6xY8zA
SERVER_PORT=8080
CORS_ALLOWED_ORIGINS=
```

---

## Comment Ajouter les Variables dans Railway

1. Dans Railway, allez dans votre service **BF4INVESTapp**
2. Cliquez sur l'onglet **"Variables"**
3. Cliquez sur **"+ New Variable"** pour chaque variable
4. Entrez le **Name** (ex: `MONGODB_URI`) et la **Value** (votre valeur)
5. Répétez pour chaque variable
6. Railway redéploiera automatiquement après l'ajout des variables

---

## Vérification

Après avoir configuré les variables et que Railway a redéployé :

1. Allez dans l'onglet **"Logs"** pour voir les logs du backend
2. Vérifiez qu'il n'y a pas d'erreurs de connexion MongoDB
3. Vérifiez que le backend démarre correctement

---

**Résumé** : Remplacez TOUTES les valeurs placeholder par vos vraies valeurs, sauf `SERVER_PORT=8080` que vous pouvez garder tel quel.





