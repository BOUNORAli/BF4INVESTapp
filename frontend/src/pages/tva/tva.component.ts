import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TVAService } from '../../services/tva.service';
import { StoreService } from '../../services/store.service';
import type { DeclarationTVA } from '../../models/types';

@Component({
  selector: 'app-tva',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6 fade-in-up pb-10">
      <!-- Header -->
      <div class="flex flex-col md:flex-row justify-between items-start md:items-end gap-4 border-b border-slate-200/60 pb-6">
        <div>
          <h1 class="text-2xl md:text-3xl font-bold text-slate-800 font-display">Déclarations TVA</h1>
          <p class="text-slate-500 mt-2 text-sm">Gestion des déclarations mensuelles de TVA</p>
        </div>
        <div class="flex gap-3">
          <button (click)="loadDeclarations()" class="px-4 py-2 bg-white border border-slate-200 text-slate-700 rounded-lg hover:bg-slate-50 transition font-medium">
            Actualiser
          </button>
          <button (click)="openCalculModal()" class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition font-medium shadow-lg shadow-blue-600/20">
            Nouvelle Déclaration
          </button>
        </div>
      </div>

      <!-- Dashboard TVA -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div class="bg-gradient-to-br from-blue-500 to-blue-600 p-6 rounded-xl shadow-lg text-white">
          <div class="text-sm font-semibold opacity-90 mb-2">TVA Collectée ({{ currentYear() }})</div>
          <div class="text-3xl font-bold">{{ totalCollectee() | number:'1.2-2' }} MAD</div>
        </div>
        <div class="bg-gradient-to-br from-orange-500 to-orange-600 p-6 rounded-xl shadow-lg text-white">
          <div class="text-sm font-semibold opacity-90 mb-2">TVA Déductible ({{ currentYear() }})</div>
          <div class="text-3xl font-bold">{{ totalDeductible() | number:'1.2-2' }} MAD</div>
        </div>
        <div class="bg-gradient-to-br from-emerald-500 to-emerald-600 p-6 rounded-xl shadow-lg text-white">
          <div class="text-sm font-semibold opacity-90 mb-2">Solde TVA ({{ currentYear() }})</div>
          <div class="text-3xl font-bold">{{ soldeTVA() | number:'1.2-2' }} MAD</div>
          <div class="text-xs mt-2 opacity-75">{{ soldeTVA() >= 0 ? 'À payer' : 'Crédit' }}</div>
        </div>
      </div>

      <!-- Liste des déclarations -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div class="p-4 border-b border-slate-100 bg-slate-50/50 flex justify-between items-center">
          <h2 class="text-lg font-bold text-slate-800">Historique des Déclarations</h2>
          <button (click)="exportDeclarations()" [disabled]="declarations().length === 0" class="px-4 py-2 bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 transition disabled:opacity-50 flex items-center gap-2">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
            Exporter Excel
          </button>
        </div>
        @if (declarations().length === 0) {
          <div class="p-12 text-center text-slate-500">
            <p>Aucune déclaration TVA trouvée.</p>
          </div>
        } @else {
          <div class="overflow-x-auto">
            <table class="w-full text-sm min-w-[1000px]">
              <thead class="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th class="px-4 py-3 text-left font-semibold text-slate-700">Période</th>
                  <th class="px-4 py-3 text-right font-semibold text-slate-700">TVA Collectée</th>
                  <th class="px-4 py-3 text-right font-semibold text-slate-700">TVA Déductible</th>
                  <th class="px-4 py-3 text-right font-semibold text-slate-700">TVA à Payer</th>
                  <th class="px-4 py-3 text-right font-semibold text-slate-700">TVA Crédit</th>
                  <th class="px-4 py-3 text-left font-semibold text-slate-700">Statut</th>
                  <th class="px-4 py-3 text-left font-semibold text-slate-700">Date Dépôt</th>
                  <th class="px-4 py-3 text-center font-semibold text-slate-700">Actions</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                @for (decl of declarations(); track decl.id) {
                  <tr class="hover:bg-slate-50">
                    <td class="px-4 py-3 font-medium">{{ decl.periode }}</td>
                    <td class="px-4 py-3 text-right">{{ decl.tvaCollecteeTotale | number:'1.2-2' }}</td>
                    <td class="px-4 py-3 text-right">{{ decl.tvaDeductibleTotale | number:'1.2-2' }}</td>
                    <td class="px-4 py-3 text-right font-bold text-blue-600">{{ decl.tvaAPayer | number:'1.2-2' }}</td>
                    <td class="px-4 py-3 text-right font-bold text-emerald-600">{{ decl.tvaCredit | number:'1.2-2' }}</td>
                    <td class="px-4 py-3">
                      <span [class]="getStatutBadgeClass(decl.statut)">
                        {{ getStatutLabel(decl.statut) }}
                      </span>
                    </td>
                    <td class="px-4 py-3 text-sm">{{ decl.dateDepot ? formatDate(decl.dateDepot) : '-' }}</td>
                    <td class="px-4 py-3">
                      <div class="flex items-center justify-center gap-2">
                        <button (click)="viewDetails(decl)" class="p-2 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-full transition">
                          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"></path></svg>
                        </button>
                        @if (decl.statut === 'BROUILLON') {
                          <button (click)="valider(decl.id!)" class="p-2 text-slate-400 hover:text-emerald-600 hover:bg-emerald-50 rounded-full transition">
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>
                          </button>
                        }
                        @if (decl.statut === 'VALIDEE') {
                          <button (click)="deposer(decl.id!)" class="p-2 text-slate-400 hover:text-purple-600 hover:bg-purple-50 rounded-full transition">
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"></path></svg>
                          </button>
                        }
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>

      <!-- Modal Calcul -->
      @if (showCalculModal()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center" aria-modal="true">
          <div (click)="closeCalculModal()" class="fixed inset-0 bg-slate-900/40 backdrop-blur-sm"></div>
          <div class="relative bg-white rounded-xl shadow-2xl p-6 w-full max-w-md mx-4">
            <h2 class="text-xl font-bold text-slate-800 mb-4">Nouvelle Déclaration TVA</h2>
            <div class="space-y-4">
              <div>
                <label class="block text-sm font-semibold text-slate-700 mb-1">Mois</label>
                <select [(ngModel)]="selectedMois" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
                  @for (m of moisList; track m.value) {
                    <option [value]="m.value">{{ m.label }}</option>
                  }
                </select>
              </div>
              <div>
                <label class="block text-sm font-semibold text-slate-700 mb-1">Année</label>
                <input type="number" [(ngModel)]="selectedAnnee" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
              </div>
            </div>
            <div class="flex gap-3 mt-6">
              <button (click)="closeCalculModal()" class="flex-1 px-4 py-2 bg-slate-100 text-slate-700 rounded-lg hover:bg-slate-200 transition font-medium">Annuler</button>
              <button (click)="calculer()" [disabled]="!selectedMois || !selectedAnnee" class="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition font-medium disabled:opacity-50">
                Calculer
              </button>
            </div>
          </div>
        </div>
      }

      <!-- Modal Détails -->
      @if (selectedDeclaration()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center" aria-modal="true">
          <div (click)="closeDetails()" class="fixed inset-0 bg-slate-900/40 backdrop-blur-sm"></div>
          <div class="relative bg-white rounded-xl shadow-2xl p-6 w-full max-w-4xl mx-4 max-h-[90vh] overflow-y-auto">
            <h2 class="text-xl font-bold text-slate-800 mb-4">Détails Déclaration TVA - {{ selectedDeclaration()!.periode }}</h2>
            @let decl = selectedDeclaration()!;
            <div class="grid grid-cols-2 gap-6">
              <div>
                <h3 class="font-bold text-slate-700 mb-3">TVA Collectée</h3>
                <div class="space-y-2 text-sm">
                  <div class="flex justify-between"><span>20%:</span><span class="font-medium">{{ decl.tvaCollectee20 | number:'1.2-2' }}</span></div>
                  <div class="flex justify-between"><span>14%:</span><span class="font-medium">{{ decl.tvaCollectee14 | number:'1.2-2' }}</span></div>
                  <div class="flex justify-between"><span>10%:</span><span class="font-medium">{{ decl.tvaCollectee10 | number:'1.2-2' }}</span></div>
                  <div class="flex justify-between"><span>7%:</span><span class="font-medium">{{ decl.tvaCollectee7 | number:'1.2-2' }}</span></div>
                  <div class="flex justify-between"><span>0%:</span><span class="font-medium">{{ decl.tvaCollectee0 | number:'1.2-2' }}</span></div>
                  <div class="flex justify-between pt-2 border-t font-bold"><span>Total:</span><span>{{ decl.tvaCollecteeTotale | number:'1.2-2' }}</span></div>
                </div>
              </div>
              <div>
                <h3 class="font-bold text-slate-700 mb-3">TVA Déductible</h3>
                <div class="space-y-2 text-sm">
                  <div class="flex justify-between"><span>20%:</span><span class="font-medium">{{ decl.tvaDeductible20 | number:'1.2-2' }}</span></div>
                  <div class="flex justify-between"><span>14%:</span><span class="font-medium">{{ decl.tvaDeductible14 | number:'1.2-2' }}</span></div>
                  <div class="flex justify-between"><span>10%:</span><span class="font-medium">{{ decl.tvaDeductible10 | number:'1.2-2' }}</span></div>
                  <div class="flex justify-between"><span>7%:</span><span class="font-medium">{{ decl.tvaDeductible7 | number:'1.2-2' }}</span></div>
                  <div class="flex justify-between"><span>0%:</span><span class="font-medium">{{ decl.tvaDeductible0 | number:'1.2-2' }}</span></div>
                  <div class="flex justify-between pt-2 border-t font-bold"><span>Total:</span><span>{{ decl.tvaDeductibleTotale | number:'1.2-2' }}</span></div>
                </div>
              </div>
            </div>
            <div class="mt-6 pt-6 border-t">
              <div class="flex justify-between items-center">
                <span class="font-bold text-lg">TVA à Payer:</span>
                <span class="font-bold text-2xl text-blue-600">{{ decl.tvaAPayer | number:'1.2-2' }} MAD</span>
              </div>
              @if (decl.tvaCredit > 0) {
                <div class="flex justify-between items-center mt-2">
                  <span class="font-bold text-lg text-emerald-600">TVA Crédit:</span>
                  <span class="font-bold text-xl text-emerald-600">{{ decl.tvaCredit | number:'1.2-2' }} MAD</span>
                </div>
              }
            </div>
            <button (click)="closeDetails()" class="mt-6 w-full px-4 py-2 bg-slate-100 text-slate-700 rounded-lg hover:bg-slate-200 transition font-medium">Fermer</button>
          </div>
        </div>
      }
    </div>
  `
})
export class TVAComponent implements OnInit {
  private tvaService = inject(TVAService);
  private store = inject(StoreService);

  declarations = signal<DeclarationTVA[]>([]);
  selectedDeclaration = signal<DeclarationTVA | null>(null);
  showCalculModal = signal(false);
  selectedMois: number | null = null;
  selectedAnnee: number = new Date().getFullYear();
  currentYear = signal(new Date().getFullYear());

  moisList = [
    { value: 1, label: 'Janvier' }, { value: 2, label: 'Février' }, { value: 3, label: 'Mars' },
    { value: 4, label: 'Avril' }, { value: 5, label: 'Mai' }, { value: 6, label: 'Juin' },
    { value: 7, label: 'Juillet' }, { value: 8, label: 'Août' }, { value: 9, label: 'Septembre' },
    { value: 10, label: 'Octobre' }, { value: 11, label: 'Novembre' }, { value: 12, label: 'Décembre' }
  ];

  totalCollectee = computed(() => 
    this.declarations().reduce((sum, d) => sum + (d.tvaCollecteeTotale || 0), 0)
  );

  totalDeductible = computed(() => 
    this.declarations().reduce((sum, d) => sum + (d.tvaDeductibleTotale || 0), 0)
  );

  soldeTVA = computed(() => this.totalCollectee() - this.totalDeductible());

  ngOnInit() {
    this.loadDeclarations();
  }

  loadDeclarations() {
    this.tvaService.getDeclarations(this.currentYear()).subscribe({
      next: (data) => this.declarations.set(data),
      error: (err) => this.store.showToast('Erreur lors du chargement', 'error')
    });
  }

  openCalculModal() {
    this.selectedMois = new Date().getMonth() + 1;
    this.selectedAnnee = this.currentYear();
    this.showCalculModal.set(true);
  }

  closeCalculModal() {
    this.showCalculModal.set(false);
  }

  calculer() {
    if (!this.selectedMois || !this.selectedAnnee) return;
    this.tvaService.calculerDeclaration(this.selectedMois, this.selectedAnnee).subscribe({
      next: () => {
        this.store.showToast('Déclaration calculée avec succès', 'success');
        this.closeCalculModal();
        this.loadDeclarations();
      },
      error: (err) => this.store.showToast('Erreur lors du calcul', 'error')
    });
  }

  viewDetails(decl: DeclarationTVA) {
    this.selectedDeclaration.set(decl);
  }

  closeDetails() {
    this.selectedDeclaration.set(null);
  }

  valider(id: string) {
    this.tvaService.validerDeclaration(id).subscribe({
      next: () => {
        this.store.showToast('Déclaration validée', 'success');
        this.loadDeclarations();
      },
      error: (err) => this.store.showToast('Erreur lors de la validation', 'error')
    });
  }

  deposer(id: string) {
    this.tvaService.deposerDeclaration(id).subscribe({
      next: () => {
        this.store.showToast('Déclaration marquée comme déposée', 'success');
        this.loadDeclarations();
      },
      error: (err) => this.store.showToast('Erreur lors du dépôt', 'error')
    });
  }

  getStatutBadgeClass(statut: string): string {
    return statut === 'DEPOSEE' ? 'px-2 py-1 bg-emerald-100 text-emerald-700 rounded-full text-xs font-semibold'
      : statut === 'VALIDEE' ? 'px-2 py-1 bg-blue-100 text-blue-700 rounded-full text-xs font-semibold'
      : 'px-2 py-1 bg-slate-100 text-slate-700 rounded-full text-xs font-semibold';
  }

  getStatutLabel(statut: string): string {
    return statut === 'DEPOSEE' ? 'Déposée' : statut === 'VALIDEE' ? 'Validée' : 'Brouillon';
  }

  formatDate(date: string): string {
    return new Date(date).toLocaleDateString('fr-FR');
  }

  exportDeclarations() {
    this.tvaService.exportDeclarations(this.currentYear()).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `declarations_tva_${this.currentYear()}.xlsx`;
        a.click();
        window.URL.revokeObjectURL(url);
        this.store.showToast('Déclarations exportées avec succès', 'success');
      },
      error: (err) => this.store.showToast('Erreur lors de l\'export', 'error')
    });
  }
}

