import { Component, inject, computed, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StoreService, PrevisionTresorerieResponse, PrevisionJournaliere, EcheanceDetail } from '../../services/store.service';

@Component({
  selector: 'app-prevision-tresorerie',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6 fade-in-up pb-10">
      <!-- Header -->
      <div class="flex flex-col md:flex-row justify-between items-start md:items-end gap-4 border-b border-slate-200/60 pb-6">
        <div>
          <h1 class="text-2xl md:text-3xl font-bold text-slate-800 font-display">Prévision de Trésorerie</h1>
          <p class="text-slate-500 mt-2 text-sm">Visualisation des prévisions de paiements et évolution du solde</p>
        </div>
        <div class="flex gap-3">
          <button (click)="loadPrevision()" class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition font-medium">
            Actualiser
          </button>
        </div>
      </div>

      <!-- Filtres de période -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
        <h2 class="text-lg font-bold text-slate-800 mb-4">Période d'analyse</h2>
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div>
            <label class="block text-sm font-semibold text-slate-700 mb-1">Date de début</label>
            <input type="date" [(ngModel)]="dateFrom" (change)="loadPrevision()" 
                   class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
          </div>
          <div>
            <label class="block text-sm font-semibold text-slate-700 mb-1">Date de fin</label>
            <input type="date" [(ngModel)]="dateTo" (change)="loadPrevision()" 
                   class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
          </div>
          <div class="flex items-end">
            <button (click)="setDefaultPeriod()" class="w-full px-4 py-2 bg-slate-100 text-slate-700 rounded-lg hover:bg-slate-200 transition font-medium">
              Période par défaut (3 mois)
            </button>
          </div>
        </div>
      </div>

      <!-- KPIs -->
      @if (previsionData(); as data) {
        <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div class="bg-white p-6 rounded-xl shadow-sm border border-slate-200">
            <p class="text-sm font-semibold text-slate-500 uppercase tracking-wide">Solde Actuel</p>
            <h3 class="text-3xl font-extrabold mt-3" 
                [class.text-emerald-600]="data.soldeActuel >= 0"
                [class.text-red-600]="data.soldeActuel < 0">
              {{ data.soldeActuel | number:'1.2-2' }} MAD
            </h3>
          </div>
          <div class="bg-white p-6 rounded-xl shadow-sm border border-slate-200">
            <p class="text-sm font-semibold text-slate-500 uppercase tracking-wide">Entrées Prévues</p>
            <h3 class="text-3xl font-extrabold mt-3 text-emerald-600">
              {{ totalEntrees() | number:'1.2-2' }} MAD
            </h3>
          </div>
          <div class="bg-white p-6 rounded-xl shadow-sm border border-slate-200">
            <p class="text-sm font-semibold text-slate-500 uppercase tracking-wide">Sorties Prévues</p>
            <h3 class="text-3xl font-extrabold mt-3 text-red-600">
              {{ totalSorties() | number:'1.2-2' }} MAD
            </h3>
          </div>
          <div class="bg-white p-6 rounded-xl shadow-sm border border-slate-200">
            <p class="text-sm font-semibold text-slate-500 uppercase tracking-wide">Solde Final Prévu</p>
            <h3 class="text-3xl font-extrabold mt-3" 
                [class.text-emerald-600]="soldeFinal() >= 0"
                [class.text-red-600]="soldeFinal() < 0">
              {{ soldeFinal() | number:'1.2-2' }} MAD
            </h3>
          </div>
        </div>

        <!-- Onglets -->
        <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
          <div class="flex border-b border-slate-200 bg-slate-50">
            <button (click)="activeView.set('tableau')" 
                    [class.bg-white]="activeView() === 'tableau'"
                    [class.text-blue-600]="activeView() === 'tableau'"
                    [class.border-b-2]="activeView() === 'tableau'"
                    [class.border-blue-600]="activeView() === 'tableau'"
                    class="flex-1 px-6 py-3 text-sm font-semibold text-slate-600 hover:text-slate-800 transition">
              Tableau
            </button>
            <button (click)="activeView.set('graphique')" 
                    [class.bg-white]="activeView() === 'graphique'"
                    [class.text-blue-600]="activeView() === 'graphique'"
                    [class.border-b-2]="activeView() === 'graphique'"
                    [class.border-blue-600]="activeView() === 'graphique'"
                    class="flex-1 px-6 py-3 text-sm font-semibold text-slate-600 hover:text-slate-800 transition">
              Graphique
            </button>
            <button (click)="activeView.set('calendrier')" 
                    [class.bg-white]="activeView() === 'calendrier'"
                    [class.text-blue-600]="activeView() === 'calendrier'"
                    [class.border-b-2]="activeView() === 'calendrier'"
                    [class.border-blue-600]="activeView() === 'calendrier'"
                    class="flex-1 px-6 py-3 text-sm font-semibold text-slate-600 hover:text-slate-800 transition">
              Calendrier
            </button>
          </div>

          <div class="p-6">
            @if (activeView() === 'tableau') {
              <!-- Tableau des échéances -->
              <div class="overflow-x-auto">
                <table class="w-full text-sm">
                  <thead class="bg-slate-50 border-b border-slate-200">
                    <tr>
                      <th class="px-4 py-3 text-left font-semibold text-slate-700">Date</th>
                      <th class="px-4 py-3 text-left font-semibold text-slate-700">Type</th>
                      <th class="px-4 py-3 text-left font-semibold text-slate-700">Facture</th>
                      <th class="px-4 py-3 text-left font-semibold text-slate-700">Partenaire</th>
                      <th class="px-4 py-3 text-right font-semibold text-slate-700">Montant</th>
                      <th class="px-4 py-3 text-left font-semibold text-slate-700">Statut</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-slate-100">
                    @for (echeance of sortedEcheances(); track echeance.date + echeance.factureId) {
                      <tr class="hover:bg-slate-50">
                        <td class="px-4 py-3">{{ echeance.date }}</td>
                        <td class="px-4 py-3">
                          <span class="px-2 py-1 rounded text-xs font-medium"
                                [class.bg-emerald-50]="echeance.type === 'VENTE'"
                                [class.text-emerald-700]="echeance.type === 'VENTE'"
                                [class.bg-red-50]="echeance.type === 'ACHAT'"
                                [class.text-red-700]="echeance.type === 'ACHAT'">
                            {{ echeance.type }}
                          </span>
                        </td>
                        <td class="px-4 py-3">{{ echeance.numeroFacture }}</td>
                        <td class="px-4 py-3">{{ echeance.partenaire }}</td>
                        <td class="px-4 py-3 text-right font-bold"
                            [class.text-emerald-600]="echeance.type === 'VENTE'"
                            [class.text-red-600]="echeance.type === 'ACHAT'">
                          {{ echeance.montant | number:'1.2-2' }} MAD
                        </td>
                        <td class="px-4 py-3">
                          <span class="px-2 py-1 rounded text-xs font-medium"
                                [class.bg-blue-50]="echeance.statut === 'PREVU'"
                                [class.text-blue-700]="echeance.statut === 'PREVU'"
                                [class.bg-emerald-50]="echeance.statut === 'REALISE'"
                                [class.text-emerald-700]="echeance.statut === 'REALISE'"
                                [class.bg-red-50]="echeance.statut === 'EN_RETARD'"
                                [class.text-red-700]="echeance.statut === 'EN_RETARD'">
                            {{ echeance.statut }}
                          </span>
                        </td>
                      </tr>
                    }
                    @empty {
                      <tr>
                        <td colspan="6" class="px-4 py-8 text-center text-slate-500">
                          Aucune échéance prévue pour cette période
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            } @else if (activeView() === 'graphique') {
              <!-- Graphique d'évolution -->
              <div class="space-y-4">
                <h3 class="text-lg font-bold text-slate-800">Évolution du Solde Prévisionnel</h3>
                <div class="bg-slate-50 rounded-lg p-6 min-h-[400px] flex items-end justify-between gap-1">
                  @for (prev of data.previsions; track prev.date) {
                    <div class="flex-1 flex flex-col items-center group relative">
                      <div class="w-full bg-blue-500 rounded-t transition-all hover:bg-blue-600 cursor-pointer"
                           [style.height.px]="getBarHeight(prev.soldePrevu)"
                           [class.bg-red-500]="prev.soldePrevu < 0"
                           [class.hover:bg-red-600]="prev.soldePrevu < 0"
                           [title]="prev.date + ': ' + (prev.soldePrevu | number:'1.2-2') + ' MAD'">
                      </div>
                      <span class="text-xs text-slate-600 mt-2 transform -rotate-45 origin-top-left whitespace-nowrap">
                        {{ formatDateShort(prev.date) }}
                      </span>
                      <div class="absolute bottom-full mb-2 hidden group-hover:block bg-slate-800 text-white text-xs rounded px-2 py-1 z-10 whitespace-nowrap">
                        {{ prev.date }}<br>
                        Solde: {{ prev.soldePrevu | number:'1.2-2' }} MAD
                      </div>
                    </div>
                  }
                </div>
                <div class="flex justify-between text-xs text-slate-500">
                  <span>Date de début</span>
                  <span>Date de fin</span>
                </div>
              </div>
            } @else {
              <!-- Calendrier -->
              <div class="space-y-4">
                <h3 class="text-lg font-bold text-slate-800">Calendrier des Échéances</h3>
                <div class="grid grid-cols-7 gap-2">
                  <!-- En-têtes des jours -->
                  @for (day of ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim']; track day) {
                    <div class="text-center text-sm font-semibold text-slate-600 py-2">{{ day }}</div>
                  }
                  <!-- Jours du calendrier -->
                  @for (day of calendarDays(); track day.date) {
                    <div class="border border-slate-200 rounded-lg p-2 min-h-[80px] hover:bg-slate-50 transition"
                         [class.bg-blue-50]="day.hasEcheance">
                      <div class="text-xs font-semibold mb-1" [class.text-slate-400]="!day.isCurrentMonth">
                        {{ day.day }}
                      </div>
                      @if (day.echeances && day.echeances.length > 0) {
                        <div class="space-y-1">
                          @for (ech of day.echeances.slice(0, 2); track ech.factureId) {
                            <div class="text-[10px] px-1 py-0.5 rounded truncate"
                                 [class.bg-emerald-100]="ech.type === 'VENTE'"
                                 [class.text-emerald-700]="ech.type === 'VENTE'"
                                 [class.bg-red-100]="ech.type === 'ACHAT'"
                                 [class.text-red-700]="ech.type === 'ACHAT'"
                                 [title]="ech.numeroFacture + ': ' + (ech.montant | number:'1.2-2') + ' MAD'">
                              {{ ech.montant | number:'1.0-0' }} MAD
                            </div>
                          }
                          @if (day.echeances.length > 2) {
                            <div class="text-[10px] text-slate-500">+{{ day.echeances.length - 2 }}</div>
                          }
                        </div>
                      }
                    </div>
                  }
                </div>
              </div>
            }
          </div>
        </div>
      } @else {
        <div class="text-center py-12 text-slate-500">
          <p>Chargement des prévisions...</p>
        </div>
      }
    </div>
  `
})
export class PrevisionTresorerieComponent implements OnInit {
  store = inject(StoreService);
  
  dateFrom = signal<string>('');
  dateTo = signal<string>('');
  activeView = signal<'tableau' | 'graphique' | 'calendrier'>('tableau');
  
  previsionData = computed(() => this.store.previsionTresorerie());
  
  totalEntrees = computed(() => {
    const data = this.previsionData();
    if (!data) return 0;
    return data.echeances
      .filter(e => e.type === 'VENTE')
      .reduce((sum, e) => sum + (e.montant || 0), 0);
  });
  
  totalSorties = computed(() => {
    const data = this.previsionData();
    if (!data) return 0;
    return data.echeances
      .filter(e => e.type === 'ACHAT')
      .reduce((sum, e) => sum + (e.montant || 0), 0);
  });
  
  soldeFinal = computed(() => {
    const data = this.previsionData();
    if (!data || data.previsions.length === 0) return 0;
    return data.previsions[data.previsions.length - 1].soldePrevu;
  });
  
  sortedEcheances = computed(() => {
    const data = this.previsionData();
    if (!data) return [];
    return [...data.echeances].sort((a, b) => 
      new Date(a.date).getTime() - new Date(b.date).getTime()
    );
  });
  
  calendarDays = computed(() => {
    const data = this.previsionData();
    if (!data) return [];
    
    const from = new Date(this.dateFrom() || new Date().toISOString().split('T')[0]);
    const to = new Date(this.dateTo() || new Date(Date.now() + 90 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]);
    
    // Trouver le premier jour de la semaine du mois de début
    const startDate = new Date(from);
    startDate.setDate(1);
    const firstDayOfWeek = startDate.getDay() === 0 ? 6 : startDate.getDay() - 1; // Lundi = 0
    
    // Trouver le dernier jour du mois de fin
    const endDate = new Date(to);
    endDate.setMonth(endDate.getMonth() + 1);
    endDate.setDate(0);
    const lastDay = endDate.getDate();
    
    const days: any[] = [];
    const currentMonth = startDate.getMonth();
    const currentYear = startDate.getFullYear();
    
    // Jours du mois précédent
    const prevMonth = new Date(currentYear, currentMonth, 0);
    for (let i = firstDayOfWeek - 1; i >= 0; i--) {
      const date = new Date(currentYear, currentMonth - 1, prevMonth.getDate() - i);
      days.push({
        date: date.toISOString().split('T')[0],
        day: date.getDate(),
        isCurrentMonth: false,
        echeances: this.getEcheancesForDate(date.toISOString().split('T')[0], data.echeances)
      });
    }
    
    // Jours du mois actuel
    for (let day = 1; day <= lastDay; day++) {
      const date = new Date(currentYear, currentMonth, day);
      const dateStr = date.toISOString().split('T')[0];
      days.push({
        date: dateStr,
        day: day,
        isCurrentMonth: true,
        echeances: this.getEcheancesForDate(dateStr, data.echeances),
        hasEcheance: this.getEcheancesForDate(dateStr, data.echeances).length > 0
      });
    }
    
    // Compléter jusqu'à 42 jours (6 semaines)
    const remainingDays = 42 - days.length;
    for (let day = 1; day <= remainingDays; day++) {
      const date = new Date(currentYear, currentMonth + 1, day);
      days.push({
        date: date.toISOString().split('T')[0],
        day: day,
        isCurrentMonth: false,
        echeances: this.getEcheancesForDate(date.toISOString().split('T')[0], data.echeances)
      });
    }
    
    return days;
  });
  
  ngOnInit() {
    this.setDefaultPeriod();
    this.loadPrevision();
  }
  
  setDefaultPeriod() {
    const today = new Date();
    this.dateFrom.set(today.toISOString().split('T')[0]);
    const in3Months = new Date(today);
    in3Months.setMonth(in3Months.getMonth() + 3);
    this.dateTo.set(in3Months.toISOString().split('T')[0]);
  }
  
  async loadPrevision() {
    const from = this.dateFrom() ? new Date(this.dateFrom()) : undefined;
    const to = this.dateTo() ? new Date(this.dateTo()) : undefined;
    await this.store.loadPrevisionTresorerie(from, to);
  }
  
  getBarHeight(solde: number): number {
    const data = this.previsionData();
    if (!data || data.previsions.length === 0) return 0;
    
    const maxSolde = Math.max(...data.previsions.map(p => Math.abs(p.soldePrevu)));
    if (maxSolde === 0) return 0;
    
    const height = (Math.abs(solde) / maxSolde) * 300;
    return Math.max(height, 10); // Minimum 10px
  }
  
  formatDateShort(dateStr: string): string {
    const date = new Date(dateStr);
    return date.getDate().toString();
  }
  
  getEcheancesForDate(date: string, echeances: EcheanceDetail[]): EcheanceDetail[] {
    return echeances.filter(e => e.date === date);
  }
}

