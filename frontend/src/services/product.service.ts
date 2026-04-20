import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { Product } from '../models/types';

/** Fallback affichage : ignore null/undefined et 0 (prix corrompu en base). */
function pickPrice(...vals: (number | null | undefined)[]): number {
  for (const v of vals) {
    if (v != null && v > 0) {
      return v;
    }
  }
  return 0;
}

export interface ProductBcUsage {
  bandeCommandeId: string;
  numeroBC: string;
  dateBC: string | null;
  quantiteAcheteeTotale?: number | null;
  prixAchatUnitaireHtPondere?: number | null;
  quantiteVendueTotale?: number | null;
  prixVenteUnitaireHtPondere?: number | null;
  fournisseurIds?: string | null;
  clientIds?: string | null;
}

@Injectable({
  providedIn: 'root'
})
export class ProductService {
  private api = inject(ApiService);

  async getProductBcs(productId: string): Promise<ProductBcUsage[]> {
    const rows = await this.api.get<any[]>(`/produits/${productId}/bcs`).toPromise() || [];
    return rows.map((r) => ({
      bandeCommandeId: r.bandeCommandeId,
      numeroBC: r.numeroBC ?? '',
      dateBC: r.dateBC ?? null,
      quantiteAcheteeTotale: r.quantiteAcheteeTotale ?? null,
      prixAchatUnitaireHtPondere: r.prixAchatUnitaireHtPondere ?? null,
      quantiteVendueTotale: r.quantiteVendueTotale ?? null,
      prixVenteUnitaireHtPondere: r.prixVenteUnitaireHtPondere ?? null,
      fournisseurIds: r.fournisseurIds ?? null,
      clientIds: r.clientIds ?? null
    }));
  }

  async getProducts(): Promise<Product[]> {
    const products = await this.api.get<any[]>('/produits').toPromise() || [];
    return products.map(p => this.mapProduct(p));
  }

  async addProduct(product: Product): Promise<Product> {
    const payload: any = {
      refArticle: product.ref,
      designation: product.name,
      unite: product.unit,
      categorie: product.category,
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
      categorie: product.category,
      prixAchatPondereHT: product.priceBuyHT, // Utiliser prix pondéré
      prixVentePondereHT: product.priceSellHT, // Utiliser prix pondéré
      // Garder aussi les anciens champs pour rétrocompatibilité
      prixAchatUnitaireHT: product.priceBuyHT,
      prixVenteUnitaireHT: product.priceSellHT,
      quantiteEnStock: product.stock !== undefined ? product.stock : 0
    };
    // Gestion de l'image :
    // - si imageUrl est présent : on met à jour l'image
    // - si imageUrl est absent (et qu'on est en édition) : on envoie une valeur vide pour demander la suppression
    if (product.imageUrl) {
      // Inclure l'image si présente (extraire base64 depuis data URL)
      const base64Data = this.extractBase64FromDataUrl(product.imageUrl);
      const contentType = this.extractContentTypeFromDataUrl(product.imageUrl);
      payload.imageBase64 = base64Data;
      payload.imageContentType = contentType;
    } else {
      // Instruction explicite : supprimer l'image côté backend
      payload.imageBase64 = '';
      payload.imageContentType = '';
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
      category: p.categorie || p.category || 'Matériaux de Construction',
      // Pondéré puis unitaire : pickPrice ignore 0 (anciens backfills corrompus)
      priceBuyHT: pickPrice(p.prixAchatPondereHT, p.prixAchatUnitaireHT, p.priceBuyHT),
      priceSellHT: pickPrice(p.prixVentePondereHT, p.prixVenteUnitaireHT, p.priceSellHT),
      priceBuyMinHT: p.prixAchatMinHT ?? p.priceBuyMinHT,
      priceBuyMaxHT: p.prixAchatMaxHT ?? p.priceBuyMaxHT,
      priceSellMinHT: p.prixVenteMinHT ?? p.priceSellMinHT,
      priceSellMaxHT: p.prixVenteMaxHT ?? p.priceSellMaxHT,
      stock: p.quantiteEnStock !== undefined ? p.quantiteEnStock : (p.stock !== undefined ? p.stock : 0),
      imageUrl: imageUrl
    };
  }
}

