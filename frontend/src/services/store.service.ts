import { Injectable, signal, computed, inject, effect } from '@angular/core';
import { ApiService } from './api.service';

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
  paymentMode?: string;
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
  partnerId?: string;
  date: string;
  amountHT: number;
  amountTTC: number;
  dueDate: string;
  status: 'paid' | 'pending' | 'overdue';
  type: 'purchase' | 'sale';
  paymentMode?: string;
}

export interface Payment {
  id?: string;
  factureAchatId?: string;
  factureVenteId?: string;
  date: string;
  montant: number;
  mode: string;
  reference?: string;
  notes?: string;
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

@Injectable({
  providedIn: 'root'
})
export class StoreService {
  private api = inject(ApiService);

  // --- NOTIFICATIONS SYSTEM (TOASTS) ---
  readonly toasts = signal<Toast[]>([]);
  private toastCounter = 0;

  showToast(message: string, type: 'success' | 'error' | 'info' = 'success') {
    const id = ++this.toastCounter;
    this.toasts.update(current => [...current, { id, message, type }]);
    setTimeout(() => this.removeToast(id), 4000);
  }

  removeToast(id: number) {
    this.toasts.update(current => current.filter(t => t.id !== id));
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
  readonly dashboardKPIs = signal<DashboardKpiResponse | null>(null);
  readonly dashboardLoading = signal<boolean>(false);
  readonly payments = signal<Map<string, Payment[]>>(new Map()); // Map<invoiceId, Payment[]>
  
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
      const clients = await this.api.get<any[]>('/clients').toPromise() || [];
      this.clients.set(clients.map(c => this.mapClient(c)));
    } catch (error) {
      console.error('Error loading clients:', error);
    }
  }

  private async loadSuppliers(): Promise<void> {
    try {
      const suppliers = await this.api.get<any[]>('/fournisseurs').toPromise() || [];
      this.suppliers.set(suppliers.map(s => this.mapSupplier(s)));
    } catch (error) {
      console.error('Error loading suppliers:', error);
    }
  }

  private async loadProducts(): Promise<void> {
    try {
      const products = await this.api.get<any[]>('/produits').toPromise() || [];
      this.products.set(products.map(p => this.mapProduct(p)));
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
      this.showToast('Donn├®es actualis├®es', 'success');
    } catch (error) {
      console.error('Error refreshing data:', error);
      this.showToast('Erreur lors du rafra├«chissement', 'error');
    } finally {
      this.refreshing.set(false);
    }
  }

  // --- MAPPERS ---
  private mapClient(c: any): Client {
    return {
      id: c.id,
      name: c.nom || c.name,
      ice: c.ice,
      referenceClient: c.referenceClient,
      contact: c.contacts?.[0]?.nom,
      phone: c.telephone || c.phone,
      email: c.email,
      address: c.adresse || c.address
    };
  }

  private mapSupplier(s: any): Supplier {
    return {
      id: s.id,
      name: s.nom || s.name,
      ice: s.ice,
      referenceFournisseur: s.referenceFournisseur,
      contact: s.contact,
      phone: s.telephone || s.phone,
      email: s.email,
      address: s.adresse || s.address,
      dateRegulariteFiscale: s.dateRegulariteFiscale || undefined
    };
  }

  private mapProduct(p: any): Product {
    return {
      id: p.id,
      ref: p.refArticle || p.ref,
      name: p.designation || p.name,
      unit: p.unite || p.unit,
      priceBuyHT: p.prixAchatUnitaireHT || p.priceBuyHT || 0,
      priceSellHT: p.prixVenteUnitaireHT || p.priceSellHT || 0,
      stock: p.quantiteEnStock !== undefined ? p.quantiteEnStock : (p.stock !== undefined ? p.stock : 0)
    };
  }

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
  addPaymentMode(name: string) {
    const newMode: PaymentMode = {
      id: `pm-${Date.now()}`,
      name,
      active: true
    };
    this.paymentModes.update(modes => [...modes, newMode]);
    this.showToast('Mode de paiement ajout├®');
  }

  togglePaymentMode(id: string) {
    this.paymentModes.update(modes => modes.map(m => m.id === id ? { ...m, active: !m.active } : m));
  }

  deletePaymentMode(id: string) {
    this.paymentModes.update(modes => modes.filter(m => m.id !== id));
    this.showToast('Mode supprim├®', 'info');
  }

  // --- ACTIONS: CLIENTS ---
  async addClient(client: Client): Promise<void> {
    try {
      const payload = {
        nom: client.name,
        ice: client.ice,
        referenceClient: client.referenceClient || null,
        telephone: client.phone,
        email: client.email,
        adresse: client.address,
        contacts: client.contact ? [{ nom: client.contact }] : []
      };
      
      const created = await this.api.post<any>('/clients', payload).toPromise();
      const mapped = this.mapClient(created);
      this.clients.update(list => [mapped, ...list]);
      this.showToast('Client ajout├® avec succ├¿s');
      this.addNotification({ title: 'Nouveau Client', message: `Client ${client.name} ajout├®.`, type: 'info' });
    } catch (error) {
      this.showToast('Erreur lors de l\'ajout du client', 'error');
      throw error;
    }
  }

  async updateClient(client: Client): Promise<void> {
    try {
      const payload = {
        nom: client.name,
        ice: client.ice,
        referenceClient: client.referenceClient || null,
        telephone: client.phone,
        email: client.email,
        adresse: client.address
      };
      
      const updated = await this.api.put<any>(`/clients/${client.id}`, payload).toPromise();
      const mapped = this.mapClient(updated);
      this.clients.update(list => list.map(c => c.id === client.id ? mapped : c));
      this.showToast('Fiche client mise ├á jour');
    } catch (error) {
      this.showToast('Erreur lors de la mise ├á jour', 'error');
      throw error;
    }
  }

  async deleteClient(id: string): Promise<boolean> {
    try {
      await this.api.delete(`/clients/${id}`).toPromise();
      this.clients.update(list => list.filter(c => c.id !== id));
      this.showToast('Client supprim├®', 'info');
      return true;
    } catch (error) {
      this.showToast('Erreur lors de la suppression', 'error');
      throw error;
    }
  }

  // --- ACTIONS: SUPPLIERS ---
  async addSupplier(supplier: Supplier): Promise<void> {
    try {
      const payload = {
        nom: supplier.name,
        ice: supplier.ice,
        referenceFournisseur: supplier.referenceFournisseur || null,
        contact: supplier.contact || null,
        telephone: supplier.phone,
        email: supplier.email,
        adresse: supplier.address,
        modesPaiementAcceptes: ['virement', 'cheque', 'LCN', 'compensation'],
        dateRegulariteFiscale: supplier.dateRegulariteFiscale || null
      };
      
      const created = await this.api.post<any>('/fournisseurs', payload).toPromise();
      const mapped = this.mapSupplier(created);
      this.suppliers.update(list => [mapped, ...list]);
      this.showToast('Fournisseur ajout├® avec succ├¿s');
    } catch (error) {
      this.showToast('Erreur lors de l\'ajout du fournisseur', 'error');
      throw error;
    }
  }

  async updateSupplier(supplier: Supplier): Promise<void> {
    try {
      const payload = {
        nom: supplier.name,
        ice: supplier.ice,
        referenceFournisseur: supplier.referenceFournisseur || null,
        contact: supplier.contact || null,
        telephone: supplier.phone,
        email: supplier.email,
        adresse: supplier.address,
        dateRegulariteFiscale: supplier.dateRegulariteFiscale || null
      };
      
      const updated = await this.api.put<any>(`/fournisseurs/${supplier.id}`, payload).toPromise();
      const mapped = this.mapSupplier(updated);
      this.suppliers.update(list => list.map(s => s.id === supplier.id ? mapped : s));
      this.showToast('Fiche fournisseur mise ├á jour');
    } catch (error) {
      this.showToast('Erreur lors de la mise ├á jour', 'error');
      throw error;
    }
  }

  async deleteSupplier(id: string): Promise<boolean> {
    try {
      await this.api.delete(`/fournisseurs/${id}`).toPromise();
      this.suppliers.update(list => list.filter(s => s.id !== id));
      this.showToast('Fournisseur supprim├®', 'info');
      return true;
    } catch (error) {
      this.showToast('Erreur lors de la suppression', 'error');
      throw error;
    }
  }

  // --- ACTIONS: PRODUCTS ---
  async addProduct(product: Product): Promise<void> {
    try {
      const payload = {
        refArticle: product.ref,
        designation: product.name,
        unite: product.unit,
        prixAchatUnitaireHT: product.priceBuyHT,
        prixVenteUnitaireHT: product.priceSellHT,
        quantiteEnStock: product.stock !== undefined ? product.stock : 0,
        tva: 20.0
      };
      
      const created = await this.api.post<any>('/produits', payload).toPromise();
      const mapped = this.mapProduct(created);
      this.products.update(list => [mapped, ...list]);
      this.showToast('Produit ajout├® au catalogue');
    } catch (error) {
      this.showToast('Erreur lors de l\'ajout du produit', 'error');
      throw error;
    }
  }

  async updateProduct(product: Product): Promise<void> {
    try {
      const payload = {
        refArticle: product.ref,
        designation: product.name,
        unite: product.unit,
        prixAchatUnitaireHT: product.priceBuyHT,
        prixVenteUnitaireHT: product.priceSellHT,
        quantiteEnStock: product.stock !== undefined ? product.stock : 0
      };
      
      const updated = await this.api.put<any>(`/produits/${product.id}`, payload).toPromise();
      const mapped = this.mapProduct(updated);
      this.products.update(list => list.map(p => p.id === product.id ? mapped : p));
      this.showToast('Produit mis ├á jour');
    } catch (error) {
      this.showToast('Erreur lors de la mise ├á jour', 'error');
      throw error;
    }
  }

  async deleteProduct(id: string): Promise<boolean> {
    try {
      await this.api.delete(`/produits/${id}`).toPromise();
      this.products.update(list => list.filter(p => p.id !== id));
      this.showToast('Produit retir├® du catalogue', 'info');
      return true;
    } catch (error) {
      this.showToast('Erreur lors de la suppression', 'error');
      throw error;
    }
  }

  // --- ACTIONS: BC ---
  async loadBCs(): Promise<void> {
    try {
      const bcs = await this.api.get<any[]>('/bandes-commandes').toPromise() || [];
      this.bcs.set(bcs.map(bc => this.mapBC(bc)));
    } catch (error) {
      console.error('Error loading BCs:', error);
    }
  }

  async addBC(bc: BC): Promise<void> {
    try {
      const payload: any = {
        // Ne pas envoyer numeroBC si vide - le backend le g├®n├®rera avec la nouvelle logique
        ...(bc.number && bc.number.trim() ? { numeroBC: bc.number } : {}),
        dateBC: bc.date,
        fournisseurId: bc.supplierId,
        etat: bc.status
      };
      
      if (bc.ajouterAuStock !== undefined) {
        payload.ajouterAuStock = bc.ajouterAuStock;
      }
      
      // Nouvelle structure multi-clients
      if (bc.lignesAchat && bc.lignesAchat.length > 0) {
        payload.lignesAchat = bc.lignesAchat.map(l => ({
          produitRef: l.produitRef,
          designation: l.designation,
          unite: l.unite,
          quantiteAchetee: l.quantiteAchetee,
          prixAchatUnitaireHT: l.prixAchatUnitaireHT,
          tva: l.tva
        }));
      }
      
      if (bc.clientsVente && bc.clientsVente.length > 0) {
        payload.clientsVente = bc.clientsVente.map(cv => ({
          clientId: cv.clientId,
          lignesVente: (cv.lignesVente || []).map(lv => ({
            produitRef: lv.produitRef,
            designation: lv.designation,
            unite: lv.unite,
            quantiteVendue: lv.quantiteVendue,
            prixVenteUnitaireHT: lv.prixVenteUnitaireHT,
            tva: lv.tva
          }))
        }));
      }
      
      // R├®trocompatibilit├® ancienne structure
      if (bc.clientId) {
        payload.clientId = bc.clientId;
      }
      if (bc.items && bc.items.length > 0) {
        payload.lignes = bc.items.map(item => ({
          produitRef: item.ref,
          designation: item.name,
          unite: item.unit,
          quantiteAchetee: item.qtyBuy,
          quantiteVendue: item.qtySell,
          prixAchatUnitaireHT: item.priceBuyHT,
          prixVenteUnitaireHT: item.priceSellHT,
          tva: item.tvaRate
        }));
      }
      
      if (bc.paymentMode) {
        payload.modePaiement = bc.paymentMode;
      }
      
      const created = await this.api.post<any>('/bandes-commandes', payload).toPromise();
      console.log('­ƒöÁ BC cr├®├® par le backend:', created);
      console.log('­ƒöÁ totalAchatHT renvoy├®:', created?.totalAchatHT);
      const mapped = this.mapBC(created);
      console.log('­ƒöÁ BC mapp├®:', mapped);
      console.log('­ƒöÁ totalAchatHT mapp├®:', mapped.totalAchatHT);
      this.bcs.update(list => [mapped, ...list]);
      
      // Recharger les produits si le stock a ├®t├® mis ├á jour
      if (bc.ajouterAuStock) {
        await this.loadProducts();
      }
      
      this.showToast('Commande cr├®├®e avec succ├¿s', 'success');
      this.addNotification({ title: 'Nouvelle Commande', message: `BC ${bc.number} cr├®├®.`, type: 'success' });
    } catch (error) {
      this.showToast('Erreur lors de la cr├®ation de la commande', 'error');
      throw error;
    }
  }

  async updateBC(updatedBc: BC): Promise<void> {
    try {
      const payload: any = {
        numeroBC: updatedBc.number,
        dateBC: updatedBc.date,
        fournisseurId: updatedBc.supplierId,
        etat: updatedBc.status
      };
      
      if (updatedBc.ajouterAuStock !== undefined) {
        payload.ajouterAuStock = updatedBc.ajouterAuStock;
      }
      
      // Nouvelle structure multi-clients
      if (updatedBc.lignesAchat && updatedBc.lignesAchat.length > 0) {
        payload.lignesAchat = updatedBc.lignesAchat.map(l => ({
          produitRef: l.produitRef,
          designation: l.designation,
          unite: l.unite,
          quantiteAchetee: l.quantiteAchetee,
          prixAchatUnitaireHT: l.prixAchatUnitaireHT,
          tva: l.tva
        }));
      }
      
      if (updatedBc.clientsVente && updatedBc.clientsVente.length > 0) {
        payload.clientsVente = updatedBc.clientsVente.map(cv => ({
          clientId: cv.clientId,
          lignesVente: (cv.lignesVente || []).map(lv => ({
            produitRef: lv.produitRef,
            designation: lv.designation,
            unite: lv.unite,
            quantiteVendue: lv.quantiteVendue,
            prixVenteUnitaireHT: lv.prixVenteUnitaireHT,
            tva: lv.tva
          }))
        }));
      }
      
      // R├®trocompatibilit├® ancienne structure
      if (updatedBc.clientId) {
        payload.clientId = updatedBc.clientId;
      }
      if (updatedBc.items && updatedBc.items.length > 0) {
        payload.lignes = updatedBc.items.map(item => ({
          produitRef: item.ref,
          designation: item.name,
          unite: item.unit,
          quantiteAchetee: item.qtyBuy,
          quantiteVendue: item.qtySell,
          prixAchatUnitaireHT: item.priceBuyHT,
          prixVenteUnitaireHT: item.priceSellHT,
          tva: item.tvaRate
        }));
      }
      
      if (updatedBc.paymentMode) {
        payload.modePaiement = updatedBc.paymentMode;
      }
      
      const updated = await this.api.put<any>(`/bandes-commandes/${updatedBc.id}`, payload).toPromise();
      const mapped = this.mapBC(updated);
      this.bcs.update(list => list.map(b => b.id === updatedBc.id ? mapped : b));
      
      // Recharger les produits si le stock a ├®t├® mis ├á jour
      if (updatedBc.ajouterAuStock) {
        await this.loadProducts();
      }
      this.showToast('Commande mise ├á jour', 'success');
    } catch (error) {
      this.showToast('Erreur lors de la mise ├á jour', 'error');
      throw error;
    }
  }

  async deleteBC(id: string): Promise<boolean> {
    try {
      await this.api.delete(`/bandes-commandes/${id}`).toPromise();
      this.bcs.update(list => list.filter(b => b.id !== id));
      this.showToast('Commande supprim├®e', 'info');
      return true;
    } catch (error) {
      this.showToast('Erreur lors de la suppression', 'error');
      throw error;
    }
  }

  private mapBC(bc: any): BC {
    // Mapper lignesAchat (nouvelle structure)
    const lignesAchat: LigneAchat[] = bc.lignesAchat ? bc.lignesAchat.map((l: any) => ({
      produitRef: l.produitRef || '',
      designation: l.designation || '',
      unite: l.unite || 'U',
      quantiteAchetee: l.quantiteAchetee || 0,
      prixAchatUnitaireHT: l.prixAchatUnitaireHT || 0,
      tva: l.tva || 20,
      totalHT: l.totalHT,
      totalTTC: l.totalTTC
    })) : [];
    
    // Mapper clientsVente (nouvelle structure)
    const clientsVente: ClientVente[] = bc.clientsVente ? bc.clientsVente.map((cv: any) => ({
      clientId: cv.clientId || '',
      lignesVente: (cv.lignesVente || []).map((lv: any) => ({
        produitRef: lv.produitRef || '',
        designation: lv.designation || '',
        unite: lv.unite || 'U',
        quantiteVendue: lv.quantiteVendue || 0,
        prixVenteUnitaireHT: lv.prixVenteUnitaireHT || 0,
        tva: lv.tva || 20,
        totalHT: lv.totalHT,
        totalTTC: lv.totalTTC,
        margeUnitaire: lv.margeUnitaire,
        margePourcentage: lv.margePourcentage
      }))
    })) : [];
    
    // Mapper items (ancienne structure pour r├®trocompatibilit├®)
    const items: LineItem[] = (bc.lignes || bc.items || []).map((item: any) => ({
      productId: item.productId || '',
      ref: item.produitRef || item.ref,
      name: item.designation || item.name,
      unit: item.unite || item.unit,
      qtyBuy: item.quantiteAchetee || item.qtyBuy,
      qtySell: item.quantiteVendue || item.qtySell,
      priceBuyHT: item.prixAchatUnitaireHT || item.priceBuyHT,
      priceSellHT: item.prixVenteUnitaireHT || item.priceSellHT,
      tvaRate: item.tva || item.tvaRate
    }));
    
    return {
      id: bc.id,
      number: bc.numeroBC || bc.number,
      date: bc.dateBC || bc.date,
      clientId: bc.clientId, // R├®trocompatibilit├®
      supplierId: bc.fournisseurId || bc.supplierId,
      ajouterAuStock: bc.ajouterAuStock || false,
      status: bc.etat || bc.status,
      paymentMode: bc.modePaiement || bc.paymentMode,
      items: items, // R├®trocompatibilit├®
      // Nouvelle structure
      lignesAchat: lignesAchat,
      clientsVente: clientsVente,
      // Totaux
      totalAchatHT: bc.totalAchatHT,
      totalAchatTTC: bc.totalAchatTTC,
      totalVenteHT: bc.totalVenteHT,
      totalVenteTTC: bc.totalVenteTTC,
      margeTotale: bc.margeTotale,
      margePourcentage: bc.margePourcentage
    };
  }

  // --- ACTIONS: INVOICES ---
  async loadInvoices(): Promise<void> {
    try {
      const purchaseInvoices = await this.api.get<any[]>('/factures-achats').toPromise() || [];
      const salesInvoices = await this.api.get<any[]>('/factures-ventes').toPromise() || [];
      
      const allInvoices: Invoice[] = [
        ...purchaseInvoices.map(inv => this.mapInvoice(inv, 'purchase')),
        ...salesInvoices.map(inv => this.mapInvoice(inv, 'sale'))
      ];
      
      this.invoices.set(allInvoices);
    } catch (error) {
      console.error('Error loading invoices:', error);
    }
  }

  async addInvoice(inv: Invoice): Promise<void> {
    console.log('­ƒƒí store.addInvoice - D├ëBUT');
    console.log('­ƒƒí store.addInvoice - Invoice re├ºue:', inv);
    console.log('­ƒƒí store.addInvoice - Montants re├ºus:', {
      amountHT: inv.amountHT,
      amountTTC: inv.amountTTC,
      'amountHT type': typeof inv.amountHT,
      'amountTTC type': typeof inv.amountTTC
    });
    
    try {
      // S'assurer que les montants sont des nombres
      const amountHT = inv.amountHT != null && inv.amountHT !== undefined ? Number(inv.amountHT) : 0;
      const amountTTC = inv.amountTTC != null && inv.amountTTC !== undefined ? Number(inv.amountTTC) : 0;

      console.log('­ƒƒí store.addInvoice - Montants convertis:', {
        amountHT,
        amountTTC,
        'amountHT type': typeof amountHT,
        'amountTTC type': typeof amountTTC
      });

      if (inv.type === 'purchase') {
        const payload: any = {
          numeroFactureAchat: inv.number,
          dateFacture: inv.date,
          bandeCommandeId: inv.bcId || null,
          fournisseurId: inv.partnerId,
          totalHT: amountHT,
          totalTTC: amountTTC,
          modePaiement: inv.paymentMode || null,
          etatPaiement: inv.status === 'paid' ? 'regle' : 'non_regle'
        };
        
        // Ajouter l'option ajouterAuStock si pr├®sente
        if ((inv as any).ajouterAuStock !== undefined) {
          payload.ajouterAuStock = (inv as any).ajouterAuStock;
        }
        
        console.log('­ƒƒí store.addInvoice - Payload pour facture achat:', payload);
        console.log('­ƒƒí store.addInvoice - Payload JSON:', JSON.stringify(payload, null, 2));
        
        const created = await this.api.post<any>('/factures-achats', payload).toPromise();
        console.log('­ƒƒí store.addInvoice - R├®ponse backend (facture achat):', created);
        console.log('­ƒƒí store.addInvoice - Montants dans r├®ponse:', {
          totalHT: created?.totalHT,
          totalTTC: created?.totalTTC,
          'totalHT type': typeof created?.totalHT,
          'totalTTC type': typeof created?.totalTTC
        });
        
        const mapped = this.mapInvoice(created, 'purchase');
        console.log('­ƒƒí store.addInvoice - Invoice mapp├®e (facture achat):', mapped);
        console.log('­ƒƒí store.addInvoice - Montants dans invoice mapp├®e:', {
          amountHT: mapped.amountHT,
          amountTTC: mapped.amountTTC
        });
        
        this.invoices.update(list => [mapped, ...list]);
      } else {
        const payload = {
          // Ne pas envoyer numeroFactureVente si vide - le backend le g├®n├®rera avec la nouvelle logique
          ...(inv.number && inv.number.trim() ? { numeroFactureVente: inv.number } : {}),
          dateFacture: inv.date,
          bandeCommandeId: inv.bcId || null,
          clientId: inv.partnerId,
          totalHT: amountHT,
          totalTTC: amountTTC,
          modePaiement: inv.paymentMode || null,
          etatPaiement: inv.status === 'paid' ? 'regle' : 'non_regle'
        };
        
        console.log('­ƒƒí store.addInvoice - Payload pour facture vente:', payload);
        console.log('­ƒƒí store.addInvoice - Payload JSON:', JSON.stringify(payload, null, 2));
        
        const created = await this.api.post<any>('/factures-ventes', payload).toPromise();
        console.log('­ƒƒí store.addInvoice - R├®ponse backend (facture vente):', created);
        console.log('­ƒƒí store.addInvoice - Montants dans r├®ponse:', {
          totalHT: created?.totalHT,
          totalTTC: created?.totalTTC,
          'totalHT type': typeof created?.totalHT,
          'totalTTC type': typeof created?.totalTTC
        });
        
        const mapped = this.mapInvoice(created, 'sale');
        console.log('­ƒƒí store.addInvoice - Invoice mapp├®e (facture vente):', mapped);
        console.log('­ƒƒí store.addInvoice - Montants dans invoice mapp├®e:', {
          amountHT: mapped.amountHT,
          amountTTC: mapped.amountTTC
        });
        
        this.invoices.update(list => [mapped, ...list]);
      }
      
      this.showToast(inv.type === 'sale' ? 'Facture vente ├®mise' : 'Facture achat enregistr├®e', 'success');
      this.addNotification({ 
        title: inv.type === 'sale' ? 'Facture Vente' : 'Facture Achat', 
        message: `${inv.number} enregistr├®e pour ${amountTTC} MAD.`, 
        type: 'info' 
      });
      
      console.log('­ƒƒí store.addInvoice - FIN - Succ├¿s');
    } catch (error) {
      console.error('ÔØî store.addInvoice - ERREUR:', error);
      this.showToast('Erreur lors de l\'enregistrement de la facture', 'error');
      throw error;
    }
  }

  async updateInvoice(inv: Invoice): Promise<void> {
    try {
      if (inv.type === 'purchase') {
        // R├®cup├®rer la facture existante pour pr├®server les montants si non fournis
        const existingInvoice = this.invoices().find(i => i.id === inv.id);
        
        // Utiliser les valeurs fournies dans inv (qui viennent de originalInvoice dans le composant)
        // Si elles sont null/undefined, utiliser les valeurs existantes
        // Note: on accepte 0 comme valeur valide
        const amountHT = (inv.amountHT != null && inv.amountHT !== undefined) 
          ? Number(inv.amountHT) 
          : (existingInvoice && existingInvoice.amountHT != null && existingInvoice.amountHT !== undefined ? Number(existingInvoice.amountHT) : 0);
        const amountTTC = (inv.amountTTC != null && inv.amountTTC !== undefined) 
          ? Number(inv.amountTTC) 
          : (existingInvoice && existingInvoice.amountTTC != null && existingInvoice.amountTTC !== undefined ? Number(existingInvoice.amountTTC) : 0);
        
        const payload: any = {
          numeroFactureAchat: inv.number || existingInvoice?.number,
          dateFacture: inv.date || existingInvoice?.date,
          bandeCommandeId: inv.bcId || existingInvoice?.bcId || null,
          fournisseurId: inv.partnerId || existingInvoice?.partnerId,
          totalHT: amountHT,
          totalTTC: amountTTC,
          modePaiement: inv.paymentMode || existingInvoice?.paymentMode || null,
          etatPaiement: inv.status === 'paid' ? 'regle' : (inv.status === 'overdue' ? 'non_regle' : 'non_regle')
        };
        
        // Ajouter l'option ajouterAuStock si pr├®sente
        if ((inv as any).ajouterAuStock !== undefined) {
          payload.ajouterAuStock = (inv as any).ajouterAuStock;
        }
        
        // Ne pas envoyer les lignes pour pr├®server les totaux existants
        // Les lignes seront pr├®serv├®es dans le backend si non fournies
        
        const updated = await this.api.put<any>(`/factures-achats/${inv.id}`, payload).toPromise();
        console.log('🟢 store.updateInvoice - Facture retournée par backend:', updated);
        console.log('🟢 store.updateInvoice - Champs calculés dans updated:', {
          tvaMois: updated?.tvaMois,
          solde: updated?.solde,
          totalTTCApresRG: updated?.totalTTCApresRG,
          totalTTCApresRG_SIGNE: updated?.totalTTCApresRG_SIGNE,
          rgTTC: updated?.rgTTC,
          rgHT: updated?.rgHT,
          factureHT_YC_RG: updated?.factureHT_YC_RG,
          bilan: updated?.bilan,
          typeMouvement: updated?.typeMouvement,
          nature: updated?.nature,
          bcReference: updated?.bcReference
        });
        const mapped = this.mapInvoice(updated, 'purchase');
        this.invoices.update(list => list.map(item => item.id === inv.id ? mapped : item));
      } else {
        console.log('­ƒƒí store.updateInvoice - Type: sale');
        console.log('­ƒƒí store.updateInvoice - Invoice re├ºue:', inv);
        console.log('­ƒƒí store.updateInvoice - Montants re├ºus:', { 
          amountHT: inv.amountHT, 
          amountTTC: inv.amountTTC,
          amountHTType: typeof inv.amountHT,
          amountTTCType: typeof inv.amountTTC
        });
        
        // R├®cup├®rer la facture existante pour pr├®server les montants si non fournis
        const existingInvoice = this.invoices().find(i => i.id === inv.id);
        console.log('­ƒƒí store.updateInvoice - Facture existante trouv├®e:', existingInvoice);
        if (existingInvoice) {
          console.log('­ƒƒí store.updateInvoice - Montants existants:', { 
            amountHT: existingInvoice.amountHT, 
            amountTTC: existingInvoice.amountTTC 
          });
        }
        
        // Utiliser les valeurs fournies dans inv (qui viennent de originalInvoice dans le composant)
        // Si elles sont null/undefined, utiliser les valeurs existantes
        // Note: on accepte 0 comme valeur valide
        const amountHT = (inv.amountHT != null && inv.amountHT !== undefined) 
          ? Number(inv.amountHT) 
          : (existingInvoice && existingInvoice.amountHT != null && existingInvoice.amountHT !== undefined ? Number(existingInvoice.amountHT) : 0);
        const amountTTC = (inv.amountTTC != null && inv.amountTTC !== undefined) 
          ? Number(inv.amountTTC) 
          : (existingInvoice && existingInvoice.amountTTC != null && existingInvoice.amountTTC !== undefined ? Number(existingInvoice.amountTTC) : 0);
        
        console.log('­ƒƒí store.updateInvoice - Montants calcul├®s:', { 
          amountHT, 
          amountTTC,
          amountHTType: typeof amountHT,
          amountTTCType: typeof amountTTC
        });
        
        const payload: any = {
          // Ne pas envoyer numeroFactureVente si vide (sauf en mode ├®dition o├╣ on garde l'existant)
          ...(inv.number && inv.number.trim() ? { numeroFactureVente: inv.number } : 
              (existingInvoice?.number ? { numeroFactureVente: existingInvoice.number } : {})),
          dateFacture: inv.date || existingInvoice?.date,
          bandeCommandeId: inv.bcId || existingInvoice?.bcId || null,
          clientId: inv.partnerId || existingInvoice?.partnerId,
          totalHT: amountHT,
          totalTTC: amountTTC,
          modePaiement: inv.paymentMode || existingInvoice?.paymentMode || null,
          etatPaiement: inv.status === 'paid' ? 'regle' : (inv.status === 'overdue' ? 'non_regle' : 'non_regle')
        };
        
        console.log('­ƒƒí store.updateInvoice - Payload ├á envoyer au backend:', payload);
        console.log('­ƒƒí store.updateInvoice - Payload JSON stringifi├®:', JSON.stringify(payload, null, 2));
        console.log('­ƒƒí store.updateInvoice - Montants dans payload:', { 
          totalHT: payload.totalHT, 
          totalTTC: payload.totalTTC,
          'payload.totalHT type': typeof payload.totalHT,
          'payload.totalTTC type': typeof payload.totalTTC
        });
        
        // Ne pas envoyer les lignes pour pr├®server les totaux existants
        // Les lignes seront pr├®serv├®es dans le backend si non fournies
        
        const updated = await this.api.put<any>(`/factures-ventes/${inv.id}`, payload).toPromise();
        console.log('­ƒƒí store.updateInvoice - R├®ponse compl├¿te du backend:', JSON.stringify(updated, null, 2));
        console.log('­ƒƒí store.updateInvoice - Montants dans la r├®ponse:', { 
          totalHT: updated?.totalHT, 
          totalTTC: updated?.totalTTC,
          'updated?.totalHT type': typeof updated?.totalHT,
          'updated?.totalTTC type': typeof updated?.totalTTC
        });
        
        // Log pour v├®rifier si les valeurs sont ailleurs dans la r├®ponse
        console.log('­ƒƒí store.updateInvoice - Tous les champs num├®riques:', {
          totalHT: updated?.totalHT,
          totalTTC: updated?.totalTTC,
          totalTVA: updated?.totalTVA,
          montantRestant: updated?.montantRestant
        });
        
        const mapped = this.mapInvoice(updated, 'sale');
        console.log('­ƒƒí store.updateInvoice - Invoice mapp├®e:', mapped);
        console.log('­ƒƒí store.updateInvoice - Montants dans invoice mapp├®e:', { 
          amountHT: mapped.amountHT, 
          amountTTC: mapped.amountTTC 
        });
        
        this.invoices.update(list => list.map(item => item.id === inv.id ? mapped : item));
        console.log('­ƒƒí store.updateInvoice - Liste mise ├á jour');
      }
      
      this.showToast('Facture mise ├á jour avec succ├¿s', 'success');
    } catch (error) {
      console.error('Error updating invoice:', error);
      this.showToast('Erreur lors de la mise ├á jour de la facture', 'error');
      throw error;
    }
  }

  async deleteInvoice(id: string): Promise<boolean> {
    try {
      // Trouver la facture pour d├®terminer son type
      const invoice = this.invoices().find(inv => inv.id === id);
      if (!invoice) {
        this.showToast('Facture non trouv├®e', 'error');
        return false;
      }
      
      // Appeler l'API appropri├®e selon le type
      if (invoice.type === 'purchase') {
        await this.api.delete(`/factures-achats/${id}`).toPromise();
      } else {
        await this.api.delete(`/factures-ventes/${id}`).toPromise();
      }
      
      // Mettre ├á jour la liste locale
      this.invoices.update(list => list.filter(inv => inv.id !== id));
      this.showToast('Facture supprim├®e', 'info');
      return true;
    } catch (error) {
      this.showToast('Erreur lors de la suppression', 'error');
      throw error;
    }
  }

  private mapInvoice(inv: any, type: 'purchase' | 'sale'): Invoice {
    console.log('­ƒƒú store.mapInvoice - D├ëBUT mapping', type);
    console.log('­ƒƒú store.mapInvoice - Objet inv re├ºu:', inv);
    console.log('­ƒƒú store.mapInvoice - Tous les champs num├®riques dans inv:', {
      totalHT: inv?.totalHT,
      totalTTC: inv?.totalTTC,
      amountHT: inv?.amountHT,
      amountTTC: inv?.amountTTC,
      montantHT: inv?.montantHT,
      montantTTC: inv?.montantTTC,
      'totalHT type': typeof inv?.totalHT,
      'totalTTC type': typeof inv?.totalTTC
    });
    
    const today = new Date().toISOString().split('T')[0];
    const dueDate = inv.dateEcheance || inv.dueDate || today;
    
    // Mapper le statut depuis le backend vers le frontend
    let status: 'paid' | 'pending' | 'overdue' = 'pending';
    if (inv.etatPaiement === 'regle') {
      status = 'paid';
    } else if (inv.etatPaiement === 'partiellement_regle') {
      // Si partiellement pay├®, on consid├¿re comme pending
      status = dueDate < today ? 'overdue' : 'pending';
    } else if (inv.etatPaiement === 'non_regle') {
      status = dueDate < today ? 'overdue' : 'pending';
    } else {
      // Si pas de statut d├®fini, d├®terminer selon la date d'├®ch├®ance
      status = dueDate < today ? 'overdue' : 'pending';
    }
    
    // Extraire les montants avec plusieurs noms possibles et g├®rer les valeurs null/undefined
    const amountHT = (inv.totalHT != null && inv.totalHT !== undefined) ? Number(inv.totalHT) : 
                     (inv.amountHT != null && inv.amountHT !== undefined) ? Number(inv.amountHT) : 
                     (inv.montantHT != null && inv.montantHT !== undefined) ? Number(inv.montantHT) : 0;
    
    const amountTTC = (inv.totalTTC != null && inv.totalTTC !== undefined) ? Number(inv.totalTTC) : 
                      (inv.amountTTC != null && inv.amountTTC !== undefined) ? Number(inv.amountTTC) : 
                      (inv.montantTTC != null && inv.montantTTC !== undefined) ? Number(inv.montantTTC) : 
                      amountHT; // Fallback sur HT si TTC non disponible
    
    console.log('­ƒƒú store.mapInvoice - Montants extraits:', {
      amountHT,
      amountTTC,
      'amountHT type': typeof amountHT,
      'amountTTC type': typeof amountTTC
    });
    
    const mappedInvoice: Invoice = {
      id: inv.id,
      number: inv.numeroFactureAchat || inv.numeroFactureVente || inv.number,
      bcId: inv.bandeCommandeId || inv.bcId || '',
      bcReference: inv.bcReference,
      partnerId: inv.fournisseurId || inv.clientId || inv.partnerId,
      date: inv.dateFacture || inv.date,
      amountHT: amountHT,
      amountTTC: amountTTC,
      dueDate: dueDate,
      status: status,
      type: type,
      paymentMode: inv.modePaiement || inv.paymentMode,
      
      // Champs pour les calculs comptables
      typeMouvement: inv.typeMouvement,
      nature: inv.nature,
      colA: inv.colA,
      colB: inv.colB,
      colD: inv.colD,
      colF: inv.colF,
      tvaRate: inv.tvaRate,
      tauxRG: inv.tauxRG,
      
      // Champs calculés selon les formules Excel
      tvaMois: inv.tvaMois,
      solde: inv.solde,
      totalTTCApresRG: inv.totalTTCApresRG,
      totalTTCApresRG_SIGNE: inv.totalTTCApresRG_SIGNE,
      totalPaiementTTC: inv.totalPaiementTTC,
      rgTTC: inv.rgTTC,
      rgHT: inv.rgHT,
      factureHT_YC_RG: inv.factureHT_YC_RG,
      htPaye: inv.htPaye,
      tvaFactureYcRg: inv.tvaFactureYcRg,
      tvaPaye: inv.tvaPaye,
      bilan: inv.bilan
    };
    
    console.log('­ƒƒú store.mapInvoice - Invoice final mapp├®:', mappedInvoice);
    console.log('­ƒƒú store.mapInvoice - Montants dans invoice final:', {
      amountHT: mappedInvoice.amountHT,
      amountTTC: mappedInvoice.amountTTC
    });
    console.log('­ƒƒú store.mapInvoice - FIN mapping');
    
    return mappedInvoice;
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
            console.log('Web Share API not available or user cancelled, falling back to window.open');
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
      this.showToast('G├®n├®ration du PDF en cours...', 'info');
      const blob = await this.api.downloadFile(`/pdf/bandes-commandes/${bcId}`).toPromise();
      if (blob) {
        const fileName = `BC-${this.getBCNumber(bcId)}.pdf`;
        await this.handlePDFDownload(blob, fileName);
        this.showToast('PDF g├®n├®r├® avec succ├¿s', 'success');
        this.addNotification({
          title: 'Export PDF',
          message: `BC ${this.getBCNumber(bcId)} export├®e en PDF.`,
          type: 'success'
        });
      }
    } catch (error) {
      console.error('Error downloading BC PDF:', error);
      this.showToast('Erreur lors de la g├®n├®ration du PDF', 'error');
      throw error;
    }
  }

  async downloadFactureVentePDF(factureId: string): Promise<void> {
    try {
      this.showToast('G├®n├®ration du PDF en cours...', 'info');
      const blob = await this.api.downloadFile(`/pdf/factures-ventes/${factureId}`).toPromise();
      if (blob) {
        const invoice = this.invoices().find(i => i.id === factureId);
        const fileName = `FV-${invoice?.number || factureId}.pdf`;
        await this.handlePDFDownload(blob, fileName);
        this.showToast('PDF g├®n├®r├® avec succ├¿s', 'success');
      }
    } catch (error) {
      console.error('Error downloading Facture Vente PDF:', error);
      this.showToast('Erreur lors de la g├®n├®ration du PDF', 'error');
      throw error;
    }
  }

  async downloadFactureAchatPDF(factureId: string): Promise<void> {
    try {
      this.showToast('G├®n├®ration du PDF en cours...', 'info');
      const blob = await this.api.downloadFile(`/pdf/factures-achats/${factureId}`).toPromise();
      if (blob) {
        const invoice = this.invoices().find(i => i.id === factureId);
        const fileName = `FA-${invoice?.number || factureId}.pdf`;
        await this.handlePDFDownload(blob, fileName);
        this.showToast('PDF g├®n├®r├® avec succ├¿s', 'success');
      }
    } catch (error) {
      console.error('Error downloading Facture Achat PDF:', error);
      this.showToast('Erreur lors de la g├®n├®ration du PDF', 'error');
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
      const mapped = paiements.map(p => this.mapPayment(p));
      
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
      
      const created = await this.api.post<any>('/paiements', payload).toPromise();
      
      // Update local payments map
      const invoiceId = payment.factureAchatId || payment.factureVenteId || '';
      if (invoiceId) {
        this.payments.update(map => {
          const newMap = new Map(map);
          const existing = newMap.get(invoiceId) || [];
          newMap.set(invoiceId, [...existing, this.mapPayment(created)]);
          return newMap;
        });
        
        // Reload invoices to get updated status
        await this.loadInvoices();
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

  private mapPayment(p: any): Payment {
    return {
      id: p.id,
      factureAchatId: p.factureAchatId,
      factureVenteId: p.factureVenteId,
      date: p.date,
      montant: p.montant || 0,
      mode: p.mode,
      reference: p.reference,
      notes: p.notes
    };
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
      this.showToast('G├®n├®ration de l\'export Excel en cours...', 'info');
      
      // Construire les param├¿tres de requ├¬te
      const queryParams = new URLSearchParams();
      if (params?.clientId) {
        queryParams.append('clientId', params.clientId);
      }
      if (params?.supplierId) {
        queryParams.append('fournisseurId', params.supplierId);
      }
      if (params?.dateMin) {
        queryParams.append('dateMin', params.dateMin);
      }
      if (params?.dateMax) {
        queryParams.append('dateMax', params.dateMax);
      }
      if (params?.status) {
        // Mapper le statut frontend vers backend
        let etat = params.status;
        if (params.status === 'sent') {
          etat = 'envoyee';
        } else if (params.status === 'completed') {
          etat = 'complete';
        } else if (params.status === 'draft') {
          etat = 'brouillon';
        }
        queryParams.append('etat', etat);
      }

      const url = `/bandes-commandes/export/excel${queryParams.toString() ? '?' + queryParams.toString() : ''}`;
      const blob = await this.api.downloadFile(url).toPromise();
      
      if (blob) {
        const urlObj = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = urlObj;
        
        // Extraire le nom de fichier depuis les headers ou utiliser un nom par d├®faut
        const today = new Date().toISOString().split('T')[0];
        link.download = `Export_BCs_${today}.xlsx`;
        
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(urlObj);
        
        this.showToast('Export Excel t├®l├®charg├® avec succ├¿s', 'success');
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
}
