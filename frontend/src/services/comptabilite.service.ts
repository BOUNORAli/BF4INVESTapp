import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import type { CompteComptable, EcritureComptable, ExerciceComptable } from '../models/types';

@Injectable({
  providedIn: 'root'
})
export class ComptabiliteService {
  private api = inject(ApiService);

  getComptes(): Observable<CompteComptable[]> {
    return this.api.get<CompteComptable[]>('/comptabilite/comptes');
  }

  getCompteByCode(code: string): Observable<CompteComptable> {
    return this.api.get<CompteComptable>(`/comptabilite/comptes/${code}`);
  }

  getExercices(): Observable<ExerciceComptable[]> {
    return this.api.get<ExerciceComptable[]>('/comptabilite/exercices');
  }

  getCurrentExercice(): Observable<ExerciceComptable> {
    return this.api.get<ExerciceComptable>('/comptabilite/exercices/current');
  }

  getEcritures(params?: { dateDebut?: string; dateFin?: string; journal?: string; exerciceId?: string; pieceType?: string; pieceId?: string }): Observable<EcritureComptable[]> {
    return this.api.get<EcritureComptable[]>('/comptabilite/ecritures', params);
  }

  getEcriture(id: string): Observable<EcritureComptable> {
    return this.api.get<EcritureComptable>(`/comptabilite/ecritures/${id}`);
  }

  getBalance(params?: { dateDebut?: string; dateFin?: string; exerciceId?: string }): Observable<CompteComptable[]> {
    return this.api.get<CompteComptable[]>('/comptabilite/balance', params);
  }

  getGrandLivre(compteCode: string, params?: { dateDebut?: string; dateFin?: string; exerciceId?: string }): Observable<EcritureComptable[]> {
    return this.api.get<EcritureComptable[]>(`/comptabilite/grand-livre?compteCode=${compteCode}`, params);
  }

  getBilan(date: string, exerciceId?: string): Observable<any> {
    return this.api.get<any>(`/comptabilite/bilan?date=${date}`, exerciceId ? { exerciceId } : undefined);
  }

  getCPC(params: { dateDebut: string; dateFin: string; exerciceId?: string }): Observable<any> {
    return this.api.get<any>('/comptabilite/cpc', params);
  }

  exportJournal(params?: { dateDebut?: string; dateFin?: string; exerciceId?: string }): Observable<Blob> {
    return this.api.downloadFile('/comptabilite/export/journal', params);
  }

  exportBalance(params?: { dateDebut?: string; dateFin?: string; exerciceId?: string }): Observable<Blob> {
    return this.api.downloadFile('/comptabilite/export/balance', params);
  }

  exportGrandLivre(compteCode: string, params?: { dateDebut?: string; dateFin?: string; exerciceId?: string }): Observable<Blob> {
    return this.api.downloadFile(`/comptabilite/export/grand-livre?compteCode=${compteCode}`, params);
  }

  initializePlanComptable(): Observable<void> {
    return this.api.post<void>('/comptabilite/init', {});
  }

  downloadJournalPdf(params?: { dateDebut?: string; dateFin?: string; exerciceId?: string; pieceType?: string; pieceId?: string }): Observable<Blob> {
    return this.api.downloadFile('/pdf/comptabilite/journal', params);
  }

  downloadBalancePdf(params?: { dateDebut?: string; dateFin?: string; exerciceId?: string }): Observable<Blob> {
    return this.api.downloadFile('/pdf/comptabilite/balance', params);
  }

  downloadGrandLivrePdf(compteCode: string, params?: { dateDebut?: string; dateFin?: string; exerciceId?: string }): Observable<Blob> {
    return this.api.downloadFile(`/pdf/comptabilite/grand-livre?compteCode=${compteCode}`, params);
  }

  downloadBilanPdf(date: string, exerciceId?: string): Observable<Blob> {
    const params = exerciceId ? { exerciceId } : undefined;
    return this.api.downloadFile(`/pdf/comptabilite/bilan?date=${date}`, params);
  }

  downloadCpcPdf(params: { dateDebut: string; dateFin: string; exerciceId?: string }): Observable<Blob> {
    return this.api.downloadFile('/pdf/comptabilite/cpc', params);
  }
}

