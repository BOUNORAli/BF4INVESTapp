import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import type { AcompteIS, AjustementFiscal, DeclarationIS } from '../models/types';

@Injectable({
  providedIn: 'root'
})
export class ISService {
  private api = inject(ApiService);

  calculerIS(dateDebut: string, dateFin: string, exerciceId?: string): Observable<any> {
    return this.api.get<any>(`/is/calculer?dateDebut=${dateDebut}&dateFin=${dateFin}`, exerciceId ? { exerciceId } : undefined);
  }

  calculerDeclaration(payload: {
    annee: number;
    dateDebut: string;
    dateFin: string;
    exerciceId?: string;
    reintegrations: AjustementFiscal[];
    deductions: AjustementFiscal[];
  }): Observable<DeclarationIS> {
    return this.api.post<DeclarationIS>('/is/declarations/calculer', payload);
  }

  getDeclarations(): Observable<DeclarationIS[]> {
    return this.api.get<DeclarationIS[]>('/is/declarations');
  }

  validerDeclaration(annee: number): Observable<DeclarationIS> {
    return this.api.post<DeclarationIS>(`/is/declarations/${annee}/valider`, {});
  }

  getAcomptes(annee: number): Observable<AcompteIS[]> {
    return this.api.get<any[]>(`/is/acomptes?annee=${annee}`);
  }

  marquerAcomptePaye(acompteId: string, datePaiement?: string, montantPaye?: number): Observable<any> {
    return this.api.post(`/is/acomptes/${acompteId}/payer`, { datePaiement, montantPaye });
  }

  exportDeclaration(annee: number): Observable<Blob> {
    return this.api.downloadFile('/is/export', { annee });
  }
}

