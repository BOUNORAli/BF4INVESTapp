import { Injectable, inject, signal, computed } from '@angular/core';
import { InvoiceService } from '../services/invoice.service';
import { DataCacheService } from '../services/data-cache.service';
import type { Invoice, Payment } from '../services/store.service';

/**
 * Store spécialisé pour la gestion des factures et paiements
 */
@Injectable({
  providedIn: 'root'
})
export class InvoiceStore {
  private invoiceService = inject(InvoiceService);
  private cache = inject(DataCacheService);

  // État
  readonly invoices = signal<Invoice[]>([]);
  readonly payments = signal<Map<string, Payment[]>>(new Map()); // Map<invoiceId, Payment[]>
  readonly loading = signal<boolean>(false);
  readonly refreshing = signal<boolean>(false);
  readonly lastUpdated = signal<Date | null>(null);

  // Computed
  readonly invoicesCount = () => this.invoices().length;
  readonly purchaseInvoices = computed(() => 
    this.invoices().filter(inv => inv.type === 'purchase')
  );
  readonly salesInvoices = computed(() => 
    this.invoices().filter(inv => inv.type === 'sale')
  );
  readonly overdueInvoices = computed(() => 
    this.invoices().filter(inv => inv.status === 'overdue')
  );
  readonly paidInvoices = computed(() => 
    this.invoices().filter(inv => inv.status === 'paid')
  );

  /**
   * Charge uniquement les factures achat
   */
  async loadPurchaseInvoices(): Promise<void> {
    try {
      this.loading.set(true);
      
      // Vérifier le cache d'abord
      const cached = this.cache.get<Invoice[]>('invoices-purchase');
      if (cached) {
        // Mettre à jour le store avec les données en cache immédiatement
        this.updateInvoicesList(cached, 'purchase');
      }
      
      // Charger depuis l'API (même si on a le cache, on rafraîchit en arrière-plan)
      try {
        const invoices = await this.invoiceService.getPurchaseInvoices();
        this.updateInvoicesList(invoices, 'purchase');
        this.cache.set('invoices-purchase', invoices);
        this.lastUpdated.set(new Date());
      } catch (error) {
        // Si l'API échoue mais qu'on a le cache, continuer avec le cache
        if (!cached) {
          throw error;
        }
        console.warn('Error loading purchase invoices from API, using cache:', error);
      }
    } catch (error) {
      console.error('Error loading purchase invoices:', error);
      throw error;
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * Charge uniquement les factures ventes
   */
  async loadSalesInvoices(): Promise<void> {
    try {
      this.loading.set(true);
      
      // Vérifier le cache d'abord
      const cached = this.cache.get<Invoice[]>('invoices-sales');
      if (cached) {
        // Mettre à jour le store avec les données en cache immédiatement
        this.updateInvoicesList(cached, 'sale');
      }
      
      // Charger depuis l'API (même si on a le cache, on rafraîchit en arrière-plan)
      try {
        const invoices = await this.invoiceService.getSalesInvoices();
        this.updateInvoicesList(invoices, 'sale');
        this.cache.set('invoices-sales', invoices);
        this.lastUpdated.set(new Date());
      } catch (error) {
        // Si l'API échoue mais qu'on a le cache, continuer avec le cache
        if (!cached) {
          throw error;
        }
        console.warn('Error loading sales invoices from API, using cache:', error);
      }
    } catch (error) {
      console.error('Error loading sales invoices:', error);
      throw error;
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * Met à jour la liste des factures en fusionnant avec les factures existantes
   */
  private updateInvoicesList(newInvoices: Invoice[], type: 'purchase' | 'sale'): void {
    this.invoices.update(current => {
      // Retirer les factures du type spécifié
      const filtered = current.filter(inv => inv.type !== type);
      // Ajouter les nouvelles factures
      return [...filtered, ...newInvoices];
    });
  }

  /**
   * Charge toutes les factures (pour compatibilité)
   */
  async loadInvoices(): Promise<void> {
    try {
      this.loading.set(true);
      
      // Vérifier le cache d'abord
      const cachedPurchase = this.cache.get<Invoice[]>('invoices-purchase');
      const cachedSales = this.cache.get<Invoice[]>('invoices-sales');
      
      // Si on a les deux en cache, les utiliser immédiatement
      if (cachedPurchase && cachedSales) {
        this.invoices.set([...cachedPurchase, ...cachedSales]);
      }
      
      // Charger depuis l'API
      const invoices = await this.invoiceService.getInvoices();
      this.invoices.set(invoices);
      
      // Mettre à jour le cache séparément
      const purchaseInvoices = invoices.filter(inv => inv.type === 'purchase');
      const salesInvoices = invoices.filter(inv => inv.type === 'sale');
      this.cache.set('invoices-purchase', purchaseInvoices);
      this.cache.set('invoices-sales', salesInvoices);
      
      this.lastUpdated.set(new Date());
    } catch (error) {
      console.error('Error loading invoices:', error);
      throw error;
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * Rafraîchit les factures (en arrière-plan, ne bloque pas l'UI)
   */
  async refresh(): Promise<void> {
    try {
      this.refreshing.set(true);
      const invoices = await this.invoiceService.getInvoices();
      this.invoices.set(invoices);
      
      // Mettre à jour le cache séparément
      const purchaseInvoices = invoices.filter(inv => inv.type === 'purchase');
      const salesInvoices = invoices.filter(inv => inv.type === 'sale');
      this.cache.set('invoices-purchase', purchaseInvoices);
      this.cache.set('invoices-sales', salesInvoices);
      
      this.lastUpdated.set(new Date());
    } catch (error) {
      console.error('Error refreshing invoices:', error);
      throw error;
    } finally {
      this.refreshing.set(false);
    }
  }

  /**
   * Force le rafraîchissement (ignore le cache)
   */
  async forceRefresh(): Promise<void> {
    await this.refresh();
  }

  /**
   * Charge les paiements pour une facture
   */
  async loadPaymentsForInvoice(invoiceId: string, invoiceType: 'purchase' | 'sale'): Promise<void> {
    try {
      const params = invoiceType === 'purchase' 
        ? { factureAchatId: invoiceId }
        : { factureVenteId: invoiceId };
      
      const payments = await this.invoiceService.getPayments(
        params.factureAchatId,
        params.factureVenteId
      );
      
      this.payments.update(map => {
        const newMap = new Map(map);
        newMap.set(invoiceId, payments);
        return newMap;
      });
    } catch (error) {
      console.error('Error loading payments:', error);
      throw error;
    }
  }

  /**
   * Ajoute ou met à jour une facture
   */
  upsertInvoice(invoice: Invoice): void {
    this.invoices.update(list => {
      const index = list.findIndex(i => i.id === invoice.id);
      if (index >= 0) {
        const updated = [...list];
        updated[index] = invoice;
        return updated;
      }
      return [...list, invoice];
    });
  }

  /**
   * Supprime une facture
   */
  removeInvoice(invoiceId: string): void {
    this.invoices.update(list => list.filter(i => i.id !== invoiceId));
    this.payments.update(map => {
      const newMap = new Map(map);
      newMap.delete(invoiceId);
      return newMap;
    });
  }

  /**
   * Trouve une facture par ID
   */
  findInvoiceById(id: string): Invoice | undefined {
    return this.invoices().find(i => i.id === id);
  }

  /**
   * Récupère les paiements d'une facture
   */
  getPaymentsForInvoice(invoiceId: string): Payment[] {
    return this.payments().get(invoiceId) || [];
  }

  /**
   * Ajoute un paiement à une facture
   */
  addPaymentToInvoice(invoiceId: string, payment: Payment): void {
    this.payments.update(map => {
      const newMap = new Map(map);
      const existing = newMap.get(invoiceId) || [];
      newMap.set(invoiceId, [...existing, payment]);
      return newMap;
    });
  }
}

