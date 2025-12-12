# Déploiement Rapide sur Vercel - BF4 Invest

## 🚀 Déploiement en 5 Minutes

### Étape 1 : Déployer le Backend (Railway - Gratuit)

1. **Créer un compte Railway** : https://railway.app
2. **Nouveau Projet** > Deploy from GitHub > Sélectionnez le dossier `backend`
3. **Ajouter MongoDB Atlas** :
   - Créez un compte sur https://www.mongodb.com/cloud/atlas
   - Créez un cluster gratuit (M0)
   - Obtenez la connection string
4. **Variables d'environnement Railway** :
   ```
   MONGODB_URI=mongodb+srv://user:password@cluster.mongodb.net/bf4invest
   JWT_SECRET=<générez avec: openssl rand -base64 32>
   CORS_ALLOWED_ORIGINS=https://votre-app.vercel.app
   ```
5. **Obtenir l'URL** : Railway vous donnera une URL comme `https://bf4-backend.railway.app`

### Étape 2 : Déployer le Frontend (Vercel)

1. **Créer un compte Vercel** : https://vercel.com
2. **Nouveau Projet** > Import GitHub repo
3. **Configuration** :
   - Root Directory: `frontend`
   - Framework: **Other**
   - Build Command: `npm run build:vercel`
   - Output Directory: `dist`
4. **Variables d'environnement Vercel** :
   ```
   NEXT_PUBLIC_API_URL=https://bf4-backend.railway.app/api
   ```
   (Remplacez par votre URL Railway)
5. **Deploy** ! Vercel déploiera automatiquement

### Étape 3 : Mettre à jour CORS

Dans Railway, mettez à jour :
```
CORS_ALLOWED_ORIGINS=https://votre-app.vercel.app
```
(Avec l'URL Vercel exacte que vous recevrez)

### ✅ C'est tout !

Votre application sera accessible sur l'URL Vercel (ex: `https://bf4-invest.vercel.app`)

---

## 📝 Variables d'Environnement Importantes

### Railway (Backend)
- `MONGODB_URI` : Connection string MongoDB Atlas
- `JWT_SECRET` : Secret JWT (généré avec `openssl rand -base64 32`)
- `CORS_ALLOWED_ORIGINS` : URL Vercel du frontend

### Vercel (Frontend)
- `NEXT_PUBLIC_API_URL` : URL complète du backend Railway

---

## 🔧 Résolution de Problèmes

**Erreur CORS ?**
→ Vérifiez que `CORS_ALLOWED_ORIGINS` contient l'URL Vercel exacte

**404 sur les routes ?**
→ Le `vercel.json` est déjà configuré pour rediriger vers `index.html`

**L'API ne répond pas ?**
→ Vérifiez que l'URL dans `NEXT_PUBLIC_API_URL` est correcte et accessible

---

**Bon déploiement ! 🎉**

