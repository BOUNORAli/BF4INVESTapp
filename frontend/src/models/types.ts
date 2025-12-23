export interface Product {
  id: string;
  ref: string;
  name: string;
  unit: string;
  priceBuyHT: number;
  priceSellHT: number;
  stock?: number; // Quantité en stock
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
  statut: 'PREVU' | 'REALISE' | 'EN_RETARD';
  notes?: string;
  dateRappel?: string; // Date de rappel optionnelle (format ISO: YYYY-MM-DD)
}

export interface PrevisionJournaliere {
  date: string;
  entreesPrevisionnelles: number;
  sortiesPrevisionnelles: number;
  soldePrevu: number;
}

export interface EcheanceDetail {
  date: string;
  type: 'VENTE' | 'ACHAT';
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
  soldePartenaireAvant: number;
  soldePartenaireApres: number;
  partenaireId?: string;
  partenaireType?: string; // "CLIENT" ou "FOURNISSEUR"
  partenaireNom?: string;
  referenceId?: string;
  referenceNumero?: string;
  date: string;
  description?: string;
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

