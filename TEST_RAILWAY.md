# Tester le Backend Railway

## 1. Vérifier que le Build est Terminé

Dans Railway, attendez que le statut passe de :
- ❌ "Building" 
- ✅ "Online"

## 2. Vérifier les Logs

1. Cliquez sur l'onglet **"Logs"** dans Railway
2. Vérifiez qu'il n'y a pas d'erreurs
3. Vous devriez voir :
   ```
   Started Bf4InvestBackendApplication
   ```

## 3. Tester l'Endpoint

### Méthode 1 : Navigateur (GET)

Ouvrez dans votre navigateur :
```
https://bf4investapp-production.up.railway.app/api/auth/login
```

**Note** : Si vous obtenez une erreur 405 (Method Not Allowed), c'est **NORMAL** ! 
Cela signifie que le backend répond, mais l'endpoint `/login` accepte seulement POST, pas GET.

### Méthode 2 : Postman ou cURL (POST)

Testez avec une requête POST :

**cURL** (dans PowerShell ou terminal) :
```bash
curl -X POST https://bf4investapp-production.up.railway.app/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"admin@bf4invest.ma\",\"password\":\"admin123\"}"
```

**Postman** :
- Method: POST
- URL: `https://bf4investapp-production.up.railway.app/api/auth/login`
- Headers: `Content-Type: application/json`
- Body (raw JSON):
  ```json
  {
    "email": "admin@bf4invest.ma",
    "password": "admin123"
  }
  ```

### Réponse Attendue

Si tout fonctionne, vous devriez recevoir :
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": "...",
    "name": "Admin",
    "email": "admin@bf4invest.ma",
    "role": "ADMIN"
  }
}
```

## 4. Erreurs Possibles et Solutions

### Erreur 403 (Forbidden)
- ✅ **Si le build n'est pas terminé** : Attendez que le statut soit "Online"
- ✅ **Si le build est terminé** : Vérifiez les logs pour des erreurs

### Erreur 405 (Method Not Allowed)
- ✅ C'est normal si vous testez avec GET dans le navigateur
- ✅ Utilisez POST avec Postman ou cURL

### Erreur 500 (Internal Server Error)
- ❌ Vérifiez que `MONGODB_URI` est correctement configuré
- ❌ Vérifiez les logs Railway pour plus de détails

### Erreur de connexion MongoDB
- ❌ Vérifiez que votre MongoDB Atlas permet les connexions depuis n'importe quelle IP (0.0.0.0/0)
- ❌ Vérifiez que le mot de passe dans `MONGODB_URI` est correct

## 5. Une fois que ça Marche

Si vous obtenez un token JWT, c'est que tout fonctionne ! 🎉

Vous pouvez passer au déploiement du frontend sur Vercel.


