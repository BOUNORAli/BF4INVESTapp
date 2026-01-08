
import { Component, inject, signal, computed, HostListener, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { StoreService, BC } from '../../services/store.service';
import { BCStore } from '../../stores/bc.store';
import { PartnerStore } from '../../stores/partner.store';
import { NavigationRefreshService } from '../../services/navigation-refresh.service';
import { SkeletonTableComponent } from '../../components/skeleton/skeleton-table.component';

@Component({
  selector: 'app-bc-list',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, SkeletonTableComponent],
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

        <div class="md:col-span-2 space-y-1">
          <label class="text-xs font-semibold text-slate-500 uppercase">Tri par Date</label>
          <select [(ngModel)]="sortOrder" class="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm bg-slate-50 focus:bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all appearance-none cursor-pointer outline-none">
            <option value="desc">Date ↓ (Récent)</option>
            <option value="asc">Date ↑ (Ancien)</option>
          </select>
        </div>

        <div class="md:col-span-1">
          <button (click)="resetFilters()" class="w-full py-2.5 bg-slate-100 text-slate-600 border border-slate-200 rounded-lg text-sm font-medium hover:bg-slate-200 transition flex items-center justify-center" title="Réinitialiser">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"></path></svg>
          </button>
        </div>
      </div>

      <!-- Totaux Globaux -->
      <div class="bg-white p-4 rounded-xl shadow-sm border border-slate-100 flex flex-wrap gap-3 items-center">
        <div class="flex items-center gap-2 px-3 py-1.5 bg-blue-50 rounded-lg border border-blue-200">
          <span class="text-xs font-semibold text-blue-700 uppercase">Total Vente HT:</span>
          <span class="text-sm font-bold text-blue-800">{{ totalGlobal() | number:'1.2-2' }} MAD</span>
        </div>
        <div class="flex items-center gap-2 px-3 py-1.5 bg-orange-50 rounded-lg border border-orange-200">
          <span class="text-xs font-semibold text-orange-700 uppercase">Total Achat HT:</span>
          <span class="text-sm font-bold text-orange-800">{{ totalAchat() | number:'1.2-2' }} MAD</span>
        </div>
        <div class="flex items-center gap-2 px-3 py-1.5 rounded-lg border" 
             [class.bg-emerald-50]="solde() >= 0" 
             [class.border-emerald-200]="solde() >= 0"
             [class.bg-red-50]="solde() < 0"
             [class.border-red-200]="solde() < 0">
          <span class="text-xs font-semibold uppercase" [class.text-emerald-700]="solde() >= 0" [class.text-red-700]="solde() < 0">Marge:</span>
          <span class="text-sm font-bold" [class.text-emerald-700]="solde() >= 0" [class.text-red-700]="solde() < 0">{{ solde() | number:'1.2-2' }} MAD</span>
        </div>
      </div>

      <!-- Data Table -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        @if (bcStore.loading()) {
          <app-skeleton-table [columns]="[
            { width: '20%' },
            { width: '25%' },
            { width: '15%', align: 'right' },
            { width: '15%', align: 'right' },
            { width: '10%', align: 'center' },
            { width: '10%', align: 'center' },
            { width: '5%', align: 'center' }
          ]" [rows]="10"></app-skeleton-table>
        } @else {
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
              @for (bc of paginatedBcs(); track bc.id) {
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
                    <div class="flex flex-col gap-1.5">
                      <!-- Clients (multi ou unique) -->
                      @if (getClientIds(bc).length > 0) {
                        <div class="flex flex-wrap gap-1">
                          @for (clientId of getClientIds(bc); track clientId; let i = $index) {
                            @if (i < 3) {
                              <span class="inline-flex items-center gap-1 px-2 py-0.5 bg-blue-50 text-blue-700 rounded-full text-xs font-medium">
                                <span class="w-1.5 h-1.5 rounded-full bg-blue-500"></span>
                                {{ store.getClientName(clientId) }}
                              </span>
                            }
                          }
                          @if (getClientIds(bc).length > 3) {
                            <span class="px-2 py-0.5 bg-slate-100 text-slate-600 rounded-full text-xs font-medium">
                              +{{ getClientIds(bc).length - 3 }}
                            </span>
                          }
                        </div>
                      } @else {
                        <span class="text-xs text-slate-400">Aucun client</span>
                      }
                      <!-- Fournisseur -->
                      <span class="text-xs text-slate-500 flex items-center gap-1 pl-1">
                        <svg class="w-3 h-3 text-orange-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4"></path></svg>
                        {{ store.getSupplierName(bc.supplierId) }}
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
                     <div class="flex items-center justify-center gap-2 opacity-100 md:opacity-0 group-hover:opacity-100 transition-opacity relative">
                        <!-- Actions Directes -->
                        <a [routerLink]="['/bc/edit', bc.id]" class="p-2 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-full transition-all" title="Voir/Modifier">
                          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"></path></svg>
                        </a>
                        <button (click)="exportPDF(bc)" class="p-2 text-slate-400 hover:text-indigo-600 hover:bg-indigo-50 rounded-full transition-all" title="Exporter PDF">
                          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
                        </button>
                        
                        <!-- Menu Dropdown -->
                        <div class="relative">
                          <button (click)="toggleDropdown(bc.id)" class="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-50 rounded-full transition-all" title="Plus d'actions">
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z"></path></svg>
                          </button>
                          
                          @if (openDropdownId() === bc.id) {
                            <div class="absolute right-0 mt-2 w-56 bg-white rounded-lg shadow-lg border border-slate-200 z-50 py-1">
                              <button (click)="viewAuditLog(bc); closeDropdown()" class="w-full px-4 py-2 text-left text-sm text-slate-700 hover:bg-slate-50 flex items-center gap-3">
                                <svg class="w-4 h-4 text-amber-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
                                <span>Journal d'activité</span>
                              </button>
                              <button (click)="viewLinkedInvoices(bc); closeDropdown()" class="w-full px-4 py-2 text-left text-sm text-slate-700 hover:bg-slate-50 flex items-center gap-3">
                                <svg class="w-4 h-4 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
                                <span>Factures liées</span>
                              </button>
                              <button (click)="viewLinkedSalesInvoices(bc); closeDropdown()" class="w-full px-4 py-2 text-left text-sm text-slate-700 hover:bg-slate-50 flex items-center gap-3">
                                <svg class="w-4 h-4 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
                                <span>Factures ventes liées</span>
                              </button>
                              <div class="border-t border-slate-200 my-1"></div>
                              <button (click)="store.deleteBC(bc.id); closeDropdown()" class="w-full px-4 py-2 text-left text-sm text-red-600 hover:bg-red-50 flex items-center gap-3">
                                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path></svg>
                                <span>Supprimer</span>
                              </button>
                            </div>
                          }
                        </div>
                     </div>
                  </td>
                </tr>
              }
              @if (paginatedBcs().length === 0) {
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
        }
        
        @if (isRefreshing() && !bcStore.loading()) {
          <div class="p-4 border-t border-slate-200 bg-blue-50/50 flex items-center justify-center gap-2 text-sm text-blue-600">
            <svg class="w-4 h-4 animate-spin" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"></path>
            </svg>
            <span>Actualisation des données...</span>
          </div>
        }

        <!-- Pagination Controls -->
        @if (filteredBcs().length > 0) {
          <div class="p-4 border-t border-slate-200 bg-slate-50 flex items-center justify-between">
            <div class="text-xs text-slate-500">
              Affichage de {{ (currentPage() - 1) * pageSize() + 1 }} à {{ Math.min(currentPage() * pageSize(), filteredBcs().length) }} sur {{ filteredBcs().length }} résultats
            </div>
            <div class="flex items-center gap-3">
              <div class="flex items-center gap-3">
                <label class="text-sm text-slate-600">Par page:</label>
                <select [ngModel]="pageSize()" (ngModelChange)="pageSize.set($event); currentPage.set(1)"
                        class="px-3 py-1.5 border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-blue-500/20 outline-none">
                  <option [value]="10">10</option>
                  <option [value]="20">20</option>
                  <option [value]="50">50</option>
                  <option [value]="100">100</option>
                </select>
              </div>
              <div class="flex items-center gap-2">
                <button (click)="prevPage()" [disabled]="currentPage() === 1" class="p-2 border border-slate-200 rounded-lg hover:bg-white disabled:opacity-50 disabled:cursor-not-allowed transition">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"></path></svg>
                </button>
                <span class="text-sm font-medium text-slate-700">Page {{ currentPage() }} sur {{ totalPages() || 1 }}</span>
                <button (click)="nextPage()" [disabled]="currentPage() === totalPages() || totalPages() === 0" class="p-2 border border-slate-200 rounded-lg hover:bg-white disabled:opacity-50 disabled:cursor-not-allowed transition">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"></path></svg>
                </button>
              </div>
            </div>
          </div>
        }
      </div>
    </div>
  `
})
export class BcListComponent implements OnInit {
  store = inject(StoreService);
  bcStore = inject(BCStore);
  partnerStore = inject(PartnerStore);
  navigationRefresh = inject(NavigationRefreshService);
  router = inject(Router);
  Math = Math; // Expose Math for template
  
  readonly isRefreshing = computed(() => this.navigationRefresh.isRefreshing() || this.bcStore.refreshing());

  // Filters State
  searchTerm = signal('');
  filterSupplier = signal('');
  filterClient = signal('');
  dateMin = signal('');
  dateMax = signal('');
  sortOrder = signal<'asc' | 'desc'>('desc'); // Tri par date

  // Pagination State
  currentPage = signal(1);
  pageSize = signal(10);
  
  // Dropdown State
  openDropdownId = signal<string | null>(null);

  filteredBcs = computed(() => {
    const term = this.searchTerm().toLowerCase();
    const supId = this.filterSupplier();
    const cliId = this.filterClient();
    const dMin = this.dateMin();
    const dMax = this.dateMax();
    const sort = this.sortOrder();

    let filtered = this.bcStore.bcs().filter(bc => {
      // Recherche dans le numéro BC et les noms des clients
      const clientIds = this.getClientIds(bc);
      const clientNames = clientIds.map(id => this.store.getClientName(id).toLowerCase());
      const matchesSearch = bc.number.toLowerCase().includes(term) || 
                           clientNames.some(name => name.includes(term));
      
      const matchesSup = supId ? bc.supplierId === supId : true;
      
      // Filtrer par client (nouveau: cherche dans tous les clients du BC)
      const matchesCli = cliId ? clientIds.includes(cliId) : true;
      
      const bcDate = new Date(bc.date).getTime();
      const matchesMin = dMin ? bcDate >= new Date(dMin).getTime() : true;
      const matchesMax = dMax ? bcDate <= new Date(dMax).getTime() : true;
      
      return matchesSearch && matchesSup && matchesCli && matchesMin && matchesMax;
    });
    
    // Trier par date selon sortOrder
    filtered.sort((a, b) => {
      const dateA = new Date(a.date).getTime();
      const dateB = new Date(b.date).getTime();
      return sort === 'desc' ? dateB - dateA : dateA - dateB;
    });
    
    return filtered;
  });

  totalPages = computed(() => {
    return Math.ceil(this.filteredBcs().length / this.pageSize());
  });

  // Totaux globaux basés sur les BC filtrés
  totalGlobal = computed(() => {
    const bcs = this.filteredBcs();
    return bcs.reduce((acc, bc) => acc + this.getSellTotal(bc), 0);
  });

  totalAchat = computed(() => {
    const bcs = this.filteredBcs();
    return bcs.reduce((acc, bc) => acc + this.getBuyTotal(bc), 0);
  });

  solde = computed(() => {
    return this.totalGlobal() - this.totalAchat();
  });

  paginatedBcs = computed(() => {
    const filtered = this.filteredBcs();
    // Reset to page 1 if current page is out of bounds
    const total = Math.ceil(filtered.length / this.pageSize());
    if (this.currentPage() > total && total > 0) {
      this.currentPage.set(1);
    }
    const start = (this.currentPage() - 1) * this.pageSize();
    const end = start + this.pageSize();
    return filtered.slice(start, end);
  });

  /**
   * Récupère tous les IDs des clients d'un BC (nouvelle et ancienne structure)
   */
  getClientIds(bc: BC): string[] {
    // Nouvelle structure: clientsVente
    if (bc.clientsVente && bc.clientsVente.length > 0) {
      return bc.clientsVente.map(cv => cv.clientId).filter(id => id);
    }
    // Ancienne structure: clientId unique
    if (bc.clientId) {
      return [bc.clientId];
    }
    return [];
  }

  resetFilters() {
    this.searchTerm.set('');
    this.filterSupplier.set('');
    this.filterClient.set('');
    this.dateMin.set('');
    this.dateMax.set('');
    this.sortOrder.set('desc'); // Réinitialiser le tri à décroissant
    // Page will be reset automatically by the effect
  }

  prevPage() {
    if (this.currentPage() > 1) {
      this.currentPage.update(p => p - 1);
    }
  }

  nextPage() {
    if (this.currentPage() < this.totalPages()) {
      this.currentPage.update(p => p + 1);
    }
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
      
      await this.store.exportBCsToExcel(params);
    } catch (error) {
      console.error('Error in exportGlobal:', error);
    }
  }

  formatCurrency(val: number | null | undefined) {
    if (val === null || val === undefined) return '0 MAD';
    return new Intl.NumberFormat('fr-MA', { style: 'currency', currency: 'MAD', maximumFractionDigits: 0 }).format(val);
  }

  /**
   * Calcule le total d'achat HT (nouvelle et ancienne structure)
   */
  getBuyTotal(bc: BC): number {
    // Utiliser les totaux pré-calculés si disponibles (vérifier null aussi)
    if (bc.totalAchatHT !== undefined && bc.totalAchatHT !== null) {
      return bc.totalAchatHT;
    }
    // Nouvelle structure
    if (bc.lignesAchat && bc.lignesAchat.length > 0) {
      return bc.lignesAchat.reduce((acc, l) => 
        acc + (l.quantiteAchetee || 0) * (l.prixAchatUnitaireHT || 0), 0);
    }
    // Ancienne structure
    if (bc.items) {
      return bc.items.reduce((acc, i) => acc + (i.qtyBuy * i.priceBuyHT), 0);
    }
    return 0;
  }

  /**
   * Calcule le total de vente HT (nouvelle et ancienne structure)
   */
  getSellTotal(bc: BC): number {
    // Utiliser les totaux pré-calculés si disponibles (vérifier null aussi)
    if (bc.totalVenteHT !== undefined && bc.totalVenteHT !== null) {
      return bc.totalVenteHT;
    }
    // Nouvelle structure
    if (bc.clientsVente && bc.clientsVente.length > 0) {
      return bc.clientsVente.reduce((acc, cv) => {
        if (cv.lignesVente) {
          return acc + cv.lignesVente.reduce((sum, l) => 
            sum + (l.quantiteVendue || 0) * (l.prixVenteUnitaireHT || 0), 0);
        }
        return acc;
      }, 0);
    }
    // Ancienne structure
    if (bc.items) {
      return bc.items.reduce((acc, i) => acc + (i.qtySell * i.priceSellHT), 0);
    }
    return 0;
  }

  getMarginPercent(bc: BC): string {
    // Utiliser le pourcentage pré-calculé si disponible (vérifier null aussi)
    if (bc.margePourcentage !== undefined && bc.margePourcentage !== null) {
      return bc.margePourcentage.toFixed(1);
    }
    const buy = this.getBuyTotal(bc);
    const sell = this.getSellTotal(bc);
    if (buy === 0 || buy === null || sell === null) return '0';
    return (((sell - buy) / buy) * 100).toFixed(1);
  }

  getMarginClass(bc: BC): string {
    const pct = parseFloat(this.getMarginPercent(bc));
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

  viewAuditLog(bc: BC) {
    this.router.navigate(['/audit'], {
      queryParams: {
        entityType: 'BandeCommande',
        entityId: bc.id
      }
    });
  }

  viewLinkedInvoices(bc: BC) {
    // Naviguer vers les factures avec filtre bcId
    // On va d'abord vers les factures achat, mais on pourrait aussi avoir un choix
    this.router.navigate(['/invoices/purchase'], {
      queryParams: {
        bcId: bc.id
      }
    });
  }

  viewLinkedSalesInvoices(bc: BC) {
    // Naviguer vers les factures ventes avec filtre bcId
    this.router.navigate(['/invoices/sales'], {
      queryParams: {
        bcId: bc.id
      }
    });
  }

  async ngOnInit() {
    // Les données sont chargées automatiquement par NavigationRefreshService
    // Mais on s'assure qu'elles sont chargées si elles ne le sont pas
    if (this.bcStore.bcs().length === 0 && !this.bcStore.loading()) {
      await this.bcStore.loadBCs();
    }
    if (this.partnerStore.clients().length === 0 && !this.partnerStore.loading()) {
      await this.partnerStore.loadAll();
    }
  }
  
  async forceRefresh() {
    await this.navigationRefresh.forceRefreshCurrentRoute();
  }

  getStatusClass(status: string): string {
     return status === 'completed' 
       ? 'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-emerald-100 text-emerald-800'
       : (status === 'sent' 
          ? 'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800'
          : 'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-800');
  }

  toggleDropdown(bcId: string) {
    if (this.openDropdownId() === bcId) {
      this.openDropdownId.set(null);
    } else {
      this.openDropdownId.set(bcId);
    }
  }

  closeDropdown() {
    this.openDropdownId.set(null);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    const target = event.target as HTMLElement;
    if (!target.closest('.relative')) {
      this.closeDropdown();
    }
  }
}
