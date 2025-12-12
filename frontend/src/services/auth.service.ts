import { Injectable, signal, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ApiService } from './api.service';

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
  token: string;
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

  // État de l'utilisateur (Signal)
  readonly currentUser = signal<User | null>(null);

  constructor() {
    // Récupérer du localStorage pour persister la session
    const savedUser = localStorage.getItem('bf4_user');
    const token = localStorage.getItem('bf4_token');
    
    if (savedUser && token) {
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

      if (response?.token && response?.user) {
        // Stocker le token et les infos utilisateur
        localStorage.setItem('bf4_token', response.token);
        if (response.refreshToken) {
          localStorage.setItem('bf4_refresh_token', response.refreshToken);
        }

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

  logout() {
    this.clearAuth();
    this.router.navigate(['/login']);
  }

  private clearAuth() {
    this.currentUser.set(null);
    localStorage.removeItem('bf4_token');
    localStorage.removeItem('bf4_refresh_token');
    localStorage.removeItem('bf4_user');
  }

  isAuthenticated(): boolean {
    const token = localStorage.getItem('bf4_token');
    return this.currentUser() !== null && token !== null;
  }
}
