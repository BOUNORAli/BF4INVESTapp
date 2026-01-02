import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ISService } from '../../services/is.service';
import { StoreService } from '../../services/store.service';

@Component({
  selector: 'app-is',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6 fade-in-up pb-10">
      <!-- Header -->
      <div class="flex flex-col md:flex-row justify-between items-start md:items-end gap-4 border-b border-slate-200/60 pb-6">
        <div>
          <h1 class="text-2xl md:text-3xl font-bold text-slate-800 font-display">Impôt sur les Sociétés (IS)</h1>
          <p class="text-slate-500 mt-2 text-sm">Simulation et gestion des acomptes IS</p>
        </div>
      </div>

      <!-- Simulation IS -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
        <h2 class="text-lg font-bold text-slate-800 mb-4">Simulation IS</h2>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
          <div>
            <label class="block text-sm font-semibold text-slate-700 mb-1">Date début</label>
            <input type="date" [(ngModel)]="dateDebut" class="w-full px-4 py-2 border border-slate-200 rounded-lg">
          </div>
          <div>
            <label class="block text-sm font-semibold text-slate-700 mb-1">Date fin</label>
            <input type="date" [(ngModel)]="dateFin" class="w-full px-4 py-2 border border-slate-200 rounded-lg">
          </div>
        </div>
        <button (click)="calculerIS()" [disabled]="calculating()" class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition font-medium disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2">
          @if (calculating()) {
            <svg class="w-4 h-4 animate-spin" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"></path>
            </svg>
            <span>Calcul en cours...</span>
          } @else {
            <span>Calculer IS</span>
          }
        </button>

        @if (isData()) {
          @let is = isData()!;
          <div class="mt-6 grid grid-cols-1 md:grid-cols-2 gap-6">
            <div class="space-y-3">
              <div class="flex justify-between"><span>Résultat net:</span><span class="font-medium">{{ is.resultatNet | number:'1.2-2' }} MAD</span></div>
              <div class="flex justify-between"><span>Résultat fiscal:</span><span class="font-medium">{{ is.resultatFiscal | number:'1.2-2' }} MAD</span></div>
              <div class="flex justify-between"><span>CA:</span><span class="font-medium">{{ is.ca | number:'1.2-2' }} MAD</span></div>
            </div>
            <div class="space-y-3">
              <div class="flex justify-between"><span>IS calculé:</span><span class="font-medium">{{ is.isCalcule | number:'1.2-2' }} MAD</span></div>
              <div class="flex justify-between"><span>Cotisation minimale:</span><span class="font-medium">{{ is.cotisationMinimale | number:'1.2-2' }} MAD</span></div>
              <div class="flex justify-between pt-2 border-t font-bold text-xl text-blue-600"><span>IS à payer:</span><span>{{ is.isAPayer | number:'1.2-2' }} MAD</span></div>
            </div>
          </div>
        }
      </div>

      <!-- Acomptes IS -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
        <div class="flex justify-between items-center mb-4">
          <h2 class="text-lg font-bold text-slate-800">Acomptes Provisionnels</h2>
          <div class="flex gap-2">
            <input type="number" [(ngModel)]="anneeAcomptes" placeholder="Année" class="px-4 py-2 border border-slate-200 rounded-lg w-32">
            <button (click)="loadAcomptes()" class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition font-medium">
              Charger
            </button>
          </div>
        </div>
        @if (acomptes().length > 0) {
          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead class="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th class="px-4 py-3 text-left font-semibold text-slate-700">N°</th>
                  <th class="px-4 py-3 text-left font-semibold text-slate-700">Date Échéance</th>
                  <th class="px-4 py-3 text-right font-semibold text-slate-700">Montant</th>
                  <th class="px-4 py-3 text-left font-semibold text-slate-700">Statut</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                @for (ac of acomptes(); track ac.numero) {
                  <tr class="hover:bg-slate-50">
                    <td class="px-4 py-3">{{ ac.numero }}</td>
                    <td class="px-4 py-3">{{ formatDate(ac.dateEcheance) }}</td>
                    <td class="px-4 py-3 text-right font-bold">{{ ac.montant | number:'1.2-2' }} MAD</td>
                    <td class="px-4 py-3">
                      <span class="px-2 py-1 bg-amber-100 text-amber-700 rounded-full text-xs font-semibold">
                        {{ ac.statut }}
                      </span>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    </div>
  `
})
export class ISComponent implements OnInit {
  private isService = inject(ISService);
  private store = inject(StoreService);

  isData = signal<any>(null);
  acomptes = signal<any[]>([]);
  calculating = signal(false);
  dateDebut: string = new Date(new Date().getFullYear(), 0, 1).toISOString().split('T')[0];
  dateFin: string = new Date().toISOString().split('T')[0];
  anneeAcomptes: number = new Date().getFullYear();

  ngOnInit() {
    this.loadAcomptes();
  }

  calculerIS() {
    this.calculating.set(true);
    this.isService.calculerIS(this.dateDebut, this.dateFin).subscribe({
      next: (data) => {
        this.isData.set(data);
        this.calculating.set(false);
        this.store.showToast('IS calculé avec succès', 'success');
      },
      error: (err) => {
        this.calculating.set(false);
        this.store.showToast('Erreur lors du calcul de l\'IS', 'error');
      }
    });
  }

  loadAcomptes() {
    this.isService.getAcomptes(this.anneeAcomptes).subscribe({
      next: (data) => this.acomptes.set(data),
      error: (err) => this.store.showToast('Erreur lors du chargement des acomptes', 'error')
    });
  }

  formatDate(date: string): string {
    return new Date(date).toLocaleDateString('fr-FR');
  }
}

