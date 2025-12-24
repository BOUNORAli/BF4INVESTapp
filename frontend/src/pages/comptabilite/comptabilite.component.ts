import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ComptabiliteService } from '../../services/comptabilite.service';
import { StoreService } from '../../services/store.service';
import type { CompteComptable, EcritureComptable } from '../../models/types';

@Component({
  selector: 'app-comptabilite',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6 fade-in-up pb-10">
      <!-- Header -->
      <div class="flex flex-col md:flex-row justify-between items-start md:items-end gap-4 border-b border-slate-200/60 pb-6">
        <div>
          <h1 class="text-2xl md:text-3xl font-bold text-slate-800 font-display">Comptabilité</h1>
          <p class="text-slate-500 mt-2 text-sm">États financiers et comptables</p>
        </div>
      </div>

      <!-- Tabs -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div class="border-b border-slate-200 flex overflow-x-auto">
          <button (click)="activeTab.set('balance')" [class]="activeTab() === 'balance' ? 'border-b-2 border-blue-600 text-blue-600' : 'text-slate-600'"
                  class="px-6 py-4 font-semibold whitespace-nowrap hover:bg-slate-50 transition">
            Balance
          </button>
          <button (click)="activeTab.set('grand-livre')" [class]="activeTab() === 'grand-livre' ? 'border-b-2 border-blue-600 text-blue-600' : 'text-slate-600'"
                  class="px-6 py-4 font-semibold whitespace-nowrap hover:bg-slate-50 transition">
            Grand Livre
          </button>
          <button (click)="activeTab.set('bilan')" [class]="activeTab() === 'bilan' ? 'border-b-2 border-blue-600 text-blue-600' : 'text-slate-600'"
                  class="px-6 py-4 font-semibold whitespace-nowrap hover:bg-slate-50 transition">
            Bilan
          </button>
          <button (click)="activeTab.set('cpc')" [class]="activeTab() === 'cpc' ? 'border-b-2 border-blue-600 text-blue-600' : 'text-slate-600'"
                  class="px-6 py-4 font-semibold whitespace-nowrap hover:bg-slate-50 transition">
            CPC
          </button>
        </div>

        <!-- Balance Tab -->
        @if (activeTab() === 'balance') {
          <div class="p-6">
            <div class="mb-4 flex gap-4">
              <input type="date" [(ngModel)]="dateDebut" (change)="loadBalance()" class="px-4 py-2 border border-slate-200 rounded-lg">
              <input type="date" [(ngModel)]="dateFin" (change)="loadBalance()" class="px-4 py-2 border border-slate-200 rounded-lg">
              <button (click)="loadBalance()" class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition">Charger</button>
              <button (click)="exportBalance()" [disabled]="balance().length === 0" class="px-4 py-2 bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 transition disabled:opacity-50 flex items-center gap-2">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
                Exporter Excel
              </button>
            </div>
            @if (balance().length > 0) {
              <div class="overflow-x-auto">
                <table class="w-full text-sm min-w-[1000px]">
                  <thead class="bg-slate-50 border-b border-slate-200">
                    <tr>
                      <th class="px-4 py-3 text-left font-semibold text-slate-700">Code</th>
                      <th class="px-4 py-3 text-left font-semibold text-slate-700">Libellé</th>
                      <th class="px-4 py-3 text-right font-semibold text-slate-700">Débit</th>
                      <th class="px-4 py-3 text-right font-semibold text-slate-700">Crédit</th>
                      <th class="px-4 py-3 text-right font-semibold text-slate-700">Solde</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-slate-100">
                    @for (compte of balance(); track compte.code) {
                      <tr class="hover:bg-slate-50">
                        <td class="px-4 py-3 font-mono">{{ compte.code }}</td>
                        <td class="px-4 py-3">{{ compte.libelle }}</td>
                        <td class="px-4 py-3 text-right">{{ compte.soldeDebit | number:'1.2-2' }}</td>
                        <td class="px-4 py-3 text-right">{{ compte.soldeCredit | number:'1.2-2' }}</td>
                        <td class="px-4 py-3 text-right font-bold" [class.text-blue-600]="compte.solde > 0" [class.text-red-600]="compte.solde < 0">
                          {{ compte.solde | number:'1.2-2' }}
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>
        }

        <!-- Grand Livre Tab -->
        @if (activeTab() === 'grand-livre') {
          <div class="p-6">
            <div class="mb-4 flex gap-4">
              <select [(ngModel)]="selectedCompteCode" class="px-4 py-2 border border-slate-200 rounded-lg">
                <option value="">Sélectionner un compte...</option>
                @for (c of comptes(); track c.code) {
                  <option [value]="c.code">{{ c.code }} - {{ c.libelle }}</option>
                }
              </select>
              <input type="date" [(ngModel)]="dateDebut" class="px-4 py-2 border border-slate-200 rounded-lg">
              <input type="date" [(ngModel)]="dateFin" class="px-4 py-2 border border-slate-200 rounded-lg">
              <button (click)="loadGrandLivre()" [disabled]="!selectedCompteCode" class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition disabled:opacity-50">Charger</button>
              <button (click)="exportGrandLivre()" [disabled]="!selectedCompteCode || grandLivre().length === 0" class="px-4 py-2 bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 transition disabled:opacity-50 flex items-center gap-2">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
                Exporter Excel
              </button>
            </div>
            @if (grandLivre().length > 0) {
              <div class="overflow-x-auto">
                <table class="w-full text-sm min-w-[1000px]">
                  <thead class="bg-slate-50 border-b border-slate-200">
                    <tr>
                      <th class="px-4 py-3 text-left font-semibold text-slate-700">Date</th>
                      <th class="px-4 py-3 text-left font-semibold text-slate-700">Journal</th>
                      <th class="px-4 py-3 text-left font-semibold text-slate-700">Pièce</th>
                      <th class="px-4 py-3 text-left font-semibold text-slate-700">Libellé</th>
                      <th class="px-4 py-3 text-right font-semibold text-slate-700">Débit</th>
                      <th class="px-4 py-3 text-right font-semibold text-slate-700">Crédit</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-slate-100">
                    @for (ecriture of grandLivre(); track ecriture.id) {
                      @for (ligne of ecriture.lignes; track ligne.compteCode + ligne.libelle) {
                        @if (ligne.compteCode === selectedCompteCode) {
                          <tr class="hover:bg-slate-50">
                            <td class="px-4 py-3">{{ formatDate(ecriture.dateEcriture) }}</td>
                            <td class="px-4 py-3">{{ ecriture.journal }}</td>
                            <td class="px-4 py-3 font-mono">{{ ecriture.numeroPiece }}</td>
                            <td class="px-4 py-3">{{ ligne.libelle }}</td>
                            <td class="px-4 py-3 text-right">{{ ligne.debit | number:'1.2-2' }}</td>
                            <td class="px-4 py-3 text-right">{{ ligne.credit | number:'1.2-2' }}</td>
                          </tr>
                        }
                      }
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>
        }

        <!-- Bilan Tab -->
        @if (activeTab() === 'bilan') {
          <div class="p-6">
            <div class="mb-4 flex gap-4">
              <input type="date" [(ngModel)]="bilanDate" (change)="loadBilan()" class="px-4 py-2 border border-slate-200 rounded-lg">
              <button (click)="loadBilan()" class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition">Charger</button>
            </div>
            @if (bilanData()) {
              @let bilan = bilanData()!;
              <div class="grid grid-cols-2 gap-6">
                <div>
                  <h3 class="font-bold text-lg mb-4">ACTIF</h3>
                  <div class="space-y-2">
                    <div class="flex justify-between"><span>Immobilisations:</span><span class="font-medium">{{ bilan.actifImmobilise | number:'1.2-2' }}</span></div>
                    <div class="flex justify-between"><span>Actif circulant:</span><span class="font-medium">{{ bilan.actifCirculant | number:'1.2-2' }}</span></div>
                    <div class="flex justify-between"><span>Créances:</span><span class="font-medium">{{ bilan.creances | number:'1.2-2' }}</span></div>
                    <div class="flex justify-between pt-2 border-t font-bold"><span>TOTAL ACTIF:</span><span>{{ bilan.totalActif | number:'1.2-2' }}</span></div>
                  </div>
                </div>
                <div>
                  <h3 class="font-bold text-lg mb-4">PASSIF</h3>
                  <div class="space-y-2">
                    <div class="flex justify-between"><span>Capitaux propres:</span><span class="font-medium">{{ bilan.capitauxPropres | number:'1.2-2' }}</span></div>
                    <div class="flex justify-between"><span>Dettes:</span><span class="font-medium">{{ bilan.dettes | number:'1.2-2' }}</span></div>
                    <div class="flex justify-between pt-2 border-t font-bold"><span>TOTAL PASSIF:</span><span>{{ bilan.totalPassif | number:'1.2-2' }}</span></div>
                    <div class="flex justify-between pt-2 border-t font-bold text-blue-600"><span>Résultat:</span><span>{{ bilan.resultat | number:'1.2-2' }}</span></div>
                  </div>
                </div>
              </div>
            }
          </div>
        }

        <!-- CPC Tab -->
        @if (activeTab() === 'cpc') {
          <div class="p-6">
            <div class="mb-4 flex gap-4">
              <input type="date" [(ngModel)]="dateDebut" class="px-4 py-2 border border-slate-200 rounded-lg">
              <input type="date" [(ngModel)]="dateFin" class="px-4 py-2 border border-slate-200 rounded-lg">
              <button (click)="loadCPC()" class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition">Charger</button>
            </div>
            @if (cpcData()) {
              @let cpc = cpcData()!;
              <div class="space-y-4">
                <div>
                  <h3 class="font-bold text-lg mb-3">PRODUITS</h3>
                  <div class="space-y-2">
                    <div class="flex justify-between"><span>Produits d'exploitation:</span><span class="font-medium">{{ cpc.produitsExploitation | number:'1.2-2' }}</span></div>
                    <div class="flex justify-between"><span>Produits financiers:</span><span class="font-medium">{{ cpc.produitsFinanciers | number:'1.2-2' }}</span></div>
                  </div>
                </div>
                <div>
                  <h3 class="font-bold text-lg mb-3">CHARGES</h3>
                  <div class="space-y-2">
                    <div class="flex justify-between"><span>Charges d'exploitation:</span><span class="font-medium">{{ cpc.chargesExploitation | number:'1.2-2' }}</span></div>
                    <div class="flex justify-between"><span>Charges financières:</span><span class="font-medium">{{ cpc.chargesFinancieres | number:'1.2-2' }}</span></div>
                    <div class="flex justify-between"><span>Impôts sur les bénéfices:</span><span class="font-medium">{{ cpc.impotBenefices | number:'1.2-2' }}</span></div>
                  </div>
                </div>
                <div class="pt-4 border-t space-y-2">
                  <div class="flex justify-between"><span>Résultat d'exploitation:</span><span class="font-bold">{{ cpc.resultatExploitation | number:'1.2-2' }}</span></div>
                  <div class="flex justify-between"><span>Résultat financier:</span><span class="font-bold">{{ cpc.resultatFinancier | number:'1.2-2' }}</span></div>
                  <div class="flex justify-between"><span>Résultat courant:</span><span class="font-bold">{{ cpc.resultatCourant | number:'1.2-2' }}</span></div>
                  <div class="flex justify-between pt-2 border-t font-bold text-2xl text-blue-600"><span>Résultat net:</span><span>{{ cpc.resultatNet | number:'1.2-2' }}</span></div>
                </div>
              </div>
            }
          </div>
        }
      </div>
    </div>
  `
})
export class ComptabiliteComponent implements OnInit {
  private comptabiliteService = inject(ComptabiliteService);
  private store = inject(StoreService);

  activeTab = signal<'balance' | 'grand-livre' | 'bilan' | 'cpc'>('balance');
  comptes = signal<CompteComptable[]>([]);
  balance = signal<CompteComptable[]>([]);
  grandLivre = signal<EcritureComptable[]>([]);
  bilanData = signal<any>(null);
  cpcData = signal<any>(null);
  selectedCompteCode: string = '';
  dateDebut: string = new Date(new Date().getFullYear(), 0, 1).toISOString().split('T')[0];
  dateFin: string = new Date().toISOString().split('T')[0];
  bilanDate: string = new Date().toISOString().split('T')[0];

  ngOnInit() {
    this.loadComptes();
    this.loadBalance();
  }

  loadComptes() {
    this.comptabiliteService.getComptes().subscribe({
      next: (data) => this.comptes.set(data),
      error: (err) => this.store.showToast('Erreur lors du chargement des comptes', 'error')
    });
  }

  loadBalance() {
    this.comptabiliteService.getBalance({ dateDebut: this.dateDebut, dateFin: this.dateFin }).subscribe({
      next: (data) => this.balance.set(data),
      error: (err) => this.store.showToast('Erreur lors du chargement de la balance', 'error')
    });
  }

  loadGrandLivre() {
    if (!this.selectedCompteCode) return;
    this.comptabiliteService.getGrandLivre(this.selectedCompteCode, { dateDebut: this.dateDebut, dateFin: this.dateFin }).subscribe({
      next: (data) => this.grandLivre.set(data),
      error: (err) => this.store.showToast('Erreur lors du chargement du grand livre', 'error')
    });
  }

  loadBilan() {
    this.comptabiliteService.getBilan(this.bilanDate).subscribe({
      next: (data) => this.bilanData.set(data),
      error: (err) => this.store.showToast('Erreur lors du calcul du bilan', 'error')
    });
  }

  loadCPC() {
    this.comptabiliteService.getCPC({ dateDebut: this.dateDebut, dateFin: this.dateFin }).subscribe({
      next: (data) => this.cpcData.set(data),
      error: (err) => this.store.showToast('Erreur lors du calcul du CPC', 'error')
    });
  }

  formatDate(date: string): string {
    return new Date(date).toLocaleDateString('fr-FR');
  }

  exportBalance() {
    this.comptabiliteService.exportBalance({ dateDebut: this.dateDebut, dateFin: this.dateFin }).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `balance_${this.dateDebut}_${this.dateFin}.xlsx`;
        a.click();
        window.URL.revokeObjectURL(url);
        this.store.showToast('Balance exportée avec succès', 'success');
      },
      error: (err) => this.store.showToast('Erreur lors de l\'export', 'error')
    });
  }

  exportGrandLivre() {
    if (!this.selectedCompteCode) return;
    this.comptabiliteService.exportGrandLivre(this.selectedCompteCode, { dateDebut: this.dateDebut, dateFin: this.dateFin }).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `grand_livre_${this.selectedCompteCode}.xlsx`;
        a.click();
        window.URL.revokeObjectURL(url);
        this.store.showToast('Grand livre exporté avec succès', 'success');
      },
      error: (err) => this.store.showToast('Erreur lors de l\'export', 'error')
    });
  }
}

