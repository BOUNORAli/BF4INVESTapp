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
    const payload: any = {
      refArticle: product.ref,
      designation: product.name,
      unite: product.unit,
      prixAchatPondereHT: product.priceBuyHT, // Utiliser prix pondéré
      prixVentePondereHT: product.priceSellHT, // Utiliser prix pondéré
      // Garder aussi les anciens champs pour rétrocompatibilité
      prixAchatUnitaireHT: product.priceBuyHT,
      prixVenteUnitaireHT: product.priceSellHT,
      quantiteEnStock: product.stock !== undefined ? product.stock : 0,
      tva: 20.0
    };
    // Inclure l'image si présente (extraire base64 depuis data URL)
    if (product.imageUrl) {
      const base64Data = this.extractBase64FromDataUrl(product.imageUrl);
      const contentType = this.extractContentTypeFromDataUrl(product.imageUrl);
      payload.imageBase64 = base64Data;
      payload.imageContentType = contentType;
    }
    const created = await this.api.post<any>('/produits', payload).toPromise();
    return this.mapProduct(created);
  }

  async updateProduct(product: Product): Promise<Product> {
    const payload: any = {
      refArticle: product.ref,
      designation: product.name,
      unite: product.unit,
      prixAchatPondereHT: product.priceBuyHT, // Utiliser prix pondéré
      prixVentePondereHT: product.priceSellHT, // Utiliser prix pondéré
      // Garder aussi les anciens champs pour rétrocompatibilité
      prixAchatUnitaireHT: product.priceBuyHT,
      prixVenteUnitaireHT: product.priceSellHT,
      quantiteEnStock: product.stock !== undefined ? product.stock : 0
    };
    // Inclure l'image si présente (extraire base64 depuis data URL)
    if (product.imageUrl) {
      const base64Data = this.extractBase64FromDataUrl(product.imageUrl);
      const contentType = this.extractContentTypeFromDataUrl(product.imageUrl);
      payload.imageBase64 = base64Data;
      payload.imageContentType = contentType;
    }
    const updated = await this.api.put<any>(`/produits/${product.id}`, payload).toPromise();
    return this.mapProduct(updated);
  }

  private extractBase64FromDataUrl(dataUrl: string): string {
    // data:image/png;base64,iVBORw0KG... -> iVBORw0KG...
    const base64Index = dataUrl.indexOf('base64,');
    if (base64Index === -1) return dataUrl; // Déjà en base64 pur
    return dataUrl.substring(base64Index + 7);
  }

  private extractContentTypeFromDataUrl(dataUrl: string): string {
    // data:image/png;base64,... -> image/png
    const match = dataUrl.match(/data:([^;]+)/);
    return match ? match[1] : 'image/png';
  }

  async deleteProduct(id: string): Promise<void> {
    await this.api.delete(`/produits/${id}`).toPromise();
  }

  private mapProduct(p: any): Product {
    // Construire l'URL de l'image depuis base64 si présente
    let imageUrl: string | undefined;
    if (p.imageBase64) {
      const contentType = p.imageContentType || 'image/png';
      imageUrl = `data:${contentType};base64,${p.imageBase64}`;
    }
    
    return {
      id: p.id,
      ref: p.refArticle || p.ref,
      name: p.designation || p.name,
      unit: p.unite || p.unit,
      // Utiliser les prix pondérés en priorité, avec fallback sur prix unitaires pour rétrocompatibilité
      priceBuyHT: p.prixAchatPondereHT ?? p.prixAchatUnitaireHT ?? p.priceBuyHT ?? 0,
      priceSellHT: p.prixVentePondereHT ?? p.prixVenteUnitaireHT ?? p.priceSellHT ?? 0,
      stock: p.quantiteEnStock !== undefined ? p.quantiteEnStock : (p.stock !== undefined ? p.stock : 0),
      imageUrl: imageUrl
    };
  }
}

