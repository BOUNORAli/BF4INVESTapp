import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';

export type UserRole = 'ADMIN' | 'COMMERCIAL' | 'COMPTABLE' | 'LECTEUR';

export interface ApiUser {
  id: string;
  name: string;
  email: string;
  role: UserRole;
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateUserPayload {
  name: string;
  email: string;
  password: string;
  role: UserRole;
}

export interface UpdateUserPayload {
  name: string;
  email: string;
  password?: string;
  role: UserRole;
  enabled: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private api = inject(ApiService);

  getUsers(): Observable<ApiUser[]> {
    return this.api.get<ApiUser[]>('/users');
  }

  createUser(payload: CreateUserPayload): Observable<ApiUser> {
    return this.api.post<ApiUser>('/users', payload);
  }

  updateUser(id: string, payload: UpdateUserPayload): Observable<ApiUser> {
    return this.api.put<ApiUser>(`/users/${id}`, payload);
  }

  deleteUser(id: string): Observable<void> {
    return this.api.delete(`/users/${id}`);
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.api.post<void>('/auth/change-password', {
      currentPassword,
      newPassword
    });
  }
}
