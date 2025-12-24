import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';

@Injectable({
  providedIn: 'root'
})
export class ISService {
  private api = inject(ApiService);

  calculerIS(dateDebut: string, dateFin: string, exerciceId?: string): Observable<any> {
    return this.api.get<any>(`/is/calculer?dateDebut=${dateDebut}&dateFin=${dateFin}`, exerciceId ? { exerciceId } : undefined);
  }

  getAcomptes(annee: number): Observable<any[]> {
    return this.api.get<any[]>(`/is/acomptes?annee=${annee}`);
  }
}

