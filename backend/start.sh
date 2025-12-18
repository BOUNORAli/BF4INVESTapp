#!/bin/bash
set -e

echo "=== Checking target directory ==="
ls -la target/ || echo "target/ directory does not exist"

echo ""
echo "=== Looking for JAR files ==="
find target -name "*.jar" -type f 2>/dev/null || echo "No JAR files found"

echo ""
echo "=== Attempting to start application ==="

# Essayer le nom exact d'abord
if [ -f "target/bf4-invest-backend-1.0.0.jar" ]; then
    echo "Found: target/bf4-invest-backend-1.0.0.jar"
    exec java -jar target/bf4-invest-backend-1.0.0.jar
fi

# Sinon, trouver le premier JAR qui n'est pas sources ou original
JAR_FILE=$(find target -name "*.jar" -not -name "*-sources.jar" -not -name "*-original.jar" -type f | head -1)

if [ -n "$JAR_FILE" ]; then
    echo "Found: $JAR_FILE"
    exec java -jar "$JAR_FILE"
else
    echo "ERROR: No executable JAR file found!"
    echo "Contents of target/ directory:"
    ls -la target/ 2>/dev/null || echo "target/ directory does not exist"
    exit 1
fi



