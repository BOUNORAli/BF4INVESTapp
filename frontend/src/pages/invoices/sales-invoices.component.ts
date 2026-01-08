import { Component, inject, computed, signal, OnInit, HostListener, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StoreService, Invoice, BC, PrevisionPaiement } from '../../services/store.service';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators, FormsModule } from '@angular/forms';
import { RouterLink, Router, ActivatedRoute } from '@angular/router';
import { ComptabiliteService } from '../../services/comptabilite.service';
import type { EcritureComptable } from '../../models/types';

@Component({
  selector: 'app-sales-invoices',
  standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule, FormsModule],
  template: `
    <div class="space-y-8 fade-in-up pb-10 relative">
      
      <!-- Header Section -->
      <div class="flex flex-col md:flex-row justify-between items-start md:items-end gap-4 border-b border-slate-200/60 pb-6">
        <div>
           <h1 class="text-2xl md:text-3xl font-bold text-slate-800 font-display">Factures Vente</h1>
           <p class="text-slate-500 mt-2 text-sm">Suivi des encaissements clients et de la trésorerie.</p>
        </div>
        <div class="flex gap-3 w-full md:w-auto">
           <button class="flex-1 md:flex-none px-4 py-2 bg-white border border-slate-200 text-slate-600 rounded-lg text-sm font-medium hover:bg-slate-50 hover:text-slate-900 transition shadow-sm text-center">
             Relances ({{ pendingSalesCount() }})
           </button>
           <button (click)="openForm()" class="flex-1 md:flex-none px-5 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 shadow-lg shadow-blue-600/20 font-medium transition flex items-center justify-center gap-2">
             <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6"></path></svg>
             Créer Facture
           </button>
        </div>
      </div>

      <!-- KPI Grid -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
        <!-- Total Encaissement -->
        <div class="bg-white p-6 rounded-2xl shadow-sm border border-slate-100 hover-card relative overflow-hidden group">
          <div class="absolute -right-6 -top-6 w-24 h-24 bg-blue-50 rounded-full group-hover:scale-110 transition-transform duration-500"></div>
          <div class="relative z-10">
            <div class="text-xs font-bold text-blue-600 uppercase tracking-wider mb-1">Chiffre d'affaires facturé</div>
            <div class="text-3xl font-extrabold text-slate-800 break-words">{{ formatLargeNumber(totalSales()) }}</div>
            @if (caGrowthPercent() !== null) {
              <div class="mt-3 flex items-center gap-2 text-xs font-medium w-fit px-2 py-1 rounded-full"
                   [class.text-emerald-600]="caGrowthPercent()! >= 0"
                   [class.bg-emerald-50]="caGrowthPercent()! >= 0"
                   [class.text-red-600]="caGrowthPercent()! < 0"
                   [class.bg-red-50]="caGrowthPercent()! < 0">
                @if (caGrowthPercent()! >= 0) {
                  <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6"></path></svg>
                } @else {
                  <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 17h8m0 0V9m0 8l-8-8-4 4-6-6"></path></svg>
                }
                {{ caGrowthPercent()! >= 0 ? '+' : '' }}{{ caGrowthPercent()!.toFixed(1) }}% ce mois
              </div>
            }
          </div>
        </div>

        <!-- En attente -->
        <div class="bg-white p-6 rounded-2xl shadow-sm border border-slate-100 hover-card">
           <div class="text-xs font-bold text-amber-500 uppercase tracking-wider mb-1">En attente de paiement</div>
           <div class="text-3xl font-extrabold text-slate-800 break-words">{{ formatLargeNumber(pendingSales()) }}</div>
           <p class="text-xs text-slate-500 mt-3">Sur {{ pendingSalesCount() }} factures émises</p>
        </div>

        <!-- Taux de recouvrement -->
        <div class="bg-gradient-to-br from-indigo-600 to-blue-700 p-6 rounded-2xl shadow-lg shadow-blue-500/20 text-white hover-card">
           <div class="text-xs font-bold text-blue-200 uppercase tracking-wider mb-1">Taux de recouvrement</div>
           <div class="flex items-center gap-3">
             <div class="text-3xl font-extrabold">{{ recoveryRate() | number:'1.0-0' }}%</div>
             <div class="w-px h-8 bg-blue-400/30"></div>
             <div class="text-xs text-blue-100 leading-snug whitespace-pre-line">{{ getRecoveryRateLabel() }}</div>
           </div>
           <div class="w-full bg-blue-900/30 rounded-full h-1.5 mt-4">
              <div class="bg-white h-1.5 rounded-full transition-all" [style.width.%]="getRecoveryRateWidth()"></div>
           </div>
        </div>
      </div>

      <!-- Invoices Table -->
      <div class="bg-white rounded-2xl shadow-sm border border-slate-200 overflow-hidden">
        
        <!-- Filter Bar inside Table Card -->
        <div class="p-4 border-b border-slate-100 flex flex-col md:flex-row gap-3 bg-slate-50/50 justify-between items-center">
           <div class="flex gap-2 overflow-x-auto w-full md:w-auto pb-2 md:pb-0">
             <button (click)="setFilter('all')" [class.bg-slate-800]="filterStatus() === 'all'" [class.text-white]="filterStatus() === 'all'" class="px-3 py-1.5 rounded-full text-xs font-semibold bg-white text-slate-600 border border-slate-200 hover:bg-slate-100 shrink-0 transition-colors">Tout voir</button>
             <button (click)="setFilter('paid')" [class.bg-emerald-600]="filterStatus() === 'paid'" [class.text-white]="filterStatus() === 'paid'" [class.border-emerald-600]="filterStatus() === 'paid'" class="px-3 py-1.5 rounded-full text-xs font-semibold bg-white text-slate-600 border border-slate-200 hover:bg-slate-100 shrink-0 transition-colors">Payées</button>
             <button (click)="setFilter('pending')" [class.bg-amber-500]="filterStatus() === 'pending'" [class.text-white]="filterStatus() === 'pending'" [class.border-amber-500]="filterStatus() === 'pending'" class="px-3 py-1.5 rounded-full text-xs font-semibold bg-white text-slate-600 border border-slate-200 hover:bg-slate-100 shrink-0 transition-colors">En attente</button>
             <button (click)="setFilter('overdue')" [class.bg-red-600]="filterStatus() === 'overdue'" [class.text-white]="filterStatus() === 'overdue'" [class.border-red-600]="filterStatus() === 'overdue'" class="px-3 py-1.5 rounded-full text-xs font-semibold bg-white text-slate-600 border border-slate-200 hover:bg-slate-100 shrink-0 transition-colors">Retard</button>
             <button (click)="setFilter('avoir')" [class.bg-purple-600]="filterStatus() === 'avoir'" [class.text-white]="filterStatus() === 'avoir'" [class.border-purple-600]="filterStatus() === 'avoir'" class="px-3 py-1.5 rounded-full text-xs font-semibold bg-white text-slate-600 border border-slate-200 hover:bg-slate-100 shrink-0 transition-colors">Avoirs</button>
           </div>
           
           <div class="flex gap-2 items-center w-full md:w-auto">
             <div class="relative w-full md:w-64">
                <span class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path></svg>
                </span>
                <input type="text" [(ngModel)]="searchTerm" placeholder="Rechercher facture..." class="w-full pl-9 pr-3 py-1.5 rounded-full border border-slate-200 text-xs bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all">
             </div>
             <select [(ngModel)]="sortOrder" class="px-3 py-1.5 rounded-full border border-slate-200 text-xs bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all appearance-none cursor-pointer">
               <option value="desc">Date ↓ (Récent)</option>
               <option value="asc">Date ↑ (Ancien)</option>
             </select>
           </div>
           @if (bcIdFilter()) {
             <div class="px-4 py-2 bg-indigo-50 border border-indigo-200 rounded-lg text-sm text-indigo-700 flex items-center gap-2">
               <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
               Filtré sur BC: {{ store.getBCNumber(bcIdFilter()!) }}
               <button (click)="clearBcFilter()" class="text-indigo-600 hover:text-indigo-800 ml-2">✕</button>
             </div>
           }
        </div>

        <div class="overflow-x-auto">
          <table class="w-full text-sm text-left min-w-[600px]">
            <thead class="text-xs text-slate-500 uppercase bg-slate-50 border-b border-slate-200">
              <tr>
                <th class="px-4 md:px-6 py-4 font-semibold">Facture</th>
                <th class="px-4 md:px-6 py-4 font-semibold">Client & BC</th>
                <th class="px-4 md:px-6 py-4 font-semibold hidden md:table-cell">Date Émission</th>
                <th class="px-4 md:px-6 py-4 font-semibold">Échéance</th>
                <th class="px-4 md:px-6 py-4 font-semibold text-right">Montant TTC</th>
                <th class="px-4 md:px-6 py-4 font-semibold text-center hidden md:table-cell">Montant Restant</th>
                <th class="px-4 md:px-6 py-4 font-semibold text-center">État</th>
                <th class="px-4 md:px-6 py-4 text-center">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              @for (inv of paginatedInvoices(); track inv.id) {
                <tr class="bg-white hover:bg-slate-50 transition-colors group" [class.bg-red-50/30]="inv.estAvoir" [attr.data-item-id]="inv.id">
                  <td class="px-4 md:px-6 py-4">
                    <div class="flex items-center gap-2 mb-0.5">
                      <div class="font-bold text-slate-800 text-sm md:text-base">{{ inv.number }}</div>
                      @if (inv.estAvoir) {
                        <span class="px-2 py-0.5 bg-red-100 text-red-700 rounded text-xs font-bold border border-red-200">
                          AVOIR
                        </span>
                      }
                    </div>
                    <span class="text-xs text-slate-400 hidden md:inline">Ref interne</span>
                  </td>
                  <td class="px-4 md:px-6 py-4">
                    <div class="flex flex-col gap-1">
                       <span class="font-medium text-slate-700 flex items-center gap-1.5 text-sm">
                         <div class="w-1.5 h-1.5 rounded-full bg-indigo-500"></div>
                         {{ store.getClientName(inv.partnerId || '') }}
                       </span>
                       @if (inv.bcId) {
                         <a [routerLink]="['/bc/edit', inv.bcId]" class="text-xs text-blue-500 hover:underline pl-3">BC: {{ store.getBCNumber(inv.bcId) }}</a>
                       }
                       @if (inv.estAvoir && inv.numeroFactureOrigine) {
                         <span class="text-xs text-purple-600 bg-purple-50 w-fit px-1.5 py-0.5 rounded border border-purple-200 ml-3">
                           Annule: {{ inv.numeroFactureOrigine }}
                         </span>
                       }
                    </div>
                  </td>
                  <td class="px-4 md:px-6 py-4 text-slate-600 hidden md:table-cell">{{ inv.date }}</td>
                  <td class="px-4 md:px-6 py-4">
                    <span class="text-slate-600 font-medium text-sm">{{ inv.dueDate }}</span>
                  </td>
                  <td class="px-4 md:px-6 py-4 text-right font-bold text-sm md:text-base" [class.text-red-600]="inv.estAvoir" [class.text-slate-800]="!inv.estAvoir">
                    {{ inv.amountTTC | number:'1.2-2' }} MAD
                  </td>
                  <td class="px-4 md:px-6 py-4 text-right hidden md:table-cell">
                    @if (getRemainingAmount(inv) === 0) {
                      <span class="inline-flex items-center bg-emerald-50 text-emerald-700 text-xs px-2.5 py-1 rounded-full font-medium border border-emerald-200">
                        {{ getRemainingAmount(inv) | number:'1.2-2' }} MAD
                      </span>
                    } @else {
                      <span class="inline-flex items-center bg-amber-50 text-amber-700 text-xs px-2.5 py-1 rounded-full font-medium border border-amber-200 font-bold">
                        {{ getRemainingAmount(inv) | number:'1.2-2' }} MAD
                      </span>
                    }
                  </td>
                  <td class="px-4 md:px-6 py-4 text-center">
                    <span [class]="getStatusClass(inv.status)">
                      {{ getStatusLabel(inv.status) }}
                    </span>
                  </td>
                  <td class="px-6 py-4 text-center">
                    <div class="flex items-center justify-center gap-2 opacity-100 md:opacity-0 group-hover:opacity-100 transition-opacity relative">
                      <!-- Actions Directes -->
                      <button (click)="showPaymentModal(inv)" class="p-2 text-slate-400 hover:text-emerald-600 hover:bg-emerald-50 rounded-full transition-all" title="Paiements">
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                      </button>
                      <button (click)="exportPDF(inv)" class="p-2 text-slate-400 hover:text-indigo-600 hover:bg-indigo-50 rounded-full transition-all" title="Exporter PDF Facture">
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
                      </button>
                      <button (click)="editInvoice(inv)" class="p-2 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-full transition-all" title="Modifier">
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"></path></svg>
                      </button>
                      
                      <!-- Menu Dropdown -->
                      <div class="relative">
                        <button (click)="toggleDropdown(inv.id)" class="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-50 rounded-full transition-all" title="Plus d'actions">
                          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z"></path></svg>
                        </button>
                        
                        @if (openDropdownId() === inv.id) {
                          <div class="absolute right-0 mt-2 w-56 bg-white rounded-lg shadow-lg border border-slate-200 z-50 py-1">
                            <button (click)="showCalculDetails(inv); closeDropdown()" class="w-full px-4 py-2 text-left text-sm text-slate-700 hover:bg-slate-50 flex items-center gap-3">
                              <svg class="w-4 h-4 text-purple-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 7h6m0 10v-3m-3 3h.01M9 17h.01M9 14h.01M12 14h.01M15 11h.01M12 11h.01M9 11h.01M7 21h10a2 2 0 002-2V5a2 2 0 00-2-2H7a2 2 0 00-2 2v14a2 2 0 002 2z"></path></svg>
                              <span>Détails comptables</span>
                            </button>
                            <button (click)="exportBonDeLivraison(inv); closeDropdown()" class="w-full px-4 py-2 text-left text-sm text-slate-700 hover:bg-slate-50 flex items-center gap-3">
                              <svg class="w-4 h-4 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
                              <span>Bon de Livraison</span>
                            </button>
                            <button (click)="viewAuditLog(inv); closeDropdown()" class="w-full px-4 py-2 text-left text-sm text-slate-700 hover:bg-slate-50 flex items-center gap-3">
                              <svg class="w-4 h-4 text-amber-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
                              <span>Journal d'activité</span>
                            </button>
                            <button (click)="showEcrituresModal(inv); closeDropdown()" class="w-full px-4 py-2 text-left text-sm text-slate-700 hover:bg-slate-50 flex items-center gap-3">
                              <svg class="w-4 h-4 text-cyan-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
                              <span>Écritures comptables</span>
                            </button>
                            <button (click)="openInComptabilite(inv); closeDropdown()" class="w-full px-4 py-2 text-left text-sm text-slate-700 hover:bg-slate-50 flex items-center gap-3">
                              <svg class="w-4 h-4 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"></path></svg>
                              <span>Ouvrir dans Comptabilité</span>
                            </button>
                            <div class="border-t border-slate-200 my-1"></div>
                            <button (click)="deleteInvoice(inv.id); closeDropdown()" class="w-full px-4 py-2 text-left text-sm text-red-600 hover:bg-red-50 flex items-center gap-3">
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
              @if (paginatedInvoices().length === 0) {
                <tr>
                  <td colspan="8" class="px-6 py-12 text-center text-slate-500">
                    Aucune facture trouvée.
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <!-- Pagination Controls -->
        <div class="p-4 border-t border-slate-200 bg-slate-50 flex items-center justify-between">
          <div class="text-xs text-slate-500">
            Affichage de {{ (currentPage() - 1) * pageSize() + 1 }} à {{ Math.min(currentPage() * pageSize(), filteredInvoices().length) }} sur {{ filteredInvoices().length }} résultats
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
              <button (click)="prevPage()" [disabled]="currentPage() === 1" class="p-2 border border-slate-200 rounded-lg hover:bg-white disabled:opacity-50 disabled:cursor-not-allowed">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"></path></svg>
              </button>
              <span class="text-sm font-medium text-slate-700">Page {{ currentPage() }} sur {{ totalPages() || 1 }}</span>
              <button (click)="nextPage()" [disabled]="currentPage() === totalPages() || totalPages() === 0" class="p-2 border border-slate-200 rounded-lg hover:bg-white disabled:opacity-50 disabled:cursor-not-allowed">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"></path></svg>
              </button>
            </div>
          </div>
        </div>
      </div>

       <!-- SLIDE OVER FORM -->
      @if (isFormOpen()) {
         <div class="fixed inset-0 z-50 flex justify-end" aria-modal="true">
            <!-- Backdrop -->
            <div (click)="closeForm()" class="fixed inset-0 bg-slate-900/40 backdrop-blur-sm transition-opacity"></div>
            
            <!-- Panel -->
            <div class="relative w-full md:w-[600px] bg-white h-full shadow-2xl flex flex-col transform transition-transform animate-[slideInRight_0.3s_ease-out]">
               <div class="flex items-center justify-between p-4 md:p-6 border-b border-slate-100 bg-slate-50/50">
                  <h2 class="text-lg md:text-xl font-bold text-slate-800">
                    {{ isEditMode() ? 'Modifier' : 'Créer' }} Facture Vente
                  </h2>
                  <button (click)="closeForm()" class="text-slate-400 hover:text-slate-600 transition">
                     <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                  </button>
               </div>
               
               <form [formGroup]="form" (ngSubmit)="onSubmit()" class="flex-1 overflow-y-auto p-4 md:p-6 space-y-6">
                  
                  <!-- Section Link -->
                  <div class="bg-indigo-50 p-4 rounded-xl border border-indigo-100 space-y-4">
                     <!-- Option Avoir -->
                     <div class="bg-red-50 p-4 rounded-xl border border-red-100">
                        <label class="flex items-center gap-3 cursor-pointer">
                           <input type="checkbox" formControlName="estAvoir" 
                                  class="w-5 h-5 text-red-600 border-red-300 rounded focus:ring-red-500 focus:ring-2">
                           <div>
                              <span class="text-sm font-semibold text-red-800">Facture d'Avoir</span>
                              <p class="text-xs text-red-600 mt-0.5">Cochez si c'est un avoir annulant une autre facture</p>
                           </div>
                        </label>
                        
                        @if (form.get('estAvoir')?.value) {
                          <div class="mt-3">
                            <label class="block text-xs font-bold text-red-700 uppercase mb-1">Facture d'origine à annuler</label>
                            <select formControlName="factureOrigineId" class="w-full p-2 border border-red-200 rounded-lg bg-white focus:ring-2 focus:ring-red-500/20 outline-none">
                              <option value="">Sélectionner...</option>
                              @for (facture of availableFacturesForAvoir(); track facture.id) {
                                <option [value]="facture.id">
                                  {{ facture.number }} - {{ facture.amountTTC | number:'1.2-2' }} MAD - {{ facture.date }}
                                </option>
                              }
                            </select>
                          </div>
                        }
                     </div>
                     
                     <div>
                        <label class="block text-xs font-bold text-indigo-700 uppercase mb-1">Client <span class="text-red-500">*</span></label>
                        <select formControlName="partnerId" (change)="onPartnerChange()" 
                                [class.border-red-300]="isFieldInvalid('partnerId')"
                                class="w-full p-2 border border-indigo-200 rounded-lg bg-white focus:ring-2 focus:ring-blue-500/20 outline-none">
                           <option value="">Sélectionner un client</option>
                           @for (c of store.clients(); track c.id) {
                              <option [value]="c.id">{{ c.name }}</option>
                           }
                        </select>
                        @if (isFieldInvalid('partnerId')) {
                           <p class="text-xs text-red-500 mt-1">Le client est requis</p>
                        }
                     </div>
                     <div>
                        <label class="block text-xs font-bold text-indigo-700 uppercase mb-1">Basé sur BC (Optionnel)</label>
                        <select formControlName="bcId" (change)="onBCChange()" class="w-full p-2 border border-indigo-200 rounded-lg bg-white focus:ring-2 focus:ring-blue-500/20 outline-none">
                           <option value="">Aucun BC</option>
                           @for (bc of availableBCs(); track bc.id) {
                              <option [value]="bc.id">{{ bc.number }} - {{ bc.date }}</option>
                           }
                        </select>
                     </div>
                  </div>

                  <div class="space-y-4">
                     <div>
                       <label class="block text-sm font-semibold text-slate-700 mb-1">Numéro Facture</label>
                       <input formControlName="number" type="text" readonly [placeholder]="isEditMode() ? 'Numéro existant' : 'Généré automatiquement'" class="w-full px-4 py-2 border border-slate-200 rounded-lg bg-slate-50 font-mono text-slate-700 cursor-not-allowed">
                       <p class="text-xs text-slate-400 mt-1">Le numéro sera généré automatiquement au format [mois][numéro]/[année]</p>
                     </div>

                     <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                          <label class="block text-sm font-semibold text-slate-700 mb-1">Date Émission <span class="text-red-500">*</span></label>
                          <input formControlName="date" type="date" 
                                 [class.border-red-300]="isFieldInvalid('date')"
                                 class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                          @if (isFieldInvalid('date')) {
                             <p class="text-xs text-red-500 mt-1">Date requise</p>
                          }
                        </div>
                        <div>
                          <label class="block text-sm font-semibold text-slate-700 mb-1">Date Échéance <span class="text-red-500">*</span></label>
                          <input formControlName="dueDate" type="date" 
                                 [class.border-red-300]="isFieldInvalid('dueDate')"
                                 class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                          @if (isFieldInvalid('dueDate')) {
                             <p class="text-xs text-red-500 mt-1">Date requise</p>
                          }
                        </div>
                     </div>
                     
                     <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                           <label class="block text-sm font-semibold text-slate-700 mb-1">Montant HT <span class="text-red-500">*</span></label>
                           <input formControlName="amountHT" type="number" step="0.01" 
                                  [class.border-red-300]="isFieldInvalid('amountHT')"
                                  class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition text-right">
                           @if (isFieldInvalid('amountHT')) {
                              <p class="text-xs text-red-500 mt-1">Montant requis (>0)</p>
                           }
                        </div>
                        <div>
                           <label class="block text-sm font-semibold text-slate-700 mb-1">Montant TTC <span class="text-red-500">*</span></label>
                           <input formControlName="amountTTC" type="number" step="0.01" 
                                  [class.border-red-300]="isFieldInvalid('amountTTC')"
                                  class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition text-right font-bold text-slate-800">
                           @if (isFieldInvalid('amountTTC')) {
                              <p class="text-xs text-red-500 mt-1">Montant requis (>0)</p>
                           }
                        </div>
                     </div>

                     <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                           <label class="block text-sm font-semibold text-slate-700 mb-1">Statut Paiement</label>
                           <select formControlName="status" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                              <option value="pending">En attente</option>
                              <option value="paid">Payée</option>
                              <option value="overdue">En retard</option>
                           </select>
                        </div>
                        <div>
                           <label class="block text-sm font-semibold text-slate-700 mb-1">Mode de Paiement</label>
                           <select formControlName="paymentMode" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                              <option value="">Non défini</option>
                              @for (mode of activePaymentModes(); track mode.id) {
                                 <option [value]="mode.name">{{ mode.name }}</option>
                              }
                           </select>
                        </div>
                     </div>

                     @if (stockWarnings().length > 0) {
                        <div class="bg-amber-50 border border-amber-200 rounded-xl p-4 space-y-2">
                           <div class="flex items-center gap-2 text-amber-800 font-semibold text-sm">
                              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path></svg>
                              <span>Stock insuffisant pour certains produits</span>
                           </div>
                           <div class="text-xs text-amber-700 space-y-1">
                              @for (warning of stockWarnings(); track warning.produitRef) {
                                 <div class="flex items-center justify-between">
                                    <span class="font-medium">{{ warning.designation || warning.produitRef }}</span>
                                    <span class="ml-2">Stock: <span class="font-bold">{{ warning.stockActuel }}</span> / Quantité: <span class="font-bold">{{ warning.quantiteDemandee }}</span></span>
                                 </div>
                              }
                           </div>
                           <p class="text-xs text-amber-600 mt-2 italic">La vente sera autorisée mais le stock deviendra négatif.</p>
                        </div>
                     }
                  </div>
               </form>

               <div class="p-4 md:p-6 border-t border-slate-100 bg-slate-50/50 flex gap-3">
                  <button (click)="closeForm()" class="flex-1 px-4 py-2.5 bg-white border border-slate-200 text-slate-700 font-medium rounded-lg hover:bg-slate-50 transition min-h-[44px]">Annuler</button>
                  <button (click)="onSubmit()" [disabled]="form.invalid" class="flex-1 px-4 py-2.5 bg-blue-600 text-white font-bold rounded-lg hover:bg-blue-700 transition shadow-lg disabled:opacity-50 shadow-blue-600/20 min-h-[44px]">
                    {{ isEditMode() ? 'Mettre à jour' : 'Créer Facture' }}
                  </button>
               </div>
            </div>
         </div>
      }

      <!-- Modal Détails Comptables -->
      @if (selectedInvoiceForDetails()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center" aria-modal="true">
          <div (click)="closeCalculDetails()" class="fixed inset-0 bg-slate-900/40 backdrop-blur-sm"></div>
          <div class="relative bg-white rounded-2xl shadow-2xl max-w-4xl w-full mx-2 md:mx-4 max-h-[90vh] overflow-hidden flex flex-col">
            <div class="flex items-center justify-between p-4 md:p-6 border-b border-slate-100 bg-gradient-to-r from-purple-50 to-indigo-50">
              <div>
                <h2 class="text-lg md:text-xl font-bold text-slate-800">Détails Comptables</h2>
                <p class="text-xs md:text-sm text-slate-600 mt-1">Facture: {{ selectedInvoiceForDetails()?.number }}</p>
              </div>
              <button (click)="closeCalculDetails()" class="text-slate-400 hover:text-slate-600 transition min-h-[44px] min-w-[44px] flex items-center justify-center">
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
              </button>
            </div>
            
            <div class="flex-1 overflow-y-auto p-4 md:p-6 space-y-4 md:space-y-6">
              @if (selectedInvoiceForDetails(); as inv) {
                <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <!-- Informations de base -->
                  <div class="bg-slate-50 p-4 rounded-lg">
                    <h3 class="font-semibold text-slate-700 mb-3">Informations</h3>
                    <div class="space-y-2 text-sm">
                      <div><span class="text-slate-500">Référence BC:</span> <span class="font-medium">{{ inv.bcReference || '-' }}</span></div>
                      <div><span class="text-slate-500">Type Mouvement:</span> <span class="font-medium">{{ inv.typeMouvement || '-' }}</span></div>
                      <div><span class="text-slate-500">Nature:</span> <span class="font-medium">{{ inv.nature || '-' }}</span></div>
                      <div><span class="text-slate-500">TVA Mois:</span> <span class="font-medium">{{ inv.tvaMois || '-' }}</span></div>
                    </div>
                  </div>

                  <!-- Totaux -->
                  <div class="bg-blue-50 p-4 rounded-lg">
                    <h3 class="font-semibold text-blue-700 mb-3">Totaux</h3>
                    <div class="space-y-2 text-sm">
                      <div><span class="text-blue-600">TTC après RG:</span> <span class="font-bold">{{ inv.totalTTCApresRG | number:'1.2-2' }} MAD</span></div>
                      <div><span class="text-blue-600">TTC après RG (signé):</span> <span class="font-bold">{{ inv.totalTTCApresRG_SIGNE | number:'1.2-2' }} MAD</span></div>
                      <div><span class="text-blue-600">Solde:</span> <span class="font-bold">{{ inv.solde | number:'1.2-2' }} MAD</span></div>
                    </div>
                  </div>

                  <!-- Remise Globale -->
                  <div class="bg-emerald-50 p-4 rounded-lg">
                    <h3 class="font-semibold text-emerald-700 mb-3">Remise Globale</h3>
                    <div class="space-y-2 text-sm">
                      <div><span class="text-emerald-600">RG TTC:</span> <span class="font-bold">{{ inv.rgTTC | number:'1.2-2' }} MAD</span></div>
                      <div><span class="text-emerald-600">RG HT:</span> <span class="font-bold">{{ inv.rgHT | number:'1.2-2' }} MAD</span></div>
                      <div><span class="text-emerald-600">Taux RG:</span> <span class="font-bold">{{ (inv.tauxRG || 0) * 100 | number:'1.0-2' }}%</span></div>
                    </div>
                  </div>

                  <!-- Facture HT -->
                  <div class="bg-amber-50 p-4 rounded-lg">
                    <h3 class="font-semibold text-amber-700 mb-3">Facture HT</h3>
                    <div class="space-y-2 text-sm">
                      <div><span class="text-amber-600">HT YC RG:</span> <span class="font-bold">{{ inv.factureHT_YC_RG | number:'1.2-2' }} MAD</span></div>
                      <div><span class="text-amber-600">TVA Facture YC RG:</span> <span class="font-bold">{{ inv.tvaFactureYcRg | number:'1.2-2' }} MAD</span></div>
                    </div>
                  </div>

                  <!-- Paiements -->
                  <div class="bg-indigo-50 p-4 rounded-lg">
                    <h3 class="font-semibold text-indigo-700 mb-3">Paiements</h3>
                    <div class="space-y-2 text-sm">
                      <div><span class="text-indigo-600">Total Paiement TTC:</span> <span class="font-bold">{{ inv.totalPaiementTTC | number:'1.2-2' }} MAD</span></div>
                      <div><span class="text-indigo-600">HT Payé:</span> <span class="font-bold">{{ inv.htPaye | number:'1.2-2' }} MAD</span></div>
                      <div><span class="text-indigo-600">TVA Payée:</span> <span class="font-bold">{{ inv.tvaPaye | number:'1.2-2' }} MAD</span></div>
                    </div>
                  </div>

                  <!-- Bilan -->
                  <div class="bg-purple-50 p-4 rounded-lg">
                    <h3 class="font-semibold text-purple-700 mb-3">Bilan</h3>
                    <div class="space-y-2 text-sm">
                      <div><span class="text-purple-600">Bilan HT:</span> <span class="font-bold text-lg">{{ inv.bilan | number:'1.2-2' }} MAD</span></div>
                    </div>
                  </div>
                </div>
              }
            </div>

            <div class="p-4 md:p-6 border-t border-slate-100 bg-slate-50/50">
              <button (click)="closeCalculDetails()" class="w-full px-4 py-2.5 bg-slate-200 text-slate-700 font-medium rounded-lg hover:bg-slate-300 transition min-h-[44px]">
                Fermer
              </button>
            </div>
          </div>
        </div>
      }

      <!-- Modal Écritures Comptables -->
      @if (selectedInvoiceForEcritures()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center" aria-modal="true">
          <div (click)="closeEcrituresModal()" class="fixed inset-0 bg-slate-900/40 backdrop-blur-sm"></div>
          <div class="relative bg-white rounded-2xl shadow-2xl max-w-5xl w-full mx-2 md:mx-4 max-h-[90vh] overflow-hidden flex flex-col">
            <div class="flex items-center justify-between p-4 md:p-6 border-b border-slate-100 bg-gradient-to-r from-cyan-50 to-blue-50">
              <div>
                <h2 class="text-xl font-bold text-slate-800">Écritures Comptables</h2>
                <p class="text-sm text-slate-600 mt-1">Facture: {{ selectedInvoiceForEcritures()?.number }}</p>
              </div>
              <div class="flex gap-2">
                <button (click)="openInComptabilite(selectedInvoiceForEcritures()!)" class="px-3 py-1.5 text-sm bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition flex items-center gap-2">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"></path></svg>
                  Ouvrir dans Comptabilité
                </button>
                <button (click)="closeEcrituresModal()" class="text-slate-400 hover:text-slate-600 transition">
                  <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                </button>
              </div>
            </div>
            <div class="flex-1 overflow-y-auto p-4 md:p-6">
              <!-- Tabs -->
              <div class="border-b border-slate-200 mb-4">
                <div class="flex gap-4">
                  <button (click)="activeEcrituresTab.set('ecritures')" [class]="activeEcrituresTab() === 'ecritures' ? 'border-b-2 border-blue-600 text-blue-600' : 'text-slate-600'" class="px-4 py-2 font-semibold">
                    Écritures
                  </button>
                  <button (click)="activeEcrituresTab.set('tva')" [class]="activeEcrituresTab() === 'tva' ? 'border-b-2 border-blue-600 text-blue-600' : 'text-slate-600'" class="px-4 py-2 font-semibold">
                    TVA
                  </button>
                </div>
              </div>

              <!-- Écritures Tab -->
              @if (activeEcrituresTab() === 'ecritures') {
                @if (loadingEcritures()) {
                  <div class="text-center py-12 text-slate-500">Chargement...</div>
                } @else if (ecrituresComptables().length > 0) {
                  <div class="space-y-4">
                    @for (ecriture of ecrituresComptables(); track ecriture.id) {
                      <div class="bg-slate-50 rounded-lg p-4 border border-slate-200">
                        <div class="flex items-center justify-between mb-3">
                          <div>
                            <span class="text-sm font-semibold text-slate-700">{{ formatDate(ecriture.dateEcriture) }}</span>
                            <span class="ml-3 px-2 py-1 bg-blue-100 text-blue-700 rounded text-xs font-semibold">{{ ecriture.journal }}</span>
                            <span class="ml-2 text-xs text-slate-500 font-mono">{{ ecriture.numeroPiece }}</span>
                          </div>
                          <span class="text-sm text-slate-600">{{ ecriture.libelle }}</span>
                        </div>
                        <div class="overflow-x-auto">
                          <table class="w-full text-sm">
                            <thead class="bg-slate-100">
                              <tr>
                                <th class="px-3 py-2 text-left font-semibold text-slate-700">Compte</th>
                                <th class="px-3 py-2 text-left font-semibold text-slate-700">Libellé</th>
                                <th class="px-3 py-2 text-right font-semibold text-slate-700">Débit</th>
                                <th class="px-3 py-2 text-right font-semibold text-slate-700">Crédit</th>
                              </tr>
                            </thead>
                            <tbody class="divide-y divide-slate-200">
                              @for (ligne of ecriture.lignes; track ligne.compteCode + ligne.libelle) {
                                <tr class="hover:bg-slate-50">
                                  <td class="px-3 py-2 font-mono text-xs">{{ ligne.compteCode }}</td>
                                  <td class="px-3 py-2">{{ ligne.libelle }}</td>
                                  <td class="px-3 py-2 text-right">{{ ligne.debit | number:'1.2-2' }}</td>
                                  <td class="px-3 py-2 text-right">{{ ligne.credit | number:'1.2-2' }}</td>
                                </tr>
                              }
                            </tbody>
                          </table>
                        </div>
                      </div>
                    }
                    <div class="mt-4 p-4 bg-blue-50 rounded-lg border border-blue-200">
                      <div class="flex justify-between items-center">
                        <span class="font-semibold text-slate-700">Total Débit:</span>
                        <span class="font-bold text-blue-700">{{ totalDebitEcritures() | number:'1.2-2' }}</span>
                      </div>
                      <div class="flex justify-between items-center mt-2">
                        <span class="font-semibold text-slate-700">Total Crédit:</span>
                        <span class="font-bold text-blue-700">{{ totalCreditEcritures() | number:'1.2-2' }}</span>
                      </div>
                      @if (isEcrituresBalanced()) {
                        <div class="mt-3 px-3 py-2 bg-emerald-100 text-emerald-700 rounded-lg text-sm font-semibold flex items-center gap-2">
                          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>
                          Écritures équilibrées
                        </div>
                      } @else {
                        <div class="mt-3 px-3 py-2 bg-red-100 text-red-700 rounded-lg text-sm font-semibold flex items-center gap-2">
                          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                          Écritures non équilibrées (différence: {{ Math.abs(totalDebitEcritures() - totalCreditEcritures()) | number:'1.2-2' }})
                        </div>
                      }
                    </div>
                  </div>
                } @else {
                  <div class="text-center py-12 text-slate-500">
                    Aucune écriture comptable trouvée pour cette facture
                  </div>
                }
              }

              <!-- TVA Tab -->
              @if (activeEcrituresTab() === 'tva') {
                @if (selectedInvoiceForEcritures(); as inv) {
                  <div class="space-y-4">
                    <div class="bg-slate-50 rounded-lg p-6 border border-slate-200">
                      <h3 class="font-bold text-lg mb-4 text-slate-800">Résumé TVA</h3>
                      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                          <div class="text-sm text-slate-600 mb-1">Montant HT</div>
                          <div class="text-2xl font-bold text-slate-800">{{ inv.amountHT | number:'1.2-2' }} DHS</div>
                        </div>
                        <div>
                          <div class="text-sm text-slate-600 mb-1">TVA</div>
                          <div class="text-2xl font-bold text-blue-600">{{ inv.totalTVA || (inv.amountTTC - inv.amountHT) | number:'1.2-2' }} DHS</div>
                        </div>
                        <div>
                          <div class="text-sm text-slate-600 mb-1">Taux TVA</div>
                          <div class="text-xl font-semibold text-slate-700">{{ (inv.totalTVA || (inv.amountTTC - inv.amountHT)) / (inv.amountHT || 1) * 100 | number:'1.0-1' }}%</div>
                        </div>
                        <div>
                          <div class="text-sm text-slate-600 mb-1">Montant TTC</div>
                          <div class="text-2xl font-bold text-emerald-600">{{ inv.amountTTC | number:'1.2-2' }} DHS</div>
                        </div>
                      </div>
                    </div>
                  </div>
                }
              }
            </div>
          </div>
        </div>
      }

      <!-- Modal Paiements -->
      @if (selectedInvoiceForPayments()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center" aria-modal="true">
          <div (click)="closePaymentModal()" class="fixed inset-0 bg-slate-900/40 backdrop-blur-sm"></div>
          <div class="relative bg-white rounded-2xl shadow-2xl max-w-4xl w-full mx-2 md:mx-4 max-h-[90vh] overflow-hidden flex flex-col">
            <div class="flex items-center justify-between p-4 md:p-6 border-b border-slate-100 bg-gradient-to-r from-emerald-50 to-teal-50">
              <div>
                <h2 class="text-xl font-bold text-slate-800">Facture {{ selectedInvoiceForPayments()?.number }}</h2>
                <p class="text-sm text-slate-600 mt-1">Gestion des paiements et prévisions</p>
              </div>
              <button (click)="closePaymentModal()" class="text-slate-400 hover:text-slate-600 transition">
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
              </button>
            </div>

            <!-- Onglets -->
            <div class="flex border-b border-slate-200 bg-slate-50">
              <button (click)="activeTab.set('paiements')" 
                      [class.bg-white]="activeTab() === 'paiements'"
                      [class.text-blue-600]="activeTab() === 'paiements'"
                      [class.border-b-2]="activeTab() === 'paiements'"
                      [class.border-blue-600]="activeTab() === 'paiements'"
                      class="flex-1 px-6 py-3 text-sm font-semibold text-slate-600 hover:text-slate-800 transition">
                Paiements
              </button>
              <button (click)="activeTab.set('previsions')" 
                      [class.bg-white]="activeTab() === 'previsions'"
                      [class.text-blue-600]="activeTab() === 'previsions'"
                      [class.border-b-2]="activeTab() === 'previsions'"
                      [class.border-blue-600]="activeTab() === 'previsions'"
                      class="flex-1 px-6 py-3 text-sm font-semibold text-slate-600 hover:text-slate-800 transition">
                Prévisions
              </button>
            </div>
            
            <div class="flex-1 overflow-y-auto p-4 md:p-6 space-y-6">
              @if (activeTab() === 'paiements') {
              @if (selectedInvoiceForPayments(); as inv) {
                <!-- Résumé de la facture -->
                @if (invoicePaymentSummary(); as summary) {
                  <div class="bg-gradient-to-r from-blue-50 to-indigo-50 p-4 md:p-6 rounded-xl border border-blue-100">
                    <h3 class="text-lg font-bold text-slate-800 mb-4">Résumé de la Facture</h3>
                    <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
                      <div class="bg-white p-4 rounded-lg border border-blue-200">
                        <p class="text-xs text-slate-600 mb-1">Montant Total</p>
                        <p class="text-2xl font-bold text-slate-800">{{ summary.montantTotal | number:'1.2-2' }} MAD</p>
                      </div>
                      <div class="bg-white p-4 rounded-lg border border-emerald-200">
                        <p class="text-xs text-slate-600 mb-1">Déjà Payé</p>
                        <p class="text-2xl font-bold text-emerald-600">{{ summary.montantPaye | number:'1.2-2' }} MAD</p>
                      </div>
                      <div class="bg-white p-4 rounded-lg border" [class.border-emerald-200]="summary.resteAPayer <= 0" [class.border-amber-200]="summary.resteAPayer > 0">
                        <p class="text-xs text-slate-600 mb-1">Reste à Payer</p>
                        <p class="text-2xl font-bold" [class.text-emerald-600]="summary.resteAPayer <= 0" [class.text-amber-600]="summary.resteAPayer > 0">
                          {{ summary.resteAPayer | number:'1.2-2' }} MAD
                        </p>
                      </div>
                    </div>
                    <!-- Barre de progression -->
                    <div class="w-full bg-slate-200 rounded-full h-3 overflow-hidden">
                      <div class="h-full bg-gradient-to-r from-emerald-500 to-teal-600 transition-all duration-500" 
                           [style.width.%]="summary.pourcentagePaye">
                      </div>
                    </div>
                    <p class="text-xs text-slate-600 mt-2 text-center">
                      {{ summary.pourcentagePaye | number:'1.0-1' }}% payé
                      @if (summary.resteAPayer <= 0) {
                        <span class="text-emerald-600 font-bold">✓ Facture réglée</span>
                      }
                    </p>
                  </div>
                }

                <!-- Liste des paiements -->
                <div class="space-y-4">
                  <h3 class="text-lg font-bold text-slate-800">Historique des Paiements</h3>
                  @if (paymentsForInvoice().length > 0) {
                    <div class="overflow-x-auto">
                      <table class="w-full text-sm min-w-[600px]">
                        <thead class="bg-slate-50 border-b border-slate-200">
                          <tr>
                            <th class="px-4 py-3 text-left font-semibold text-slate-700">Date</th>
                            <th class="px-4 py-3 text-left font-semibold text-slate-700">Type</th>
                            <th class="px-4 py-3 text-left font-semibold text-slate-700">Mode</th>
                            <th class="px-4 py-3 text-right font-semibold text-slate-700">Montant</th>
                            <th class="px-4 py-3 text-right font-semibold text-slate-700">Solde Global Après</th>
                            <th class="px-4 py-3 text-right font-semibold text-slate-700">Solde Client Après</th>
                            <th class="px-4 py-3 text-left font-semibold text-slate-700">Référence</th>
                          </tr>
                        </thead>
                        <tbody class="divide-y divide-slate-100">
                          @for (payment of paymentsForInvoice(); track payment.id) {
                            <tr class="hover:bg-slate-50">
                              <td class="px-4 py-3">{{ payment.date }}</td>
                              <td class="px-4 py-3">
                                <span class="px-2 py-1 bg-blue-50 text-blue-700 rounded text-xs font-medium">Paiement Client</span>
                              </td>
                              <td class="px-4 py-3">{{ payment.mode }}</td>
                              <td class="px-4 py-3 text-right font-bold text-emerald-600">{{ payment.montant | number:'1.2-2' }} MAD</td>
                              <td class="px-4 py-3 text-right" [class.text-emerald-600]="(payment.soldeGlobalApres || 0) >= 0" [class.text-red-600]="(payment.soldeGlobalApres || 0) < 0">
                                {{ payment.soldeGlobalApres | number:'1.2-2' }} MAD
                              </td>
                              <td class="px-4 py-3 text-right" [class.text-emerald-600]="(payment.soldePartenaireApres || 0) >= 0" [class.text-red-600]="(payment.soldePartenaireApres || 0) < 0">
                                {{ payment.soldePartenaireApres | number:'1.2-2' }} MAD
                              </td>
                              <td class="px-4 py-3 text-xs text-slate-500">{{ payment.reference || '-' }}</td>
                            </tr>
                          }
                        </tbody>
                      </table>
                    </div>
                  } @else {
                    <div class="text-center py-8 text-slate-500">
                      <p>Aucun paiement enregistré pour cette facture.</p>
                    </div>
                  }
                </div>

                <!-- Formulaire pour ajouter un paiement -->
                <div class="bg-slate-50 p-4 md:p-6 rounded-xl border border-slate-200">
                  <h3 class="text-lg font-bold text-slate-800 mb-4">Ajouter un Paiement</h3>
                  <form [formGroup]="paymentForm" (ngSubmit)="addPayment()" class="space-y-4">
                    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                      <div>
                        <label class="block text-sm font-semibold text-slate-700 mb-1">Date</label>
                        <input formControlName="date" type="date" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
                      </div>
                      <div>
                        <label class="block text-sm font-semibold text-slate-700 mb-1">Montant (MAD)</label>
                        <input formControlName="montant" type="number" step="0.01" 
                               [max]="invoicePaymentSummary()?.resteAPayer || 0"
                               class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none text-right">
                        @if (invoicePaymentSummary()?.resteAPayer) {
                          <p class="text-xs text-slate-500 mt-1">Reste à payer: {{ invoicePaymentSummary()!.resteAPayer | number:'1.2-2' }} MAD</p>
                        }
                      </div>
                    </div>
                    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                      <div>
                        <label class="block text-sm font-semibold text-slate-700 mb-1">Mode de Paiement</label>
                        <select formControlName="mode" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
                          <option value="">Sélectionner</option>
                          @for (mode of activePaymentModes(); track mode.id) {
                            <option [value]="mode.name">{{ mode.name }}</option>
                          }
                        </select>
                      </div>
                      <div>
                        <label class="block text-sm font-semibold text-slate-700 mb-1">Référence</label>
                        <input formControlName="reference" type="text" placeholder="N° chèque, virement..." class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
                      </div>
                    </div>
                    <div>
                      <label class="block text-sm font-semibold text-slate-700 mb-1">Notes</label>
                      <textarea formControlName="notes" rows="2" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none"></textarea>
                    </div>
                    <button type="submit" [disabled]="paymentForm.invalid" class="w-full px-4 py-2 bg-emerald-600 text-white font-bold rounded-lg hover:bg-emerald-700 transition disabled:opacity-50">
                      Enregistrer le Paiement
                    </button>
                    </form>
                </div>
              }
              } @else {
                <!-- Onglet Prévisions -->
                <div class="space-y-6">
                  <h3 class="text-lg font-bold text-slate-800">Prévisions de Paiement</h3>
                  
                  <!-- Liste des prévisions -->
                  @if (previsionsForInvoice().length > 0) {
                    <div class="overflow-x-auto">
                      <table class="w-full text-sm min-w-[500px]">
                        <thead class="bg-slate-50 border-b border-slate-200">
                          <tr>
                            <th class="px-4 py-3 text-left font-semibold text-slate-700">Date Prévue</th>
                            <th class="px-4 py-3 text-right font-semibold text-slate-700">Montant</th>
                            <th class="px-4 py-3 text-left font-semibold text-slate-700">Statut</th>
                            <th class="px-4 py-3 text-left font-semibold text-slate-700">Paiement</th>
                            <th class="px-4 py-3 text-left font-semibold text-slate-700">Date de Rappel</th>
                            <th class="px-4 py-3 text-left font-semibold text-slate-700">Notes</th>
                            <th class="px-4 py-3 text-center font-semibold text-slate-700">Actions</th>
                          </tr>
                        </thead>
                        <tbody class="divide-y divide-slate-100">
                          @for (prev of previsionsForInvoice(); track prev.id) {
                            <tr class="hover:bg-slate-50">
                              <td class="px-4 py-3">{{ prev.datePrevue }}</td>
                              <td class="px-4 py-3 text-right font-bold text-blue-600">{{ prev.montantPrevu | number:'1.2-2' }} MAD</td>
                              <td class="px-4 py-3">
                                <span class="px-2 py-1 rounded text-xs font-medium"
                                      [class.bg-blue-50]="prev.statut === 'PREVU' || prev.statut === 'EN_ATTENTE'"
                                      [class.text-blue-700]="prev.statut === 'PREVU' || prev.statut === 'EN_ATTENTE'"
                                      [class.bg-emerald-50]="prev.statut === 'REALISE' || prev.statut === 'PAYEE'"
                                      [class.text-emerald-700]="prev.statut === 'REALISE' || prev.statut === 'PAYEE'"
                                      [class.bg-orange-50]="prev.statut === 'PARTIELLE'"
                                      [class.text-orange-700]="prev.statut === 'PARTIELLE'"
                                      [class.bg-red-50]="prev.statut === 'EN_RETARD'"
                                      [class.text-red-700]="prev.statut === 'EN_RETARD'">
                                  {{ getPrevisionStatutLabel(prev.statut) }}
                                </span>
                              </td>
                              <td class="px-4 py-3 text-xs">
                                @if (prev.montantPaye !== undefined && prev.montantPaye > 0) {
                                  <div class="text-emerald-600 font-medium">Payé: {{ prev.montantPaye | number:'1.2-2' }} MAD</div>
                                }
                                @if (prev.statut === 'PARTIELLE' && prev.montantRestant !== undefined && prev.montantRestant > 0) {
                                  <div class="text-orange-600 font-bold mt-1">Restant: {{ prev.montantRestant | number:'1.2-2' }} MAD</div>
                                } @else if (prev.montantRestant !== undefined && prev.montantRestant > 0) {
                                  <div class="text-slate-500">Restant: {{ prev.montantRestant | number:'1.2-2' }} MAD</div>
                                }
                                @if (!prev.montantPaye || prev.montantPaye === 0) {
                                  <span class="text-slate-400">Non payé</span>
                                }
                              </td>
                              <td class="px-4 py-3">
                                @if (prev.dateRappel) {
                                  <div class="flex items-center gap-2">
                                    <svg class="w-4 h-4 text-amber-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"></path>
                                    </svg>
                                    <span class="text-xs text-slate-600">{{ prev.dateRappel }}</span>
                                    @if (store.isReminderToday(prev.dateRappel)) {
                                      <span class="px-2 py-0.5 bg-amber-100 text-amber-700 rounded text-xs font-bold">Aujourd'hui</span>
                                    }
                                  </div>
                                } @else {
                                  <span class="text-xs text-slate-400">-</span>
                                }
                              </td>
                              <td class="px-4 py-3 text-xs text-slate-500">{{ prev.notes || '-' }}</td>
                              <td class="px-4 py-3 text-center">
                                <button (click)="editPrevision(prev)" class="text-blue-600 hover:text-blue-800 mr-2">
                                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path></svg>
                                </button>
                                <button (click)="deletePrevision(prev.id!)" class="text-red-600 hover:text-red-800">
                                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path></svg>
                                </button>
                              </td>
                            </tr>
                          }
                        </tbody>
                      </table>
                    </div>
                  } @else {
                    <div class="text-center py-8 text-slate-500">
                      <p>Aucune prévision enregistrée pour cette facture.</p>
                    </div>
                  }

                  <!-- Formulaire pour ajouter une prévision -->
                  <div class="bg-slate-50 p-4 md:p-6 rounded-xl border border-slate-200">
                    <h3 class="text-lg font-bold text-slate-800 mb-4">{{ editingPrevisionId() ? 'Modifier' : 'Ajouter' }} une Prévision</h3>
                    <form [formGroup]="previsionForm" (ngSubmit)="savePrevision()" class="space-y-4">
                      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                          <label class="block text-sm font-semibold text-slate-700 mb-1">Date Prévue</label>
                          <input formControlName="datePrevue" type="date" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
                        </div>
                        <div>
                          <label class="block text-sm font-semibold text-slate-700 mb-1">Montant Prévu (MAD)</label>
                          <input formControlName="montantPrevu" type="number" step="0.01" 
                                 class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none text-right">
                        </div>
                      </div>
                      <div>
                        <label class="block text-sm font-semibold text-slate-700 mb-1">Notes (Optionnel)</label>
                        <textarea formControlName="notes" rows="2" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none"></textarea>
                      </div>
                      <div>
                        <label class="block text-sm font-semibold text-slate-700 mb-1">
                          Date de Rappel (Optionnel)
                          <span class="text-xs text-slate-500 font-normal ml-2">
                            Vous recevrez une notification le jour du rappel
                          </span>
                        </label>
                        <input formControlName="dateRappel" type="date" 
                               class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
                      </div>
                      <div class="flex gap-3">
                        <button type="submit" [disabled]="previsionForm.invalid" class="flex-1 px-4 py-2 bg-blue-600 text-white font-bold rounded-lg hover:bg-blue-700 transition disabled:opacity-50">
                          {{ editingPrevisionId() ? 'Modifier' : 'Ajouter' }} la Prévision
                        </button>
                        @if (editingPrevisionId()) {
                          <button type="button" (click)="cancelEditPrevision()" class="px-4 py-2.5 bg-slate-200 text-slate-700 font-medium rounded-lg hover:bg-slate-300 transition min-h-[44px]">
                            Annuler
                          </button>
                        }
                      </div>
                    </form>
                  </div>
                </div>
              }
            </div>

            <div class="p-4 md:p-6 border-t border-slate-100 bg-slate-50/50">
              <button (click)="closePaymentModal()" class="w-full px-4 py-2.5 bg-slate-200 text-slate-700 font-medium rounded-lg hover:bg-slate-300 transition min-h-[44px]">
                Fermer
              </button>
            </div>
          </div>
        </div>
      }

    </div>
  `,
  styles: [`
    @keyframes slideInRight {
      from { transform: translateX(100%); }
      to { transform: translateX(0); }
    }
  `]
})
export class SalesInvoicesComponent implements OnInit {
  Math = Math; // Make Math available in template

  store = inject(StoreService);
  fb = inject(FormBuilder);
  router = inject(Router);
  route = inject(ActivatedRoute);
  comptabiliteService = inject(ComptabiliteService);

  isFormOpen = signal(false);
  isEditMode = signal(false);
  editingId: string | null = null;
  originalInvoice: Invoice | null = null; // Stocker la facture originale pour préserver les valeurs
  availableBCs = signal<BC[]>([]);
  selectedInvoiceForDetails = signal<Invoice | null>(null);
  selectedInvoiceForPayments = signal<Invoice | null>(null);
  selectedInvoiceForEcritures = signal<Invoice | null>(null);
  activeTab = signal<'paiements' | 'previsions'>('paiements');
  activeEcrituresTab = signal<'ecritures' | 'tva'>('ecritures');
  editingPrevisionId = signal<string | null>(null);
  ecrituresComptables = signal<EcritureComptable[]>([]);
  loadingEcritures = signal(false);
  openDropdownId = signal<string | null>(null);

  // Payment form
  paymentForm = this.fb.group({
    date: [new Date().toISOString().split('T')[0], Validators.required],
    montant: [0, [Validators.required, Validators.min(0.01)]],
    mode: ['', Validators.required],
    reference: [''],
    notes: ['']
  });

  // Prevision form
  previsionForm = this.fb.group({
    datePrevue: [new Date().toISOString().split('T')[0], Validators.required],
    montantPrevu: [0, [Validators.required, Validators.min(0.01)]],
    notes: [''],
    dateRappel: ['']
  });

  // Filters
  filterStatus = signal<'all' | 'paid' | 'pending' | 'overdue' | 'avoir'>('all');
  searchTerm = signal('');
  bcIdFilter = signal<string | null>(null);
  sortOrder = signal<'asc' | 'desc'>('desc'); // Tri par date

  // Pagination
  currentPage = signal(1);
  pageSize = signal(10);

  // Active payment modes
  activePaymentModes = computed(() => this.store.paymentModes().filter(m => m.active));

  // Stock warnings for selected BC
  stockWarnings = computed(() => {
    const bcId = this.form.get('bcId')?.value;
    const clientId = this.form.get('partnerId')?.value;
    
    if (!bcId || !clientId) {
      return [];
    }
    
    const bc = this.store.bcs().find(b => b.id === bcId);
    if (!bc) {
      return [];
    }
    
    const warnings: Array<{ produitRef: string; designation: string; stockActuel: number; quantiteDemandee: number }> = [];
    
    // Nouvelle structure multi-clients
    if (bc.clientsVente && bc.clientsVente.length > 0) {
      const clientVente = bc.clientsVente.find(cv => cv.clientId === clientId);
      if (clientVente && clientVente.lignesVente) {
        for (const ligne of clientVente.lignesVente) {
          if (ligne.produitRef && ligne.quantiteVendue) {
            const product = this.store.products().find(p => p.ref === ligne.produitRef);
            const stockActuel = product?.stock ?? 0;
            if (stockActuel < ligne.quantiteVendue) {
              warnings.push({
                produitRef: ligne.produitRef,
                designation: ligne.designation || ligne.produitRef,
                stockActuel,
                quantiteDemandee: ligne.quantiteVendue
              });
            }
          }
        }
      }
    }
    // Ancienne structure
    else if (bc.items) {
      for (const item of bc.items) {
        if (item.ref && item.qtySell) {
          const product = this.store.products().find(p => p.ref === item.ref);
          const stockActuel = product?.stock ?? 0;
          if (stockActuel < item.qtySell) {
            warnings.push({
              produitRef: item.ref,
              designation: item.name || item.ref,
              stockActuel,
              quantiteDemandee: item.qtySell
            });
          }
        }
      }
    }
    
    return warnings;
  });

  form: FormGroup = this.fb.group({
    number: [''], // Laisser vide - sera généré par le backend avec la nouvelle logique
    partnerId: ['', Validators.required],
    bcId: [''],
    date: [new Date().toISOString().split('T')[0], Validators.required],
    dueDate: ['', Validators.required],
    amountHT: [0, [Validators.required, Validators.min(0)]],
    amountTTC: [0, [Validators.required, Validators.min(0)]],
    status: ['pending', Validators.required],
    paymentMode: [''],
    // Champs pour avoirs
    estAvoir: [false],
    factureOrigineId: ['']
  });
  
  // Factures disponibles pour liaison avec avoir
  availableFacturesForAvoir = computed(() => {
    const partnerId = this.form.get('partnerId')?.value;
    if (!partnerId) return [];
    
    // Retourner les factures de vente normales (non avoirs) du même client
    return this.store.salesInvoices().filter(f => 
      f.partnerId === partnerId && 
      !f.estAvoir &&
      f.type === 'sale'
    );
  });

  ngOnInit() {
    // Lire les query params pour bcId
    this.route.queryParams.subscribe(params => {
      if (params['bcId']) {
        this.bcIdFilter.set(params['bcId']);
      }
    });
  }

  // Set pour tracker les factures pour lesquelles on a déjà tenté de charger les paiements
  private paymentLoadAttempted = new Set<string>();
  private paymentLoadTimeoutId: any = null;

  constructor() {
    // Auto calculate due date default (+30 days for sales)
    this.form.get('date')?.valueChanges.subscribe(dateVal => {
      if (dateVal && !this.isEditMode()) {
        const d = new Date(dateVal);
        d.setDate(d.getDate() + 30);
        this.form.patchValue({ dueDate: d.toISOString().split('T')[0] }, { emitEvent: false });
      }
    });

    // Effet pour charger automatiquement les paiements quand les factures changent
    // Utiliser un debounce pour éviter les appels multiples
    effect(() => {
      const invoices = this.allSalesInvoices();
      if (invoices.length > 0) {
        // Clear previous timeout
        if (this.paymentLoadTimeoutId) {
          clearTimeout(this.paymentLoadTimeoutId);
        }
        // Debounce pour éviter les appels multiples
        this.paymentLoadTimeoutId = setTimeout(() => {
          // Charger les paiements pour toutes les factures en arrière-plan
          invoices.forEach(inv => {
            // Vérifier si les paiements ne sont pas déjà chargés ET qu'on n'a pas déjà tenté
            if (!this.store.payments().has(inv.id) && !this.paymentLoadAttempted.has(inv.id)) {
              this.paymentLoadAttempted.add(inv.id);
              this.store.loadPaymentsForInvoice(inv.id, 'sale').catch(() => {
                // En cas d'erreur, retirer de la liste pour permettre un nouvel essai plus tard
                this.paymentLoadAttempted.delete(inv.id);
              });
            }
          });
        }, 300); // Debounce de 300ms pour éviter les appels trop fréquents
      }
    });
  }

  // Raw list
  allSalesInvoices = computed(() => this.store.invoices().filter(i => i.type === 'sale'));

  // Computed pour recalculer le statut basé sur les paiements réels
  invoicesWithCorrectStatus = computed(() => {
    return this.allSalesInvoices().map(inv => {
      const paiements = this.store.payments().get(inv.id) || [];
      const montantPaye = paiements.reduce((sum, p) => sum + (p.montant || 0), 0);
      const montantTotal = inv.amountTTC || 0;
      const today = new Date().toISOString().split('T')[0];
      const dueDate = inv.dueDate || today;
      const tolerance = 0.01; // Tolérance pour les arrondis
      
      // Si payé à 100% (avec tolérance), statut = 'paid'
      if (montantPaye >= (montantTotal - tolerance) && montantTotal > 0) {
        return { ...inv, status: 'paid' as const };
      }
      // Si pas payé et date dépassée, statut = 'overdue'
      else if (montantPaye < (montantTotal - tolerance) && dueDate < today) {
        return { ...inv, status: 'overdue' as const };
      }
      // Si partiellement payé et date dépassée, statut = 'overdue'
      else if (montantPaye > 0 && montantPaye < (montantTotal - tolerance) && dueDate < today) {
        return { ...inv, status: 'overdue' as const };
      }
      // Si partiellement payé et date non dépassée, statut = 'pending'
      else if (montantPaye > 0 && montantPaye < (montantTotal - tolerance) && dueDate >= today) {
        return { ...inv, status: 'pending' as const };
      }
      // Sinon, garder le statut existant
      return inv;
    });
  });

  // Filtered List
  filteredInvoices = computed(() => {
    let list = this.invoicesWithCorrectStatus();
    
    // BC Filter
    const bcId = this.bcIdFilter();
    if (bcId) {
      list = list.filter(i => i.bcId === bcId);
    }
    
    // Status Filter
    const status = this.filterStatus();
    if (status === 'avoir') {
      list = list.filter(i => i.estAvoir === true);
    } else if (status !== 'all') {
      list = list.filter(i => i.status === status && !i.estAvoir);
    }

    // Search Filter
    const term = this.searchTerm().toLowerCase();
    if (term) {
      list = list.filter(i => 
        i.number.toLowerCase().includes(term) ||
        this.store.getClientName(i.partnerId || '').toLowerCase().includes(term)
      );
    }
    
    // Sort by date according to sortOrder
    const sort = this.sortOrder();
    return list.sort((a, b) => {
      const dateA = new Date(a.date).getTime();
      const dateB = new Date(b.date).getTime();
      return sort === 'desc' ? dateB - dateA : dateA - dateB;
    });
  });

  // Paginated List
  paginatedInvoices = computed(() => {
    const list = this.filteredInvoices();
    const start = (this.currentPage() - 1) * this.pageSize();
    const end = start + this.pageSize();
    return list.slice(start, end);
  });

  totalPages = computed(() => Math.ceil(this.filteredInvoices().length / this.pageSize()));

  totalSales = computed(() => {
    const invoices = this.allSalesInvoices();
    return invoices.reduce((acc, i) => acc + (i.amountTTC || 0), 0);
  });
  
  pendingSales = computed(() => {
    const invoices = this.allSalesInvoices().filter(i => i.status === 'pending' || i.status === 'overdue');
    return invoices.reduce((acc, i) => acc + (i.amountTTC || 0), 0);
  });
  
  pendingSalesCount = computed(() => {
    return this.allSalesInvoices().filter(i => i.status === 'pending' || i.status === 'overdue').length;
  });

  // Calculate CA growth percentage (this month vs last month)
  caGrowthPercent = computed(() => {
    const invoices = this.allSalesInvoices();
    if (invoices.length === 0) return null;

    const now = new Date();
    const currentMonth = now.getMonth();
    const currentYear = now.getFullYear();

    // Filter invoices for current month
    const currentMonthInvoices = invoices.filter(inv => {
      if (!inv.date) return false;
      const invDate = new Date(inv.date);
      return invDate.getMonth() === currentMonth && invDate.getFullYear() === currentYear;
    });
    const currentMonthTotal = currentMonthInvoices.reduce((acc, curr) => acc + (curr.amountTTC || 0), 0);

    // Filter invoices for last month
    const lastMonth = currentMonth === 0 ? 11 : currentMonth - 1;
    const lastMonthYear = currentMonth === 0 ? currentYear - 1 : currentYear;
    const lastMonthInvoices = invoices.filter(inv => {
      if (!inv.date) return false;
      const invDate = new Date(inv.date);
      return invDate.getMonth() === lastMonth && invDate.getFullYear() === lastMonthYear;
    });
    const lastMonthTotal = lastMonthInvoices.reduce((acc, curr) => acc + (curr.amountTTC || 0), 0);

    if (lastMonthTotal === 0) return currentMonthTotal > 0 ? 100 : null;
    
    return ((currentMonthTotal - lastMonthTotal) / lastMonthTotal) * 100;
  });

  // Calculate recovery rate (total paid / total invoiced)
  recoveryRate = computed(() => {
    const invoices = this.allSalesInvoices();
    if (invoices.length === 0) return 0;

    const totalInvoiced = invoices.reduce((acc, curr) => acc + curr.amountTTC, 0);
    if (totalInvoiced === 0) return 0;

    // Get total paid from payments
    let totalPaid = 0;
    invoices.forEach(inv => {
      const payments = this.store.getPaymentsForInvoice(inv.id);
      const paymentsTotal = payments.reduce((sum, p) => sum + (p.montant || 0), 0);
      totalPaid += paymentsTotal;
      
      // If invoice marked as paid but no payments recorded, assume full amount paid
      if (inv.status === 'paid' && paymentsTotal === 0) {
        totalPaid += inv.amountTTC;
      }
    });

    return (totalPaid / totalInvoiced) * 100;
  });

  getRecoveryRateLabel(): string {
    const rate = this.recoveryRate();
    if (rate >= 90) return 'Excellent\nniveau';
    if (rate >= 75) return 'Bon niveau';
    if (rate >= 50) return 'À améliorer';
    return 'Faible';
  }

  getRecoveryRateWidth(): number {
    const rate = this.recoveryRate();
    return Math.min(Math.max(rate, 0), 100);
  }

  formatLargeNumber(value: number): string {
    if (!value && value !== 0) return '0';
    // Formater avec séparateurs de milliers (espace en français)
    return new Intl.NumberFormat('fr-MA', {
      minimumFractionDigits: 0,
      maximumFractionDigits: 0
    }).format(value);
  }

  setFilter(status: 'all' | 'paid' | 'pending' | 'overdue' | 'avoir') {
    this.filterStatus.set(status);
    this.currentPage.set(1); // Reset page on filter change
  }

  // Pagination Actions
  nextPage() {
    if (this.currentPage() < this.totalPages()) {
      this.currentPage.update(p => p + 1);
    }
  }

  prevPage() {
    if (this.currentPage() > 1) {
      this.currentPage.update(p => p - 1);
    }
  }

  getRemainingAmount(inv: Invoice): number {
    const paiements = this.store.payments().get(inv.id) || [];
    const montantPaye = paiements.reduce((sum, p) => sum + (p.montant || 0), 0);
    const montantTotal = inv.amountTTC || 0;
    
    // Pour les avoirs, permettre les montants négatifs
    if (inv.estAvoir) {
      return montantTotal - montantPaye; // Peut être négatif pour avoir
    }
    
    return Math.max(0, montantTotal - montantPaye); // Pour factures normales, ne pas afficher de valeurs négatives
  }

  getStatusClass(status: string): string {
    switch(status) {
      case 'paid': return 'inline-flex items-center bg-emerald-100 text-emerald-800 text-xs px-3 py-1.5 rounded-full font-bold border border-emerald-200 shadow-sm whitespace-nowrap';
      case 'pending': return 'inline-flex items-center bg-amber-100 text-amber-800 text-xs px-3 py-1.5 rounded-full font-bold border border-amber-200 shadow-sm whitespace-nowrap';
      case 'overdue': return 'inline-flex items-center bg-red-100 text-red-800 text-xs px-3 py-1.5 rounded-full font-bold border border-red-200 shadow-sm whitespace-nowrap';
      default: return 'inline-flex items-center text-xs px-3 py-1.5 rounded-full font-bold whitespace-nowrap';
    }
  }

  getPrevisionStatutLabel(statut: string): string {
    switch (statut) {
      case 'PAYEE':
        return 'Payée';
      case 'PARTIELLE':
        return 'Partielle';
      case 'EN_ATTENTE':
        return 'En attente';
      case 'PREVU':
        return 'Prévu';
      case 'REALISE':
        return 'Réalisé';
      case 'EN_RETARD':
        return 'En retard';
      default:
        return statut;
    }
  }

  getStatusLabel(status: string): string {
    const map: any = { 'paid': 'Payée', 'pending': 'En attente', 'overdue': 'En retard' };
    return map[status] || status;
  }

  // --- ACTIONS ---

  openForm() {
    this.isEditMode.set(false);
    this.editingId = null;
    this.form.reset({ 
      number: '', // Laisser vide - sera généré par le backend
      date: new Date().toISOString().split('T')[0],
      status: 'pending',
      amountHT: 0,
      amountTTC: 0,
      estAvoir: false,
      factureOrigineId: ''
    });
    
    // Set default due date
    const today = new Date();
    today.setDate(today.getDate() + 30);
    this.form.patchValue({ dueDate: today.toISOString().split('T')[0] });

    this.availableBCs.set([]);
    this.isFormOpen.set(true);
  }

  closeForm() {
    this.isFormOpen.set(false);
    this.originalInvoice = null; // Réinitialiser lors de la fermeture
  }

  onPartnerChange() {
    const clientId = this.form.get('partnerId')?.value;
    if (clientId) {
      // Filtrer les BCs qui contiennent ce client (nouvelle structure multi-clients ou ancienne structure)
      const bcs = this.store.bcs().filter(bc => {
        // Nouvelle structure: clientsVente
        if (bc.clientsVente && bc.clientsVente.length > 0) {
          return bc.clientsVente.some(cv => cv.clientId === clientId);
        }
        // Ancienne structure: clientId unique
        return bc.clientId === clientId;
      });
      this.availableBCs.set(bcs);
    } else {
      this.availableBCs.set([]);
    }
  }

  onBCChange() {
    const bcId = this.form.get('bcId')?.value;
    const clientId = this.form.get('partnerId')?.value;
    const bc = this.store.bcs().find(b => b.id === bcId);
    
    if (bc) {
      let totalHT = 0;
      let totalTva = 0;

      // Nouvelle structure multi-clients
      if (bc.clientsVente && bc.clientsVente.length > 0) {
        // Trouver le client spécifique dans le BC
        const clientVente = bc.clientsVente.find(cv => cv.clientId === clientId);
        if (clientVente && clientVente.lignesVente) {
          // Utiliser les totaux pré-calculés si disponibles
          if (clientVente.totalVenteHT !== undefined && clientVente.totalVenteTTC !== undefined) {
            totalHT = clientVente.totalVenteHT;
            totalTva = (clientVente.totalVenteTTC || 0) - totalHT;
          } else {
            // Calculer à partir des lignes de vente
            totalHT = clientVente.lignesVente.reduce((acc, l) => 
              acc + (l.quantiteVendue || 0) * (l.prixVenteUnitaireHT || 0), 0);
            totalTva = clientVente.lignesVente.reduce((acc, l) => 
              acc + (l.quantiteVendue || 0) * (l.prixVenteUnitaireHT || 0) * ((l.tva || 0) / 100), 0);
          }
        } else {
          console.warn('🔵 sales-invoices.onBCChange - ClientVente ou lignesVente non trouvé pour clientId:', clientId);
        }
      } 
      // Ancienne structure
      else if (bc.items) {
        totalHT = bc.items.reduce((acc, i) => acc + (i.qtySell * i.priceSellHT), 0);
        totalTva = bc.items.reduce((acc, i) => acc + (i.qtySell * i.priceSellHT * (i.tvaRate/100)), 0);
      }
      
      // Calculer la date d'émission = dernier jour du mois du BC
      let dateEmission: string = '';
      if (bc.date) {
        const bcDate = new Date(bc.date);
        // Obtenir le dernier jour du mois du BC
        const lastDayOfMonth = new Date(bcDate.getFullYear(), bcDate.getMonth() + 1, 0);
        dateEmission = lastDayOfMonth.toISOString().split('T')[0];
      }
      
      this.form.patchValue({
        amountHT: totalHT,
        amountTTC: totalHT + totalTva,
        date: dateEmission // Définir la date d'émission au dernier jour du mois du BC
      });
    } else {
      console.warn('🔵 sales-invoices.onBCChange - BC non trouvé pour id:', bcId);
    }
  }

  editInvoice(inv: Invoice) {
    this.isEditMode.set(true);
    this.editingId = inv.id;
    // Stocker la facture originale pour préserver les valeurs
    this.originalInvoice = { ...inv };
    
    // S'assurer que tous les champs sont bien remplis, notamment les montants
    const formValues = {
      number: inv.number || '',
      partnerId: inv.partnerId || '',
      bcId: inv.bcId || '',
      date: inv.date || '',
      dueDate: inv.dueDate || '',
      amountHT: inv.amountHT != null && inv.amountHT !== undefined ? Math.abs(Number(inv.amountHT)) : 0, // Valeur absolue pour affichage
      amountTTC: inv.amountTTC != null && inv.amountTTC !== undefined ? Math.abs(Number(inv.amountTTC)) : 0, // Valeur absolue pour affichage
      status: inv.status || 'pending',
      paymentMode: inv.paymentMode || '',
      estAvoir: inv.estAvoir || false,
      factureOrigineId: inv.factureOrigineId || ''
    };
    this.form.patchValue(formValues);
    
    if (inv.partnerId) {
      const bcs = this.store.bcs().filter(b => b.clientId === inv.partnerId);
      this.availableBCs.set(bcs);
    }
    this.isFormOpen.set(true);
  }

  async exportPDF(inv: Invoice) {
    try {
      await this.store.downloadFactureVentePDF(inv.id);
    } catch (error) {
      // Error already handled in store service
    }
  }

  async exportBonDeLivraison(inv: Invoice) {
    try {
      await this.store.downloadBonDeLivraisonPDF(inv.id);
    } catch (error) {
      // Error already handled in store service
    }
  }

  async deleteInvoice(id: string) {
    if (confirm('Êtes-vous sûr de vouloir supprimer cette facture ?')) {
      try {
        await this.store.deleteInvoice(id);
      } catch (error) {
        // Error already handled in store
      }
    }
  }

  viewAuditLog(inv: Invoice) {
    this.router.navigate(['/audit'], {
      queryParams: {
        entityType: 'FactureVente',
        entityId: inv.id
      }
    });
  }

  showCalculDetails(inv: Invoice) {
    this.selectedInvoiceForDetails.set(inv);
  }

  closeCalculDetails() {
    this.selectedInvoiceForDetails.set(null);
  }

  async showPaymentModal(inv: Invoice) {
    this.selectedInvoiceForPayments.set(inv);
    this.activeTab.set('paiements');
    // Charger les paiements pour cette facture (de manière asynchrone, non bloquant)
    this.store.loadPaymentsForInvoice(inv.id, 'sale').catch(err => {
      console.error('Erreur lors du chargement des paiements:', err);
    });
    // Les prévisions sont déjà dans la facture (previsionsPaiement), pas besoin de recharger toutes les factures
    // Réinitialiser les formulaires
    this.paymentForm.reset({
      date: new Date().toISOString().split('T')[0],
      montant: 0,
      mode: '',
      reference: '',
      notes: ''
    });
    this.previsionForm.reset({
      datePrevue: new Date().toISOString().split('T')[0],
      montantPrevu: 0,
      notes: '',
      dateRappel: ''
    });
    this.editingPrevisionId.set(null);
  }

  closePaymentModal() {
    this.selectedInvoiceForPayments.set(null);
    this.editingPrevisionId.set(null);
  }

  previsionsForInvoice = computed(() => {
    const inv = this.selectedInvoiceForPayments();
    if (!inv) return [];
    return inv.previsionsPaiement || [];
  });

  async savePrevision() {
    if (this.previsionForm.invalid || !this.selectedInvoiceForPayments()) {
      return;
    }

    const inv = this.selectedInvoiceForPayments()!;
    const invoiceId = inv.id; // Sauvegarder l'ID avant le rechargement
    
    const previsionData: PrevisionPaiement = {
      datePrevue: this.previsionForm.value.datePrevue!,
      montantPrevu: this.previsionForm.value.montantPrevu!,
      notes: this.previsionForm.value.notes || '',
      dateRappel: this.previsionForm.value.dateRappel || undefined,
      statut: 'PREVU'
    };

    try {
      if (this.editingPrevisionId()) {
        await this.store.updatePrevision(inv.id, this.editingPrevisionId()!, 'vente', previsionData);
      } else {
        await this.store.addPrevision(inv.id, 'vente', previsionData);
      }
      
      this.previsionForm.reset({
        datePrevue: new Date().toISOString().split('T')[0],
        montantPrevu: 0,
        notes: '',
        dateRappel: ''
      });
      this.editingPrevisionId.set(null);
      
      // addPrevision/updatePrevision mettent à jour localement la facture
      // On met à jour immédiatement la facture sélectionnée avec les nouvelles données
      const updatedInvoice = this.store.invoices().find(inv => inv.id === invoiceId);
      if (updatedInvoice) {
        // Créer un nouvel objet pour forcer la détection de changement
        this.selectedInvoiceForPayments.set({ ...updatedInvoice });
      }
    } catch (error) {
      // Error already handled in store
    }
  }

  editPrevision(prev: PrevisionPaiement) {
    this.editingPrevisionId.set(prev.id!);
    this.previsionForm.patchValue({
      datePrevue: prev.datePrevue,
      montantPrevu: prev.montantPrevu,
      notes: prev.notes || '',
      dateRappel: prev.dateRappel || ''
    });
  }

  async deletePrevision(id: string) {
    if (!confirm('Êtes-vous sûr de vouloir supprimer cette prévision ?')) {
      return;
    }

    const inv = this.selectedInvoiceForPayments();
    if (!inv) return;

    try {
      await this.store.deletePrevision(inv.id, id, 'vente');
      // Mettre à jour localement la facture sélectionnée
      const updatedInvoice = this.store.invoices().find(i => i.id === inv.id);
      if (updatedInvoice) {
        this.selectedInvoiceForPayments.set({ ...updatedInvoice });
      }
      // Recharger les factures en arrière-plan (non bloquant) pour synchroniser le store
      this.store.loadInvoices().catch(err => {
        console.error('Erreur lors du rechargement des factures:', err);
      });
    } catch (error) {
      // Error already handled in store
    }
  }

  cancelEditPrevision() {
    this.editingPrevisionId.set(null);
    this.previsionForm.reset({
      datePrevue: new Date().toISOString().split('T')[0],
      montantPrevu: 0,
      notes: ''
    });
  }

  paymentsForInvoice = computed(() => {
    const inv = this.selectedInvoiceForPayments();
    if (!inv) return [];
    return this.store.payments().get(inv.id) || [];
  });

  invoicePaymentSummary = computed(() => {
    const inv = this.selectedInvoiceForPayments();
    if (!inv) return null;
    
    const montantTotal = inv.amountTTC || 0;
    const paiements = this.paymentsForInvoice();
    const montantPaye = paiements.reduce((sum, p) => sum + (p.montant || 0), 0);
    const resteAPayer = montantTotal - montantPaye;
    const pourcentagePaye = montantTotal > 0 ? (montantPaye / montantTotal) * 100 : 0;
    
    return {
      montantTotal,
      montantPaye,
      resteAPayer,
      pourcentagePaye: Math.min(pourcentagePaye, 100)
    };
  });

  async addPayment() {
    if (this.paymentForm.invalid || !this.selectedInvoiceForPayments()) {
      return;
    }

    const inv = this.selectedInvoiceForPayments()!;
    const invoiceId = inv.id; // Sauvegarder l'ID avant les modifications
    const paymentData = {
      factureVenteId: inv.id,
      date: this.paymentForm.value.date!,
      montant: this.paymentForm.value.montant!,
      mode: this.paymentForm.value.mode!,
      reference: this.paymentForm.value.reference || '',
      notes: this.paymentForm.value.notes || ''
    };

    try {
      await this.store.addPaiement(paymentData);
      
      // Réinitialiser le formulaire immédiatement pour une meilleure UX
      this.paymentForm.reset({
        date: new Date().toISOString().split('T')[0],
        montant: 0,
        mode: '',
        reference: '',
        notes: ''
      });
      
      // Mettre à jour localement la facture sélectionnée si elle a été mise à jour dans le store
      const updatedInvoice = this.store.invoices().find(i => i.id === invoiceId);
      if (updatedInvoice) {
        this.selectedInvoiceForPayments.set({ ...updatedInvoice });
      }
      
      // Recharger les paiements et factures en arrière-plan (non bloquant) pour synchroniser
      this.store.loadPaymentsForInvoice(inv.id, 'sale').catch(err => {
        console.error('Erreur lors du rechargement des paiements:', err);
      });
      this.store.loadInvoices().catch(err => {
        console.error('Erreur lors du rechargement des factures:', err);
      });
    } catch (error) {
      console.error('Error adding payment:', error);
    }
  }

  isFieldInvalid(fieldName: string): boolean {
    const field = this.form.get(fieldName);
    return !!(field && field.invalid && (field.dirty || field.touched));
  }

  async onSubmit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.store.showToast('Veuillez corriger les erreurs dans le formulaire', 'error');
      console.warn('🟡 onSubmit - Formulaire invalide');
      return;
    }
    
    const val = this.form.value;
    
    // En mode édition, toujours préserver les montants et autres valeurs de la facture originale
    if (this.isEditMode() && this.editingId && this.originalInvoice) {
      
      // TOUJOURS utiliser les montants de la facture originale, jamais ceux du formulaire
      // Le formulaire peut avoir des valeurs vides ou 0 à cause du formatage
      const originalHT = this.originalInvoice.amountHT;
      const originalTTC = this.originalInvoice.amountTTC;
      
      val.amountHT = originalHT;
      val.amountTTC = originalTTC;
      
      // Préserver aussi les autres champs essentiels de l'original
      val.number = this.originalInvoice.number || val.number || '';
      val.date = this.originalInvoice.date || val.date || '';
      val.dueDate = this.originalInvoice.dueDate || val.dueDate || '';
      val.partnerId = this.originalInvoice.partnerId || val.partnerId || '';
      val.bcId = this.originalInvoice.bcId || val.bcId || '';
      
      // Le statut peut être modifié, on utilise la valeur du formulaire
      val.status = val.status || this.originalInvoice.status || 'pending';
    }
    
    // S'assurer que les montants sont des nombres (important pour la création)
    const amountHT = val.amountHT != null && val.amountHT !== undefined ? Number(val.amountHT) : 0;
    const amountTTC = val.amountTTC != null && val.amountTTC !== undefined ? Number(val.amountTTC) : 0;
    
    const invoice: Invoice = {
      id: this.editingId || `fv-${Date.now()}`,
      type: 'sale',
      number: val.number,
      partnerId: val.partnerId,
      bcId: val.bcId || undefined,
      date: val.date,
      dueDate: val.dueDate,
      amountHT: val.estAvoir && amountHT > 0 ? -amountHT : amountHT, // Inverser si avoir
      amountTTC: val.estAvoir && amountTTC > 0 ? -amountTTC : amountTTC, // Inverser si avoir
      status: val.status || 'pending',
      paymentMode: val.paymentMode || undefined,
      // Champs pour avoirs
      estAvoir: val.estAvoir || false,
      typeFacture: val.estAvoir ? 'AVOIR' : 'NORMALE',
      factureOrigineId: val.factureOrigineId || undefined,
      numeroFactureOrigine: val.factureOrigineId ? undefined : undefined // Sera rempli par le backend
    };
    
    try {
      if (this.isEditMode()) {
        await this.store.updateInvoice(invoice);
      } else {
        await this.store.addInvoice(invoice);
      }
      this.closeForm();
      this.originalInvoice = null; // Réinitialiser après sauvegarde
    } catch (error) {
      // Error already handled in store service
      console.error('❌ Error saving invoice:', error);
    }
  }

  async showEcrituresModal(inv: Invoice) {
    this.selectedInvoiceForEcritures.set(inv);
    this.activeEcrituresTab.set('ecritures');
    this.loadingEcritures.set(true);
    this.ecrituresComptables.set([]);

    try {
      // Charger les écritures de la facture
      const ecrituresFacture = await this.comptabiliteService.getEcritures({
        pieceType: inv.type === 'sale' ? 'FACTURE_VENTE' : 'FACTURE_ACHAT',
        pieceId: inv.id
      }).toPromise();

      // Charger les écritures des paiements associés
      const payments = this.store.payments().get(inv.id) || [];
      const ecrituresPaiements = await Promise.all(
        payments.map(p => 
          this.comptabiliteService.getEcritures({
            pieceType: 'PAIEMENT',
            pieceId: p.id
          }).toPromise().catch(() => [])
        )
      );

      const allEcritures = [
        ...(ecrituresFacture || []),
        ...ecrituresPaiements.flat()
      ];
      
      this.ecrituresComptables.set(allEcritures);
    } catch (error) {
      this.store.showToast('Erreur lors du chargement des écritures', 'error');
    } finally {
      this.loadingEcritures.set(false);
    }
  }

  closeEcrituresModal() {
    this.selectedInvoiceForEcritures.set(null);
    this.ecrituresComptables.set([]);
  }

  openInComptabilite(inv: Invoice) {
    const pieceType = inv.type === 'sale' ? 'FACTURE_VENTE' : 'FACTURE_ACHAT';
    this.router.navigate(['/comptabilite'], {
      queryParams: {
        pieceType,
        pieceId: inv.id
      }
    });
  }

  totalDebitEcritures = computed(() => {
    return this.ecrituresComptables().reduce((sum, ecriture) => {
      return sum + (ecriture.lignes?.reduce((ligneSum, ligne) => {
        return ligneSum + (ligne.debit || 0);
      }, 0) || 0);
    }, 0);
  });

  totalCreditEcritures = computed(() => {
    return this.ecrituresComptables().reduce((sum, ecriture) => {
      return sum + (ecriture.lignes?.reduce((ligneSum, ligne) => {
        return ligneSum + (ligne.credit || 0);
      }, 0) || 0);
    }, 0);
  });

  isEcrituresBalanced = computed(() => {
    const diff = Math.abs(this.totalDebitEcritures() - this.totalCreditEcritures());
    return diff < 0.01;
  });

  clearBcFilter() {
    this.bcIdFilter.set(null);
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {},
      replaceUrl: true
    });
  }

  toggleDropdown(invoiceId: string) {
    if (this.openDropdownId() === invoiceId) {
      this.openDropdownId.set(null);
    } else {
      this.openDropdownId.set(invoiceId);
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
