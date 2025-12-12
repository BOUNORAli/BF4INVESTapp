# Guide Utilisateur - BF4 Invest

## Table des Matières

1. [Premier Démarrage](#premier-démarrage)
2. [Connexion à l'Application](#connexion-à-lapplication)
3. [Changement du Mot de Passe](#changement-du-mot-de-passe)
4. [Navigation dans l'Application](#navigation-dans-lapplication)
5. [Gestion des Clients et Fournisseurs](#gestion-des-clients-et-fournisseurs)
6. [Gestion des Produits](#gestion-des-produits)
7. [Création de Bandes de Commandes (BC)](#création-de-bandes-de-commandes-bc)
8. [Gestion des Factures](#gestion-des-factures)
9. [Import de Données Excel](#import-de-données-excel)
10. [Export et Génération de PDF](#export-et-génération-de-pdf)
11. [Tableau de Bord](#tableau-de-bord)
12. [Paramètres](#paramètres)

---

## Premier Démarrage

### Prérequis

- Docker et Docker Compose installés sur votre machine
- Accès à un terminal (PowerShell sur Windows, Terminal sur Linux/Mac)

### Installation

1. **Copier le fichier de configuration**
   ```bash
   cp .env.example .env
   ```

2. **Modifier le fichier `.env`** (optionnel pour le développement)
   - Générer un `JWT_SECRET` fort pour la production
   - Configurer les paramètres SMTP si nécessaire

3. **Démarrer l'application**
   ```bash
   docker-compose up -d --build
   ```

4. **Vérifier que tout fonctionne**
   ```bash
   docker-compose logs -f
   ```
   
   Attendez de voir des messages indiquant que le backend est démarré.

5. **Accéder à l'application**
   - Ouvrez votre navigateur et allez sur: `http://localhost`

---

## Connexion à l'Application

### Identifiants par Défaut

Lors du premier démarrage, un utilisateur administrateur est créé automatiquement:

- **Email**: `admin@bf4invest.ma` ou `admin@bf4.com`
- **Mot de passe**: `admin123`

⚠️ **IMPORTANT**: Changez ce mot de passe immédiatement après la première connexion !

### Procédure de Connexion

1. Ouvrez l'application dans votre navigateur
2. Sur la page de connexion, entrez votre email et mot de passe
3. Cliquez sur "Se connecter"

---

## Changement du Mot de Passe

1. Connectez-vous avec les identifiants admin
2. Allez dans **Paramètres** (menu latéral)
3. Dans la section "Sécurité", modifiez votre mot de passe
4. Cliquez sur "Enregistrer"

---

## Navigation dans l'Application

### Menu Principal

L'application dispose d'un menu latéral avec les sections suivantes:

- **Dashboard**: Vue d'ensemble des KPIs et statistiques
- **Commandes (BC)**: Gestion des bandes de commandes
- **Factures Achat**: Suivi des factures fournisseurs
- **Factures Vente**: Suivi des factures clients
- **Catalogue Produits**: Gestion du catalogue produits
- **Clients & Fournisseurs**: Gestion des partenaires
- **Import Excel**: Import de données depuis Excel
- **Paramètres**: Configuration de l'application

### Barre de Recherche Globale

- Utilisez `Ctrl+K` (ou `Cmd+K` sur Mac) pour ouvrir la recherche globale
- Recherchez des clients, fournisseurs, produits, BC ou factures
- Appuyez sur Entrée pour voir tous les résultats

### Notifications

- Cliquez sur l'icône de cloche pour voir les notifications
- Les notifications d'alerte s'affichent automatiquement (ex: factures en retard)

---

## Gestion des Clients et Fournisseurs

### Ajouter un Client ou Fournisseur

1. Allez dans **Clients & Fournisseurs**
2. Cliquez sur **"Ajouter Client"** ou **"Ajouter Fournisseur"**
3. Remplissez le formulaire:
   - Nom de l'entreprise
   - ICE (Identifiant Commun de l'Entreprise)
   - Adresse
   - Téléphone
   - Email
   - Modes de paiement acceptés (pour les fournisseurs)
4. Cliquez sur **"Enregistrer"**

### Modifier ou Supprimer

- Cliquez sur l'icône de modification (crayon) pour modifier
- Cliquez sur l'icône de suppression (poubelle) pour supprimer

---

## Gestion des Produits

### Ajouter un Produit

1. Allez dans **Catalogue Produits**
2. Cliquez sur **"Ajouter Produit"**
3. Remplissez le formulaire:
   - Référence
   - Nom
   - Description
   - Prix d'achat HT
   - Prix de vente HT
   - TVA (%)
4. Cliquez sur **"Enregistrer"**

### Calcul Automatique

- La marge se calcule automatiquement
- Le prix TTC est calculé automatiquement avec la TVA

---

## Création de Bandes de Commandes (BC)

### Créer une Nouvelle BC

1. Allez dans **Commandes (BC)**
2. Cliquez sur **"Créer BC"**
3. Remplissez les informations principales:
   - Client
   - Fournisseur
   - Date de commande
   - Mode de paiement
4. Ajoutez les lignes de produits:
   - Cliquez sur **"Ajouter Ligne"**
   - Recherchez un produit ou créez-en un nouveau
   - Entrez la quantité
   - Le prix d'achat et de vente sont pré-remplis
   - Ajustez la marge si nécessaire (ou le prix de vente)
5. Cliquez sur **"Enregistrer"**

### Gestion des Marges

- Vous pouvez entrer soit le prix de vente, soit la marge en pourcentage
- L'autre valeur se calcule automatiquement
- Les marges négatives sont affichées en rouge

### Statuts des BC

- **Brouillon**: BC en cours de création
- **Envoyée**: BC envoyée au fournisseur
- **Validée**: BC validée et livrée

---

## Gestion des Factures

### Factures d'Achat

1. Allez dans **Factures Achat**
2. Cliquez sur **"Enregistrer Facture"**
3. Liez la facture à un fournisseur et optionnellement à une BC
4. Remplissez:
   - Numéro de facture (du fournisseur)
   - Date de facture
   - Date d'échéance (calculée automatiquement: date + 60 jours)
   - Montant HT et TTC
   - Statut de paiement
   - Mode de paiement
5. Cliquez sur **"Enregistrer"**

### Factures de Vente

Similaire aux factures d'achat, mais liées à un client.

### Statuts de Paiement

- **En attente**: Paiement non effectué
- **Payée**: Paiement complété
- **En retard**: Date d'échéance dépassée

---

## Import de Données Excel

### Préparation

1. Allez dans **Import Excel**
2. Téléchargez le modèle Excel en cliquant sur **"Télécharger modèle"**
3. Remplissez le modèle avec vos données

### Import

1. Cliquez ou glissez-déposez votre fichier Excel (.xlsx)
2. Attendez que l'import se termine
3. Consultez le journal d'import pour voir les résultats

### Données Importables

- **Catalogue Produits**: Référence, nom, prix, TVA
- **Bandes de Commandes**: Client, fournisseur, produits, quantités, dates

### Notes Importantes

- Les clients et fournisseurs sont créés automatiquement s'ils n'existent pas
- Les produits sont créés automatiquement s'ils n'existent pas
- Les erreurs sont affichées dans le journal d'import

---

## Export et Génération de PDF

### Export Excel des BC

1. Allez dans **Commandes (BC)**
2. Utilisez les filtres pour sélectionner les BC à exporter
3. Cliquez sur **"Export Global"**
4. Le fichier Excel est téléchargé automatiquement

### Génération de PDF

#### BC (Bande de Commande)

1. Dans la liste des BC, cliquez sur l'icône PDF
2. Le PDF est généré et téléchargé automatiquement
3. Le PDF contient le logo BF4 Invest, les informations du client et fournisseur, et le détail des produits

#### Factures

1. Dans la liste des factures, cliquez sur l'icône PDF
2. Le PDF est généré avec toutes les informations de la facture
3. Le montant est écrit en toutes lettres

#### Rapport du Dashboard

1. Allez dans **Dashboard**
2. Cliquez sur **"Rapport"**
3. Un PDF complet avec tous les KPIs et statistiques est généré

---

## Tableau de Bord

Le tableau de bord affiche:

### KPIs Principaux

- **Chiffre d'Affaires**: Total des ventes HT
- **Total Achats**: Total des achats HT
- **Marge Nette**: Différence entre CA et achats
- **Action Requise**: Nombre d'alertes importantes

### Graphiques

- **Répartition Activité**: Répartition mensuelle du CA
- **Évolution Mensuelle**: Graphique d'évolution du CA sur les 12 derniers mois

### Commandes Récentes

- Liste des dernières BC créées
- Cliquez sur une BC pour voir ses détails

---

## Paramètres

### Modes de Paiement

Les modes de paiement disponibles sont:

- Virement Bancaire
- Chèque
- Espèces
- LCN (Lettre de Change)
- Compensation

Vous pouvez activer/désactiver chaque mode depuis les paramètres.

### Autres Paramètres

- Configuration des utilisateurs (admin uniquement)
- Gestion des notifications

---

## Conseils et Bonnes Pratiques

1. **Sauvegardes**: Pensez à sauvegarder régulièrement votre base de données MongoDB
2. **Mots de passe**: Utilisez des mots de passe forts et changez-les régulièrement
3. **Import**: Vérifiez toujours les données importées dans le journal
4. **Factures**: Vérifiez régulièrement les factures en retard depuis le tableau de bord
5. **Marges**: Surveillez les marges négatives qui peuvent indiquer des erreurs de saisie

---

## Support

Pour toute question ou problème:

1. Consultez les logs: `docker-compose logs -f backend`
2. Vérifiez que tous les services sont démarrés: `docker-compose ps`
3. Redémarrez les services si nécessaire: `docker-compose restart`

---

**Bonne utilisation de BF4 Invest !**

