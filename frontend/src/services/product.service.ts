import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { Product } from '../models/types';

@Injectable({
  providedIn: 'root'
})
export class ProductService {
  private api = inject(ApiService);

  async getProducts(): Promise<Product[]> {
    const products = await this.api.get<any[]>('/produits').toPromise() || [];
    return products.map(p => this.mapProduct(p));
  }

  async addProduct(product: Product): Promise<Product> {
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
    return this.mapProduct(created);
  }

  async updateProduct(product: Product): Promise<Product> {
    const payload = {
      refArticle: product.ref,
      designation: product.name,
      unite: product.unit,
      prixAchatUnitaireHT: product.priceBuyHT,
      prixVenteUnitaireHT: product.priceSellHT,
      quantiteEnStock: product.stock !== undefined ? product.stock : 0
    };
    const updated = await this.api.put<any>(`/produits/${product.id}`, payload).toPromise();
    return this.mapProduct(updated);
  }

  async deleteProduct(id: string): Promise<void> {
    await this.api.delete(`/produits/${id}`).toPromise();
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
}

