# Correction CORS et URL API

## Problème 1 : URL API incorrecte

L'application utilise encore `https://votre-backend.railway.app` au lieu de `https://bf4investapp-production.up.railway.app`

### Solution : Mettre à jour la variable d'environnement Vercel

1. Allez sur https://vercel.com
2. Sélectionnez votre projet `BF4INVESTapp`
3. Allez dans **Settings** → **Environment Variables**
4. Cherchez `NEXT_PUBLIC_API_URL` :
   - Si elle existe : Modifiez-la
   - Si elle n'existe pas : Créez-la
5. Définissez la valeur :
   ```
   https://bf4investapp-production.up.railway.app/api
   ```
6. Sélectionnez **Production**, **Preview**, et **Development**
7. Cliquez sur **Save**
8. **Redéployez** le projet : Allez dans **Deployments** → Cliquez sur le menu (3 points) → **Redeploy**

---

## Problème 2 : CORS retourne `https://railway.com` au lieu de votre URL Vercel

### Solution : Corriger CORS_ALLOWED_ORIGINS dans Railway

1. Allez sur https://railway.app
2. Sélectionnez votre service **BF4INVESTapp**
3. Allez dans l'onglet **Variables**
4. Cherchez `CORS_ALLOWED_ORIGINS`
5. **Supprimez** toute valeur existante (comme `https://railway.com`)
6. Entrez exactement votre URL Vercel :
   ```
   https://bf4invest-app.vercel.app
   ```
   ⚠️ **Important** : Pas de slash à la fin, pas d'espace
7. Cliquez sur **Save** ou laissez Railway sauvegarder automatiquement
8. Railway va **redéployer automatiquement** (attendez 1-2 minutes)

---

## Vérification

Après avoir fait les deux corrections ci-dessus :

1. **Attendez que Railway redéploie** (vérifiez les logs)
2. **Attendez que Vercel redéploie** (si vous avez redéployé)
3. **Rafraîchissez la page de login** dans votre navigateur
4. **Testez la connexion** :
   - Email: `admin@bf4invest.ma`
   - Mot de passe: `admin123`

---

## Si ça ne fonctionne toujours pas

### Vérifier les logs Railway

1. Dans Railway, allez dans l'onglet **Logs**
2. Regardez si le backend démarre correctement
3. Vérifiez qu'il n'y a pas d'erreurs de connexion MongoDB

### Vérifier la console du navigateur

1. Ouvrez les **Outils de développement** (F12)
2. Allez dans l'onglet **Console**
3. Regardez quelle URL est utilisée :
   - Devrait être : `https://bf4investapp-production.up.railway.app/api/auth/login`
   - Si c'est encore `https://votre-backend.railway.app`, le problème vient de Vercel

### Vérifier la configuration CORS dans Railway

Dans les logs Railway, cherchez :
```
cors.allowed.origins
```
La valeur devrait être : `https://bf4invest-app.vercel.app`

---

## Résumé des URLs

- **Frontend Vercel** : `https://bf4invest-app.vercel.app`
- **Backend Railway** : `https://bf4investapp-production.up.railway.app`
- **API Endpoint** : `https://bf4investapp-production.up.railway.app/api`





