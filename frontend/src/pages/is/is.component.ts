import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ISService } from '../../services/is.service';
import { StoreService } from '../../services/store.service';
import type { AjustementFiscal } from '../../models/types';

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
        <h2 class="text-lg font-bold text-slate-800 mb-4">Déclaration IS</h2>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
          <div>
            <label class="block text-sm font-semibold text-slate-700 mb-1">Année</label>
            <input type="number" [(ngModel)]="anneeDeclaration" class="w-full px-4 py-2 border border-slate-200 rounded-lg">
          </div>
          <div>
            <label class="block text-sm font-semibold text-slate-700 mb-1">Date début</label>
            <input type="date" [(ngModel)]="dateDebut" class="w-full px-4 py-2 border border-slate-200 rounded-lg">
          </div>
          <div>
            <label class="block text-sm font-semibold text-slate-700 mb-1">Date fin</label>
            <input type="date" [(ngModel)]="dateFin" class="w-full px-4 py-2 border border-slate-200 rounded-lg">
          </div>
        </div>
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-4">
          <div>
            <div class="flex justify-between items-center mb-2">
              <h3 class="font-semibold text-slate-700">Réintégrations fiscales</h3>
              <button (click)="addReintegration()" class="text-sm px-2 py-1 rounded bg-slate-100 hover:bg-slate-200">+ Ligne</button>
            </div>
            <div class="space-y-2">
              @for (line of reintegrations; track $index) {
                <div class="grid grid-cols-12 gap-2">
                  <input [(ngModel)]="line.libelle" placeholder="Libellé" class="col-span-7 px-3 py-2 border border-slate-200 rounded-lg text-sm">
                  <input type="number" [(ngModel)]="line.montant" placeholder="Montant" class="col-span-4 px-3 py-2 border border-slate-200 rounded-lg text-sm">
                  <button (click)="removeReintegration($index)" class="col-span-1 text-slate-500 hover:text-red-600">x</button>
                </div>
              }
            </div>
          </div>
          <div>
            <div class="flex justify-between items-center mb-2">
              <h3 class="font-semibold text-slate-700">Déductions fiscales</h3>
              <button (click)="addDeduction()" class="text-sm px-2 py-1 rounded bg-slate-100 hover:bg-slate-200">+ Ligne</button>
            </div>
            <div class="space-y-2">
              @for (line of deductions; track $index) {
                <div class="grid grid-cols-12 gap-2">
                  <input [(ngModel)]="line.libelle" placeholder="Libellé" class="col-span-7 px-3 py-2 border border-slate-200 rounded-lg text-sm">
                  <input type="number" [(ngModel)]="line.montant" placeholder="Montant" class="col-span-4 px-3 py-2 border border-slate-200 rounded-lg text-sm">
                  <button (click)="removeDeduction($index)" class="col-span-1 text-slate-500 hover:text-red-600">x</button>
                </div>
              }
            </div>
          </div>
        </div>
        <div class="flex flex-wrap gap-2">
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
          <button (click)="validerDeclaration()" class="px-4 py-2 bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 transition font-medium">Valider</button>
          <button (click)="exportDeclaration()" [disabled]="exporting()" class="px-4 py-2 bg-slate-700 text-white rounded-lg hover:bg-slate-800 transition font-medium disabled:opacity-50">
            {{ exporting() ? 'Export...' : 'Exporter (.xlsx)' }}
          </button>
        </div>

        @if (isData()) {
          @let is = isData()!;
          <div class="mt-6 grid grid-cols-1 md:grid-cols-2 gap-6">
            <div class="space-y-3">
              <div class="flex justify-between"><span>Résultat comptable:</span><span class="font-medium">{{ is.resultatComptable | number:'1.2-2' }} MAD</span></div>
              <div class="flex justify-between"><span>Réintégrations:</span><span class="font-medium">{{ is.totalReintegrations | number:'1.2-2' }} MAD</span></div>
              <div class="flex justify-between"><span>Déductions:</span><span class="font-medium">{{ is.totalDeductions | number:'1.2-2' }} MAD</span></div>
              <div class="flex justify-between"><span>Résultat fiscal:</span><span class="font-medium">{{ is.resultatFiscal | number:'1.2-2' }} MAD</span></div>
              <div class="flex justify-between"><span>CA:</span><span class="font-medium">{{ is.chiffreAffaires | number:'1.2-2' }} MAD</span></div>
            </div>
            <div class="space-y-3">
              <div class="flex justify-between"><span>IS calculé:</span><span class="font-medium">{{ is.isCalcule | number:'1.2-2' }} MAD</span></div>
              <div class="flex justify-between"><span>Cotisation minimale:</span><span class="font-medium">{{ is.cotisationMinimale | number:'1.2-2' }} MAD</span></div>
              <div class="flex justify-between"><span>Acomptes payés:</span><span class="font-medium">{{ is.acomptesPayes | number:'1.2-2' }} MAD</span></div>
              <div class="flex justify-between"><span>Reliquat:</span><span class="font-medium">{{ is.reliquat | number:'1.2-2' }} MAD</span></div>
              <div class="flex justify-between pt-2 border-t font-bold text-xl text-blue-600"><span>IS dû:</span><span>{{ is.isDu | number:'1.2-2' }} MAD</span></div>
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
                  <th class="px-4 py-3 text-right font-semibold text-slate-700">Action</th>
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
                    <td class="px-4 py-3 text-right">
                      @if (ac.statut !== 'PAYE') {
                        <button (click)="marquerAcomptePaye(ac)" class="px-3 py-1 text-xs rounded bg-emerald-600 text-white hover:bg-emerald-700">
                          Marquer payé
                        </button>
                      }
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
  declarations = signal<any[]>([]);
  calculating = signal(false);
  exporting = signal(false);
  dateDebut: string = new Date(new Date().getFullYear(), 0, 1).toISOString().split('T')[0];
  dateFin: string = new Date().toISOString().split('T')[0];
  anneeAcomptes: number = new Date().getFullYear();
  anneeDeclaration: number = new Date().getFullYear();
  reintegrations: AjustementFiscal[] = [{ libelle: '', montant: 0 }];
  deductions: AjustementFiscal[] = [{ libelle: '', montant: 0 }];

  ngOnInit() {
    this.loadAcomptes();
    this.loadDeclarations();
  }

  calculerIS() {
    this.calculating.set(true);
    const payload = {
      annee: this.anneeDeclaration,
      dateDebut: this.dateDebut,
      dateFin: this.dateFin,
      reintegrations: this.reintegrations.filter(r => r.libelle?.trim() || (r.montant ?? 0) > 0),
      deductions: this.deductions.filter(d => d.libelle?.trim() || (d.montant ?? 0) > 0),
    };
    this.isService.calculerDeclaration(payload).subscribe({
      next: (data) => {
        this.isData.set(data);
        this.calculating.set(false);
        this.loadAcomptes();
        this.loadDeclarations();
        this.store.showToast('Declaration IS calculee avec succes', 'success');
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

  loadDeclarations() {
    this.isService.getDeclarations().subscribe({
      next: (data) => this.declarations.set(data),
      error: () => this.store.showToast('Erreur lors du chargement des declarations IS', 'error')
    });
  }

  addReintegration() {
    this.reintegrations.push({ libelle: '', montant: 0 });
  }

  removeReintegration(index: number) {
    if (this.reintegrations.length === 1) return;
    this.reintegrations.splice(index, 1);
  }

  addDeduction() {
    this.deductions.push({ libelle: '', montant: 0 });
  }

  removeDeduction(index: number) {
    if (this.deductions.length === 1) return;
    this.deductions.splice(index, 1);
  }

  marquerAcomptePaye(ac: any) {
    if (!ac?.id) return;
    this.isService.marquerAcomptePaye(ac.id).subscribe({
      next: () => {
        this.loadAcomptes();
        this.store.showToast(`Acompte T${ac.numero} marque comme paye`, 'success');
      },
      error: () => this.store.showToast('Erreur lors du paiement de l\'acompte', 'error')
    });
  }

  validerDeclaration() {
    this.isService.validerDeclaration(this.anneeDeclaration).subscribe({
      next: (d) => {
        this.isData.set(d);
        this.loadDeclarations();
        this.store.showToast('Declaration IS validee', 'success');
      },
      error: () => this.store.showToast('Aucune declaration a valider pour cette annee', 'error')
    });
  }

  exportDeclaration() {
    this.exporting.set(true);
    this.isService.exportDeclaration(this.anneeDeclaration).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `declaration_is_${this.anneeDeclaration}.xlsx`;
        a.click();
        window.URL.revokeObjectURL(url);
        this.exporting.set(false);
      },
      error: () => {
        this.exporting.set(false);
        this.store.showToast('Erreur lors de l\'export IS', 'error');
      }
    });
  }

  formatDate(date: string): string {
    return new Date(date).toLocaleDateString('fr-FR');
  }
}

