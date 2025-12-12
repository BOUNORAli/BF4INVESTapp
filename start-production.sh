#!/bin/bash

# Script de démarrage pour BF4 Invest - Production
# Usage: ./start-production.sh

set -e

echo "=========================================="
echo "  BF4 Invest - Démarrage Production"
echo "=========================================="
echo ""

# Vérifier si Docker est installé
if ! command -v docker &> /dev/null; then
    echo "❌ Docker n'est pas installé. Veuillez l'installer d'abord."
    exit 1
fi

# Vérifier si Docker Compose est installé
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "❌ Docker Compose n'est pas installé. Veuillez l'installer d'abord."
    exit 1
fi

# Vérifier si le fichier .env existe
if [ ! -f .env ]; then
    echo "📝 Création du fichier .env depuis .env.example..."
    if [ -f .env.example ]; then
        cp .env.example .env
        echo "✅ Fichier .env créé."
        echo ""
        echo "⚠️  IMPORTANT: Veuillez modifier le fichier .env avec vos paramètres de production !"
        echo "   - Générer un JWT_SECRET fort: openssl rand -base64 32"
        echo "   - Configurer les paramètres MongoDB si nécessaire"
        echo ""
        read -p "Appuyez sur Entrée pour continuer après avoir modifié .env..."
    else
        echo "❌ Fichier .env.example non trouvé."
        exit 1
    fi
fi

# Vérifier le JWT_SECRET
if grep -q "your-production-secret-key" .env || grep -q "change-in-production" .env; then
    echo "⚠️  ATTENTION: Le JWT_SECRET par défaut est toujours utilisé !"
    echo "   Veuillez générer un secret fort pour la production."
    echo "   Commande: openssl rand -base64 32"
    echo ""
    read -p "Continuer quand même ? (oui/non): " -r
    if [[ ! $REPLY =~ ^[Oo]ui$ ]]; then
        echo "Arrêt du démarrage."
        exit 1
    fi
fi

echo "🔨 Construction et démarrage des conteneurs..."
echo ""

# Utiliser docker compose ou docker-compose selon ce qui est disponible
if docker compose version &> /dev/null; then
    COMPOSE_CMD="docker compose"
else
    COMPOSE_CMD="docker-compose"
fi

$COMPOSE_CMD up -d --build

echo ""
echo "⏳ Attente du démarrage des services..."
sleep 10

# Vérifier que les services sont démarrés
echo ""
echo "📊 État des services:"
$COMPOSE_CMD ps

echo ""
echo "=========================================="
echo "✅ Application démarrée avec succès !"
echo "=========================================="
echo ""
echo "🌐 Frontend: http://localhost"
echo "🔧 Backend API: http://localhost/api"
echo ""
echo "👤 Identifiants par défaut:"
echo "   Email: admin@bf4invest.ma"
echo "   Mot de passe: admin123"
echo ""
echo "⚠️  IMPORTANT: Changez le mot de passe admin après la première connexion !"
echo ""
echo "📝 Voir les logs: $COMPOSE_CMD logs -f"
echo "🛑 Arrêter: $COMPOSE_CMD down"
echo ""

