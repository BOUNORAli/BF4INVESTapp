import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { OrdreVirement } from '../models/types';
import { ApiService } from './api.service';

@Injectable({
  providedIn: 'root'
})
export class OrdreVirementService {
  private api = inject(ApiService);

  getOrdresVirement(params?: {
    beneficiaireId?: string;
    statut?: string;
    dateDebut?: string;
    dateFin?: string;
  }): Observable<OrdreVirement[]> {
    return this.api.get<any[]>('/ordres-virement', params).pipe(
      map(ordres => ordres.map(this.mapOrdreVirement))
    );
  }

  getOrdreVirementById(id: string): Observable<OrdreVirement> {
    return this.api.get<any>(`/ordres-virement/${id}`).pipe(
      map(this.mapOrdreVirement)
    );
  }

  addOrdreVirement(ov: OrdreVirement): Observable<OrdreVirement> {
    return this.api.post<any>('/ordres-virement', this.toPayload(ov)).pipe(
      map(this.mapOrdreVirement)
    );
  }

  updateOrdreVirement(id: string, ov: OrdreVirement): Observable<OrdreVirement> {
    return this.api.put<any>(`/ordres-virement/${id}`, this.toPayload(ov)).pipe(
      map(this.mapOrdreVirement)
    );
  }

  deleteOrdreVirement(id: string): Observable<void> {
    return this.api.delete(`/ordres-virement/${id}`);
  }

  executerOrdreVirement(id: string): Observable<OrdreVirement> {
    return this.api.post<any>(`/ordres-virement/${id}/executer`, {}).pipe(
      map(this.mapOrdreVirement)
    );
  }

  annulerOrdreVirement(id: string): Observable<OrdreVirement> {
    return this.api.post<any>(`/ordres-virement/${id}/annuler`, {}).pipe(
      map(this.mapOrdreVirement)
    );
  }

  downloadOrdreVirementPDF(id: string): Observable<Blob> {
    return this.api.downloadFile(`/pdf/ordres-virement/${id}`);
  }

  private mapOrdreVirement(ov: any): OrdreVirement {
    return {
      id: ov.id,
      numeroOV: ov.numeroOV || '',
      dateOV: ov.dateOV || '',
      montant: ov.montant || 0,
      beneficiaireId: ov.beneficiaireId || '',
      nomBeneficiaire: ov.nomBeneficiaire,
      banqueBeneficiaire: ov.banqueBeneficiaire,
      ribBeneficiaire: ov.ribBeneficiaire || '',
      motif: ov.motif || '',
      facturesIds: ov.facturesIds || [],
      facturesMontants: ov.facturesMontants || [],
      banqueEmettrice: ov.banqueEmettrice || '',
      dateExecution: ov.dateExecution,
      statut: ov.statut || 'EN_ATTENTE',
      type: ov.type || 'NORMAL'
    };
  }

  private toPayload(ov: OrdreVirement): any {
    const payload: any = {
      numeroOV: ov.numeroOV,
      dateOV: ov.dateOV,
      montant: ov.montant,
      beneficiaireId: ov.beneficiaireId ?? null, // null si undefined pour inclure dans JSON
      nomBeneficiaire: ov.nomBeneficiaire ?? null, // Important : toujours inclure nomBeneficiaire
      banqueBeneficiaire: ov.banqueBeneficiaire,
      ribBeneficiaire: ov.ribBeneficiaire,
      motif: ov.motif,
      banqueEmettrice: ov.banqueEmettrice,
      dateExecution: ov.dateExecution ?? null,
      statut: ov.statut,
      type: ov.type || 'NORMAL'
    };
    
    // Inclure facturesIds et facturesMontants seulement s'ils existent
    if (ov.facturesIds && ov.facturesIds.length > 0) {
      payload.facturesIds = ov.facturesIds;
    } else {
      payload.facturesIds = null;
    }
    
    if (ov.facturesMontants && ov.facturesMontants.length > 0) {
      payload.facturesMontants = ov.facturesMontants;
    } else {
      payload.facturesMontants = null;
    }
    
    return payload;
  }
}

