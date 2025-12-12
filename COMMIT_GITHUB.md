# Commandes pour enregistrer sur GitHub

Exécutez ces commandes dans l'ordre :

## 0. Si le repository n'est pas encore initialisé (première fois)
```bash
git init
git remote add origin https://github.com/BOUNORAli/BF4INVESTapp.git
git branch -M main
```

## 1. Vérifier l'état actuel
```bash
git status
```

## 2. Ajouter tous les fichiers modifiés
```bash
git add .
```

## 3. Créer un commit
```bash
git commit -m "feat: Préparation production - Configuration Docker Compose, Vercel et documentation complète

- Configuration frontend avec variables d'environnement pour production
- Architecture Docker Compose avec nginx pour servir le frontend
- Configuration CORS configurable via variables d'environnement
- Scripts de démarrage pour Windows et Linux
- Documentation utilisateur complète (GUIDE_UTILISATEUR.md)
- Guides de déploiement Vercel et Railway
- Configuration pour déploiement sur Vercel
- Support des historiques de notifications et d'imports
- Correction des problèmes de compilation et d'encodage
- Fichier .env.example avec toutes les variables nécessaires"
```

## 4. Pousser sur GitHub
```bash
git push
```

## Script Automatique (Recommandé)

Utilisez le script batch qui fait tout automatiquement :
```cmd
init-git-and-push.bat
```

Ce script :
1. Initialise le repository Git (si nécessaire)
2. Ajoute le remote GitHub
3. Crée la branche main
4. Ajoute tous les fichiers
5. Fait le commit
6. Push sur GitHub

---

**Note**: Si c'est la première fois, le script initialisera tout automatiquement. Sinon, utilisez simplement `push-to-github.bat` pour les prochaines fois.

