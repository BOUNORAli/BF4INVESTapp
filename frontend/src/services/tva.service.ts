import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import type { DeclarationTVA } from '../models/types';

@Injectable({
  providedIn: 'root'
})
export class TVAService {
  private api = inject(ApiService);

  getDeclarations(annee?: number): Observable<DeclarationTVA[]> {
    return this.api.get<DeclarationTVA[]>('/tva/declarations', annee ? { annee } : undefined);
  }

  getDeclaration(mois: number, annee: number): Observable<DeclarationTVA> {
    return this.api.get<DeclarationTVA>(`/tva/declarations/${mois}/${annee}`);
  }

  calculerDeclaration(mois: number, annee: number): Observable<DeclarationTVA> {
    return this.api.get<DeclarationTVA>(`/tva/declarations/calculer?mois=${mois}&annee=${annee}`);
  }

  validerDeclaration(id: string): Observable<DeclarationTVA> {
    return this.api.post<DeclarationTVA>(`/tva/declarations/${id}/valider`, {});
  }

  deposerDeclaration(id: string, dateDepot?: string): Observable<DeclarationTVA> {
    const url = dateDepot 
      ? `/tva/declarations/${id}/deposer?dateDepot=${dateDepot}`
      : `/tva/declarations/${id}/deposer`;
    return this.api.post<DeclarationTVA>(url, {});
  }

  exportDeclarations(annee?: number): Observable<Blob> {
    return this.api.downloadFile('/tva/export/declarations', annee ? { annee } : undefined);
  }
}

