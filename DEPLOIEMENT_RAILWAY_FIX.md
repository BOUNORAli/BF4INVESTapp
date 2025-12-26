# Fix : Erreur Railway "Railpack could not determine how to build"

## Problème

Railway essaie de déployer depuis la racine du projet au lieu du dossier `backend`, ce qui cause l'erreur "Railpack could not determine how to build".

## Solution

### Option 1 : Configurer le Root Directory dans Railway (Recommandé)

1. Dans Railway, allez dans votre projet
2. Cliquez sur votre service (BF4INVESTapp)
3. Allez dans **Settings**
4. Trouvez la section **Source**
5. Dans **Root Directory**, entrez : `backend`
6. Cliquez sur **Save**
7. Railway redéploiera automatiquement

### Option 2 : Déployer depuis le dossier backend

1. Dans Railway, créez un nouveau service
2. Sélectionnez "Deploy from GitHub repo"
3. Choisissez votre repository
4. Dans les options, spécifiez **Root Directory** : `backend`
5. Railway déploiera uniquement le backend

## Vérification

Après configuration, Railway devrait :
- Détecter le `pom.xml` dans `backend/`
- Utiliser Maven pour builder
- Démarrer avec `java -jar target/bf4-invest-backend-1.0.0.jar`

## Variables d'Environnement

Assurez-vous d'ajouter ces variables dans Railway :

```
MONGODB_URI=mongodb+srv://user:password@cluster.mongodb.net/bf4invest
JWT_SECRET=<généré avec: openssl rand -base64 32>
SERVER_PORT=8080
CORS_ALLOWED_ORIGINS=https://votre-frontend.vercel.app
```

---

**Note** : Le fichier `backend/railway.json` est déjà présent pour aider Railway à comprendre la configuration, mais la méthode la plus fiable est de configurer le Root Directory dans l'interface Railway.




