
import { Component, inject, computed, OnInit, effect, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StoreService } from '../../services/store.service';
import { DashboardStore } from '../../stores/dashboard.store';
import { BCStore } from '../../stores/bc.store';
import { NavigationRefreshService } from '../../services/navigation-refresh.service';
import { Router, RouterLink } from '@angular/router';
import { SkeletonCardComponent } from '../../components/skeleton/skeleton-card.component';
import { SkeletonTableComponent } from '../../components/skeleton/skeleton-table.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, SkeletonCardComponent, SkeletonTableComponent],
  template: `
    <div class="space-y-6 md:space-y-8 fade-in-up pb-10">
      
      <!-- Welcome Section -->
      <div class="flex flex-col md:flex-row justify-between items-start md:items-end gap-4">
        <div>
          <h1 class="text-2xl md:text-3xl font-bold text-slate-800 font-display">Tableau de Bord</h1>
          <p class="text-sm md:text-base text-slate-500 mt-1">Aperçu en temps réel de votre activité.</p>
        </div>
        <div class="flex gap-3 w-full md:w-auto">
          <button (click)="downloadReport()" class="flex-1 md:flex-none px-4 py-2 bg-white border border-slate-200 text-slate-700 rounded-lg text-sm font-medium hover:bg-slate-50 transition shadow-sm text-center">
            Rapport
          </button>
          <a routerLink="/bc/new" class="flex-1 md:flex-none px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition shadow-lg shadow-blue-600/20 flex items-center justify-center gap-2 text-center">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path></svg>
            Nouveau BC
          </a>
        </div>
      </div>

      <!-- KPI Cards with Premium Look -->
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 md:gap-6">
        
        <!-- Solde Global Card -->
        <div class="bg-gradient-to-br from-indigo-500 to-purple-600 p-6 rounded-2xl shadow-lg shadow-indigo-500/20 hover-card text-white">
          <div class="flex flex-col h-full justify-between">
            <div>
              <p class="text-sm font-medium text-indigo-100 uppercase tracking-wide">Solde Global</p>
              @if (isLoadingSolde()) {
                <app-skeleton-card [type]="'kpi'" [showHeader]="false"></app-skeleton-card>
              } @else {
                <h3 class="text-3xl font-extrabold text-white mt-2 tracking-tight break-words" 
                    [class.text-emerald-200]="soldeActuel() >= 0" 
                    [class.text-red-200]="soldeActuel() < 0">
                  {{ formatCurrency(soldeActuel()) }}
                </h3>
              }
            </div>
            <div class="mt-4 flex items-center gap-2">
              <svg class="w-4 h-4 text-indigo-200" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
              </svg>
              <span class="text-xs text-indigo-100">Trésorerie</span>
            </div>
          </div>
        </div>
        
        <!-- CA Card -->
        <div class="bg-white p-6 rounded-2xl shadow-[0_2px_15px_-3px_rgba(0,0,0,0.07),0_10px_20px_-2px_rgba(0,0,0,0.04)] hover-card border border-slate-100 relative overflow-hidden group">
          <div class="absolute top-0 right-0 p-4 opacity-10 group-hover:opacity-20 transition-opacity">
            <svg class="w-24 h-24 text-blue-600" fill="currentColor" viewBox="0 0 24 24"><path d="M12 2a10 10 0 1010 10A10 10 0 0012 2zm1 15h-2v-2h2zm0-4h-2V7h2z"/></svg>
          </div>
          <div class="flex flex-col relative z-10">
            <p class="text-sm font-semibold text-slate-500 uppercase tracking-wide">Chiffre d'Affaires</p>
            @if (isLoadingKPIs()) {
              <div class="animate-pulse">
                <div class="h-8 bg-slate-200 rounded w-32 mt-3"></div>
              </div>
            } @else {
              <h3 class="text-3xl font-extrabold text-slate-800 mt-3 tracking-tight break-words">{{ formatLargeNumber(store.totalSalesHT()) }}</h3>
            }
            <div class="mt-4 flex items-center gap-2">
              @if (kpis()?.margeMoyenne) {
                <span class="bg-emerald-50 text-emerald-600 px-2 py-0.5 rounded-full text-xs font-bold flex items-center">
                  <svg class="w-3 h-3 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6"></path></svg>
                  {{ kpis()?.margeMoyenne.toFixed(1) }}%
                </span>
              }
              <span class="text-xs text-slate-400">Marge moyenne</span>
            </div>
          </div>
        </div>

        <!-- Achats Card -->
        <div class="bg-white p-6 rounded-2xl shadow-[0_2px_15px_-3px_rgba(0,0,0,0.07),0_10px_20px_-2px_rgba(0,0,0,0.04)] hover-card border border-slate-100">
          <div class="flex flex-col">
            <p class="text-sm font-semibold text-slate-500 uppercase tracking-wide">Total Achats</p>
            @if (isLoadingKPIs()) {
              <app-skeleton-card [type]="'kpi'" [showHeader]="false"></app-skeleton-card>
            } @else {
              <h3 class="text-3xl font-extrabold text-slate-800 mt-3 tracking-tight break-words">{{ formatLargeNumber(store.totalPurchasesHT()) }}</h3>
            }
            <div class="mt-4 flex items-center gap-2">
              @if (kpis()?.tvaDeductible) {
                <span class="bg-blue-50 text-blue-600 px-2 py-0.5 rounded-full text-xs font-bold">{{ formatCurrency(kpis()!.tvaDeductible) }}</span>
              }
              <span class="text-xs text-slate-400">TVA déductible</span>
            </div>
          </div>
        </div>

        <!-- Marge Card -->
        <div class="bg-gradient-to-br from-emerald-500 to-teal-600 p-6 rounded-2xl shadow-lg shadow-emerald-500/20 hover-card text-white">
          <div class="flex flex-col h-full justify-between">
            <div>
              <p class="text-sm font-medium text-emerald-100 uppercase tracking-wide">Marge Nette</p>
              @if (isLoadingKPIs()) {
                <app-skeleton-card [type]="'kpi'" [showHeader]="false"></app-skeleton-card>
              } @else {
                <h3 class="text-3xl font-extrabold text-white mt-2 tracking-tight break-words">{{ formatLargeNumber(store.marginTotal()) }}</h3>
              }
            </div>
            <div class="mt-4">
              @if (kpis()?.margeMoyenne) {
                <div class="w-full bg-emerald-800/30 rounded-full h-1.5 mb-2">
                  <div class="bg-white h-1.5 rounded-full transition-all" [style.width.%]="getMargeWidthPercent(kpis()!.margeMoyenne)"></div>
                </div>
                <p class="text-xs text-emerald-100 font-medium">
                  {{ kpis()!.margeMoyenne >= 15 ? 'Performance excellente' : kpis()!.margeMoyenne >= 10 ? 'Performance bonne' : 'Performance à améliorer' }}
                </p>
              } @else {
                <div class="w-full bg-emerald-800/30 rounded-full h-1.5 mb-2">
                  <div class="bg-white h-1.5 rounded-full" style="width: 70%"></div>
                </div>
                <p class="text-xs text-emerald-100 font-medium">Performance excellente</p>
              }
            </div>
          </div>
        </div>

        <!-- Alerts Card -->
        <div class="bg-white p-6 rounded-2xl shadow-[0_2px_15px_-3px_rgba(0,0,0,0.07),0_10px_20px_-2px_rgba(0,0,0,0.04)] hover-card border border-red-100 relative">
          <div class="absolute top-4 right-4 animate-pulse">
            <span class="flex h-3 w-3">
              <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-red-400 opacity-75"></span>
              <span class="relative inline-flex rounded-full h-3 w-3 bg-red-500"></span>
            </span>
          </div>
          <div class="flex flex-col">
            <p class="text-sm font-semibold text-red-500 uppercase tracking-wide">Action Requise</p>
            @if (isLoadingKPIs()) {
              <app-skeleton-card [type]="'kpi'" [showHeader]="false"></app-skeleton-card>
            } @else {
              <h3 class="text-3xl font-extrabold text-slate-800 mt-3 tracking-tight">{{ store.overduePurchaseInvoices() }}</h3>
            }
            <p class="text-sm text-slate-500 mt-1">
              @if (kpis()?.impayes) {
                Factures en retard / {{ formatLargeNumber(kpis()!.impayes.totalImpayes) }} MAD impayés
              } @else {
                Factures en retard / alerte TVA
              }
            </p>
            <a routerLink="/invoices/purchase" class="mt-4 text-xs font-bold text-red-600 hover:text-red-700 flex items-center">
              Voir les détails <svg class="w-4 h-4 ml-1" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 8l4 4m0 0l-4 4m4-4H3"></path></svg>
            </a>
          </div>
        </div>
      </div>

      <!-- Charts & Tables Layout -->
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
        
        <!-- Main Table (Takes 2 columns) -->
        <div class="lg:col-span-2 bg-white rounded-2xl shadow-sm border border-slate-100 flex flex-col">
          <div class="p-6 border-b border-slate-100 flex justify-between items-center bg-slate-50/50 rounded-t-2xl">
            <div>
               <h3 class="text-lg font-bold text-slate-800">Commandes Récentes</h3>
               <p class="text-xs text-slate-500">Derniers mouvements enregistrés</p>
            </div>
            <div class="flex items-center gap-3">
              @if (isRefreshing()) {
                <div class="flex items-center gap-2 text-xs text-slate-500">
                  <svg class="w-4 h-4 animate-spin" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"></path>
                  </svg>
                  <span>Actualisation...</span>
                </div>
              }
              <button (click)="viewAllBCs()" class="text-sm text-blue-600 font-medium hover:text-blue-800 transition">Voir tout</button>
            </div>
          </div>
          @if (bcStore.loading()) {
            <app-skeleton-table [columns]="[
              { width: '20%' },
              { width: '30%' },
              { width: '20%' },
              { width: '30%', align: 'right' }
            ]" [rows]="5"></app-skeleton-table>
          } @else {
          <div class="overflow-x-auto">
            <table class="w-full text-sm text-left min-w-[600px]">
              <thead class="text-xs text-slate-500 uppercase bg-slate-50 border-b border-slate-100">
                <tr>
                  <th class="px-6 py-4 font-semibold">Référence</th>
                  <th class="px-6 py-4 font-semibold">Client</th>
                  <th class="px-6 py-4 font-semibold">Statut</th>
                  <th class="px-6 py-4 font-semibold text-right">Montant</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                @for (bc of recentBCs(); track bc.id) {
                  <tr (click)="viewBC(bc.id)" class="hover:bg-slate-50 transition-colors group cursor-pointer">
                    <td class="px-6 py-4">
                      <div class="flex items-center gap-3">
                        <div class="p-2 bg-blue-50 text-blue-600 rounded-lg group-hover:bg-blue-100 transition-colors">
                          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
                        </div>
                        <span class="font-medium text-slate-700 group-hover:text-blue-600 transition-colors">{{ bc.number }}</span>
                      </div>
                    </td>
                    <td class="px-6 py-4 text-slate-600">{{ store.getClientName(bc.clientId) }}</td>
                    <td class="px-6 py-4">
                      <span [class]="getStatusClass(bc.status)">
                        {{ getStatusLabel(bc.status) }}
                      </span>
                    </td>
                    <td class="px-6 py-4 text-right font-bold text-slate-800">
                      {{ formatCurrency(getBCTotal(bc)) }}
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          }
        </div>

        <!-- Side Panel / Quick Stats -->
        <div class="bg-white rounded-2xl shadow-sm border border-slate-100 p-6">
           <h3 class="text-lg font-bold text-slate-800 mb-6">Répartition Activité</h3>
           
           <div class="space-y-6">
              @if (monthlyData().length > 0) {
                <!-- Real Chart Representation from backend data -->
                <div class="relative h-48 w-full flex items-end justify-between px-1 gap-1.5 mb-2">
                  @for (month of monthlyData().slice(-5); track month.mois) {
                    @let maxValue = getMaxMonthlyValue();
                    @let heightPercent = maxValue > 0 ? (month.caHT / maxValue) * 100 : 0;
                    @let monthLabel = formatMonthLabel(month.mois);
                    <div class="w-full bg-blue-500 rounded-t-md hover:bg-blue-600 transition-all cursor-pointer relative group flex-1 flex flex-col items-center justify-end"
                         [style.height.%]="heightPercent">
                      <!-- Valeur sur la barre -->
                      <div class="absolute -top-5 left-1/2 -translate-x-1/2 text-[10px] font-semibold text-slate-700 opacity-0 group-hover:opacity-100 transition whitespace-nowrap">
                        {{ formatCurrency(month.caHT) }}
                      </div>
                    </div>
                  }
                </div>
                <!-- Labels des mois -->
                <div class="flex justify-between text-xs text-slate-500 font-medium px-1">
                  @for (month of monthlyData().slice(-5); track month.mois) {
                    <span class="text-center flex-1">{{ formatMonthLabel(month.mois).split(' ')[0] }}</span>
                  }
                </div>
              } @else {
                <!-- Fallback: Weekly activity from BCs -->
                <div class="relative h-48 w-full flex items-end justify-between px-1 gap-1.5 mb-2">
                  @for (day of weeklyActivity(); track day.day) {
                    @let maxValue = getMaxWeeklyValue();
                    @let heightPercent = maxValue > 0 ? (day.total / maxValue) * 100 : 0;
                    <div class="w-full bg-blue-500 rounded-t-md hover:bg-blue-600 transition-all cursor-pointer relative group flex-1 flex flex-col items-center justify-end"
                         [style.height.%]="heightPercent">
                      <!-- Valeur sur la barre -->
                      <div class="absolute -top-5 left-1/2 -translate-x-1/2 text-[10px] font-semibold text-slate-700 opacity-0 group-hover:opacity-100 transition whitespace-nowrap">
                        {{ formatCurrency(day.total) }}
                      </div>
                    </div>
                  }
                </div>
                <div class="flex justify-between text-xs text-slate-500 font-medium px-1">
                  @for (day of weeklyActivity(); track day.day) {
                    <span class="text-center flex-1">{{ day.day }}</span>
                  }
                </div>
              }

              <!-- Comparaison claire entre ce mois et le mois précédent -->
              <div class="border-t border-slate-100 pt-4 mt-4 space-y-3">
                 @let comparison = getMonthlyComparison();
                 @if (comparison) {
                   <!-- Ce mois -->
                   <div class="space-y-1">
                     <div class="flex items-center justify-between">
                       <span class="flex items-center text-sm text-slate-600 gap-2">
                         <div class="w-2 h-2 rounded-full bg-blue-500"></div>
                         <span class="font-medium">{{ comparison.thisMonth.label }}</span>
                       </span>
                       <span class="text-sm font-bold text-slate-800">{{ formatCurrency(comparison.thisMonth.value) }}</span>
                     </div>
                     
                     <!-- Mois précédent et évolution -->
                     @if (comparison.lastMonth) {
                       <div class="flex items-center justify-between">
                         <span class="flex items-center text-sm text-slate-500 gap-2">
                           <div class="w-2 h-2 rounded-full bg-slate-300"></div>
                           <span>{{ comparison.lastMonth.label }}</span>
                         </span>
                         <span class="text-sm font-medium text-slate-600">{{ formatCurrency(comparison.lastMonth.value) }}</span>
                       </div>
                       
                       @if (comparison.evolution) {
                         <div class="flex items-center justify-end gap-1.5 pt-1">
                           @if (comparison.evolution.isPositive) {
                             <svg class="w-4 h-4 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                               <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6"></path>
                             </svg>
                             <span class="text-xs font-bold text-emerald-600">+{{ comparison.evolution.percent.toFixed(1) }}%</span>
                           } @else {
                             <svg class="w-4 h-4 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                               <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 17h8m0 0V9m0 8l-8-8-4 4-6-6"></path>
                             </svg>
                             <span class="text-xs font-bold text-red-600">-{{ comparison.evolution.percent.toFixed(1) }}%</span>
                           }
                         </div>
                       }
                     }
                   </div>
                 } @else {
                   <div class="text-sm text-slate-500 text-center py-2">Données insuffisantes pour la comparaison</div>
                 }
              </div>
           </div>
        </div>

      </div>

    </div>
  `
})
export class DashboardComponent implements OnInit {
  store = inject(StoreService);
  dashboardStore = inject(DashboardStore);
  bcStore = inject(BCStore);
  navigationRefresh = inject(NavigationRefreshService);
  router = inject(Router);

  recentBCs = computed(() => this.bcStore.bcs().slice(0, 5));
  
  // KPIs from backend
  readonly kpis = computed(() => this.dashboardStore.dashboardKPIs());
  readonly isLoadingKPIs = computed(() => this.dashboardStore.dashboardLoading());
  readonly isRefreshingKPIs = computed(() => this.dashboardStore.refreshing());
  
  // Solde global
  readonly soldeGlobal = computed(() => this.dashboardStore.soldeGlobal());
  readonly soldeActuel = computed(() => this.soldeGlobal()?.soldeActuel || 0);
  readonly isLoadingSolde = signal(false);
  
  // État de rafraîchissement global
  readonly isRefreshing = computed(() => this.navigationRefresh.isRefreshing());

  // Monthly data for chart
  monthlyData = computed(() => {
    const kpis = this.kpis();
    if (kpis?.caMensuel && kpis.caMensuel.length > 0) {
      return kpis.caMensuel;
    }
    return [];
  });

  // Weekly activity fallback (from BCs if monthly data not available)
  weeklyActivity = computed(() => {
    const bcs = this.store.bcs();
    const now = new Date();
    const days = ['Dim', 'Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam'];
    
    // Get BCs from last 7 days
    const last7Days = Array.from({ length: 7 }, (_, i) => {
      const date = new Date(now);
      date.setDate(date.getDate() - (6 - i));
      const dayOfWeek = days[date.getDay()];
      
      const dayBCs = bcs.filter(bc => {
        const bcDate = new Date(bc.date);
        return bcDate.toDateString() === date.toDateString();
      });
      
      const total = dayBCs.reduce((sum, bc) => {
        return sum + bc.items.reduce((s, item) => s + (item.qtySell * item.priceSellHT), 0);
      }, 0);
      
      return {
        day: dayOfWeek,
        label: date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' }),
        total: total
      };
    });
    
    return last7Days;
  });

  getMaxMonthlyValue(): number {
    const data = this.monthlyData();
    if (data.length === 0) return 1;
    return Math.max(...data.map(m => m.caHT), 1);
  }

  getMaxWeeklyValue(): number {
    const data = this.weeklyActivity();
    if (data.length === 0) return 1;
    return Math.max(...data.map(d => d.total), 1);
  }

  constructor() {
    // Reload KPIs when BCs or invoices change
    effect(() => {
      this.store.bcs();
      this.store.invoices();
      // Optionally reload KPIs on data change
    });
  }

  async ngOnInit() {
    // Load KPIs on component initialization
    this.isLoadingSolde.set(true);
    try {
      await Promise.all([
        this.dashboardStore.loadDashboardKPIs(),
        this.dashboardStore.loadSoldeGlobal()
      ]);
    } catch (error) {
      console.error('Error loading dashboard data:', error);
    } finally {
      this.isLoadingSolde.set(false);
    }
  }

  async reloadKPIs(from?: Date, to?: Date) {
    const fromStr = from?.toISOString().split('T')[0];
    const toStr = to?.toISOString().split('T')[0];
    await this.dashboardStore.loadDashboardKPIs(fromStr, toStr);
  }
  
  async forceRefresh() {
    await this.navigationRefresh.forceRefreshCurrentRoute();
  }

  formatCurrency(value: number): string {
    if (!value && value !== 0) return '0 MAD';
    // Utiliser des séparateurs de milliers pour les grands nombres
    const formatted = new Intl.NumberFormat('fr-MA', { 
      style: 'currency', 
      currency: 'MAD', 
      maximumFractionDigits: 0,
      minimumFractionDigits: 0
    }).format(value);
    return formatted;
  }

  formatLargeNumber(value: number): string {
    if (!value && value !== 0) return '0';
    // Formater avec séparateurs de milliers (espace en français)
    return new Intl.NumberFormat('fr-MA', {
      minimumFractionDigits: 0,
      maximumFractionDigits: 0
    }).format(value) + ' MAD';
  }

  getBCTotal(bc: any): number {
    // Utiliser les totaux pré-calculés si disponibles
    if (bc.totalVenteHT !== undefined && bc.totalVenteHT !== null) {
      return bc.totalVenteHT;
    }
    // Nouvelle structure multi-clients
    if (bc.clientsVente && bc.clientsVente.length > 0) {
      return bc.clientsVente.reduce((acc: number, cv: any) => {
        if (cv.lignesVente) {
          return acc + cv.lignesVente.reduce((sum: number, l: any) => 
            sum + (l.quantiteVendue || 0) * (l.prixVenteUnitaireHT || 0), 0);
        }
        return acc;
      }, 0);
    }
    // Ancienne structure (compatibilité)
    if (bc.items && bc.items.length > 0) {
      return bc.items.reduce((acc: number, item: any) => acc + (item.qtySell * item.priceSellHT), 0);
    }
    return 0;
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'draft': return 'bg-slate-100 text-slate-600 text-xs font-bold px-3 py-1 rounded-full border border-slate-200';
      case 'sent': return 'bg-blue-50 text-blue-600 text-xs font-bold px-3 py-1 rounded-full border border-blue-100';
      case 'completed': return 'bg-emerald-50 text-emerald-600 text-xs font-bold px-3 py-1 rounded-full border border-emerald-100';
      default: return 'bg-slate-100 text-slate-600 text-xs font-bold px-3 py-1 rounded-full';
    }
  }

  getStatusLabel(status: string): string {
    const labels: Record<string, string> = { 'draft': 'Brouillon', 'sent': 'Envoyée', 'completed': 'Validée' };
    return labels[status] || status;
  }

  async downloadReport() {
    try {
      // Télécharger le rapport PDF complet du dashboard
      await this.store.downloadDashboardReport();
    } catch (error) {
      console.error('Error downloading report:', error);
      // Error already handled in store service
    }
  }

  viewAllBCs() {
    this.router.navigate(['/bc']);
  }

  viewBC(bcId: string) {
    this.router.navigate(['/bc/edit', bcId]);
  }

  getMargeWidthPercent(marge: number | undefined): number {
    if (marge === undefined || marge === null) return 0;
    return Math.min(Math.max(marge, 0), 100);
  }

  // Formate un mois au format "2025-01" en "Jan 2025"
  formatMonthLabel(monthString: string): string {
    if (!monthString) return '';
    try {
      // Parse "2025-01" format
      const [year, month] = monthString.split('-');
      const date = new Date(parseInt(year), parseInt(month) - 1, 1);
      return date.toLocaleDateString('fr-FR', { month: 'short', year: 'numeric' });
    } catch (e) {
      return monthString;
    }
  }

  // Calcule l'évolution en % entre deux valeurs
  calculateEvolution(current: number, previous: number): { percent: number; isPositive: boolean } {
    if (!previous || previous === 0) {
      return { percent: current > 0 ? 100 : 0, isPositive: current > 0 };
    }
    const percent = ((current - previous) / previous) * 100;
    return { percent: Math.abs(percent), isPositive: percent >= 0 };
  }

  // Obtient les données de comparaison mensuelle (ce mois vs mois précédent)
  getMonthlyComparison() {
    const data = this.monthlyData();
    if (data.length === 0) return null;
    
    const sorted = [...data].sort((a, b) => a.mois.localeCompare(b.mois));
    const thisMonth = sorted[sorted.length - 1];
    const lastMonth = sorted.length > 1 ? sorted[sorted.length - 2] : null;
    
    if (!thisMonth) return null;
    
    const evolution = lastMonth ? this.calculateEvolution(thisMonth.caHT, lastMonth.caHT) : null;
    
    return {
      thisMonth: {
        label: this.formatMonthLabel(thisMonth.mois),
        value: thisMonth.caHT
      },
      lastMonth: lastMonth ? {
        label: this.formatMonthLabel(lastMonth.mois),
        value: lastMonth.caHT
      } : null,
      evolution
    };
  }
}
