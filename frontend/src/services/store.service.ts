import { Injectable, signal, computed, inject, effect } from '@angular/core';
import { ApiService } from './api.service';
import { ToastService, Toast } from './toast.service';
import { ProductService } from './product.service';
import { PartnerService } from './partner.service';
import { BcService } from './bc.service';
import { InvoiceService } from './invoice.service';
import { OrdreVirementService } from './ordre-virement.service';
import { OrdreVirement } from '../models/types';

export interface Product {
  id: string;
  ref: string;
  name: string;
  unit: string;
  priceBuyHT: number;
  priceSellHT: number;
  stock?: number; // Quantit├® en stock
}

export interface Client {
  id: string;
  name: string;
  ice: string;
  referenceClient?: string; // R├®f├®rence client (3 premi├¿res lettres du nom par d├®faut)
  contact?: string;
  phone: string;
  email: string;
  address: string;
}

export interface Supplier {
  id: string;
  name: string;
  ice: string;
  referenceFournisseur?: string; // R├®f├®rence fournisseur (3 premi├¿res lettres du nom par d├®faut)
  contact?: string;
  phone: string;
  email: string;
  address: string;
  rib?: string; // RIB du fournisseur pour les virements
  dateRegulariteFiscale?: string; // Date de r├®gularit├® fiscale (format ISO: YYYY-MM-DD)
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
  ajouterAuStock?: boolean; // Option pour ajouter les quantit├®s achet├®es au stock
  
  // Nouvelle structure multi-clients
  lignesAchat?: LigneAchat[];
  clientsVente?: ClientVente[];
  
  // Ancienne structure (r├®trocompatibilit├®)
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

export type { Toast };

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

@Injectable({
  providedIn: 'root'
})
export class StoreService {
  private api = inject(ApiService);
  private toastService = inject(ToastService);
  private productService = inject(ProductService);
  private partnerService = inject(PartnerService);
  private bcService = inject(BcService);
  private invoiceService = inject(InvoiceService);
  private ordreVirementService = inject(OrdreVirementService);

  // --- NOTIFICATIONS SYSTEM (TOASTS) ---
  readonly toasts = this.toastService.toasts;

  showToast(message: string, type: 'success' | 'error' | 'info' = 'success') {
    this.toastService.showToast(message, type);
  }

  removeToast(id: number) {
    this.toastService.removeToast(id);
  }

  // --- NOTIFICATION CENTER (HISTORY) ---
  readonly notifications = signal<Notification[]>([]);
  readonly loading = signal<boolean>(false);

  readonly unreadNotificationsCount = computed(() => 
    this.notifications().filter(n => !n.read).length
  );

  async loadNotifications(unreadOnly: boolean = false): Promise<void> {
    try {
      this.loading.set(true);
      const params: Record<string, any> = { unreadOnly };
      const backendNotifications = await this.api.get<any[]>('/notifications', params).toPromise() || [];
      const mapped = backendNotifications.map(n => this.mapNotification(n));
      this.notifications.set(mapped);
    } catch (error) {
      console.error('Error loading notifications:', error);
    } finally {
      this.loading.set(false);
    }
  }

  async markNotificationAsRead(id: string): Promise<void> {
    try {
      await this.api.put(`/notifications/${id}/read`, {}).toPromise();
      this.notifications.update(list => 
        list.map(n => n.id === id ? { ...n, read: true } : n)
      );
    } catch (error) {
      console.error('Error marking notification as read:', error);
    }
  }

  async markAllAsRead(): Promise<void> {
    try {
      await this.api.put('/notifications/read-all', {}).toPromise();
      this.notifications.update(list => list.map(n => ({ ...n, read: true })));
    } catch (error) {
      console.error('Error marking all notifications as read:', error);
    }
  }

  // Keep local method for immediate UI feedback (notifications created locally)
  addNotification(n: Omit<Notification, 'id' | 'read' | 'time'>) {
    const newNotif: Notification = {
      id: `local-${Date.now()}`,
      read: false,
      time: '├Ç l\'instant',
      ...n
    };
    this.notifications.update(list => [newNotif, ...list]);
    // Optionally sync to backend if needed
  }

  private mapNotification(n: any): Notification {
    // Map backend notification to frontend format
    const niveau = n.niveau || 'info';
    let type: 'info' | 'alert' | 'success' = 'info';
    if (niveau === 'critique' || niveau === 'warning') {
      type = 'alert';
    } else if (niveau === 'info') {
      type = 'info';
    }

    // Format time
    let timeStr = '├Ç l\'instant';
    if (n.createdAt) {
      const date = new Date(n.createdAt);
      const now = new Date();
      const diffMs = now.getTime() - date.getTime();
      const diffMins = Math.floor(diffMs / 60000);
      const diffHours = Math.floor(diffMs / 3600000);
      const diffDays = Math.floor(diffMs / 86400000);

      if (diffMins < 1) {
        timeStr = '├Ç l\'instant';
      } else if (diffMins < 60) {
        timeStr = `Il y a ${diffMins} min`;
      } else if (diffHours < 24) {
        timeStr = `Il y a ${diffHours}h`;
      } else if (diffDays === 1) {
        timeStr = 'Hier';
      } else if (diffDays < 7) {
        timeStr = `Il y a ${diffDays} jours`;
      } else {
        timeStr = date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' });
      }
    }

    return {
      id: n.id,
      title: n.titre || n.title || 'Notification',
      message: n.message || '',
      time: timeStr,
      read: n.read || false,
      type: type
    };
  }

  // --- DATA STATE SIGNALS ---
  readonly paymentModes = signal<PaymentMode[]>([]);
  readonly products = signal<Product[]>([]);
  readonly clients = signal<Client[]>([]);
  readonly suppliers = signal<Supplier[]>([]);
  readonly bcs = signal<BC[]>([]);
  readonly invoices = signal<Invoice[]>([]);
  readonly ordresVirement = signal<OrdreVirement[]>([]);
  readonly dashboardKPIs = signal<DashboardKpiResponse | null>(null);
  readonly dashboardLoading = signal<boolean>(false);
  readonly payments = signal<Map<string, Payment[]>>(new Map()); // Map<invoiceId, Payment[]>
  readonly soldeGlobal = signal<SoldeGlobal | null>(null);
  readonly historiqueSolde = signal<HistoriqueSolde[]>([]);
  readonly previsionTresorerie = signal<PrevisionTresorerieResponse | null>(null);
  
  // Flag pour ├®viter les rechargements multiples
  private dataLoaded = false;
  private dataLoading = false;

  constructor() {
    // Charger les donn├®es au d├®marrage (une seule fois)
    this.loadInitialData();
  }

  private async loadInitialData() {
    // ├ëviter les rechargements multiples
    if (this.dataLoaded || this.dataLoading) {
      return;
    }
    
    this.dataLoading = true;
    
    try {
      // Charger TOUTES les donn├®es en parall├¿le pour plus de rapidit├®
      await Promise.all([
        this.loadClients(),
        this.loadSuppliers(),
        this.loadProducts(),
        this.loadPaymentModes(),
        this.loadBCs(),
        this.loadInvoices(),
        this.loadNotifications(false)
      ]);
      
      this.dataLoaded = true;
    } catch (error) {
      console.error('Error loading initial data:', error);
      // Ne pas afficher de toast pour ne pas bloquer l'UX
    } finally {
      this.dataLoading = false;
    }
  }

  private async loadClients(): Promise<void> {
    try {
      const clients = await this.partnerService.getClients();
      this.clients.set(clients);
    } catch (error) {
      console.error('Error loading clients:', error);
    }
  }

  async loadSuppliers(): Promise<void> {
    try {
      const suppliers = await this.partnerService.getSuppliers();
      this.suppliers.set(suppliers);
    } catch (error) {
      console.error('Error loading suppliers:', error);
    }
  }

  private async loadProducts(): Promise<void> {
    try {
      const products = await this.productService.getProducts();
      this.products.set(products);
    } catch (error) {
      console.error('Error loading products:', error);
    }
  }

  async loadPaymentModes(): Promise<void> {
    try {
      const modes = await this.api.get<any[]>('/settings/payment-modes').toPromise() || [];
      this.paymentModes.set(modes.map(m => ({
        id: m.id || `pm-${Date.now()}-${Math.random()}`,
        name: m.name,
        active: m.active !== false
      })));
    } catch (error) {
      console.error('Error loading payment modes:', error);
    }
  }

  // --- REFRESH ALL DATA ---
  // Signal pour indiquer un rafra├«chissement en cours (non bloquant)
  readonly refreshing = signal<boolean>(false);
  
  async refreshAllData(): Promise<void> {
    if (this.refreshing()) return; // ├ëviter les rafra├«chissements simultan├®s
    
    this.refreshing.set(true);
    this.showToast('Actualisation...', 'info');
    
    try {
      await Promise.all([
        this.loadClients(),
        this.loadSuppliers(), 
        this.loadProducts(),
        this.loadBCs(),
        this.loadInvoices(),
        this.loadPaymentModes()
      ]);
      this.showToast('Données actualisées', 'success');
    } catch (error) {
      console.error('Error refreshing data:', error);
      this.showToast('Erreur lors du rafra├«chissement', 'error');
    } finally {
      this.refreshing.set(false);
    }
  }

  // --- MAPPERS ---
  // Mappers removed as they are now in specific services


  // --- DASHBOARD KPIs ---
  async loadDashboardKPIs(from?: Date, to?: Date): Promise<void> {
    try {
      this.dashboardLoading.set(true);
      const params: Record<string, any> = {};
      if (from) {
        params.from = from.toISOString().split('T')[0];
      }
      if (to) {
        params.to = to.toISOString().split('T')[0];
      }
      
      const kpis = await this.api.get<DashboardKpiResponse>('/dashboard/kpis', params).toPromise();
      if (kpis) {
        this.dashboardKPIs.set(kpis);
      }
    } catch (error) {
      console.error('Error loading dashboard KPIs:', error);
      this.showToast('Erreur lors du chargement des KPIs', 'error');
    } finally {
      this.dashboardLoading.set(false);
    }
  }

  // --- COMPUTED SIGNALS (KPIs) - Fallback to backend data or local calculation ---
  readonly totalSalesHT = computed(() => {
    const backendKPIs = this.dashboardKPIs();
    if (backendKPIs) {
      return backendKPIs.caHT;
    }
    // Fallback to local calculation
    return this.invoices().filter(i => i.type === 'sale').reduce((acc, curr) => acc + curr.amountHT, 0);
  });

  readonly totalPurchasesHT = computed(() => {
    const backendKPIs = this.dashboardKPIs();
    if (backendKPIs) {
      return backendKPIs.totalAchatsHT;
    }
    // Fallback to local calculation
    return this.invoices().filter(i => i.type === 'purchase').reduce((acc, curr) => acc + curr.amountHT, 0);
  });

  readonly marginTotal = computed(() => {
    const backendKPIs = this.dashboardKPIs();
    if (backendKPIs) {
      return backendKPIs.margeTotale;
    }
    // Fallback to local calculation
    return this.totalSalesHT() - this.totalPurchasesHT();
  });
  
  readonly overduePurchaseInvoices = computed(() => {
    const backendKPIs = this.dashboardKPIs();
    if (backendKPIs) {
      return backendKPIs.facturesEnRetard;
    }
    // Fallback to local calculation
    const today = new Date().toISOString().split('T')[0];
    return this.invoices().filter(i => i.type === 'purchase' && i.status === 'pending' && i.dueDate < today).length;
  });

  // --- ACTIONS: PAYMENT MODES ---
  async addPaymentMode(name: string): Promise<void> {
    try {
      const response = await this.api.post<PaymentMode>('/settings/payment-modes', { name }).toPromise();
      if (response) {
        this.paymentModes.update(modes => [...modes, response]);
        this.showToast('Mode de paiement ajouté', 'success');
      }
    } catch (error) {
      console.error('Error adding payment mode:', error);
      this.showToast('Erreur lors de l\'ajout du mode de paiement', 'error');
      throw error;
    }
  }

  async togglePaymentMode(id: string): Promise<void> {
    try {
      const response = await this.api.put<PaymentMode>(`/settings/payment-modes/${id}/toggle`, {}).toPromise();
      if (response) {
        this.paymentModes.update(modes => modes.map(m => m.id === id ? response : m));
        this.showToast(`Mode ${response.active ? 'activé' : 'désactivé'}`, 'success');
      }
    } catch (error) {
      console.error('Error toggling payment mode:', error);
      this.showToast('Erreur lors de la modification du mode de paiement', 'error');
      throw error;
    }
  }

  async deletePaymentMode(id: string): Promise<void> {
    try {
      await this.api.delete(`/settings/payment-modes/${id}`).toPromise();
      this.paymentModes.update(modes => modes.filter(m => m.id !== id));
      this.showToast('Mode supprimé', 'success');
    } catch (error) {
      console.error('Error deleting payment mode:', error);
      this.showToast('Erreur lors de la suppression du mode de paiement', 'error');
      throw error;
    }
  }

  // --- ACTIONS: CLIENTS ---
  async addClient(client: Client): Promise<void> {
    try {
      const created = await this.partnerService.addClient(client);
      this.clients.update(list => [created, ...list]);
      this.showToast('Client ajouté avec succès');
      this.addNotification({ title: 'Nouveau Client', message: `Client ${client.name} ajouté.`, type: 'info' });
    } catch (error) {
      this.showToast('Erreur lors de l\'ajout du client', 'error');
      throw error;
    }
  }

  async updateClient(client: Client): Promise<void> {
    try {
      const updated = await this.partnerService.updateClient(client);
      this.clients.update(list => list.map(c => c.id === client.id ? updated : c));
      this.showToast('Fiche client mise à jour');
    } catch (error) {
      this.showToast('Erreur lors de la mise à jour', 'error');
      throw error;
    }
  }

  async deleteClient(id: string): Promise<boolean> {
    try {
      await this.partnerService.deleteClient(id);
      this.clients.update(list => list.filter(c => c.id !== id));
      this.showToast('Client supprimé', 'info');
      return true;
    } catch (error) {
      this.showToast('Erreur lors de la suppression', 'error');
      throw error;
    }
  }

  // --- ACTIONS: SUPPLIERS ---
  async addSupplier(supplier: Supplier): Promise<void> {
    try {
      const created = await this.partnerService.addSupplier(supplier);
      this.suppliers.update(list => [created, ...list]);
      this.showToast('Fournisseur ajouté avec succès');
    } catch (error) {
      this.showToast('Erreur lors de l\'ajout du fournisseur', 'error');
      throw error;
    }
  }

  async updateSupplier(supplier: Supplier): Promise<void> {
    try {
      const updated = await this.partnerService.updateSupplier(supplier);
      this.suppliers.update(list => list.map(s => s.id === supplier.id ? updated : s));
      this.showToast('Fiche fournisseur mise à jour');
    } catch (error) {
      this.showToast('Erreur lors de la mise à jour', 'error');
      throw error;
    }
  }

  async deleteSupplier(id: string): Promise<boolean> {
    try {
      await this.partnerService.deleteSupplier(id);
      this.suppliers.update(list => list.filter(s => s.id !== id));
      this.showToast('Fournisseur supprimé', 'info');
      return true;
    } catch (error) {
      this.showToast('Erreur lors de la suppression', 'error');
      throw error;
    }
  }

  // --- ACTIONS: PRODUCTS ---
  async addProduct(product: Product): Promise<void> {
    try {
      const created = await this.productService.addProduct(product);
      this.products.update(list => [created, ...list]);
      this.showToast('Produit ajouté au catalogue');
    } catch (error) {
      this.showToast('Erreur lors de l\'ajout du produit', 'error');
      throw error;
    }
  }

  async updateProduct(product: Product): Promise<void> {
    try {
      const updated = await this.productService.updateProduct(product);
      this.products.update(list => list.map(p => p.id === product.id ? updated : p));
      this.showToast('Produit mis à jour');
    } catch (error) {
      this.showToast('Erreur lors de la mise à jour', 'error');
      throw error;
    }
  }

  async deleteProduct(id: string): Promise<boolean> {
    try {
      await this.productService.deleteProduct(id);
      this.products.update(list => list.filter(p => p.id !== id));
      this.showToast('Produit retiré du catalogue', 'info');
      return true;
    } catch (error) {
      this.showToast('Erreur lors de la suppression', 'error');
      throw error;
    }
  }

  // --- ACTIONS: BC ---
  async loadBCs(): Promise<void> {
    try {
      const bcs = await this.bcService.getBCs();
      this.bcs.set(bcs);
    } catch (error) {
      console.error('Error loading BCs:', error);
    }
  }

  async addBC(bc: BC): Promise<void> {
    try {
      const created = await this.bcService.addBC(bc);
      this.bcs.update(list => [created, ...list]);
      
      // Recharger les produits si le stock a été mis à jour
      if (bc.ajouterAuStock) {
        await this.loadProducts();
      }
      
      this.showToast('Commande créée avec succès', 'success');
      this.addNotification({ title: 'Nouvelle Commande', message: `BC ${bc.number} créée.`, type: 'success' });
    } catch (error) {
      this.showToast('Erreur lors de la création de la commande', 'error');
      throw error;
    }
  }

  async updateBC(updatedBc: BC): Promise<void> {
    try {
      const updated = await this.bcService.updateBC(updatedBc);
      this.bcs.update(list => list.map(b => b.id === updatedBc.id ? updated : b));
      
      // Recharger les produits si le stock a été mis à jour
      if (updatedBc.ajouterAuStock) {
        await this.loadProducts();
      }
      this.showToast('Commande mise à jour', 'success');
    } catch (error) {
      this.showToast('Erreur lors de la mise à jour', 'error');
      throw error;
    }
  }

  async deleteBC(id: string): Promise<boolean> {
    try {
      await this.bcService.deleteBC(id);
      this.bcs.update(list => list.filter(b => b.id !== id));
      this.showToast('Commande supprimée', 'info');
      return true;
    } catch (error) {
      this.showToast('Erreur lors de la suppression', 'error');
      throw error;
    }
  }


  // --- ACTIONS: ORDRES VIREMENT ---
  async loadOrdresVirement(params?: {
    beneficiaireId?: string;
    statut?: string;
    dateDebut?: string;
    dateFin?: string;
  }): Promise<void> {
    try {
      const ordres = await this.ordreVirementService.getOrdresVirement(params).toPromise() || [];
      this.ordresVirement.set(ordres);
    } catch (error) {
      console.error('Error loading ordres virement:', error);
    }
  }

  async addOrdreVirement(ov: OrdreVirement): Promise<OrdreVirement> {
    try {
      const saved = await this.ordreVirementService.addOrdreVirement(ov).toPromise();
      if (saved) {
        this.ordresVirement.update(list => [...list, saved]);
        this.showToast('Ordre de virement créé', 'success');
      }
      return saved!;
    } catch (error) {
      this.showToast('Erreur lors de la création', 'error');
      throw error;
    }
  }

  async updateOrdreVirement(id: string, ov: OrdreVirement): Promise<OrdreVirement> {
    try {
      const updated = await this.ordreVirementService.updateOrdreVirement(id, ov).toPromise();
      if (updated) {
        this.ordresVirement.update(list => 
          list.map(o => o.id === id ? updated : o)
        );
        this.showToast('Ordre de virement modifié', 'success');
      }
      return updated!;
    } catch (error) {
      this.showToast('Erreur lors de la modification', 'error');
      throw error;
    }
  }

  async deleteOrdreVirement(id: string): Promise<boolean> {
    try {
      await this.ordreVirementService.deleteOrdreVirement(id).toPromise();
      this.ordresVirement.update(list => list.filter(o => o.id !== id));
      this.showToast('Ordre de virement supprimé', 'info');
      return true;
    } catch (error) {
      this.showToast('Erreur lors de la suppression', 'error');
      throw error;
    }
  }

  async executerOrdreVirement(id: string): Promise<void> {
    try {
      const updated = await this.ordreVirementService.executerOrdreVirement(id).toPromise();
      if (updated) {
        this.ordresVirement.update(list => 
          list.map(o => o.id === id ? updated : o)
        );
        this.showToast('Ordre de virement exécuté', 'success');
      }
    } catch (error) {
      this.showToast('Erreur lors de l\'exécution', 'error');
      throw error;
    }
  }

  async annulerOrdreVirement(id: string): Promise<void> {
    try {
      const updated = await this.ordreVirementService.annulerOrdreVirement(id).toPromise();
      if (updated) {
        this.ordresVirement.update(list => 
          list.map(o => o.id === id ? updated : o)
        );
        this.showToast('Ordre de virement annulé', 'info');
      }
    } catch (error) {
      this.showToast('Erreur lors de l\'annulation', 'error');
      throw error;
    }
  }

  // --- ACTIONS: INVOICES ---
  async loadInvoices(): Promise<void> {
    try {
      const allInvoices = await this.invoiceService.getInvoices();
      this.invoices.set(allInvoices);
    } catch (error) {
      console.error('Error loading invoices:', error);
    }
  }

  async addInvoice(inv: Invoice): Promise<void> {
    try {
      const created = await this.invoiceService.addInvoice(inv);
      this.invoices.update(list => [created, ...list]);
      
      this.showToast(inv.type === 'sale' ? 'Facture vente émise' : 'Facture achat enregistrée', 'success');
      this.addNotification({ 
        title: inv.type === 'sale' ? 'Facture Vente' : 'Facture Achat', 
        message: `${inv.number} enregistrée pour ${created.amountTTC} MAD.`, 
        type: 'info' 
      });
    } catch (error) {
      console.error('❌ store.addInvoice - ERREUR:', error);
      this.showToast('Erreur lors de l\'enregistrement de la facture', 'error');
      throw error;
    }
  }

  async updateInvoice(inv: Invoice): Promise<void> {
    try {
      const existingInvoice = this.invoices().find(i => i.id === inv.id);
      const updated = await this.invoiceService.updateInvoice(inv, existingInvoice);
      this.invoices.update(list => list.map(item => item.id === inv.id ? updated : item));
      
      this.showToast('Facture mise à jour avec succès', 'success');
    } catch (error) {
      console.error('Error updating invoice:', error);
      this.showToast('Erreur lors de la mise à jour de la facture', 'error');
      throw error;
    }
  }

  async deleteInvoice(id: string): Promise<boolean> {
    try {
      const invoice = this.invoices().find(inv => inv.id === id);
      if (!invoice) {
        this.showToast('Facture non trouvée', 'error');
        return false;
      }
      
      await this.invoiceService.deleteInvoice(id, invoice.type);
      this.invoices.update(list => list.filter(inv => inv.id !== id));
      this.showToast('Facture supprimée', 'info');
      return true;
    } catch (error) {
      this.showToast('Erreur lors de la suppression', 'error');
      throw error;
    }
  }


  // --- HELPERS ---
  getClientName(id: string) {
    return this.clients().find(c => c.id === id)?.name || 'Inconnu';
  }

  getSupplierName(id: string) {
    return this.suppliers().find(s => s.id === id)?.name || 'Inconnu';
  }

  getBCNumber(id: string) {
    return this.bcs().find(b => b.id === id)?.number || '-';
  }

  // --- PDF EXPORT ---
  
  // Helper method pour g├®rer le t├®l├®chargement/ouverture des PDFs
  private async handlePDFDownload(blob: Blob, fileName: string): Promise<void> {
    const isMobile = /iPhone|iPad|iPod|Android/i.test(navigator.userAgent);
    
    if (isMobile) {
      // Sur mobile : essayer d'abord l'API Web Share si disponible
      if (navigator.share && navigator.canShare) {
        try {
          const file = new File([blob], fileName, { type: 'application/pdf' });
          if (navigator.canShare({ files: [file] })) {
            await navigator.share({
              files: [file],
              title: fileName,
              text: fileName
            });
            return; // Succ├¿s, on sort
          }
        } catch (error: any) {
          // Si l'utilisateur annule le partage ou erreur, continuer avec l'ouverture
          if (error.name !== 'AbortError') {
            // Web Share API not available or user cancelled, falling back to window.open
          }
        }
      }
      
      // Fallback : ouvrir dans une nouvelle fen├¬tre
      const url = window.URL.createObjectURL(blob);
      const newWindow = window.open(url, '_blank');
      if (newWindow) {
        // R├®vocuer l'URL apr├¿s un court d├®lai
        setTimeout(() => {
          window.URL.revokeObjectURL(url);
        }, 1000);
      } else {
        // Si popup bloqu├®e, fallback sur t├®l├®chargement
        this.forceDownload(url, fileName);
        setTimeout(() => window.URL.revokeObjectURL(url), 1000);
      }
    } else {
      // Sur desktop : t├®l├®charger normalement
      const url = window.URL.createObjectURL(blob);
      this.forceDownload(url, fileName);
      setTimeout(() => window.URL.revokeObjectURL(url), 100);
    }
  }
  
  // Force le t├®l├®chargement avec un nom de fichier propre
  private forceDownload(url: string, fileName: string): void {
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.style.display = 'none';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }

  async downloadBCPDF(bcId: string): Promise<void> {
    try {
      this.showToast('Génération du PDF en cours...', 'info');
      const blob = await this.bcService.downloadBCPDF(bcId);
      if (blob) {
        const fileName = `BC-${this.getBCNumber(bcId)}.pdf`;
        await this.handlePDFDownload(blob, fileName);
        this.showToast('PDF généré avec succès', 'success');
        this.addNotification({
          title: 'Export PDF',
          message: `BC ${this.getBCNumber(bcId)} exportée en PDF.`,
          type: 'success'
        });
      }
    } catch (error) {
      console.error('Error downloading BC PDF:', error);
      this.showToast('Erreur lors de la génération du PDF', 'error');
      throw error;
    }
  }

  async downloadFactureVentePDF(factureId: string): Promise<void> {
    try {
      this.showToast('Génération du PDF en cours...', 'info');
      const blob = await this.invoiceService.downloadFactureVentePDF(factureId);
      if (blob) {
        const invoice = this.invoices().find(i => i.id === factureId);
        const fileName = `FV-${invoice?.number || factureId}.pdf`;
        await this.handlePDFDownload(blob, fileName);
        this.showToast('PDF généré avec succès', 'success');
      }
    } catch (error) {
      console.error('Error downloading Facture Vente PDF:', error);
      this.showToast('Erreur lors de la génération du PDF', 'error');
      throw error;
    }
  }

  async downloadBonDeLivraisonPDF(factureId: string): Promise<void> {
    try {
      this.showToast('Génération du bon de livraison en cours...', 'info');
      const blob = await this.invoiceService.downloadBonDeLivraisonPDF(factureId);
      if (blob) {
        const invoice = this.invoices().find(i => i.id === factureId);
        const fileName = `BL-${invoice?.number || factureId}.pdf`;
        await this.handlePDFDownload(blob, fileName);
        this.showToast('Bon de livraison généré avec succès', 'success');
      }
    } catch (error) {
      console.error('Error downloading Bon de Livraison PDF:', error);
      this.showToast('Erreur lors de la génération du bon de livraison', 'error');
      throw error;
    }
  }

  async downloadFactureAchatPDF(factureId: string): Promise<void> {
    try {
      this.showToast('Génération du PDF en cours...', 'info');
      const blob = await this.invoiceService.downloadFactureAchatPDF(factureId);
      if (blob) {
        const invoice = this.invoices().find(i => i.id === factureId);
        const fileName = `FA-${invoice?.number || factureId}.pdf`;
        await this.handlePDFDownload(blob, fileName);
        this.showToast('PDF généré avec succès', 'success');
      }
    } catch (error) {
      console.error('Error downloading Facture Achat PDF:', error);
      this.showToast('Erreur lors de la génération du PDF', 'error');
      throw error;
    }
  }

  // --- ACTIONS: PAYMENTS ---
  async loadPaiements(factureAchatId?: string, factureVenteId?: string): Promise<Payment[]> {
    try {
      const params: Record<string, any> = {};
      if (factureAchatId) {
        params.factureAchatId = factureAchatId;
      }
      if (factureVenteId) {
        params.factureVenteId = factureVenteId;
      }
      
      const paiements = await this.api.get<any[]>('/paiements', params).toPromise() || [];
      const mapped = await this.invoiceService.getPayments(factureAchatId, factureVenteId);
      
      // Update payments map
      const invoiceId = factureAchatId || factureVenteId || '';
      if (invoiceId) {
        this.payments.update(map => {
          const newMap = new Map(map);
          newMap.set(invoiceId, mapped);
          return newMap;
        });
      }
      
      return mapped;
    } catch (error) {
      console.error('Error loading payments:', error);
      return [];
    }
  }

  getPaymentsForInvoice(invoiceId: string): Payment[] {
    return this.payments().get(invoiceId) || [];
  }

  async loadPaymentsForInvoice(invoiceId: string, invoiceType: 'sale' | 'purchase'): Promise<void> {
    if (invoiceType === 'sale') {
      await this.loadPaiements(undefined, invoiceId);
    } else {
      await this.loadPaiements(invoiceId, undefined);
    }
  }

  getTotalPaidForInvoice(invoiceId: string): number {
    const payments = this.getPaymentsForInvoice(invoiceId);
    return payments.reduce((sum, p) => sum + (p.montant || 0), 0);
  }

  getRemainingAmountForInvoice(invoice: Invoice): number {
    const totalPaid = this.getTotalPaidForInvoice(invoice.id);
    return invoice.amountTTC - totalPaid;
  }

  async addPaiement(payment: Payment): Promise<void> {
    try {
      const payload = {
        factureAchatId: payment.factureAchatId,
        factureVenteId: payment.factureVenteId,
        date: payment.date,
        montant: payment.montant,
        mode: payment.mode,
        reference: payment.reference || '',
        notes: payment.notes || ''
      };
      
      const created = await this.invoiceService.addPayment(payment);
      
      // Update local payments map
      const invoiceId = payment.factureAchatId || payment.factureVenteId || '';
      if (invoiceId) {
        this.payments.update(map => {
          const newMap = new Map(map);
          const existing = newMap.get(invoiceId) || [];
          newMap.set(invoiceId, [...existing, created]);
          return newMap;
        });
        
        // Reload invoices to get updated status
        await this.loadInvoices();
        
        // Recharger le solde global après un paiement
        await this.loadSoldeGlobal();
      }
      
      this.showToast('Paiement enregistr├® avec succ├¿s', 'success');
      this.addNotification({
        title: 'Nouveau Paiement',
        message: `Paiement de ${payment.montant} MAD enregistr├®.`,
        type: 'info'
      });
    } catch (error) {
      this.showToast('Erreur lors de l\'enregistrement du paiement', 'error');
      throw error;
    }
  }


  // T├®l├®charger le rapport complet du dashboard
  async downloadDashboardReport(from?: Date, to?: Date): Promise<void> {
    try {
      this.showToast('G├®n├®ration du rapport en cours...', 'info');
      
      // Construire les param├¿tres de requ├¬te
      const params: Record<string, string> = {};
      if (from) {
        params.from = from.toISOString().split('T')[0];
      }
      if (to) {
        params.to = to.toISOString().split('T')[0];
      }
      
      // Construire l'URL avec les param├¿tres
      let url = '/dashboard/report/pdf';
      const queryString = new URLSearchParams(params).toString();
      if (queryString) {
        url += '?' + queryString;
      }
      
      // T├®l├®charger le PDF
      const blob = await this.api.downloadFile(url).toPromise();
      
      if (blob) {
        const today = new Date().toISOString().split('T')[0];
        const fileName = `Rapport_Activite_${today}.pdf`;
        await this.handlePDFDownload(blob, fileName);
        
        this.showToast('Rapport g├®n├®r├® avec succ├¿s', 'success');
        this.addNotification({ 
          title: 'Rapport d\'Activit├®', 
          message: 'Le rapport PDF a ├®t├® g├®n├®r├® et t├®l├®charg├®.', 
          type: 'success'
        });
      }
    } catch (error) {
      console.error('Error downloading dashboard report:', error);
      this.showToast('Erreur lors de la g├®n├®ration du rapport', 'error');
      throw error;
    }
  }

  // Export global des BCs en Excel
  async exportBCsToExcel(params?: { 
    clientId?: string; 
    supplierId?: string; 
    dateMin?: string; 
    dateMax?: string; 
    status?: string 
  }): Promise<void> {
    try {
      this.showToast('Génération de l\'export Excel en cours...', 'info');
      const blob = await this.bcService.exportBCsToExcel(params);
      
      if (blob) {
        const urlObj = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = urlObj;
        
        // Extraire le nom de fichier depuis les headers ou utiliser un nom par défaut
        const today = new Date().toISOString().split('T')[0];
        link.download = `Export_BCs_${today}.xlsx`;
        
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(urlObj);
        
        this.showToast('Export Excel téléchargé avec succès', 'success');
      }
    } catch (error) {
      console.error('Error exporting BCs to Excel:', error);
      this.showToast('Erreur lors de l\'export Excel', 'error');
      throw error;
    }
  }

  // --- PARAMETRES CALCUL ---
  async loadParametresCalcul(): Promise<any> {
    try {
      const params = await this.api.get<any>('/parametres-calcul').toPromise();
      return params;
    } catch (error) {
      console.error('Error loading parametres calcul:', error);
      return null;
    }
  }

  async saveParametresCalcul(params: any): Promise<void> {
    try {
      await this.api.put('/parametres-calcul', params).toPromise();
      this.showToast('Paramètres de calcul mis à jour avec succès', 'success');
    } catch (error) {
      console.error('Error saving parametres calcul:', error);
      this.showToast('Erreur lors de la sauvegarde des paramètres', 'error');
      throw error;
    }
  }

  // --- INFORMATIONS SOCIETE (FOOTER PDF) ---
  async loadCompanyInfo(): Promise<any> {
    try {
      const info = await this.api.get<any>('/company-info').toPromise();
      return info;
    } catch (error) {
      console.error('Error loading company info:', error);
      return null;
    }
  }

  async saveCompanyInfo(info: any): Promise<void> {
    try {
      await this.api.put('/company-info', info).toPromise();
      this.showToast('Informations de la société mises à jour avec succès', 'success');
    } catch (error) {
      console.error('Error saving company info:', error);
      this.showToast('Erreur lors de la sauvegarde des informations société', 'error');
      throw error;
    }
  }

  // --- GESTION DES SOLDES ---
  async loadSoldeGlobal(): Promise<void> {
    try {
      const solde = await this.api.get<SoldeGlobal>('/solde/global/complet').toPromise();
      if (solde) {
        this.soldeGlobal.set(solde);
      }
    } catch (error) {
      console.error('Error loading solde global:', error);
    }
  }

  async getSoldeGlobalActuel(): Promise<number> {
    try {
      const solde = await this.api.get<number>('/solde/global').toPromise();
      return solde || 0;
    } catch (error) {
      console.error('Error getting solde global actuel:', error);
      return 0;
    }
  }

  async initialiserSoldeDepart(montant: number, dateDebut?: string): Promise<void> {
    try {
      let url = `/solde/initial?montant=${montant}`;
      if (dateDebut) {
        url += `&dateDebut=${dateDebut}`;
      }
      const solde = await this.api.put<SoldeGlobal>(url, {}).toPromise();
      if (solde) {
        this.soldeGlobal.set(solde);
        this.showToast('Solde de départ initialisé avec succès', 'success');
      }
    } catch (error) {
      console.error('Error initializing solde depart:', error);
      this.showToast('Erreur lors de l\'initialisation du solde de départ', 'error');
      throw error;
    }
  }

  async ajouterApportExterne(montant: number, motif: string, date?: string): Promise<void> {
    try {
      let url = `/solde/apport-externe?montant=${montant}&motif=${encodeURIComponent(motif)}`;
      if (date) {
        url += `&date=${date}`;
      }
      const historique = await this.api.post<HistoriqueSolde>(url, {}).toPromise();
      if (historique) {
        // Recharger le solde global pour mettre à jour l'affichage
        await this.loadSoldeGlobal();
        this.showToast(`Apport externe de ${montant.toFixed(2)} MAD ajouté avec succès`, 'success');
      }
    } catch (error) {
      console.error('Error adding apport externe:', error);
      this.showToast('Erreur lors de l\'ajout de l\'apport externe', 'error');
      throw error;
    }
  }

  async getSoldePartenaire(type: string, id: string): Promise<number> {
    try {
      const solde = await this.api.get<number>(`/solde/partenaire/${type}/${id}`).toPromise();
      return solde || 0;
    } catch (error) {
      console.error('Error getting solde partenaire:', error);
      return 0;
    }
  }

  // --- PRÉVISIONS DE TRÉSORERIE ---
  async loadPrevisionTresorerie(from?: Date, to?: Date): Promise<void> {
    try {
      const params: Record<string, string> = {};
      if (from) {
        params.from = from.toISOString().split('T')[0];
      }
      if (to) {
        params.to = to.toISOString().split('T')[0];
      }
      
      let url = '/prevision/tresorerie';
      const queryString = new URLSearchParams(params).toString();
      if (queryString) {
        url += '?' + queryString;
      }
      
      const response = await this.api.get<PrevisionTresorerieResponse>(url).toPromise();
      if (response) {
        this.previsionTresorerie.set(response);
      }
    } catch (error) {
      console.error('Error loading prevision tresorerie:', error);
      this.showToast('Erreur lors du chargement de la prévision de trésorerie', 'error');
    }
  }

  async addPrevision(factureId: string, type: 'vente' | 'achat', prevision: PrevisionPaiement): Promise<PrevisionPaiement> {
    try {
      const endpoint = type === 'vente' 
        ? `/prevision/facture-vente/${factureId}`
        : `/prevision/facture-achat/${factureId}`;
      
      const saved = await this.api.post<PrevisionPaiement>(endpoint, prevision).toPromise();
      
      if (saved) {
        this.showToast('Prévision ajoutée avec succès', 'success');
        // Mettre à jour localement la facture au lieu de recharger toutes les factures
        this.invoices.update(invoices => {
          const invoiceIndex = invoices.findIndex(inv => inv.id === factureId);
          if (invoiceIndex !== -1) {
            const updatedInvoice = { ...invoices[invoiceIndex] };
            if (!updatedInvoice.previsionsPaiement) {
              updatedInvoice.previsionsPaiement = [];
            }
            updatedInvoice.previsionsPaiement = [...updatedInvoice.previsionsPaiement, saved];
            const newInvoices = [...invoices];
            newInvoices[invoiceIndex] = updatedInvoice;
            return newInvoices;
          }
          return invoices;
        });
      }
      return saved!;
    } catch (error) {
      console.error('Error adding prevision:', error);
      this.showToast('Erreur lors de l\'ajout de la prévision', 'error');
      throw error;
    }
  }

  async updatePrevision(factureId: string, previsionId: string, type: 'vente' | 'achat', prevision: PrevisionPaiement): Promise<PrevisionPaiement> {
    try {
      const saved = await this.api.put<PrevisionPaiement>(
        `/prevision/${factureId}/${previsionId}?type=${type}`, 
        prevision
      ).toPromise();
      
      if (saved) {
        this.showToast('Prévision modifiée avec succès', 'success');
        // Mettre à jour localement la facture au lieu de recharger toutes les factures
        this.invoices.update(invoices => {
          const invoiceIndex = invoices.findIndex(inv => inv.id === factureId);
          if (invoiceIndex !== -1) {
            const updatedInvoice = { ...invoices[invoiceIndex] };
            if (!updatedInvoice.previsionsPaiement) {
              updatedInvoice.previsionsPaiement = [];
            }
            updatedInvoice.previsionsPaiement = updatedInvoice.previsionsPaiement.map(p => 
              p.id === previsionId ? saved : p
            );
            const newInvoices = [...invoices];
            newInvoices[invoiceIndex] = updatedInvoice;
            return newInvoices;
          }
          return invoices;
        });
      }
      return saved!;
    } catch (error) {
      console.error('Error updating prevision:', error);
      this.showToast('Erreur lors de la modification de la prévision', 'error');
      throw error;
    }
  }

  async deletePrevision(factureId: string, previsionId: string, type: 'vente' | 'achat'): Promise<void> {
    try {
      await this.api.delete(`/prevision/${factureId}/${previsionId}?type=${type}`).toPromise();
      this.showToast('Prévision supprimée avec succès', 'success');
      // Recharger les factures pour mettre à jour les prévisions
      await this.loadInvoices();
    } catch (error) {
      console.error('Error deleting prevision:', error);
      this.showToast('Erreur lors de la suppression de la prévision', 'error');
      throw error;
    }
  }

  async loadHistoriqueSolde(
    partenaireId?: string,
    partenaireType?: string,
    type?: string,
    dateDebut?: string,
    dateFin?: string
  ): Promise<void> {
    try {
      const params: any = {};
      if (partenaireId) params.partenaireId = partenaireId;
      if (partenaireType) params.partenaireType = partenaireType;
      if (type) params.type = type;
      if (dateDebut) params.dateDebut = dateDebut;
      if (dateFin) params.dateFin = dateFin;
      
      const historique = await this.api.get<HistoriqueSolde[]>('/solde/historique', params).toPromise();
      if (historique) {
        this.historiqueSolde.set(historique);
      }
    } catch (error) {
      console.error('Error loading historique solde:', error);
    }
  }

  // Vérification des rappels de paiement
  private reminderNotificationsCache = new Set<string>(); // Cache pour éviter les doublons

  async checkPaymentReminders(): Promise<void> {
    const today = new Date().toISOString().split('T')[0];
    const invoices = this.invoices();
    
    for (const invoice of invoices) {
      if (!invoice.previsionsPaiement || invoice.previsionsPaiement.length === 0) continue;
      
      for (const prevision of invoice.previsionsPaiement) {
        // Vérifier si le rappel est aujourd'hui et que la prévision n'est pas réalisée
        if (prevision.dateRappel === today && 
            prevision.statut !== 'REALISE' && 
            prevision.dateRappel) {
          
          // Créer une clé unique pour ce rappel
          const reminderKey = `${invoice.id}-${prevision.id || prevision.datePrevue}-${prevision.dateRappel}`;
          
          // Vérifier si on a déjà créé une notification pour ce rappel
          if (!this.reminderNotificationsCache.has(reminderKey)) {
            const partnerName = invoice.type === 'sale' 
              ? this.getClientName(invoice.partnerId || '')
              : this.getSupplierName(invoice.partnerId || '');
            
            this.addNotification({
              title: 'Rappel: Paiement prévu',
              message: `Facture ${invoice.number} - ${partnerName}: ${prevision.montantPrevu.toFixed(2)} MAD prévu le ${prevision.datePrevue}`,
              type: 'alert'
            });
            
            // Marquer comme notifié dans le cache
            this.reminderNotificationsCache.add(reminderKey);
          }
        }
      }
    }
  }

  // Helper pour vérifier si une date de rappel est aujourd'hui
  isReminderToday(dateRappel: string | undefined): boolean {
    if (!dateRappel) return false;
    const today = new Date().toISOString().split('T')[0];
    return dateRappel === today;
  }
}
