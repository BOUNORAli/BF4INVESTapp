import { Injectable, inject, signal, computed } from '@angular/core';
import { InvoiceService } from '../services/invoice.service';
import type { Invoice, Payment } from '../services/store.service';

/**
 * Store spécialisé pour la gestion des factures et paiements
 */
@Injectable({
  providedIn: 'root'
})
export class InvoiceStore {
  private invoiceService = inject(InvoiceService);

  // État
  readonly invoices = signal<Invoice[]>([]);
  readonly payments = signal<Map<string, Payment[]>>(new Map()); // Map<invoiceId, Payment[]>
  readonly loading = signal<boolean>(false);

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
   * Charge toutes les factures
   */
  async loadInvoices(): Promise<void> {
    try {
      this.loading.set(true);
      const invoices = await this.invoiceService.getInvoices();
      this.invoices.set(invoices);
    } catch (error) {
      console.error('Error loading invoices:', error);
      throw error;
    } finally {
      this.loading.set(false);
    }
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

