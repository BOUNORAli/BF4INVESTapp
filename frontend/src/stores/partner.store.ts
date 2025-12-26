import { Injectable, inject, signal } from '@angular/core';
import { PartnerService } from '../services/partner.service';
import type { Client, Supplier } from '../services/store.service';

/**
 * Store spécialisé pour la gestion des clients et fournisseurs
 */
@Injectable({
  providedIn: 'root'
})
export class PartnerStore {
  private partnerService = inject(PartnerService);

  // État
  readonly clients = signal<Client[]>([]);
  readonly suppliers = signal<Supplier[]>([]);
  readonly loading = signal<boolean>(false);

  // Computed
  readonly clientsCount = () => this.clients().length;
  readonly suppliersCount = () => this.suppliers().length;

  /**
   * Charge tous les clients
   */
  async loadClients(): Promise<void> {
    try {
      this.loading.set(true);
      const clients = await this.partnerService.getClients();
      this.clients.set(clients);
    } catch (error) {
      console.error('Error loading clients:', error);
      throw error;
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * Charge tous les fournisseurs
   */
  async loadSuppliers(): Promise<void> {
    try {
      this.loading.set(true);
      const suppliers = await this.partnerService.getSuppliers();
      this.suppliers.set(suppliers);
    } catch (error) {
      console.error('Error loading suppliers:', error);
      throw error;
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * Charge clients et fournisseurs en parallèle
   */
  async loadAll(): Promise<void> {
    await Promise.all([this.loadClients(), this.loadSuppliers()]);
  }

  /**
   * Ajoute ou met à jour un client
   */
  upsertClient(client: Client): void {
    this.clients.update(list => {
      const index = list.findIndex(c => c.id === client.id);
      if (index >= 0) {
        const updated = [...list];
        updated[index] = client;
        return updated;
      }
      return [...list, client];
    });
  }

  /**
   * Ajoute ou met à jour un fournisseur
   */
  upsertSupplier(supplier: Supplier): void {
    this.suppliers.update(list => {
      const index = list.findIndex(s => s.id === supplier.id);
      if (index >= 0) {
        const updated = [...list];
        updated[index] = supplier;
        return updated;
      }
      return [...list, supplier];
    });
  }

  /**
   * Supprime un client
   */
  removeClient(clientId: string): void {
    this.clients.update(list => list.filter(c => c.id !== clientId));
  }

  /**
   * Supprime un fournisseur
   */
  removeSupplier(supplierId: string): void {
    this.suppliers.update(list => list.filter(s => s.id !== supplierId));
  }

  /**
   * Trouve un client par ID
   */
  findClientById(id: string): Client | undefined {
    return this.clients().find(c => c.id === id);
  }

  /**
   * Trouve un fournisseur par ID
   */
  findSupplierById(id: string): Supplier | undefined {
    return this.suppliers().find(s => s.id === id);
  }
}

