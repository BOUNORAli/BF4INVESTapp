# Guide de Lancement des Services BF4 Invest

## 🚀 Méthode 1 : Script Automatique (Recommandé)

Exécutez simplement :

```powershell
cd C:\Users\PC\Documents\BF4INVESTapp
.\start-all.ps1
```

Ce script va :
1. ✅ Démarrer MongoDB (si pas déjà lancé)
2. ✅ Compiler et lancer le Backend Spring Boot
3. ✅ Lancer le Frontend Angular

---

## 🔧 Méthode 2 : Lancement Manuel

### Étape 1 : Démarrer MongoDB

Dans un terminal PowerShell :

```powershell
# Vérifier si MongoDB est déjà lancé
docker ps --filter "name=mongodb"

# Si pas lancé, démarrer MongoDB
docker start mongodb

# Si le conteneur n'existe pas, le créer
docker run -d -p 27017:27017 --name mongodb mongo:7.0
```

### Étape 2 : Lancer le Backend Spring Boot

Dans un **nouveau terminal** PowerShell :

```powershell
cd C:\Users\PC\Documents\BF4INVESTapp\backend
mvn spring-boot:run
```

**OU** si vous préférez utiliser le JAR compilé :

```powershell
cd C:\Users\PC\Documents\BF4INVESTapp\backend
java -jar target\bf4-invest-backend-1.0.0.jar
```

Le backend démarre généralement sur **http://localhost:8080**

### Étape 3 : Le Frontend est déjà lancé ! ✅

Vous devriez voir dans la console :
```
➜  Local:   http://localhost:4200/
```

---

## 📍 URLs des Services

Une fois tout démarré :

- **Frontend** : http://localhost:4200
- **Backend API** : http://localhost:8080/api
- **Swagger UI** : http://localhost:8080/api/swagger-ui.html
- **MongoDB** : localhost:27017

---

## 🔐 Identifiants de Connexion

- **Email** : `admin@bf4invest.ma`
- **Mot de passe** : `admin123`

---

## ⚠️ Vérifications

Pour vérifier que tout fonctionne :

1. **MongoDB** : `docker ps` → vous devriez voir `mongodb` dans la liste
2. **Backend** : Ouvrez http://localhost:8080/api/health (devrait répondre)
3. **Frontend** : Ouvrez http://localhost:4200 (devrait afficher la page de login)

---

## 🛑 Arrêter les Services

Pour arrêter les services :

```powershell
# Arrêter MongoDB
docker stop mongodb

# Arrêter le Backend : Appuyez sur Ctrl+C dans la fenêtre PowerShell du backend
# Arrêter le Frontend : Appuyez sur Ctrl+C dans la fenêtre PowerShell du frontend
```

---

## 🐛 En cas de Problème

### Le backend ne démarre pas ?
- Vérifiez que MongoDB est bien lancé : `docker ps`
- Vérifiez les logs dans la console du backend
- Vérifiez que le port 8080 n'est pas déjà utilisé

### Le frontend ne se connecte pas au backend ?
- Vérifiez que le backend est bien lancé sur http://localhost:8080
- Vérifiez les erreurs dans la console du navigateur (F12)

### MongoDB ne démarre pas ?
- Vérifiez que Docker est bien démarré
- Essayez de supprimer et recréer le conteneur :
  ```powershell
  docker stop mongodb
  docker rm mongodb
  docker run -d -p 27017:27017 --name mongodb mongo:7.0
  ```



