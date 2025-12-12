# Configuration Git et Push sur GitHub

## Problème : "Author identity unknown"

Si vous obtenez cette erreur, vous devez configurer Git avec votre nom et email.

## Solution Rapide

### Option 1 : Script automatique
```cmd
configure-git.bat
```
Entrez votre nom et email quand demandé.

### Option 2 : Configuration manuelle
```cmd
git config user.name "Votre Nom"
git config user.email "votre@email.com"
```

### Option 3 : Configuration globale (pour tous les projets)
```cmd
git config --global user.name "Votre Nom"
git config --global user.email "votre@email.com"
```

## Après la configuration

Une fois Git configuré, relancez :
```cmd
init-git-and-push.bat
```

Ou manuellement :
```cmd
git add .
git commit -m "feat: Preparation production complete"
git push -u origin main
```

---

**Note** : L'email peut être n'importe quel email valide. Il n'a pas besoin de correspondre à votre compte GitHub, mais c'est recommandé.

