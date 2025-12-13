
import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { StoreService, BC } from '../../services/store.service';

@Component({
  selector: 'app-bc-list',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  template: `
    <div class="space-y-6 fade-in-up pb-10">
      
      <!-- Header & Actions -->
      <div class="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h1 class="text-2xl font-bold text-slate-800 font-display">Bandes de Commandes</h1>
          <p class="text-slate-500 text-sm mt-1">Gérez vos commandes clients et fournisseurs.</p>
        </div>
        <div class="flex gap-3 w-full md:w-auto">
          <button (click)="exportGlobal()" class="flex-1 md:flex-none px-4 py-2 bg-white border border-slate-200 text-slate-700 rounded-lg text-sm font-medium hover:bg-slate-50 transition shadow-sm flex items-center justify-center gap-2">
            <svg class="w-4 h-4 text-slate-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"></path></svg>
            Export Global
          </button>
          <a routerLink="/bc/new" class="flex-1 md:flex-none px-5 py-2 bg-blue-600 text-white rounded-lg text-sm font-bold hover:bg-blue-700 transition shadow-lg shadow-blue-600/20 flex items-center justify-center gap-2">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6"></path></svg>
            Créer BC
          </a>
        </div>
      </div>

      <!-- Control Panel (Filters) -->
      <div class="bg-white p-5 rounded-xl shadow-sm border border-slate-100 grid grid-cols-1 md:grid-cols-12 gap-4 items-end">
        
        <div class="md:col-span-3 space-y-1">
          <label class="text-xs font-semibold text-slate-500 uppercase">Recherche</label>
          <div class="relative">
            <span class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
              <svg class="h-4 w-4 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
              </svg>
            </span>
            <input type="text" [(ngModel)]="searchTerm" placeholder="N° BC..." class="w-full pl-9 pr-3 py-2.5 border border-slate-200 rounded-lg text-sm bg-slate-50 focus:bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all outline-none">
          </div>
        </div>

        <div class="md:col-span-2 space-y-1">
          <label class="text-xs font-semibold text-slate-500 uppercase">Fournisseur</label>
          <select [(ngModel)]="filterSupplier" class="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm bg-slate-50 focus:bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all appearance-none cursor-pointer outline-none">
            <option value="">Tous</option>
            @for (sup of store.suppliers(); track sup.id) {
              <option [value]="sup.id">{{ sup.name }}</option>
            }
          </select>
        </div>

        <div class="md:col-span-2 space-y-1">
          <label class="text-xs font-semibold text-slate-500 uppercase">Client</label>
          <select [(ngModel)]="filterClient" class="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm bg-slate-50 focus:bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all appearance-none cursor-pointer outline-none">
            <option value="">Tous</option>
            @for (cli of store.clients(); track cli.id) {
              <option [value]="cli.id">{{ cli.name }}</option>
            }
          </select>
        </div>

        <div class="md:col-span-2 space-y-1">
          <label class="text-xs font-semibold text-slate-500 uppercase">Date Min</label>
          <input type="date" [(ngModel)]="dateMin" class="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm bg-slate-50 focus:bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all outline-none text-slate-600">
        </div>

        <div class="md:col-span-2 space-y-1">
          <label class="text-xs font-semibold text-slate-500 uppercase">Date Max</label>
          <input type="date" [(ngModel)]="dateMax" class="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm bg-slate-50 focus:bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all outline-none text-slate-600">
        </div>

        <div class="md:col-span-1">
          <button (click)="resetFilters()" class="w-full py-2.5 bg-slate-100 text-slate-600 border border-slate-200 rounded-lg text-sm font-medium hover:bg-slate-200 transition flex items-center justify-center" title="Réinitialiser">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"></path></svg>
          </button>
        </div>
      </div>

      <!-- Data Table -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div class="overflow-x-auto">
          <table class="w-full text-sm text-left min-w-[900px]">
            <thead class="text-xs text-slate-500 uppercase bg-slate-50/80 border-b border-slate-200">
              <tr>
                <th class="px-6 py-4 font-semibold tracking-wider">BC Info</th>
                <th class="px-6 py-4 font-semibold tracking-wider">Partenaires</th>
                <th class="px-6 py-4 font-semibold tracking-wider text-right">Achat HT</th>
                <th class="px-6 py-4 font-semibold tracking-wider text-right">Vente HT</th>
                <th class="px-6 py-4 font-semibold tracking-wider text-center">Marge</th>
                <th class="px-6 py-4 font-semibold tracking-wider text-center">Statut</th>
                <th class="px-6 py-4 text-center w-32">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              @for (bc of filteredBcs(); track bc.id) {
                <tr class="bg-white hover:bg-slate-50 transition-colors group" [attr.data-item-id]="bc.id">
                  <td class="px-6 py-4">
                    <div class="flex flex-col">
                      <a [routerLink]="['/bc/edit', bc.id]" class="font-bold text-blue-600 hover:text-blue-800 text-base mb-1">{{ bc.number }}</a>
                      <span class="text-xs text-slate-400 flex items-center gap-1">
                        <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"></path></svg>
                        {{ bc.date }}
                      </span>
                    </div>
                  </td>
                  <td class="px-6 py-4">
                    <div class="flex flex-col gap-1">
                      <span class="text-slate-700 font-medium flex items-center gap-1">
                        <span class="w-1.5 h-1.5 rounded-full bg-indigo-500"></span> {{ store.getClientName(bc.clientId) }}
                      </span>
                      <span class="text-xs text-slate-500 flex items-center gap-1 pl-2.5 border-l-2 border-slate-200 ml-1">
                        Fourn: {{ store.getSupplierName(bc.supplierId) }}
                      </span>
                    </div>
                  </td>
                  
                  <td class="px-6 py-4 text-right text-slate-600 font-medium">{{ formatCurrency(getBuyTotal(bc)) }}</td>
                  <td class="px-6 py-4 text-right text-slate-800 font-bold">{{ formatCurrency(getSellTotal(bc)) }}</td>
                  
                  <td class="px-6 py-4 text-center">
                    <span [class]="getMarginClass(bc)">
                      {{ getMarginPercent(bc) }}%
                    </span>
                  </td>

                  <td class="px-6 py-4 text-center">
                    <span [class]="getStatusClass(bc.status)">
                       {{ bc.status === 'sent' ? 'Envoyée' : (bc.status === 'completed' ? 'Validée' : 'Brouillon') }}
                    </span>
                  </td>

                  <td class="px-6 py-4 text-center">
                     <div class="flex items-center justify-center gap-2 opacity-100 md:opacity-0 group-hover:opacity-100 transition-opacity">
                        <a [routerLink]="['/bc/edit', bc.id]" class="p-2 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-full transition-all" title="Modifier">
                          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"></path></svg>
                        </a>
                        
                        <button (click)="exportPDF(bc)" class="p-2 text-slate-400 hover:text-indigo-600 hover:bg-indigo-50 rounded-full transition-all" title="Exporter PDF">
                          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
                        </button>

                        <button (click)="store.deleteBC(bc.id)" class="p-2 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-full transition-all" title="Supprimer">
                          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path></svg>
                        </button>
                     </div>
                  </td>
                </tr>
              }
              @if (filteredBcs().length === 0) {
                <tr>
                  <td colspan="7" class="px-6 py-16 text-center">
                    <div class="flex flex-col items-center justify-center">
                      <div class="w-16 h-16 bg-slate-50 rounded-full flex items-center justify-center mb-4">
                        <svg class="w-8 h-8 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
                      </div>
                      <h3 class="text-slate-900 font-medium text-lg">Aucun résultat</h3>
                      <p class="text-slate-500 mt-1 max-w-sm">Aucune commande ne correspond à vos filtres.</p>
                      <button (click)="resetFilters()" class="mt-4 px-4 py-2 text-blue-600 hover:bg-blue-50 rounded-lg text-sm font-medium transition">Réinitialiser les filtres</button>
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `
})
export class BcListComponent implements OnInit {
  store = inject(StoreService);

  // Filters State
  searchTerm = signal('');
  filterSupplier = signal('');

  async ngOnInit() {
    if (this.store.bcs().length === 0) {
      await this.store.loadBCs();
    }
  }
  filterClient = signal('');
  dateMin = signal('');
  dateMax = signal('');

  filteredBcs = computed(() => {
    const term = this.searchTerm().toLowerCase();
    const supId = this.filterSupplier();
    const cliId = this.filterClient();
    const dMin = this.dateMin();
    const dMax = this.dateMax();

    return this.store.bcs().filter(bc => {
      const clientName = this.store.getClientName(bc.clientId).toLowerCase();
      const matchesSearch = bc.number.toLowerCase().includes(term) || clientName.includes(term);
      const matchesSup = supId ? bc.supplierId === supId : true;
      const matchesCli = cliId ? bc.clientId === cliId : true;
      
      const bcDate = new Date(bc.date).getTime();
      const matchesMin = dMin ? bcDate >= new Date(dMin).getTime() : true;
      const matchesMax = dMax ? bcDate <= new Date(dMax).getTime() : true;
      
      return matchesSearch && matchesSup && matchesCli && matchesMin && matchesMax;
    });
  });

  resetFilters() {
    this.searchTerm.set('');
    this.filterSupplier.set('');
    this.filterClient.set('');
    this.dateMin.set('');
    this.dateMax.set('');
  }

  async exportPDF(bc: BC) {
    try {
      await this.store.downloadBCPDF(bc.id);
    } catch (error) {
      // Error already handled in store service
    }
  }

  async exportGlobal() {
    try {
      // Préparer les paramètres de filtrage
      const params: any = {};
      
      if (this.filterClient()) {
        params.clientId = this.filterClient();
      }
      if (this.filterSupplier()) {
        params.supplierId = this.filterSupplier();
      }
      if (this.dateMin()) {
        params.dateMin = this.dateMin();
      }
      if (this.dateMax()) {
        params.dateMax = this.dateMax();
      }
      
      // Note: Le statut n'est pas filtré dans l'interface actuelle, mais on pourrait l'ajouter
      
      await this.store.exportBCsToExcel(params);
    } catch (error) {
      // Error already handled in store service
      console.error('Error in exportGlobal:', error);
    }
  }

  formatCurrency(val: number) {
    return new Intl.NumberFormat('fr-MA', { style: 'currency', currency: 'MAD', maximumFractionDigits: 0 }).format(val);
  }

  getBuyTotal(bc: BC): number {
    return bc.items.reduce((acc, i) => acc + (i.qtyBuy * i.priceBuyHT), 0);
  }

  getSellTotal(bc: BC): number {
    return bc.items.reduce((acc, i) => acc + (i.qtySell * i.priceSellHT), 0);
  }

  getMarginPercent(bc: BC): string {
    const buy = this.getBuyTotal(bc);
    const sell = this.getSellTotal(bc);
    if (buy === 0) return '0';
    return (((sell - buy) / buy) * 100).toFixed(1);
  }

  getMarginClass(bc: BC): string {
    const pct = parseFloat(this.getMarginPercent(bc));
    // Badge styles
    if (pct >= 15) return 'bg-emerald-50 text-emerald-700 text-xs font-bold px-2.5 py-1 rounded-full border border-emerald-100 shadow-sm';
    if (pct > 0) return 'bg-amber-50 text-amber-700 text-xs font-bold px-2.5 py-1 rounded-full border border-amber-100 shadow-sm';
    return 'bg-red-50 text-red-700 text-xs font-bold px-2.5 py-1 rounded-full border border-red-100 shadow-sm';
  }

  async deleteBC(bc: BC) {
    if (confirm(`Êtes-vous sûr de vouloir supprimer la commande ${bc.number} ?`)) {
      try {
        await this.store.deleteBC(bc.id);
      } catch (error) {
        // Error already handled in store
      }
    }
  }

  getStatusClass(status: string): string {
     return status === 'completed' 
       ? 'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-emerald-100 text-emerald-800'
       : (status === 'sent' 
          ? 'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800'
          : 'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-800');
  }
}
