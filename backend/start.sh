#!/bin/bash
# Script de démarrage pour Railway
# Trouve et exécute le JAR Spring Boot

# Aller dans le répertoire backend si nécessaire
cd "$(dirname "$0")" || exit 1

# Trouver le JAR exécutable (celui qui n'est pas -sources ou -original)
JAR_FILE=$(find target -name "*.jar" -not -name "*-sources.jar" -not -name "*-original.jar" | head -1)

# Vérifier que le JAR existe
if [ -z "$JAR_FILE" ]; then
    echo "Error: No JAR file found in target/ directory"
    echo "Contents of target/ directory:"
    ls -la target/ 2>/dev/null || echo "target/ directory does not exist"
    exit 1
fi

echo "Starting application with JAR: $JAR_FILE"
java -jar "$JAR_FILE"

