import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { Client, Supplier } from '../models/types';

@Injectable({
  providedIn: 'root'
})
export class PartnerService {
  private api = inject(ApiService);

  // Clients
  async getClients(): Promise<Client[]> {
    const clients = await this.api.get<any[]>('/clients').toPromise() || [];
    return clients.map(c => this.mapClient(c));
  }

  async addClient(client: Client): Promise<Client> {
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
    return this.mapClient(created);
  }

  async updateClient(client: Client): Promise<Client> {
    const payload = {
        nom: client.name,
        ice: client.ice,
        referenceClient: client.referenceClient || null,
        telephone: client.phone,
        email: client.email,
        adresse: client.address
    };
    const updated = await this.api.put<any>(`/clients/${client.id}`, payload).toPromise();
    return this.mapClient(updated);
  }

  async deleteClient(id: string): Promise<void> {
    await this.api.delete(`/clients/${id}`).toPromise();
  }

  // Suppliers
  async getSuppliers(): Promise<Supplier[]> {
    const suppliers = await this.api.get<any[]>('/fournisseurs').toPromise() || [];
    return suppliers.map(s => this.mapSupplier(s));
  }

  async addSupplier(supplier: Supplier): Promise<Supplier> {
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
    return this.mapSupplier(created);
  }

  async updateSupplier(supplier: Supplier): Promise<Supplier> {
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
    return this.mapSupplier(updated);
  }

  async deleteSupplier(id: string): Promise<void> {
    await this.api.delete(`/fournisseurs/${id}`).toPromise();
  }

  // Mappers
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
}

