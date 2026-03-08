# Déploiement backend (Railway) – Éviter les 403 après login

Si après connexion toutes les requêtes API renvoient **403 Forbidden** (dashboard, BC, factures, etc.), le navigateur n’envoie pas les cookies d’authentification au backend (appel cross-origin : frontend Vercel → backend Railway).

## Variables d’environnement obligatoires sur Railway

À configurer dans **Railway** → ton service backend → **Variables** :

| Variable | Valeur | Rôle |
|----------|--------|------|
| **CORS_ALLOWED_ORIGINS** | `https://ton-app-manager.vercel.app` | Origine exacte du frontend (sans slash final). Ex. `https://bf4invest-app.vercel.app` |
| **COOKIE_SECURE** | `true` | Indispensable en HTTPS pour que les cookies soient envoyés en cross-origin (`SameSite=None`) |

Sans `COOKIE_SECURE=true`, les cookies sont émis avec `SameSite=Lax` et ne sont **pas** envoyés sur les requêtes cross-origin → le backend ne reçoit pas le JWT → 403.

Sans l’origine exacte dans **CORS_ALLOWED_ORIGINS**, CORS peut bloquer les requêtes avec credentials.

## Vérifications rapides

1. **Origine** : même schéma et même domaine que l’URL du manager (ex. `https://bf4invest-app.vercel.app`), **sans** slash final.
2. **Plusieurs origines** : séparer par des virgules, sans espaces :  
   `https://bf4invest-app.vercel.app,https://bf4invest-website.vercel.app`
3. Après modification des variables, **redéployer** le service sur Railway.

## Vérifier dans le navigateur

1. Onglet **Network** (F12).
2. Connexion sur l’app manager.
3. Regarder la réponse **login** : en-têtes **Set-Cookie** avec `bf4_token` et `bf4_refresh_token` (avec `Secure` et `SameSite=None` si tout est bon).
4. Cliquer sur une requête API (ex. `bandes-commandes` ou `dashboard/kpis`) : dans **Request Headers**, la ligne **Cookie** doit contenir `bf4_token=...`. Si **Cookie** est absente, le problème vient bien des cookies (CORS / COOKIE_SECURE / origine).

Une fois **CORS_ALLOWED_ORIGINS** et **COOKIE_SECURE=true** correctement définis et le backend redéployé, les 403 après login devraient disparaître.
