import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams, HttpEvent, HttpEventType } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map, filter } from 'rxjs/operators';
import { getApiBaseUrlDynamic } from '../config/environment';

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private http = inject(HttpClient);

  private getApiUrl(): string {
    // Utiliser la fonction dynamique pour récupérer l'URL (au cas où window.__API_URL__ serait défini)
    const url = getApiBaseUrlDynamic();
    // Fallback si l'URL contient encore "votre-backend"
    if (url.includes('votre-backend')) {
      return 'https://bf4investapp-production.up.railway.app/api';
    }
    return url;
  }

  private getHeaders(): HttpHeaders {
    const token = localStorage.getItem('bf4_token');
    let headers = new HttpHeaders({
      'Content-Type': 'application/json'
    });
    
    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }
    
    return headers;
  }

  get<T>(endpoint: string, params?: Record<string, any>): Observable<T> {
    let httpParams = new HttpParams();
    if (params) {
      Object.keys(params).forEach(key => {
        if (params[key] !== null && params[key] !== undefined) {
          httpParams = httpParams.set(key, params[key].toString());
        }
      });
    }

    return this.http.get<T>(`${this.getApiUrl()}${endpoint}`, {
      headers: this.getHeaders(),
      params: httpParams
    });
  }

  post<T>(endpoint: string, body: any): Observable<T> {
    return this.http.post<T>(`${this.getApiUrl()}${endpoint}`, body, {
      headers: this.getHeaders()
    });
  }

  put<T>(endpoint: string, body: any): Observable<T> {
    return this.http.put<T>(`${this.getApiUrl()}${endpoint}`, body, {
      headers: this.getHeaders()
    });
  }

  delete(endpoint: string): Observable<void> {
    return this.http.delete<void>(`${this.getApiUrl()}${endpoint}`, {
      headers: this.getHeaders()
    });
  }

  uploadFile(endpoint: string, file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    
    const token = localStorage.getItem('bf4_token');
    let headers = new HttpHeaders();
    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }
    // Don't set Content-Type - browser will set it automatically with boundary for FormData

    return this.http.post(`${this.getApiUrl()}${endpoint}`, formData, {
      headers: headers
    });
  }

  uploadFileWithParams<T = any>(endpoint: string, file: File, params?: Record<string, any>): Observable<T> {
    const formData = new FormData();
    formData.append('file', file);
    
    if (params) {
      Object.keys(params).forEach(key => {
        if (params[key] !== null && params[key] !== undefined) {
          formData.append(key, params[key].toString());
        }
      });
    }
    
    const token = localStorage.getItem('bf4_token');
    let headers = new HttpHeaders();
    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }
    // Don't set Content-Type - browser will set it automatically with boundary for FormData

    return this.http.post<T>(`${this.getApiUrl()}${endpoint}`, formData, {
      headers: headers
    });
  }

  downloadFile(endpoint: string, params?: any): Observable<Blob> {
    const token = localStorage.getItem('bf4_token');
    let headers = new HttpHeaders();
    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }

    let url = `${this.getApiUrl()}${endpoint}`;
    if (params) {
      const queryString = new URLSearchParams(params).toString();
      if (queryString) {
        url += '?' + queryString;
      }
    }

    return this.http.get(url, {
      headers: headers,
      responseType: 'blob'
    });
  }

  /**
   * Upload un fichier vers GridFS avec progression
   * @param file Le fichier à uploader
   * @param type Type de fichier (facture_achat, releve_bancaire, etc.)
   * @param entityId ID de l'entité associée (optionnel)
   * @param entityType Type d'entité (FactureAchat, etc.) (optionnel)
   * @param onProgress Callback pour la progression (optionnel)
   */
  uploadFileToGridFS(
    file: File,
    type?: string,
    entityId?: string,
    entityType?: string,
    onProgress?: (progress: number) => void
  ): Observable<{ fileId: string; filename: string; contentType: string; size: number; message: string }> {
    const params: Record<string, any> = {};
    if (type) params.type = type;
    if (entityId) params.entityId = entityId;
    if (entityType) params.entityType = entityType;

    const formData = new FormData();
    formData.append('file', file);
    
    if (params) {
      Object.keys(params).forEach(key => {
        if (params[key] !== null && params[key] !== undefined) {
          formData.append(key, params[key].toString());
        }
      });
    }
    
    const token = localStorage.getItem('bf4_token');
    let headers = new HttpHeaders();
    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }

    return this.http.post<{ fileId: string; filename: string; contentType: string; size: number; message: string }>(
      `${this.getApiUrl()}/files/upload`,
      formData,
      {
        headers: headers,
        reportProgress: true,
        observe: 'events'
      }
    ).pipe(
      map((event: HttpEvent<any>) => {
        if (event.type === HttpEventType.UploadProgress && event.total) {
          const progress = Math.round((100 * event.loaded) / event.total);
          if (onProgress) {
            onProgress(progress);
          }
          // Retourner un objet avec la progression pour le suivi
          return { progress, type: 'progress', fileId: null } as any;
        } else if (event.type === HttpEventType.Response) {
          // Retourner la réponse finale
          return event.body;
        }
        return null;
      }),
      filter((result: any) => result !== null)
    ) as Observable<any>;
  }

  /**
   * Télécharge un fichier depuis GridFS
   * @param fileId ID du fichier dans GridFS
   */
  downloadFileFromGridFS(fileId: string): Observable<Blob> {
    return this.downloadFile(`/files/${fileId}`);
  }

  /**
   * Récupère les métadonnées d'un fichier
   * @param fileId ID du fichier dans GridFS
   */
  getFileMetadata(fileId: string): Observable<any> {
    return this.get(`/files/${fileId}/metadata`);
  }

  /**
   * Supprime un fichier
   * @param fileId ID du fichier dans GridFS
   */
  deleteFileFromGridFS(fileId: string): Observable<void> {
    return this.delete(`/files/${fileId}`);
  }

  /**
   * Vérifie si un fichier existe
   * @param fileId ID du fichier dans GridFS
   */
  fileExists(fileId: string): Observable<{ exists: boolean }> {
    return this.get(`/files/${fileId}/exists`);
  }

  /**
   * Upload spécifique factures achat (Supabase)
   */
  uploadFactureAchatFile(
    file: File,
    factureId?: string,
    onProgress?: (progress: number) => void
  ): Observable<any> {
    console.log('🌐 [API] uploadFactureAchatFile - Nom:', file.name, 'Type:', file.type, 'Taille:', file.size, 'FactureId:', factureId);
    
    const formData = new FormData();
    formData.append('file', file);
    if (factureId) {
      formData.append('factureId', factureId);
    }

    const token = localStorage.getItem('bf4_token');
    let headers = new HttpHeaders();
    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }

    const url = `${this.getApiUrl()}/factures-achats/files/upload`;
    console.log('🌐 [API] Envoi POST vers:', url);

    return this.http.post(url, formData, {
      headers,
      observe: 'events',
      reportProgress: true
    }).pipe(
      map(event => {
        if (event.type === HttpEventType.UploadProgress && event.total) {
          const progress = Math.round((100 * event.loaded) / event.total);
          if (onProgress) {
            onProgress(progress);
          }
          return { type: 'progress', progress };
        } else if (event.type === HttpEventType.Response) {
          return event.body;
        }
        return null;
      }),
      filter(event => event !== null)
    );
  }

  downloadFactureAchatFile(fileId: string): Observable<Blob> {
    const token = localStorage.getItem('bf4_token');
    let headers = new HttpHeaders();
    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }
    return this.http.get(`${this.getApiUrl()}/factures-achats/files/${fileId}`, {
      headers,
      responseType: 'blob'
    });
  }

  deleteFactureAchatFile(fileId: string, factureId?: string): Observable<any> {
    const token = localStorage.getItem('bf4_token');
    let headers = new HttpHeaders();
    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }
    const encodedFileId = encodeURIComponent(fileId);
    const params: any = { fileId: encodedFileId };
    if (factureId) params.factureId = factureId;
    return this.http.delete(`${this.getApiUrl()}/factures-achats/files`, { headers, params });
  }

  getFactureAchatFileUrl(fileId: string, contentType?: string): Observable<{ url: string }> {
    const token = localStorage.getItem('bf4_token');
    let headers = new HttpHeaders();
    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }
    // Encoder le fileId car il peut contenir des slashes (ex: bf4/factures/uuid)
    const encodedFileId = encodeURIComponent(fileId);
    let params = new HttpParams().set('fileId', encodedFileId);
    if (contentType) {
      params = params.set('contentType', contentType);
    }
    return this.http.get<{ url: string }>(`${this.getApiUrl()}/factures-achats/files/url`, { headers, params });
  }

  getReleveFileUrl(fileId: string): Observable<{ url: string }> {
    const token = localStorage.getItem('bf4_token');
    let headers = new HttpHeaders();
    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }
    // Encoder le fileId car il peut contenir des slashes (ex: bf4/releves/uuid)
    const encodedFileId = encodeURIComponent(fileId);
    return this.http.get<{ url: string }>(`${this.getApiUrl()}/releve-bancaire/files/url?fileId=${encodedFileId}`, { headers });
  }
  
  deleteReleveFile(fileId: string): Observable<any> {
    const token = localStorage.getItem('bf4_token');
    let headers = new HttpHeaders();
    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }
    const encodedFileId = encodeURIComponent(fileId);
    return this.http.delete(`${this.getApiUrl()}/releve-bancaire/files?fileId=${encodedFileId}`, { headers });
  }
}
