# BF4 Invest - Application de Gestion des Commandes et Factures

Application complète de gestion des bandes de commandes, factures achat/vente, et tableaux de bord financiers.

## Architecture

- **Backend**: Spring Boot 3.2 + MongoDB
- **Frontend**: Angular 21 (standalone, signals)
- **Base de données**: MongoDB
- **Sécurité**: JWT + Spring Security
- **Déploiement**: Docker Compose

## Prérequis

- Java 17+
- Maven 3.9+
- Node.js 18+
- Docker & Docker Compose (optionnel)

## Installation et Démarrage

### Avec Docker Compose (Recommandé pour Production)

**Windows:**
```cmd
# Option 1: Utiliser le fichier .bat (recommandé)
start-production.bat

# Option 2: Si vous avez une erreur de politique PowerShell
powershell -ExecutionPolicy Bypass -File .\start-production.ps1

# Option 3: Directement avec Docker Compose
docker-compose up -d --build
```

**Linux/Mac:**
```bash
chmod +x start-production.sh
./start-production.sh

# Ou directement:
docker-compose up -d --build
```

**Avant de démarrer:**
1. Copier `.env.example` vers `.env`: `cp .env.example .env`
2. (Optionnel) Éditer `.env` avec vos paramètres de production

L'application sera disponible sur `http://localhost`

Le frontend et le backend sont servis ensemble via nginx.

> 💡 **Note**: Si vous rencontrez des problèmes avec les scripts, consultez [README_DEMARRAGE_RAPIDE.md](README_DEMARRAGE_RAPIDE.md)

### Backend Manuellement

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Le frontend sera disponible sur `http://localhost:4200` (ou le port configuré dans angular.json)

## Configuration

### Backend

Éditer `backend/src/main/resources/application.yml`:

- MongoDB URI
- JWT Secret (changer en production!)
- SMTP configuration pour les emails

### Frontend

L'URL de l'API est configurée dans `frontend/src/services/api.service.ts`:
```typescript
const API_BASE_URL = 'http://localhost:8080/api';
```

## Utilisateur par Défaut

- **Email**: admin@bf4invest.ma ou admin@bf4.com
- **Mot de passe**: admin123

⚠️ **Important**: Changez ce mot de passe immédiatement après la première connexion en production!

## Documentation

- **[Guide Utilisateur](GUIDE_UTILISATEUR.md)**: Guide complet pour utiliser l'application
- **[Déploiement Production](DEPLOIEMENT_PRODUCTION.md)**: Guide de déploiement en production

## API Endpoints Principaux

- `POST /api/auth/login` - Connexion
- `GET /api/clients` - Liste clients
- `GET /api/bandes-commandes` - Liste BC
- `GET /api/factures-achats` - Liste factures achat
- `GET /api/factures-ventes` - Liste factures vente
- `GET /api/dashboard/kpis` - KPIs dashboard
- `POST /api/import/excel` - Import Excel
- `GET /api/pdf/bandes-commandes/{id}` - PDF BC

Voir Swagger UI: `http://localhost:8080/api/swagger-ui.html`

## Fonctionnalités

- ✅ Gestion clients/fournisseurs/produits
- ✅ Création et suivi des bandes de commandes
- ✅ Factures achat/vente avec calcul automatique échéances
- ✅ Paiements et suivi des règlements
- ✅ Rappels automatiques (scheduler quotidien à 02:00)
- ✅ Génération PDF (BC, factures)
- ✅ Import Excel
- ✅ Dashboard avec KPIs
- ✅ Audit trail
- ✅ Sécurité RBAC (ADMIN, COMMERCIAL, COMPTABLE, LECTEUR)

## Documentation

- [Plan d'implémentation](.cursor/plans/bf4_invest_full_stack_integration_42e661a2.plan.md)

## Support

Pour toute question ou problème, consulter la documentation technique dans le code source.




