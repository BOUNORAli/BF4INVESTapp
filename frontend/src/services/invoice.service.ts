import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { Invoice, Payment } from '../models/types';

@Injectable({
  providedIn: 'root'
})
export class InvoiceService {
  private api = inject(ApiService);

  async getInvoices(): Promise<Invoice[]> {
    const purchaseInvoices = await this.api.get<any[]>('/factures-achats').toPromise() || [];
    const salesInvoices = await this.api.get<any[]>('/factures-ventes').toPromise() || [];
    
    return [
      ...purchaseInvoices.map(inv => this.mapInvoice(inv, 'purchase')),
      ...salesInvoices.map(inv => this.mapInvoice(inv, 'sale'))
    ];
  }

  async addInvoice(inv: Invoice): Promise<Invoice> {
    // S'assurer que les montants sont des nombres
    const amountHT = inv.amountHT != null && inv.amountHT !== undefined ? Number(inv.amountHT) : 0;
    const amountTTC = inv.amountTTC != null && inv.amountTTC !== undefined ? Number(inv.amountTTC) : 0;

    let created: any;

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
      
      if ((inv as any).ajouterAuStock !== undefined) {
        payload.ajouterAuStock = (inv as any).ajouterAuStock;
      }
      
      created = await this.api.post<any>('/factures-achats', payload).toPromise();
      return this.mapInvoice(created, 'purchase');
    } else {
      const payload = {
        ...(inv.number && inv.number.trim() ? { numeroFactureVente: inv.number } : {}),
        dateFacture: inv.date,
        bandeCommandeId: inv.bcId || null,
        clientId: inv.partnerId,
        totalHT: amountHT,
        totalTTC: amountTTC,
        modePaiement: inv.paymentMode || null,
        etatPaiement: inv.status === 'paid' ? 'regle' : 'non_regle'
      };
      
      created = await this.api.post<any>('/factures-ventes', payload).toPromise();
      return this.mapInvoice(created, 'sale');
    }
  }

  async updateInvoice(inv: Invoice, existingInvoice?: Invoice): Promise<Invoice> {
    // Utiliser les valeurs fournies ou existantes
    const amountHT = (inv.amountHT != null && inv.amountHT !== undefined) 
      ? Number(inv.amountHT) 
      : (existingInvoice && existingInvoice.amountHT != null ? Number(existingInvoice.amountHT) : 0);
    const amountTTC = (inv.amountTTC != null && inv.amountTTC !== undefined) 
      ? Number(inv.amountTTC) 
      : (existingInvoice && existingInvoice.amountTTC != null ? Number(existingInvoice.amountTTC) : 0);
    
    if (inv.type === 'purchase') {
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
      
      if ((inv as any).ajouterAuStock !== undefined) {
        payload.ajouterAuStock = (inv as any).ajouterAuStock;
      }
      
      const updated = await this.api.put<any>(`/factures-achats/${inv.id}`, payload).toPromise();
      return this.mapInvoice(updated, 'purchase');
    } else {
      const payload: any = {
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
      
      const updated = await this.api.put<any>(`/factures-ventes/${inv.id}`, payload).toPromise();
      return this.mapInvoice(updated, 'sale');
    }
  }

  async deleteInvoice(id: string, type: 'purchase' | 'sale'): Promise<void> {
    if (type === 'purchase') {
      await this.api.delete(`/factures-achats/${id}`).toPromise();
    } else {
      await this.api.delete(`/factures-ventes/${id}`).toPromise();
    }
  }

  // Payments
  async getPayments(factureAchatId?: string, factureVenteId?: string): Promise<Payment[]> {
    const params: Record<string, any> = {};
    if (factureAchatId) params.factureAchatId = factureAchatId;
    if (factureVenteId) params.factureVenteId = factureVenteId;
    
    const paiements = await this.api.get<any[]>('/paiements', params).toPromise() || [];
    return paiements.map(p => this.mapPayment(p));
  }

  async addPayment(payment: Payment): Promise<Payment> {
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
    return this.mapPayment(created);
  }

  // PDF Methods
  async downloadFactureVentePDF(factureId: string): Promise<Blob> {
    return await this.api.downloadFile(`/pdf/factures-ventes/${factureId}`).toPromise();
  }

  async downloadBonDeLivraisonPDF(factureId: string): Promise<Blob> {
    return await this.api.downloadFile(`/pdf/factures-ventes/${factureId}/bon-de-livraison`).toPromise();
  }

  async downloadFactureAchatPDF(factureId: string): Promise<Blob> {
    return await this.api.downloadFile(`/pdf/factures-achats/${factureId}`).toPromise();
  }

  private mapPayment(p: any): Payment {
    return {
      id: p.id,
      factureAchatId: p.factureAchatId,
      factureVenteId: p.factureVenteId,
      bcReference: p.bcReference,
      date: p.date,
      montant: p.montant || 0,
      mode: p.mode,
      reference: p.reference,
      notes: p.notes,
      
      // Champs pour les calculs comptables
      typeMouvement: p.typeMouvement,
      nature: p.nature,
      colD: p.colD,
      tvaRate: p.tvaRate,
      
      // Champs calculés selon les formules Excel
      totalPaiementTTC: p.totalPaiementTTC,
      htPaye: p.htPaye,
      tvaPaye: p.tvaPaye,
      
      // Soldes après ce paiement
      soldeGlobalApres: p.soldeGlobalApres,
      soldePartenaireApres: p.soldePartenaireApres
    };
  }

  private mapInvoice(inv: any, type: 'purchase' | 'sale'): Invoice {
    const today = new Date().toISOString().split('T')[0];
    const dueDate = inv.dateEcheance || inv.dueDate || today;
    
    let status: 'paid' | 'pending' | 'overdue' = 'pending';
    if (inv.etatPaiement === 'regle') {
      status = 'paid';
    } else if (inv.etatPaiement === 'partiellement_regle') {
      status = dueDate < today ? 'overdue' : 'pending';
    } else if (inv.etatPaiement === 'non_regle') {
      status = dueDate < today ? 'overdue' : 'pending';
    } else {
      status = dueDate < today ? 'overdue' : 'pending';
    }
    
    const amountHT = (inv.totalHT != null && inv.totalHT !== undefined) ? Number(inv.totalHT) : 
                     (inv.amountHT != null && inv.amountHT !== undefined) ? Number(inv.amountHT) : 
                     (inv.montantHT != null && inv.montantHT !== undefined) ? Number(inv.montantHT) : 0;
    
    const amountTTC = (inv.totalTTC != null && inv.totalTTC !== undefined) ? Number(inv.totalTTC) : 
                      (inv.amountTTC != null && inv.amountTTC !== undefined) ? Number(inv.amountTTC) : 
                      (inv.montantTTC != null && inv.montantTTC !== undefined) ? Number(inv.montantTTC) : 
                      amountHT;
    
    return {
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
      
      typeMouvement: inv.typeMouvement,
      nature: inv.nature,
      colA: inv.colA,
      colB: inv.colB,
      colD: inv.colD,
      colF: inv.colF,
      tvaRate: inv.tvaRate,
      tauxRG: inv.tauxRG,
      
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
      bilan: inv.bilan,
      previsionsPaiement: inv.previsionsPaiement ? inv.previsionsPaiement.map((p: any) => ({
        id: p.id,
        datePrevue: p.datePrevue,
        montantPrevu: p.montantPrevu || 0,
        statut: p.statut || 'PREVU',
        notes: p.notes
      })) : undefined
    };
  }
}

