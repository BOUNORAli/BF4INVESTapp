import { Injectable, signal, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ApiService } from './api.service';
import { HttpBackend, HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { tap } from 'rxjs/operators';
import { getApiBaseUrlDynamic } from '../config/environment';

export interface User {
  id: string;
  name: string;
  email: string;
  role: 'admin' | 'user' | 'ADMIN' | 'COMMERCIAL' | 'COMPTABLE' | 'LECTEUR';
  avatar?: string;
}

interface LoginRequest {
  email: string;
  password: string;
}

interface LoginResponse {
  token?: string;
  refreshToken?: string;
  user: {
    id: string;
    name: string;
    email: string;
    role: string;
  };
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private api = inject(ApiService);
  private router = inject(Router);
  private httpBackend = inject(HttpBackend);
  private httpClient: HttpClient;

  // État de l'utilisateur (Signal)
  readonly currentUser = signal<User | null>(null);

  constructor() {
    this.httpClient = new HttpClient(this.httpBackend);

    // Restore user from localStorage (auth is cookie-based; token not readable by JS)
    const savedUser = localStorage.getItem('bf4_user');
    if (savedUser) {
      try {
        this.currentUser.set(JSON.parse(savedUser));
      } catch (e) {
        console.error('Error parsing saved user', e);
        this.clearAuth();
      }
    }
  }

  async login(email: string, password: string): Promise<boolean> {
    try {
      const response = await this.api.post<LoginResponse>('/auth/login', {
        email,
        password
      } as LoginRequest).toPromise();

      if (response?.user) {
        const user: User = {
          id: response.user.id,
          name: response.user.name,
          email: response.user.email,
          role: response.user.role as any,
          avatar: `https://ui-avatars.com/api/?name=${encodeURIComponent(response.user.name)}&background=2563EB&color=fff`
        };
        localStorage.setItem('bf4_user', JSON.stringify(user));
        this.currentUser.set(user);
        this.router.navigate(['/dashboard']);
        return true;
      }
      return false;
    } catch (error: any) {
      console.error('Login error:', error);
      return false;
    }
  }

  refreshToken(): Observable<LoginResponse> {
    const baseUrl = getApiBaseUrlDynamic();
    const apiUrl = baseUrl.includes('votre-backend')
      ? 'https://bf4investapp-production.up.railway.app/api'
      : baseUrl;

    return this.httpClient.post<LoginResponse>(`${apiUrl}/auth/refresh`, {}, { withCredentials: true }).pipe(
      tap((response) => {
        if (response?.user) {
          const user: User = {
            id: response.user.id,
            name: response.user.name,
            email: response.user.email,
            role: response.user.role as any,
            avatar: `https://ui-avatars.com/api/?name=${encodeURIComponent(response.user.name)}&background=2563EB&color=fff`
          };
          localStorage.setItem('bf4_user', JSON.stringify(user));
          this.currentUser.set(user);
        }
      })
    );
  }

  async logout(): Promise<void> {
    try {
      await this.api.post('/auth/logout', {}).toPromise();
    } catch {
      // Ignore errors (e.g. already logged out, network)
    }
    this.clearAuth();
    this.router.navigate(['/login']);
  }

  private clearAuth() {
    this.currentUser.set(null);
    localStorage.removeItem('bf4_user');
  }

  isAuthenticated(): boolean {
    return this.currentUser() !== null;
  }

  isAdmin(): boolean {
    const role = this.currentUser()?.role;
    return role === 'ADMIN' || role === 'admin';
  }

  hasRole(role: string): boolean {
    const r = this.currentUser()?.role;
    if (!r) return false;
    return r === role || (role === 'ADMIN' && r === 'admin') || (role === 'admin' && r === 'ADMIN');
  }

  hasAnyRole(roles: string[]): boolean {
    const r = this.currentUser()?.role;
    if (!r) return false;
    return roles.some(role => this.hasRole(role));
  }
}
