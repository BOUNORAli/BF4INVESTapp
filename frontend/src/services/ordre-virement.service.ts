import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { OrdreVirement } from '../models/types';

@Injectable({
  providedIn: 'root'
})
export class OrdreVirementService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8080/api';

  getOrdresVirement(params?: {
    beneficiaireId?: string;
    statut?: string;
    dateDebut?: string;
    dateFin?: string;
  }): Observable<OrdreVirement[]> {
    let url = `${this.apiUrl}/ordres-virement`;
    const queryParams: string[] = [];
    
    if (params?.beneficiaireId) {
      queryParams.push(`beneficiaireId=${params.beneficiaireId}`);
    }
    if (params?.statut) {
      queryParams.push(`statut=${params.statut}`);
    }
    if (params?.dateDebut) {
      queryParams.push(`dateDebut=${params.dateDebut}`);
    }
    if (params?.dateFin) {
      queryParams.push(`dateFin=${params.dateFin}`);
    }
    
    if (queryParams.length > 0) {
      url += '?' + queryParams.join('&');
    }
    
    return this.http.get<any[]>(url).pipe(
      map(ordres => ordres.map(this.mapOrdreVirement))
    );
  }

  getOrdreVirementById(id: string): Observable<OrdreVirement> {
    return this.http.get<any>(`${this.apiUrl}/ordres-virement/${id}`).pipe(
      map(this.mapOrdreVirement)
    );
  }

  addOrdreVirement(ov: OrdreVirement): Observable<OrdreVirement> {
    return this.http.post<any>(`${this.apiUrl}/ordres-virement`, this.toPayload(ov)).pipe(
      map(this.mapOrdreVirement)
    );
  }

  updateOrdreVirement(id: string, ov: OrdreVirement): Observable<OrdreVirement> {
    return this.http.put<any>(`${this.apiUrl}/ordres-virement/${id}`, this.toPayload(ov)).pipe(
      map(this.mapOrdreVirement)
    );
  }

  deleteOrdreVirement(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/ordres-virement/${id}`);
  }

  executerOrdreVirement(id: string): Observable<OrdreVirement> {
    return this.http.post<any>(`${this.apiUrl}/ordres-virement/${id}/executer`, {}).pipe(
      map(this.mapOrdreVirement)
    );
  }

  annulerOrdreVirement(id: string): Observable<OrdreVirement> {
    return this.http.post<any>(`${this.apiUrl}/ordres-virement/${id}/annuler`, {}).pipe(
      map(this.mapOrdreVirement)
    );
  }

  private mapOrdreVirement(ov: any): OrdreVirement {
    return {
      id: ov.id,
      numeroOV: ov.numeroOV || '',
      dateOV: ov.dateOV || '',
      montant: ov.montant || 0,
      beneficiaireId: ov.beneficiaireId || '',
      nomBeneficiaire: ov.nomBeneficiaire,
      ribBeneficiaire: ov.ribBeneficiaire || '',
      motif: ov.motif || '',
      facturesIds: ov.facturesIds || [],
      banqueEmettrice: ov.banqueEmettrice || '',
      dateExecution: ov.dateExecution,
      statut: ov.statut || 'EN_ATTENTE'
    };
  }

  private toPayload(ov: OrdreVirement): any {
    return {
      numeroOV: ov.numeroOV,
      dateOV: ov.dateOV,
      montant: ov.montant,
      beneficiaireId: ov.beneficiaireId,
      ribBeneficiaire: ov.ribBeneficiaire,
      motif: ov.motif,
      facturesIds: ov.facturesIds,
      banqueEmettrice: ov.banqueEmettrice,
      dateExecution: ov.dateExecution,
      statut: ov.statut
    };
  }
}

