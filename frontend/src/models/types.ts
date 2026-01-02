export interface Product {
  id: string;
  ref: string;
  name: string;
  unit: string;
  priceBuyHT: number;
  priceSellHT: number;
  stock?: number; // Quantité en stock
  imageUrl?: string; // URL de l'image (base64 data URL)
}

export interface Client {
  id: string;
  name: string;
  ice: string;
  referenceClient?: string; // Référence client (3 premières lettres du nom par défaut)
  contact?: string;
  phone: string;
  email: string;
  address: string;
  rib?: string; // RIB du client pour les virements
}

export interface Supplier {
  id: string;
  name: string;
  ice: string;
  referenceFournisseur?: string; // Référence fournisseur (3 premières lettres du nom par défaut)
  contact?: string;
  phone: string;
  email: string;
  address: string;
  rib?: string; // RIB du fournisseur pour les virements
  banque?: string; // Banque du fournisseur
  dateRegulariteFiscale?: string; // Date de régularité fiscale (format ISO: YYYY-MM-DD)
}

export interface LineItem {
  productId: string;
  ref: string;
  name: string;
  unit: string;
  qtyBuy: number;
  qtySell: number;
  priceBuyHT: number;
  priceSellHT: number;
  tvaRate: number;
}

// === NOUVELLE STRUCTURE MULTI-CLIENTS ===

export interface LigneAchat {
  produitRef: string;
  designation: string;
  unite: string;
  quantiteAchetee: number;
  prixAchatUnitaireHT: number;
  tva: number;
  totalHT?: number;
  totalTTC?: number;
}

export interface LigneVente {
  produitRef: string;
  designation: string;
  unite: string;
  quantiteVendue: number;
  prixVenteUnitaireHT: number;
  tva: number;
  totalHT?: number;
  totalTTC?: number;
  margeUnitaire?: number;
  margePourcentage?: number;
}

export interface ClientVente {
  clientId: string;
  lignesVente: LigneVente[];
  totalVenteHT?: number;
  totalVenteTTC?: number;
  totalTVA?: number;
  margeTotale?: number;
  margePourcentage?: number;
}

// === FIN NOUVELLE STRUCTURE ===

export interface BC {
  id: string;
  number: string;
  date: string;
  supplierId: string;
  status: 'draft' | 'sent' | 'completed';
  paymentMode?: string; // Type de paiement (LCN, chèque, virement, etc.)
  delaiPaiement?: string; // Délai de paiement en jours (ex: "120J", "30J")
  lieuLivraison?: string;
  conditionLivraison?: string;
  responsableLivraison?: string;
  ajouterAuStock?: boolean; // Option pour ajouter les quantités achetées au stock
  
  // Nouvelle structure multi-clients
  lignesAchat?: LigneAchat[];
  clientsVente?: ClientVente[];
  
  // Ancienne structure (rétrocompatibilité)
  clientId?: string;
  items?: LineItem[];
  
  // Totaux
  totalAchatHT?: number;
  totalAchatTTC?: number;
  totalVenteHT?: number;
  totalVenteTTC?: number;
  margeTotale?: number;
  margePourcentage?: number;
}

export interface PaymentMode {
  id: string;
  name: string;
  active: boolean;
}

export interface Invoice {
  id: string;
  number: string;
  bcId: string;
  bcReference?: string; // Référence BC (colonne AFFECTATION de l'Excel)
  partnerId?: string;
  date: string;
  amountHT: number;
  amountTTC: number;
  dueDate: string;
  status: 'paid' | 'pending' | 'overdue';
  type: 'purchase' | 'sale';
  paymentMode?: string;
  
  // Champs pour les calculs comptables
  typeMouvement?: string; // "C" = Client, "F" = Fournisseur, "IB", "FB", "CTP", "CTD", etc.
  nature?: string; // "facture", "paiement", "loy", etc.
  colA?: string; // Colonne A (ex: "CAPITAL")
  colB?: string; // Colonne B
  colD?: string; // Utilisé pour les filtres (ex: "CCA")
  colF?: string; // Utilisé dans le calcul du solde pour IB
  tvaRate?: number; // Taux TVA (ex: 0.20 pour 20%)
  tauxRG?: number; // Taux de remise globale (ex: 0.10 pour 10%)
  
  // Champs calculés selon les formules Excel
  tvaMois?: string; // Format "mois/année" (ex: "01/2025")
  solde?: number; // Solde calculé selon type mouvement
  totalTTCApresRG?: number; // TTC après remise globale
  totalTTCApresRG_SIGNE?: number; // TTC après RG avec signe
  totalPaiementTTC?: number; // Total paiement TTC
  rgTTC?: number; // Remise globale TTC
  rgHT?: number; // Remise globale HT
  factureHT_YC_RG?: number; // Facture HT incluant RG
  htPaye?: number; // HT payé
  tvaFactureYcRg?: number; // TVA facture incluant RG
  tvaPaye?: number; // TVA payée
  bilan?: number; // Bilan HT
  
  // Prévisions de paiement
  previsionsPaiement?: PrevisionPaiement[];
  
  // Fichier facture (pour factures achat)
  fichierFactureId?: string;
  fichierFactureNom?: string;
  fichierFactureType?: string;
  fichierFactureUrl?: string;
}

export interface Payment {
  id?: string;
  factureAchatId?: string;
  factureVenteId?: string;
  bcReference?: string; // Référence BC associée
  date: string;
  montant: number;
  mode: string;
  reference?: string;
  notes?: string;
  
  // Champs pour les calculs comptables
  typeMouvement?: string; // "C" = Client, "F" = Fournisseur, "FB", "CTP", "CTD", etc.
  nature?: string; // "paiement", "facture", etc.
  colD?: string; // Utilisé pour les filtres (ex: "CCA")
  tvaRate?: number; // Taux TVA (ex: 0.20 pour 20%)
  
  // Champs calculés selon les formules Excel
  totalPaiementTTC?: number; // Total paiement TTC calculé
  htPaye?: number; // HT payé
  tvaPaye?: number; // TVA payée
  
  // Soldes après ce paiement
  soldeGlobalApres?: number;
  soldePartenaireApres?: number;
}

export interface PrevisionPaiement {
  id?: string;
  datePrevue: string;
  montantPrevu: number;
  statut: 'PREVU' | 'REALISE' | 'EN_RETARD' | 'EN_ATTENTE' | 'PAYEE' | 'PARTIELLE';
  notes?: string;
  dateRappel?: string; // Date de rappel optionnelle (format ISO: YYYY-MM-DD)
  montantPaye?: number; // Montant déjà payé sur cette prévision
  montantRestant?: number; // Montant restant à payer
}

export interface PrevisionJournaliere {
  date: string;
  entreesPrevisionnelles: number;
  sortiesPrevisionnelles: number;
  soldePrevu: number;
}

export interface EcheanceDetail {
  date: string;
  type: 'VENTE' | 'ACHAT' | 'CHARGE';
  numeroFacture: string;
  partenaire: string;
  montant: number;
  statut: string;
  factureId: string;
}

export interface PrevisionTresorerieResponse {
  soldeActuel: number;
  previsions: PrevisionJournaliere[];
  echeances: EcheanceDetail[];
}

export interface SoldeGlobal {
  id?: string;
  soldeInitial: number;
  soldeActuel: number;
  dateDebut: string;
}

export interface HistoriqueSolde {
  id: string;
  type: string; // "FACTURE_VENTE", "FACTURE_ACHAT", "PAIEMENT_CLIENT", "PAIEMENT_FOURNISSEUR"
  montant: number;
  soldeGlobalAvant: number;
  soldeGlobalApres: number;
  soldePartenaireAvant: number | null;
  soldePartenaireApres: number | null;
  partenaireId?: string;
  partenaireType?: string; // "CLIENT" ou "FOURNISSEUR"
  partenaireNom?: string;
  referenceId?: string;
  referenceNumero?: string;
  date: string;
  description?: string;
}

export interface Charge {
  id?: string;
  libelle: string;
  categorie?: string;
  montant: number;
  dateEcheance: string; // ISO YYYY-MM-DD
  statut: 'PREVUE' | 'PAYEE';
  datePaiement?: string; // ISO YYYY-MM-DD
  imposable: boolean; // Déductible fiscalement
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface Toast {
  id: number;
  message: string;
  type: 'success' | 'error' | 'info';
}

export interface Notification {
  id: string;
  title: string;
  message: string;
  time: string;
  read: boolean;
  type: 'info' | 'alert' | 'success';
}

export interface FactureMontant {
  factureId: string;
  montant: number; // Montant partiel pour cette facture
}

export interface OrdreVirement {
  id?: string;
  numeroOV: string;
  dateOV: string;
  montant: number;
  beneficiaireId?: string; // Optionnel pour permettre les personnes physiques
  nomBeneficiaire?: string;
  banqueBeneficiaire?: string; // Banque du bénéficiaire
  ribBeneficiaire: string;
  motif: string;
  facturesIds?: string[]; // Optionnel : peut être vide pour virements sans factures
  facturesMontants?: FactureMontant[]; // Liste des factures avec montants partiels
  banqueEmettrice: string;
  dateExecution?: string;
  statut: 'EN_ATTENTE' | 'EXECUTE' | 'ANNULE';
  type: 'NORMAL' | 'EXPRESS';
}

export interface DashboardKpiResponse {
  caHT: number;
  caTTC: number;
  totalAchatsHT: number;
  totalAchatsTTC: number;
  margeTotale: number;
  margeMoyenne: number;
  tvaCollectee: number;
  tvaDeductible: number;
  impayes: {
    totalImpayes: number;
    impayes0_30: number;
    impayes31_60: number;
    impayesPlus60: number;
  };
  facturesEnRetard: number;
  caMensuel: Array<{
    mois: string;
    caHT: number;
    marge: number;
  }>;
  topFournisseurs: Array<{
    id: string;
    nom: string;
    montant: number;
  }>;
  topClients: Array<{
    id: string;
    nom: string;
    montant: number;
  }>;
}

// ========== COMPTABILITÉ ==========

export interface CompteComptable {
  id?: string;
  code: string;
  libelle: string;
  classe: string;
  type: 'ACTIF' | 'PASSIF' | 'CHARGE' | 'PRODUIT' | 'TRESORERIE';
  collectif?: boolean;
  compteParent?: string;
  soldeDebit: number;
  soldeCredit: number;
  solde: number;
  actif: boolean;
}

export interface LigneEcriture {
  compteCode: string;
  compteLibelle: string;
  debit?: number;
  credit?: number;
  libelle: string;
}

export interface EcritureComptable {
  id?: string;
  dateEcriture: string;
  journal: string;
  numeroPiece: string;
  libelle: string;
  lignes: LigneEcriture[];
  pieceJustificativeType?: string;
  pieceJustificativeId?: string;
  exerciceId?: string;
  lettree?: boolean;
  pointage?: boolean;
}

export interface ExerciceComptable {
  id?: string;
  code: string;
  dateDebut: string;
  dateFin: string;
  statut: 'OUVERT' | 'CLOTURE';
}

export interface DeclarationTVA {
  id?: string;
  mois: number;
  annee: number;
  periode: string;
  tvaCollectee20: number;
  tvaCollectee14: number;
  tvaCollectee10: number;
  tvaCollectee7: number;
  tvaCollectee0: number;
  tvaCollecteeTotale: number;
  tvaDeductible20: number;
  tvaDeductible14: number;
  tvaDeductible10: number;
  tvaDeductible7: number;
  tvaDeductible0: number;
  tvaDeductibleTotale: number;
  tvaAPayer: number;
  tvaCredit: number;
  statut: 'BROUILLON' | 'VALIDEE' | 'DEPOSEE';
  dateDepot?: string;
  notes?: string;
}

export interface TransactionBancaire {
  id?: string;
  dateOperation: string; // ISO date
  dateValeur?: string;
  libelle: string;
  debit?: number;
  credit?: number;
  reference?: string;
  factureVenteId?: string;
  factureAchatId?: string;
  paiementId?: string;
  mapped: boolean;
  mois?: number;
  annee?: number;
  createdAt?: string;
  updatedAt?: string;
}

