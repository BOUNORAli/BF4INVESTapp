# Configuration CORS pour le Rapport PDF Dashboard

## Problème

L'erreur CORS se produit car l'origine frontend (`https://bf4invest-app.vercel.app`) n'est pas autorisée dans la configuration CORS du backend.

## Solution

### 1. Variable d'environnement Backend (Railway)

Ajoutez ou modifiez la variable d'environnement `CORS_ALLOWED_ORIGINS` dans votre configuration Railway :

```
CORS_ALLOWED_ORIGINS=https://bf4invest-app.vercel.app,https://bf4investapp-production.up.railway.app
```

**Note** : Séparez les origines multiples par des virgules, sans espaces.

### 2. Vérification

Après avoir configuré la variable d'environnement, redéployez le backend. L'erreur CORS devrait être résolue.

### 3. Erreur 502 Bad Gateway

Si vous obtenez toujours une erreur 502, cela peut indiquer :
- Un crash du backend lors de la génération du PDF
- Un timeout (la génération peut prendre du temps)
- Un problème de mémoire

Les logs du backend (dans Railway) devraient indiquer l'erreur exacte. Les améliorations apportées au code incluent :
- Gestion d'erreur améliorée avec try-catch par section
- Logs détaillés pour le débogage
- Vérifications de nullité

## Test

Pour tester localement, ajoutez `http://localhost:4200` à la liste des origines autorisées.

