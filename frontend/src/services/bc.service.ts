import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { BC, LigneAchat, ClientVente, LineItem } from '../models/types';

@Injectable({
  providedIn: 'root'
})
export class BcService {
  private api = inject(ApiService);

  async getBCs(): Promise<BC[]> {
    const bcs = await this.api.get<any[]>('/bandes-commandes').toPromise() || [];
    return bcs.map(bc => this.mapBC(bc));
  }

  async addBC(bc: BC): Promise<BC> {
    const payload: any = {
        // Ne pas envoyer numeroBC si vide - le backend le gérera avec la nouvelle logique
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
    
    // Rétrocompatibilité ancienne structure
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
    return this.mapBC(created);
  }

  async updateBC(updatedBc: BC): Promise<BC> {
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
    
    // Rétrocompatibilité ancienne structure
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
    return this.mapBC(updated);
  }

  async deleteBC(id: string): Promise<void> {
    await this.api.delete(`/bandes-commandes/${id}`).toPromise();
  }

  async downloadBCPDF(bcId: string): Promise<Blob> {
      return await this.api.downloadFile(`/pdf/bandes-commandes/${bcId}`).toPromise();
  }

  async exportBCsToExcel(params?: { 
    clientId?: string; 
    supplierId?: string; 
    dateMin?: string; 
    dateMax?: string; 
    status?: string 
  }): Promise<Blob> {
      const queryParams = new URLSearchParams();
      if (params?.clientId) queryParams.append('clientId', params.clientId);
      if (params?.supplierId) queryParams.append('fournisseurId', params.supplierId);
      if (params?.dateMin) queryParams.append('dateMin', params.dateMin);
      if (params?.dateMax) queryParams.append('dateMax', params.dateMax);
      if (params?.status) {
        let etat = params.status;
        if (params.status === 'sent') etat = 'envoyee';
        else if (params.status === 'completed') etat = 'complete';
        else if (params.status === 'draft') etat = 'brouillon';
        queryParams.append('etat', etat);
      }

      const url = `/bandes-commandes/export/excel${queryParams.toString() ? '?' + queryParams.toString() : ''}`;
      return await this.api.downloadFile(url).toPromise();
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
}

