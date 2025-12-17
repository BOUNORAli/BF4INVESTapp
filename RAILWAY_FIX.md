# Fix Railway Deployment - Custom Start Command

## Problème
Railway essaie d'exécuter `java -jar target/bf4-invest-backend-1.0.0.jar` mais le Dockerfile copie le JAR vers `app.jar`.

## Solution

### Option 1 : Supprimer le Custom Start Command (RECOMMANDÉ)

1. Dans Railway, allez dans **Settings** de votre service
2. Trouvez la section **Deploy** 
3. Localisez **Custom Start Command**
4. **SUPPRIMEZ** complètement cette commande (laissez vide)
5. Sauvegardez

Le Dockerfile utilise déjà `ENTRYPOINT ["java", "-jar", "app.jar"]` qui est correct.

### Option 2 : Changer le Custom Start Command

Si vous ne pouvez pas supprimer le champ, changez-le pour :

```
java -jar app.jar
```

## Vérification

Après avoir fait cette modification :
1. Railway redémarrera automatiquement le service
2. Le conteneur devrait démarrer correctement
3. Vérifiez les logs pour confirmer que l'application démarre

## Pourquoi ça marche

- Le Dockerfile build le projet avec Maven
- Il copie le JAR généré vers `app.jar` dans le conteneur final
- L'ENTRYPOINT du Dockerfile exécute `java -jar app.jar`
- Si vous définissez un Custom Start Command, il surcharge l'ENTRYPOINT
- Donc il faut soit le supprimer, soit utiliser `app.jar` au lieu de `target/bf4-invest-backend-1.0.0.jar`


