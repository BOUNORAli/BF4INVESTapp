import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
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
}
