import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';

export interface OcrProductLine {
  designation: string;
  quantite?: number;
  prixUnitaireHT?: number;
  prixTotalHT?: number;
  unite?: string;
}

export interface OcrExtractResult {
  rawText: string;
  numeroDocument?: string;
  dateDocument?: string;
  fournisseurNom?: string;
  lignes: OcrProductLine[];
  confidence: number;
}

@Injectable({
  providedIn: 'root'
})
export class OcrService {
  private api = inject(ApiService);

  /**
   * Upload une image et extrait les informations via OCR Cloudinary
   */
  extractFromImage(file: File): Observable<OcrExtractResult> {
    return this.api.uploadFileWithParams<OcrExtractResult>('/ocr/extract-bc', file);
  }
}

