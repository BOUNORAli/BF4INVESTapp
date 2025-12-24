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
                        <td class="px-3 md:px-4 py-3 text-sm">{{ echeance.date }}</td>
                        <td class="px-3 md:px-4 py-3">
                          <span class="px-2 py-1 rounded text-xs font-medium"
                                [class.bg-emerald-50]="echeance.type === 'VENTE'"
                                [class.text-emerald-700]="echeance.type === 'VENTE'"
                                [class.bg-red-50]="echeance.type === 'ACHAT'"
                                [class.text-red-700]="echeance.type === 'ACHAT'">
                            {{ echeance.type }}
                          </span>
                        </td>
                        <td class="px-3 md:px-4 py-3 text-sm hidden md:table-cell">{{ echeance.numeroFacture }}</td>
                        <td class="px-3 md:px-4 py-3 text-sm">{{ echeance.partenaire }}</td>
                        <td class="px-3 md:px-4 py-3 text-right font-bold text-sm md:text-base"
                            [class.text-emerald-600]="echeance.type === 'VENTE'"
                            [class.text-red-600]="echeance.type === 'ACHAT'">
                          {{ echeance.montant | number:'1.2-2' }} MAD
                        </td>
                        <td class="px-3 md:px-4 py-3 hidden md:table-cell">
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
                        <td colspan="6" class="px-3 md:px-4 py-8 text-center text-slate-500">
                          Aucune échéance prévue pour cette période
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            } @else if (activeView() === 'graphique') {
              <!-- Graphique d'évolution en ligne -->
              <div class="space-y-4">
                <div class="flex flex-col md:flex-row items-start md:items-center justify-between gap-3">
                  <h3 class="text-base md:text-lg font-bold text-slate-800">Évolution du Solde Prévisionnel</h3>
                  <div class="flex flex-col md:flex-row items-start md:items-center gap-2 md:gap-4 text-xs text-slate-600">
                    <div class="flex items-center gap-2">
                      <div class="w-3 h-3 rounded-full bg-emerald-500"></div>
                      <span>Solde positif</span>
                    </div>
                    <div class="flex items-center gap-2">
                      <div class="w-3 h-3 rounded-full bg-red-500"></div>
                      <span>Solde négatif</span>
                    </div>
                  </div>
                </div>
                <div class="bg-white rounded-lg border border-slate-200 p-3 md:p-6 min-h-[300px] md:min-h-[450px] relative chart-container">
                  <svg [attr.viewBox]="'0 0 ' + chartWidth() + ' ' + chartHeight()" 
                       class="w-full h-full" 
                       preserveAspectRatio="none"
                       (mousemove)="onChartMouseMove($event)"
                       (mouseleave)="onChartMouseLeave()">
                    <!-- Définitions de gradients -->
                    <defs>
                      <linearGradient id="gradient-positive" x1="0%" y1="0%" x2="0%" y2="100%">
                        <stop offset="0%" style="stop-color:#10b981;stop-opacity:0.4" />
                        <stop offset="100%" style="stop-color:#10b981;stop-opacity:0.1" />
                      </linearGradient>
                      <linearGradient id="gradient-mixed" x1="0%" y1="0%" x2="0%" y2="100%">
                        <stop offset="0%" style="stop-color:#10b981;stop-opacity:0.3" />
                        <stop offset="50%" style="stop-color:#fbbf24;stop-opacity:0.2" />
                        <stop offset="100%" style="stop-color:#ef4444;stop-opacity:0.3" />
                      </linearGradient>
                    </defs>
                    <!-- Grille de fond -->
                    <g class="grid-lines">
                      @for (line of gridLines(); track line) {
                        <line [attr.x1]="line.x1" [attr.y1]="line.y1" 
                              [attr.x2]="line.x2" [attr.y2]="line.y2" 
                              stroke="#e2e8f0" stroke-width="1" stroke-dasharray="2,2"/>
                      }
                    </g>
                    
                    <!-- Ligne de référence à 0 -->
                    <line [attr.x1]="chartPadding" 
                          [attr.y1]="zeroLineY()" 
                          [attr.x2]="chartWidth() - chartPadding" 
                          [attr.y2]="zeroLineY()" 
                          stroke="#94a3b8" 
                          stroke-width="1.5" 
                          stroke-dasharray="4,4"/>
                    
                    <!-- Zone remplie sous la courbe -->
                    <path [attr.d]="areaPath()" 
                          [attr.fill]="areaGradient()" 
                          opacity="0.3"/>
                    
                    <!-- Courbe principale -->
                    <path [attr.d]="linePath()" 
                          [attr.stroke]="lineColor()" 
                          stroke-width="3" 
                          fill="none" 
                          class="transition-all duration-300"/>
                    
                    <!-- Points interactifs -->
                    @for (point of chartPoints(); track point.date; let i = $index) {
                      <circle [attr.cx]="point.x" 
                              [attr.cy]="point.y" 
                              r="4" 
                              [attr.fill]="point.solde >= 0 ? '#10b981' : '#ef4444'"
                              stroke="white" 
                              stroke-width="2"
                              class="cursor-pointer hover:r-6 transition-all"
                              [attr.data-index]="i"
                              (mouseenter)="showTooltip(point, $event)"/>
                    }
                    
                    <!-- Tooltip -->
                    @if (tooltipData()) {
                      <g [attr.transform]="'translate(' + tooltipData()!.x + ',' + tooltipData()!.y + ')'">
                        <rect x="-60" y="-50" width="120" height="50" rx="4" fill="#1e293b" opacity="0.95"/>
                        <text x="0" y="-30" text-anchor="middle" fill="white" font-size="10" font-weight="bold">
                          {{ tooltipData()!.date }}
                        </text>
                        <text x="0" y="-15" text-anchor="middle" fill="white" font-size="10">
                          {{ tooltipData()!.solde | number:'1.2-2' }} MAD
                        </text>
                        <polygon points="-5,-50 5,-50 0,-60" fill="#1e293b" opacity="0.95"/>
                      </g>
                    }
                    
                    <!-- Axe Y (montants) -->
                    <g class="y-axis">
                      @for (tick of yAxisTicks(); track tick.value) {
                        <line [attr.x1]="chartPadding" 
                              [attr.y1]="tick.y" 
                              [attr.x2]="chartWidth() - chartPadding" 
                              [attr.y2]="tick.y" 
                              stroke="transparent" 
                              stroke-width="1"/>
                        <text [attr.x]="chartPadding - 10" 
                              [attr.y]="tick.y" 
                              text-anchor="end" 
                              fill="#64748b" 
                              font-size="10" 
                              alignment-baseline="middle">
                          {{ tick.value | number:'1.0-0' }}
                        </text>
                      }
                    </g>
                    
                    <!-- Axe X (dates) -->
                    <g class="x-axis">
                      @for (tick of xAxisTicks(); track tick.date) {
                        <line [attr.x1]="tick.x" 
                              [attr.y1]="chartHeight() - chartPadding" 
                              [attr.x2]="tick.x" 
                              [attr.y2]="chartHeight() - chartPadding + 5" 
                              stroke="#64748b" 
                              stroke-width="1"/>
                        <text [attr.x]="tick.x" 
                              [attr.y]="chartHeight() - chartPadding + 20" 
                              text-anchor="middle" 
                              fill="#64748b" 
                              font-size="10" 
                              transform="rotate(-45, {{ tick.x }}, {{ chartHeight() - chartPadding + 20 }})">
                          {{ formatDateShort(tick.date) }}
                        </text>
                      }
                    </g>
                  </svg>
                </div>
                <div class="flex justify-between text-xs text-slate-500 px-2">
                  <span>{{ data.previsions[0]?.date || '' }}</span>
                  <span>{{ data.previsions[data.previsions.length - 1]?.date || '' }}</span>
                </div>
              </div>
            } @else {
              <!-- Calendrier -->
              <div class="space-y-4">
                <div class="flex flex-col md:flex-row items-start md:items-center justify-between mb-4 gap-3">
                  <h3 class="text-base md:text-lg font-bold text-slate-800">Calendrier des Échéances</h3>
                  <!-- Navigation du calendrier -->
                  <div class="flex flex-col md:flex-row items-stretch md:items-center gap-2 md:gap-3 w-full md:w-auto">
                    <div class="flex items-center gap-2 justify-center md:justify-start">
                      <button (click)="prevMonth()" 
                              class="p-2 rounded-lg border border-slate-200 hover:bg-slate-50 transition min-h-[44px] min-w-[44px]">
                        <svg class="w-5 h-5 text-slate-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"></path>
                        </svg>
                      </button>
                      <div class="flex items-center gap-2 flex-1 md:flex-none">
                        <select [(ngModel)]="selectedMonth" (change)="onMonthChange()" 
                                class="flex-1 md:flex-none px-3 py-1.5 border border-slate-200 rounded-lg text-sm font-medium focus:ring-2 focus:ring-blue-500/20 outline-none min-h-[44px]">
                          @for (month of months; track month.value) {
                            <option [value]="month.value">{{ month.label }}</option>
                          }
                        </select>
                        <select [(ngModel)]="selectedYear" (change)="onYearChange()" 
                                class="flex-1 md:flex-none px-3 py-1.5 border border-slate-200 rounded-lg text-sm font-medium focus:ring-2 focus:ring-blue-500/20 outline-none min-h-[44px]">
                          @for (year of availableYears(); track year) {
                            <option [value]="year">{{ year }}</option>
                          }
                        </select>
                      </div>
                      <button (click)="nextMonth()" 
                              class="p-2 rounded-lg border border-slate-200 hover:bg-slate-50 transition min-h-[44px] min-w-[44px]">
                        <svg class="w-5 h-5 text-slate-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"></path>
                        </svg>
                      </button>
                    </div>
                  </div>
                </div>
                <div class="grid grid-cols-7 gap-1 md:gap-2">
                  <!-- En-têtes des jours -->
                  @for (day of ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim']; track day) {
                    <div class="text-center text-xs md:text-sm font-semibold text-slate-600 py-2">{{ day }}</div>
                  }
                  <!-- Jours du calendrier -->
                  @for (day of calendarDays(); track day.date) {
                    <div class="border border-slate-200 rounded-lg p-1.5 md:p-2 min-h-[60px] md:min-h-[80px] hover:bg-slate-50 transition cursor-pointer"
                         [class.bg-blue-50]="day.hasEcheance"
                         [class.bg-emerald-50]="day.isToday"
                         (click)="showDayDetails(day)">
                      <div class="text-xs font-semibold mb-1 flex items-center justify-between"
                           [class.text-slate-400]="!day.isCurrentMonth"
                           [class.text-blue-600]="day.isToday"
                           [class.font-bold]="day.isToday">
                        <span>{{ day.day }}</span>
                        @if (day.isToday) {
                          <span class="w-1.5 h-1.5 rounded-full bg-blue-600"></span>
                        }
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

      <!-- Modal Détails du Jour -->
      @if (selectedDayDetails()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center" aria-modal="true">
          <div (click)="closeDayDetails()" class="fixed inset-0 bg-slate-900/40 backdrop-blur-sm"></div>
          <div class="relative bg-white rounded-2xl shadow-2xl max-w-3xl w-full mx-2 md:mx-4 max-h-[90vh] overflow-hidden flex flex-col">
            <div class="flex items-center justify-between p-4 md:p-6 border-b border-slate-100 bg-gradient-to-r from-blue-50 to-indigo-50">
              <div>
                <h2 class="text-lg md:text-xl font-bold text-slate-800">Détails des Prévisions</h2>
                <p class="text-xs md:text-sm text-slate-600 mt-1">{{ formatDateLong(selectedDayDetails()!.date) }}</p>
              </div>
              <button (click)="closeDayDetails()" class="text-slate-400 hover:text-slate-600 transition min-h-[44px] min-w-[44px] flex items-center justify-center">
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
              </button>
            </div>
            
            <div class="flex-1 overflow-y-auto p-4 md:p-6">
              @if (selectedDayDetails()!.echeances && selectedDayDetails()!.echeances.length > 0) {
                <div class="space-y-4">
                  <div class="bg-slate-50 p-4 rounded-lg border border-slate-200">
                    <div class="grid grid-cols-2 gap-4 text-sm">
                      <div>
                        <span class="text-slate-600">Total Entrées:</span>
                        <span class="font-bold text-emerald-600 ml-2">
                          {{ getTotalForDay(selectedDayDetails()!.echeances, 'VENTE') | number:'1.2-2' }} MAD
                        </span>
                      </div>
                      <div>
                        <span class="text-slate-600">Total Sorties:</span>
                        <span class="font-bold text-red-600 ml-2">
                          {{ getTotalForDay(selectedDayDetails()!.echeances, 'ACHAT') | number:'1.2-2' }} MAD
                        </span>
                      </div>
                    </div>
                  </div>

                  <div class="overflow-x-auto">
                    <table class="w-full text-sm min-w-[600px]">
                      <thead class="bg-slate-50 border-b border-slate-200">
                        <tr>
                          <th class="px-4 py-3 text-left font-semibold text-slate-700">Type</th>
                          <th class="px-4 py-3 text-left font-semibold text-slate-700">Facture</th>
                          <th class="px-4 py-3 text-left font-semibold text-slate-700">Partenaire</th>
                          <th class="px-4 py-3 text-right font-semibold text-slate-700">Montant</th>
                          <th class="px-4 py-3 text-left font-semibold text-slate-700">Statut</th>
                        </tr>
                      </thead>
                      <tbody class="divide-y divide-slate-100">
                        @for (ech of selectedDayDetails()!.echeances; track ech.factureId + ech.date) {
                          <tr class="hover:bg-slate-50">
                            <td class="px-4 py-3">
                              <span class="px-2 py-1 rounded text-xs font-medium"
                                    [class.bg-emerald-50]="ech.type === 'VENTE'"
                                    [class.text-emerald-700]="ech.type === 'VENTE'"
                                    [class.bg-red-50]="ech.type === 'ACHAT'"
                                    [class.text-red-700]="ech.type === 'ACHAT'">
                                {{ ech.type }}
                              </span>
                            </td>
                            <td class="px-4 py-3 text-sm font-medium">{{ ech.numeroFacture }}</td>
                            <td class="px-4 py-3 text-sm">{{ ech.partenaire }}</td>
                            <td class="px-4 py-3 text-right font-bold"
                                [class.text-emerald-600]="ech.type === 'VENTE'"
                                [class.text-red-600]="ech.type === 'ACHAT'">
                              {{ ech.montant | number:'1.2-2' }} MAD
                            </td>
                            <td class="px-4 py-3">
                              <span class="px-2 py-1 rounded text-xs font-medium"
                                    [class.bg-blue-50]="ech.statut === 'PREVU'"
                                    [class.text-blue-700]="ech.statut === 'PREVU'"
                                    [class.bg-emerald-50]="ech.statut === 'REALISE'"
                                    [class.text-emerald-700]="ech.statut === 'REALISE'"
                                    [class.bg-orange-50]="ech.statut === 'PARTIELLE'"
                                    [class.text-orange-700]="ech.statut === 'PARTIELLE'"
                                    [class.bg-red-50]="ech.statut === 'EN_RETARD'"
                                    [class.text-red-700]="ech.statut === 'EN_RETARD'">
                                {{ ech.statut }}
                              </span>
                            </td>
                          </tr>
                        }
                      </tbody>
                    </table>
                  </div>
                </div>
              } @else {
                <div class="text-center py-12 text-slate-500">
                  <p>Aucune échéance prévue pour ce jour</p>
                </div>
              }
            </div>

            <div class="p-4 md:p-6 border-t border-slate-100 bg-slate-50/50">
              <button (click)="closeDayDetails()" class="w-full px-4 py-2.5 bg-slate-200 text-slate-700 font-medium rounded-lg hover:bg-slate-300 transition min-h-[44px]">
                Fermer
              </button>
            </div>
          </div>
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
  
  // Navigation calendrier
  currentCalendarMonth = signal<number>(new Date().getMonth());
  currentCalendarYear = signal<number>(new Date().getFullYear());
  selectedMonth = new Date().getMonth();
  selectedYear = new Date().getFullYear();
  
  // Modal détails jour
  selectedDayDetails = signal<{ date: string; echeances: EcheanceDetail[] } | null>(null);
  
  months = [
    { value: 0, label: 'Janvier' },
    { value: 1, label: 'Février' },
    { value: 2, label: 'Mars' },
    { value: 3, label: 'Avril' },
    { value: 4, label: 'Mai' },
    { value: 5, label: 'Juin' },
    { value: 6, label: 'Juillet' },
    { value: 7, label: 'Août' },
    { value: 8, label: 'Septembre' },
    { value: 9, label: 'Octobre' },
    { value: 10, label: 'Novembre' },
    { value: 11, label: 'Décembre' }
  ];
  
  // Graphique
  chartPadding = 60;
  chartWidth = signal(800);
  chartHeight = signal(400);
  tooltipData = signal<{ x: number; y: number; date: string; solde: number } | null>(null);
  
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
  
  availableYears = computed(() => {
    const years: number[] = [];
    const currentYear = new Date().getFullYear();
    for (let i = currentYear - 2; i <= currentYear + 5; i++) {
      years.push(i);
    }
    return years;
  });

  calendarDays = computed(() => {
    const data = this.previsionData();
    if (!data) return [];
    
    const month = this.currentCalendarMonth();
    const year = this.currentCalendarYear();
    
    // Trouver le premier jour du mois
    const firstDay = new Date(year, month, 1);
    const firstDayOfWeek = firstDay.getDay() === 0 ? 6 : firstDay.getDay() - 1; // Lundi = 0
    
    // Trouver le dernier jour du mois
    const lastDay = new Date(year, month + 1, 0).getDate();
    
    const days: any[] = [];
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    
    // Jours du mois précédent
    const prevMonth = new Date(year, month, 0);
    for (let i = firstDayOfWeek - 1; i >= 0; i--) {
      const date = new Date(year, month - 1, prevMonth.getDate() - i);
      const dateStr = date.toISOString().split('T')[0];
      days.push({
        date: dateStr,
        day: date.getDate(),
        isCurrentMonth: false,
        isToday: false,
        echeances: this.getEcheancesForDate(dateStr, data.echeances),
        hasEcheance: this.getEcheancesForDate(dateStr, data.echeances).length > 0
      });
    }
    
    // Jours du mois actuel
    for (let day = 1; day <= lastDay; day++) {
      const date = new Date(year, month, day);
      const dateStr = date.toISOString().split('T')[0];
      const isToday = date.getTime() === today.getTime();
      days.push({
        date: dateStr,
        day: day,
        isCurrentMonth: true,
        isToday: isToday,
        echeances: this.getEcheancesForDate(dateStr, data.echeances),
        hasEcheance: this.getEcheancesForDate(dateStr, data.echeances).length > 0
      });
    }
    
    // Compléter jusqu'à 42 jours (6 semaines)
    const remainingDays = 42 - days.length;
    for (let day = 1; day <= remainingDays; day++) {
      const date = new Date(year, month + 1, day);
      const dateStr = date.toISOString().split('T')[0];
      days.push({
        date: dateStr,
        day: day,
        isCurrentMonth: false,
        isToday: false,
        echeances: this.getEcheancesForDate(dateStr, data.echeances),
        hasEcheance: this.getEcheancesForDate(dateStr, data.echeances).length > 0
      });
    }
    
    return days;
  });
  
  // Graphique - Calculs
  chartPoints = computed(() => {
    const data = this.previsionData();
    if (!data || data.previsions.length === 0) return [];
    
    const width = this.chartWidth();
    const height = this.chartHeight();
    const padding = this.chartPadding;
    const plotWidth = width - (padding * 2);
    const plotHeight = height - (padding * 2);
    
    const values = data.previsions.map(p => p.soldePrevu);
    const minValue = Math.min(...values, 0);
    const maxValue = Math.max(...values, 0);
    const range = maxValue - minValue || 1;
    
    return data.previsions.map((prev, index) => {
      const x = padding + (index / (data.previsions.length - 1 || 1)) * plotWidth;
      const normalizedValue = (prev.soldePrevu - minValue) / range;
      const y = padding + plotHeight - (normalizedValue * plotHeight);
      
      return {
        x,
        y,
        date: prev.date,
        solde: prev.soldePrevu
      };
    });
  });
  
  linePath = computed(() => {
    const points = this.chartPoints();
    if (points.length === 0) return '';
    
    let path = `M ${points[0].x} ${points[0].y}`;
    
    for (let i = 1; i < points.length; i++) {
      const prev = points[i - 1];
      const curr = points[i];
      const cp1x = prev.x + (curr.x - prev.x) / 3;
      const cp1y = prev.y;
      const cp2x = curr.x - (curr.x - prev.x) / 3;
      const cp2y = curr.y;
      path += ` C ${cp1x} ${cp1y}, ${cp2x} ${cp2y}, ${curr.x} ${curr.y}`;
    }
    
    return path;
  });
  
  areaPath = computed(() => {
    const points = this.chartPoints();
    if (points.length === 0) return '';
    
    const height = this.chartHeight();
    const padding = this.chartPadding;
    const zeroY = this.zeroLineY();
    
    let path = `M ${points[0].x} ${zeroY}`;
    
    // Courbe supérieure
    for (let i = 0; i < points.length; i++) {
      if (i === 0) {
        path += ` L ${points[i].x} ${points[i].y}`;
      } else {
        const prev = points[i - 1];
        const curr = points[i];
        const cp1x = prev.x + (curr.x - prev.x) / 3;
        const cp1y = prev.y;
        const cp2x = curr.x - (curr.x - prev.x) / 3;
        const cp2y = curr.y;
        path += ` C ${cp1x} ${cp1y}, ${cp2x} ${cp2y}, ${curr.x} ${curr.y}`;
      }
    }
    
    // Fermer la zone
    const lastPoint = points[points.length - 1];
    path += ` L ${lastPoint.x} ${zeroY} Z`;
    
    return path;
  });
  
  areaGradient = computed(() => {
    const data = this.previsionData();
    if (!data || data.previsions.length === 0) return 'url(#gradient-positive)';
    
    const hasNegative = data.previsions.some(p => p.soldePrevu < 0);
    return hasNegative ? 'url(#gradient-mixed)' : 'url(#gradient-positive)';
  });
  
  lineColor = computed(() => {
    const data = this.previsionData();
    if (!data || data.previsions.length === 0) return '#3b82f6';
    
    const lastSolde = data.previsions[data.previsions.length - 1].soldePrevu;
    return lastSolde >= 0 ? '#10b981' : '#ef4444';
  });
  
  zeroLineY = computed(() => {
    const data = this.previsionData();
    if (!data || data.previsions.length === 0) return this.chartHeight() / 2;
    
    const values = data.previsions.map(p => p.soldePrevu);
    const minValue = Math.min(...values, 0);
    const maxValue = Math.max(...values, 0);
    const range = maxValue - minValue || 1;
    
    const height = this.chartHeight();
    const padding = this.chartPadding;
    const plotHeight = height - (padding * 2);
    
    const normalizedZero = (0 - minValue) / range;
    return padding + plotHeight - (normalizedZero * plotHeight);
  });
  
  gridLines = computed(() => {
    const lines: any[] = [];
    const height = this.chartHeight();
    const width = this.chartWidth();
    const padding = this.chartPadding;
    
    // Lignes horizontales (Y)
    const yTicks = this.yAxisTicks();
    yTicks.forEach(tick => {
      lines.push({
        x1: padding,
        y1: tick.y,
        x2: width - padding,
        y2: tick.y
      });
    });
    
    return lines;
  });
  
  yAxisTicks = computed(() => {
    const data = this.previsionData();
    if (!data || data.previsions.length === 0) return [];
    
    const height = this.chartHeight();
    const padding = this.chartPadding;
    const plotHeight = height - (padding * 2);
    
    const values = data.previsions.map(p => p.soldePrevu);
    const minValue = Math.min(...values, 0);
    const maxValue = Math.max(...values, 0);
    const range = maxValue - minValue || 1;
    
    const numTicks = 5;
    const ticks: any[] = [];
    
    for (let i = 0; i <= numTicks; i++) {
      const value = minValue + (range * i / numTicks);
      const normalizedValue = (value - minValue) / range;
      const y = padding + plotHeight - (normalizedValue * plotHeight);
      
      ticks.push({
        value: Math.round(value),
        y
      });
    }
    
    return ticks;
  });
  
  xAxisTicks = computed(() => {
    const data = this.previsionData();
    if (!data || data.previsions.length === 0) return [];
    
    const width = this.chartWidth();
    const padding = this.chartPadding;
    const plotWidth = width - (padding * 2);
    
    const numTicks = Math.min(8, data.previsions.length);
    const step = Math.max(1, Math.floor(data.previsions.length / numTicks));
    
    const ticks: any[] = [];
    
    for (let i = 0; i < data.previsions.length; i += step) {
      const x = padding + (i / (data.previsions.length - 1 || 1)) * plotWidth;
      ticks.push({
        x,
        date: data.previsions[i].date
      });
    }
    
    // Ajouter le dernier point si pas déjà inclus
    if (ticks[ticks.length - 1].date !== data.previsions[data.previsions.length - 1].date) {
      const lastIndex = data.previsions.length - 1;
      const x = padding + plotWidth;
      ticks.push({
        x,
        date: data.previsions[lastIndex].date
      });
    }
    
    return ticks;
  });
  
  ngOnInit() {
    this.setDefaultPeriod();
    this.loadPrevision();
    this.updateChartSize();
    
    // Mettre à jour la taille du graphique au redimensionnement
    if (typeof window !== 'undefined') {
      window.addEventListener('resize', () => this.updateChartSize());
    }
  }
  
  updateChartSize() {
    // Ajuster la largeur du graphique selon le conteneur
    const container = document.querySelector('.chart-container');
    if (container) {
      this.chartWidth.set(container.clientWidth || 800);
    }
  }
  
  // Navigation calendrier
  prevMonth() {
    let month = this.currentCalendarMonth();
    let year = this.currentCalendarYear();
    
    if (month === 0) {
      month = 11;
      year--;
    } else {
      month--;
    }
    
    this.currentCalendarMonth.set(month);
    this.currentCalendarYear.set(year);
    this.selectedMonth = month;
    this.selectedYear = year;
  }
  
  nextMonth() {
    let month = this.currentCalendarMonth();
    let year = this.currentCalendarYear();
    
    if (month === 11) {
      month = 0;
      year++;
    } else {
      month++;
    }
    
    this.currentCalendarMonth.set(month);
    this.currentCalendarYear.set(year);
    this.selectedMonth = month;
    this.selectedYear = year;
  }
  
  onMonthChange() {
    this.currentCalendarMonth.set(this.selectedMonth);
  }
  
  onYearChange() {
    this.currentCalendarYear.set(this.selectedYear);
  }
  
  // Graphique - Interactions
  onChartMouseMove(event: MouseEvent) {
    const svg = event.currentTarget as SVGElement;
    const rect = svg.getBoundingClientRect();
    const x = event.clientX - rect.left;
    const y = event.clientY - rect.top;
    
    const points = this.chartPoints();
    if (points.length === 0) return;
    
    // Trouver le point le plus proche
    let closestPoint = points[0];
    let minDistance = Math.abs(x - closestPoint.x);
    
    for (const point of points) {
      const distance = Math.abs(x - point.x);
      if (distance < minDistance) {
        minDistance = distance;
        closestPoint = point;
      }
    }
    
    // Afficher le tooltip si on est proche d'un point
    if (minDistance < 30) {
      this.tooltipData.set({
        x: closestPoint.x,
        y: closestPoint.y - 20,
        date: closestPoint.date,
        solde: closestPoint.solde
      });
    }
  }
  
  onChartMouseLeave() {
    this.tooltipData.set(null);
  }
  
  showTooltip(point: any, event: MouseEvent) {
    this.tooltipData.set({
      x: point.x,
      y: point.y - 20,
      date: point.date,
      solde: point.solde
    });
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
  
  formatDateShort(dateStr: string): string {
    const date = new Date(dateStr);
    const day = date.getDate();
    const month = date.getMonth() + 1;
    return `${day}/${month}`;
  }
  
  getEcheancesForDate(date: string, echeances: EcheanceDetail[]): EcheanceDetail[] {
    return echeances.filter(e => e.date === date);
  }
  
  showDayDetails(day: any) {
    this.selectedDayDetails.set({
      date: day.date,
      echeances: day.echeances || []
    });
  }
  
  closeDayDetails() {
    this.selectedDayDetails.set(null);
  }
  
  formatDateLong(dateStr: string): string {
    const date = new Date(dateStr);
    const options: Intl.DateTimeFormatOptions = { 
      weekday: 'long', 
      year: 'numeric', 
      month: 'long', 
      day: 'numeric' 
    };
    return date.toLocaleDateString('fr-FR', options);
  }
  
  getTotalForDay(echeances: EcheanceDetail[], type: 'VENTE' | 'ACHAT'): number {
    return echeances
      .filter(e => e.type === type)
      .reduce((sum, e) => sum + (e.montant || 0), 0);
  }
}

