# Fix Final Railway - Problème JAR introuvable

## Le problème
Railway essaie d'exécuter `java -jar target/bf4-invest-backend-1.0.0.jar` mais ne trouve pas le fichier.

## Cause probable
Railway détecte le Dockerfile mais un "Custom Start Command" dans l'interface surcharge l'ENTRYPOINT du Dockerfile.

## Solution ULTIME

### Option 1 : Utiliser uniquement le Dockerfile (RECOMMANDÉ)

1. Dans Railway, allez dans **Settings** de votre service
2. Section **Deploy**
3. Trouvez **Custom Start Command**
4. **SUPPRIMEZ COMPLÈTEMENT** ce champ (laissez-le vide)
5. Sauvegardez

Le Dockerfile gère déjà tout avec :
- Build via Maven
- Copie du JAR vers `app.jar`
- Exécution avec `java -jar app.jar`

### Option 2 : Si vous devez garder Nixpacks

Si Railway ne peut pas utiliser Docker, alors :

1. **Assurez-vous que le Root Directory est `/backend`** dans Settings > Source
2. **Vérifiez les Build Logs** pour confirmer que `mvn clean package` réussit
3. **Vérifiez que le JAR est généré** en cherchant dans les logs :
   ```
   BUILD SUCCESS
   bf4-invest-backend-1.0.0.jar
   ```

4. Dans **Settings > Deploy > Custom Start Command**, utilisez :
   ```bash
   bash start.sh
   ```

## Vérification

Après avoir supprimé le Custom Start Command :
- Railway utilisera uniquement le Dockerfile
- Le conteneur devrait démarrer avec `java -jar app.jar`
- Plus d'erreur "Unable to access jarfile"

## Si le problème persiste

1. Regardez les **Build Logs** (pas Deploy Logs)
2. Cherchez :
   - `BUILD SUCCESS` ou `BUILD FAILURE`
   - Liste des fichiers dans `target/`
   - Messages d'erreur Maven

3. Partagez ces informations pour diagnostic


