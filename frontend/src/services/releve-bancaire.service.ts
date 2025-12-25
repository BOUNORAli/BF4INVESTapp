import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import type { TransactionBancaire } from '../models/types';

export interface ImportResult {
  totalRows: number;
  successCount: number;
  errorCount: number;
  errors: string[];
  warnings?: string[];
}

@Injectable({
  providedIn: 'root'
})
export class ReleveBancaireService {
  private api = inject(ApiService);

  importReleve(file: File, mois: number, annee: number): Observable<ImportResult> {
    return this.api.uploadFileWithParams<ImportResult>('/releve-bancaire/import', file, { mois, annee });
  }

  mapperTransactions(mois: number, annee: number): Observable<{ total: number; mapped: number; paiementsCrees: number; errors: number; nonMapped: number }> {
    return this.api.post<{ total: number; mapped: number; paiementsCrees: number; errors: number; nonMapped: number }>(
      `/releve-bancaire/mapper/${mois}/${annee}`, 
      {}
    );
  }

  getTransactions(params?: { mois?: number; annee?: number; mapped?: boolean }): Observable<TransactionBancaire[]> {
    return this.api.get<TransactionBancaire[]>('/releve-bancaire/transactions', params);
  }

  getTransaction(id: string): Observable<TransactionBancaire> {
    return this.api.get<TransactionBancaire>(`/releve-bancaire/transactions/${id}`);
  }

  linkTransaction(transactionId: string, factureVenteId?: string, factureAchatId?: string): Observable<void> {
    const body: any = {};
    if (factureVenteId) body.factureVenteId = factureVenteId;
    if (factureAchatId) body.factureAchatId = factureAchatId;
    
    return this.api.put<void>(`/releve-bancaire/transactions/${transactionId}/link`, body);
  }

  // PDF file management (Cloudinary)
  uploadPdfReleve(file: File, mois: number, annee: number): Observable<{ id: string; fileId: string; filename: string; signedUrl?: string }> {
    return this.api.uploadFileWithParams('/releve-bancaire/files/upload', file, { mois, annee });
  }

  getPdfFiles(params?: { mois?: number; annee?: number }): Observable<Array<{ id: string; fichierId: string; nomFichier: string; contentType?: string; taille?: number; mois: number; annee: number; uploadedAt: string; url?: string }>> {
    return this.api.get<Array<{ id: string; fichierId: string; nomFichier: string; contentType?: string; taille?: number; mois: number; annee: number; uploadedAt: string; url?: string }>>('/releve-bancaire/files', params);
  }

  deletePdfFile(fileId: string): Observable<any> {
    return this.api.deleteReleveFile(fileId);
  }
}

