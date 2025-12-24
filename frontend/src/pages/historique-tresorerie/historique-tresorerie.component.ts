import { Component, inject, computed, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StoreService, HistoriqueSolde } from '../../services/store.service';

@Component({
  selector: 'app-historique-tresorerie',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6 fade-in-up pb-10">
      <!-- Header -->
      <div class="flex flex-col md:flex-row justify-between items-start md:items-end gap-4 border-b border-slate-200/60 pb-6">
        <div>
          <h1 class="text-2xl md:text-3xl font-bold text-slate-800 font-display">Historique de Trésorerie</h1>
          <p class="text-slate-500 mt-2 text-sm">Historique complet de toutes les transactions de trésorerie</p>
        </div>
        <div class="flex gap-3">
          <button (click)="loadHistorique()" class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition font-medium">
            Actualiser
          </button>
        </div>
      </div>

      <!-- Filtres -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
        <h2 class="text-lg font-bold text-slate-800 mb-4">Filtres</h2>
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-4">
          <div>
            <label class="block text-sm font-semibold text-slate-700 mb-1">Date de début</label>
            <input type="date" [(ngModel)]="dateFrom" (change)="applyFilters()" 
                   class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
          </div>
          <div>
            <label class="block text-sm font-semibold text-slate-700 mb-1">Date de fin</label>
            <input type="date" [(ngModel)]="dateTo" (change)="applyFilters()" 
                   class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
          </div>
          <div>
            <label class="block text-sm font-semibold text-slate-700 mb-1">Type de transaction</label>
            <select [(ngModel)]="selectedType" (change)="applyFilters()" 
                    class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
              <option value="">Tous les types</option>
              <option value="FACTURE_VENTE">Facture Vente</option>
              <option value="FACTURE_ACHAT">Facture Achat</option>
              <option value="PAIEMENT_CLIENT">Paiement Client</option>
              <option value="PAIEMENT_FOURNISSEUR">Paiement Fournisseur</option>
              <option value="CHARGE_IMPOSABLE">Charge Imposable</option>
              <option value="CHARGE_NON_IMPOSABLE">Charge Non Imposable</option>
              <option value="APPORT_EXTERNE">Apport Externe</option>
            </select>
          </div>
          <div>
            <label class="block text-sm font-semibold text-slate-700 mb-1">Partenaire</label>
            <select [(ngModel)]="selectedPartenaireId" (change)="applyFilters()" 
                    class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
              <option value="">Tous les partenaires</option>
              @for (client of store.clients(); track client.id) {
                <option [value]="client.id">Client: {{ client.name }}</option>
              }
              @for (supplier of store.suppliers(); track supplier.id) {
                <option [value]="supplier.id">Fournisseur: {{ supplier.name }}</option>
              }
            </select>
          </div>
        </div>
        <div class="mb-4">
          <label class="block text-sm font-semibold text-slate-700 mb-1">Recherche</label>
          <input type="text" [(ngModel)]="searchTerm" (input)="applyFilters()" 
                 placeholder="Rechercher par référence, partenaire, description..." 
                 class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
        </div>
        <div class="flex gap-3">
          <button (click)="resetFilters()" class="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg hover:bg-slate-200 transition font-medium">
            Réinitialiser
          </button>
          <button (click)="setDefaultPeriod()" class="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg hover:bg-slate-200 transition font-medium">
            Période par défaut (3 mois)
          </button>
        </div>
      </div>

      <!-- Tableau -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div class="p-4 border-b border-slate-100 bg-slate-50/50 flex flex-col md:flex-row justify-between items-center gap-4">
          <div class="text-sm text-slate-600">
            @if (isLoading()) {
              <span>Chargement...</span>
            } @else {
              <span>Affichage de {{ (currentPage() - 1) * pageSize() + 1 }} à {{ Math.min(currentPage() * pageSize(), filteredHistorique().length) }} sur {{ filteredHistorique().length }} résultats</span>
            }
          </div>
          <div class="flex items-center gap-3">
            <label class="text-sm text-slate-600">Par page:</label>
            <select [ngModel]="pageSize()" (ngModelChange)="pageSize.set($event); currentPage.set(1)" 
                    class="px-3 py-1.5 border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-blue-500/20 outline-none">
              <option [value]="10">10</option>
              <option [value]="20">20</option>
              <option [value]="50">50</option>
              <option [value]="100">100</option>
            </select>
            <button (click)="exportExcel()" [disabled]="isLoading() || filteredHistorique().length === 0" 
                    class="px-4 py-2 bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 transition font-medium disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
              </svg>
              Exporter Excel
            </button>
          </div>
        </div>

        @if (isLoading()) {
          <div class="p-12 text-center text-slate-500">
            <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600 mx-auto mb-4"></div>
            <p>Chargement de l'historique...</p>
          </div>
        } @else if (paginatedHistorique().length === 0) {
          <div class="p-12 text-center text-slate-500">
            <p>Aucune transaction trouvée pour les filtres sélectionnés.</p>
          </div>
        } @else {
          <div class="overflow-x-auto">
            <table class="w-full text-sm min-w-[1200px]">
              <thead class="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th class="px-4 py-3 text-left font-semibold text-slate-700">Date</th>
                  <th class="px-4 py-3 text-left font-semibold text-slate-700">Type</th>
                  <th class="px-4 py-3 text-left font-semibold text-slate-700">Partenaire</th>
                  <th class="px-4 py-3 text-left font-semibold text-slate-700">Référence</th>
                  <th class="px-4 py-3 text-right font-semibold text-slate-700">Montant</th>
                  <th class="px-4 py-3 text-right font-semibold text-slate-700">Solde Global Avant</th>
                  <th class="px-4 py-3 text-right font-semibold text-slate-700">Solde Global Après</th>
                  <th class="px-4 py-3 text-right font-semibold text-slate-700">Solde Partenaire Avant</th>
                  <th class="px-4 py-3 text-right font-semibold text-slate-700">Solde Partenaire Après</th>
                  <th class="px-4 py-3 text-left font-semibold text-slate-700">Description</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                @for (item of paginatedHistorique(); track item.id) {
                  <tr class="hover:bg-slate-50">
                    <td class="px-4 py-3 text-sm">{{ formatDate(item.date) }}</td>
                    <td class="px-4 py-3">
                      <span [class]="getTypeBadgeClass(item.type)">
                        {{ getTypeLabel(item.type) }}
                      </span>
                    </td>
                    <td class="px-4 py-3 text-sm">
                      {{ item.partenaireNom || '-' }}
                    </td>
                    <td class="px-4 py-3 text-sm font-medium">{{ item.referenceNumero || '-' }}</td>
                    <td class="px-4 py-3 text-right font-bold" [class]="getAmountClass(item.type)">
                      {{ item.montant | number:'1.2-2' }} MAD
                    </td>
                    <td class="px-4 py-3 text-right text-sm">
                      {{ item.soldeGlobalAvant | number:'1.2-2' }} MAD
                    </td>
                    <td class="px-4 py-3 text-right font-semibold" 
                        [class.text-emerald-600]="(item.soldeGlobalApres || 0) >= 0" 
                        [class.text-red-600]="(item.soldeGlobalApres || 0) < 0">
                      {{ item.soldeGlobalApres | number:'1.2-2' }} MAD
                    </td>
                    <td class="px-4 py-3 text-right text-sm">
                      {{ item.soldePartenaireAvant != null ? (item.soldePartenaireAvant | number:'1.2-2') + ' MAD' : '-' }}
                    </td>
                    <td class="px-4 py-3 text-right text-sm">
                      {{ item.soldePartenaireApres != null ? (item.soldePartenaireApres | number:'1.2-2') + ' MAD' : '-' }}
                    </td>
                    <td class="px-4 py-3 text-sm text-slate-500">{{ item.description || '-' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <!-- Pagination -->
          <div class="p-4 border-t border-slate-200 bg-slate-50 flex items-center justify-between">
            <div class="text-xs text-slate-500">
              Page {{ currentPage() }} sur {{ totalPages() || 1 }}
            </div>
            <div class="flex items-center gap-2">
              <button (click)="prevPage()" [disabled]="currentPage() === 1" 
                      class="p-2 border border-slate-200 rounded-lg hover:bg-white disabled:opacity-50 disabled:cursor-not-allowed">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"></path>
                </svg>
              </button>
              <button (click)="nextPage()" [disabled]="currentPage() === totalPages() || totalPages() === 0" 
                      class="p-2 border border-slate-200 rounded-lg hover:bg-white disabled:opacity-50 disabled:cursor-not-allowed">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"></path>
                </svg>
              </button>
            </div>
          </div>
        }
      </div>
    </div>
  `
})
export class HistoriqueTresorerieComponent implements OnInit {
  store = inject(StoreService);
  
  // Filtres
  dateFrom = signal<string>('');
  dateTo = signal<string>('');
  selectedType = signal<string>('');
  selectedPartenaireId = signal<string>('');
  searchTerm = signal<string>('');
  
  // Pagination
  currentPage = signal<number>(1);
  pageSize = signal<number>(20);
  
  // État
  isLoading = signal<boolean>(false);
  
  // Exposer Math pour le template
  Math = Math;
  
  totalPages = computed(() => {
    return Math.ceil(this.filteredHistorique().length / this.pageSize());
  });
  
  filteredHistorique = computed(() => {
    let items = this.store.historiqueSolde();
    
    // Filtre par recherche
    const search = this.searchTerm().toLowerCase();
    if (search) {
      items = items.filter(item => 
        (item.referenceNumero?.toLowerCase().includes(search)) ||
        (item.partenaireNom?.toLowerCase().includes(search)) ||
        (item.description?.toLowerCase().includes(search))
      );
    }
    
    return items;
  });
  
  paginatedHistorique = computed(() => {
    const filtered = this.filteredHistorique();
    const start = (this.currentPage() - 1) * this.pageSize();
    const end = start + this.pageSize();
    return filtered.slice(start, end);
  });
  
  ngOnInit() {
    this.setDefaultPeriod();
    this.loadHistorique();
  }
  
  setDefaultPeriod() {
    const today = new Date();
    this.dateTo.set(today.toISOString().split('T')[0]);
    const threeMonthsAgo = new Date(today);
    threeMonthsAgo.setMonth(threeMonthsAgo.getMonth() - 3);
    this.dateFrom.set(threeMonthsAgo.toISOString().split('T')[0]);
  }
  
  async loadHistorique() {
    this.isLoading.set(true);
    try {
      const dateDebut = this.dateFrom() ? new Date(this.dateFrom()).toISOString() : undefined;
      const dateFin = this.dateTo() ? new Date(this.dateTo() + 'T23:59:59').toISOString() : undefined;
      
      let partenaireType: string | undefined;
      if (this.selectedPartenaireId()) {
        const client = this.store.clients().find(c => c.id === this.selectedPartenaireId());
        partenaireType = client ? 'CLIENT' : 'FOURNISSEUR';
      }
      
      await this.store.loadHistoriqueSolde(
        this.selectedPartenaireId() || undefined,
        partenaireType,
        this.selectedType() || undefined,
        dateDebut,
        dateFin
      );
      this.currentPage.set(1);
    } catch (error) {
      console.error('Error loading historique:', error);
      this.store.showToast('Erreur lors du chargement de l\'historique', 'error');
    } finally {
      this.isLoading.set(false);
    }
  }
  
  applyFilters() {
    this.currentPage.set(1);
    this.loadHistorique();
  }
  
  resetFilters() {
    this.selectedType.set('');
    this.selectedPartenaireId.set('');
    this.searchTerm.set('');
    this.setDefaultPeriod();
    this.applyFilters();
  }
  
  prevPage() {
    if (this.currentPage() > 1) {
      this.currentPage.set(this.currentPage() - 1);
    }
  }
  
  nextPage() {
    if (this.currentPage() < this.totalPages()) {
      this.currentPage.set(this.currentPage() + 1);
    }
  }
  
  formatDate(dateStr: string): string {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    return date.toLocaleDateString('fr-FR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }
  
  getTypeLabel(type: string): string {
    const labels: Record<string, string> = {
      'FACTURE_VENTE': 'Facture Vente',
      'FACTURE_ACHAT': 'Facture Achat',
      'PAIEMENT_CLIENT': 'Paiement Client',
      'PAIEMENT_FOURNISSEUR': 'Paiement Fournisseur',
      'CHARGE_IMPOSABLE': 'Charge Imposable',
      'CHARGE_NON_IMPOSABLE': 'Charge Non Imposable',
      'APPORT_EXTERNE': 'Apport Externe'
    };
    return labels[type] || type;
  }
  
  getTypeBadgeClass(type: string): string {
    const baseClass = 'px-2 py-1 rounded text-xs font-medium';
    const classes: Record<string, string> = {
      'FACTURE_VENTE': `${baseClass} bg-emerald-50 text-emerald-700`,
      'FACTURE_ACHAT': `${baseClass} bg-red-50 text-red-700`,
      'PAIEMENT_CLIENT': `${baseClass} bg-blue-50 text-blue-700`,
      'PAIEMENT_FOURNISSEUR': `${baseClass} bg-orange-50 text-orange-700`,
      'CHARGE_IMPOSABLE': `${baseClass} bg-amber-50 text-amber-700`,
      'CHARGE_NON_IMPOSABLE': `${baseClass} bg-slate-100 text-slate-700`,
      'APPORT_EXTERNE': `${baseClass} bg-purple-50 text-purple-700`
    };
    return classes[type] || `${baseClass} bg-slate-50 text-slate-700`;
  }
  
  getAmountClass(type: string): string {
    const classes: Record<string, string> = {
      'FACTURE_VENTE': 'text-emerald-600',
      'FACTURE_ACHAT': 'text-red-600',
      'PAIEMENT_CLIENT': 'text-emerald-600',
      'PAIEMENT_FOURNISSEUR': 'text-red-600',
      'CHARGE_IMPOSABLE': 'text-red-600',
      'CHARGE_NON_IMPOSABLE': 'text-red-600',
      'APPORT_EXTERNE': 'text-purple-600'
    };
    return classes[type] || 'text-slate-800';
  }
  
  async exportExcel() {
    try {
      const dateDebut = this.dateFrom() ? new Date(this.dateFrom()).toISOString() : undefined;
      const dateFin = this.dateTo() ? new Date(this.dateTo() + 'T23:59:59').toISOString() : undefined;
      
      let partenaireType: string | undefined;
      if (this.selectedPartenaireId()) {
        const client = this.store.clients().find(c => c.id === this.selectedPartenaireId());
        partenaireType = client ? 'CLIENT' : 'FOURNISSEUR';
      }
      
      await this.store.exportHistoriqueTresorerieExcel(
        this.selectedPartenaireId() || undefined,
        partenaireType,
        this.selectedType() || undefined,
        dateDebut,
        dateFin
      );
    } catch (error) {
      console.error('Error exporting Excel:', error);
      this.store.showToast('Erreur lors de l\'export Excel', 'error');
    }
  }
}

