# Guide de Déploiement sur Vercel - BF4 Invest

## Vue d'Ensemble

Ce guide vous explique comment déployer l'application BF4 Invest sur Vercel. Notez que **Vercel ne peut déployer que le frontend Angular**. Le backend Spring Boot et MongoDB devront être déployés ailleurs (Railway, Render, etc.).

## Architecture Recommandée

- **Frontend (Angular)**: Vercel (gratuit)
- **Backend (Spring Boot)**: Railway ou Render (gratuit disponible)
- **MongoDB**: MongoDB Atlas (gratuit jusqu'à 512MB)

## Déploiement Rapide

Consultez **[DEPLOIEMENT_VERCEL_QUICKSTART.md](DEPLOIEMENT_VERCEL_QUICKSTART.md)** pour un guide rapide en 5 minutes.

## Déploiement Détaillé

### Étape 1 : Préparer le Backend

Le backend doit être déployé avant le frontend. Consultez **[DEPLOIEMENT_BACKEND_RAILWAY.md](DEPLOIEMENT_BACKEND_RAILWAY.md)**.

**Important**: Notez l'URL de votre backend déployé (ex: `https://bf4-backend.railway.app`)

### Étape 2 : Déployer le Frontend sur Vercel

#### Méthode 1 : Via l'Interface Vercel (Recommandé)

1. **Aller sur Vercel** : https://vercel.com
   - Connectez-vous avec GitHub

2. **Nouveau projet**
   - Cliquez sur "Add New Project"
   - Importez votre repository GitHub
   - Configurez le projet :
     - **Root Directory**: Sélectionnez `frontend`
     - **Framework Preset**: Sélectionnez **"Other"** (pas Angular automatique)
     - **Build Command**: `npm run build:vercel`
     - **Output Directory**: `dist`
     - **Install Command**: `npm install`

3. **Variables d'environnement**
   - Cliquez sur "Environment Variables"
   - Ajoutez :
     ```
     Variable Name: NEXT_PUBLIC_API_URL
     Value: https://votre-backend.railway.app/api
     ```
     (Remplacez par l'URL de votre backend déployé)
   - Sélectionnez "Production", "Preview", et "Development"

4. **Déployer**
   - Cliquez sur "Deploy"
   - Vercel déploiera automatiquement
   - Vous recevrez une URL comme `https://bf4-invest.vercel.app`

#### Méthode 2 : Via Vercel CLI

1. **Installer Vercel CLI** :
   ```bash
   npm i -g vercel
   ```

2. **Se connecter** :
   ```bash
   vercel login
   ```

3. **Dans le dossier frontend** :
   ```bash
   cd frontend
   vercel
   ```

4. **Configurer les variables d'environnement** :
   ```bash
   vercel env add NEXT_PUBLIC_API_URL production
   # Entrez: https://votre-backend.railway.app/api
   ```

5. **Déployer en production** :
   ```bash
   vercel --prod
   ```

### Étape 3 : Configuration CORS

Dans les variables d'environnement de votre backend (Railway/Render), assurez-vous d'ajouter :

```
CORS_ALLOWED_ORIGINS=https://votre-app.vercel.app
```

**Important**: Utilisez l'URL exacte fournie par Vercel (avec `https://`)

### Étape 4 : Vérification

1. **Accéder à votre application** :
   - Ouvrez l'URL Vercel dans votre navigateur
   - Ouvrez la console développeur (F12)

2. **Vérifier les erreurs** :
   - Regardez la console pour voir l'URL API utilisée
   - Vérifiez qu'il n'y a pas d'erreurs CORS

3. **Se connecter** :
   - Utilisez les identifiants par défaut : `admin@bf4invest.ma` / `admin123`
   - Changez immédiatement le mot de passe

## Mise à Jour Continue

Vercel déploie automatiquement à chaque push sur la branche principale (`main` ou `master`).

Pour forcer un nouveau déploiement :
- Via l'interface : Projet > Deployments > Re-deploy
- Via CLI : `vercel --prod`

## Résolution de Problèmes

### Erreur CORS

**Symptômes**: Erreurs dans la console du navigateur concernant CORS

**Solution**:
1. Vérifiez que `CORS_ALLOWED_ORIGINS` dans le backend contient l'URL Vercel exacte
2. L'URL doit commencer par `https://` et correspondre exactement
3. Redéployez le backend après modification

### Erreur 404 sur les routes Angular

**Symptômes**: Les routes comme `/dashboard` retournent 404

**Solution**:
- Le fichier `vercel.json` est déjà configuré pour rediriger toutes les routes vers `index.html`
- Si ça ne fonctionne pas, vérifiez que le fichier existe dans le dossier `frontend`

### L'API ne répond pas

**Symptômes**: Erreurs "Failed to fetch" ou timeout

**Solution**:
1. Vérifiez que le backend est bien déployé et accessible
2. Testez l'URL directement dans le navigateur :
   ```
   https://votre-backend.railway.app/api/auth/login
   ```
3. Vérifiez la variable `NEXT_PUBLIC_API_URL` dans Vercel
4. Ouvrez la console du navigateur et vérifiez l'URL API utilisée

### Build échoue sur Vercel

**Symptômes**: Le déploiement échoue avec des erreurs de build

**Solution**:
1. Vérifiez les logs de build dans Vercel
2. Assurez-vous que `package.json` contient tous les scripts nécessaires
3. Vérifiez que Node.js 18+ est utilisé (Vercel le détecte automatiquement)

## Structure des Fichiers

Les fichiers suivants sont nécessaires pour Vercel :

- `frontend/vercel.json` : Configuration Vercel
- `frontend/package.json` : Scripts de build
- `frontend/scripts/inject-env.js` : Script d'injection des variables d'environnement
- `frontend/src/config/environment.ts` : Configuration de l'URL API

## Coûts Estimés

- **Vercel** : Gratuit pour projets personnels (illimité)
- **Railway** : Gratuit jusqu'à 5$ de crédit/mois
- **Render** : Gratuit (avec limitations de performance)
- **MongoDB Atlas** : Gratuit jusqu'à 512MB

## Notes Importantes

1. **Sécurité** : Changez le `JWT_SECRET` en production
2. **Mots de passe** : Changez le mot de passe admin immédiatement
3. **HTTPS** : Vercel fournit HTTPS automatiquement
4. **Sauvegardes** : Configurez des sauvegardes automatiques sur MongoDB Atlas

---

**Bon déploiement !**
