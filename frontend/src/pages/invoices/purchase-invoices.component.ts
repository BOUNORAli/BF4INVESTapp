import { Component, inject, signal, computed, OnInit, HostListener, NgZone, ChangeDetectorRef, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StoreService, Invoice, BC, PrevisionPaiement } from '../../services/store.service';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators, FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { ComptabiliteService } from '../../services/comptabilite.service';
import { ApiService } from '../../services/api.service';
import { InvoiceStore } from '../../stores/invoice.store';
import { NavigationRefreshService } from '../../services/navigation-refresh.service';
import { SkeletonTableComponent } from '../../components/skeleton/skeleton-table.component';
import { firstValueFrom, take } from 'rxjs';
import type { EcritureComptable } from '../../models/types';

@Component({
  selector: 'app-purchase-invoices',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, SkeletonTableComponent],
  template: `
    <div class="space-y-6 fade-in-up relative pb-10">
      
      <!-- Header -->
      <div class="flex flex-col md:flex-row justify-between items-start md:items-center pb-4 border-b border-slate-200 gap-4">
        <div>
           <h1 class="text-2xl font-bold text-slate-800 font-display">Factures Achat</h1>
           <p class="text-sm text-slate-500 mt-1">Suivi des règlements fournisseurs et échéances TVA.</p>
        </div>
        <button (click)="openForm()" class="px-5 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 shadow-lg shadow-blue-600/20 font-medium transition flex items-center gap-2">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path></svg>
          Enregistrer Facture
        </button>
      </div>

      <!-- Alerts Section -->
      @if (store.overduePurchaseInvoices() > 0) {
        <div class="bg-red-50 border border-red-100 rounded-xl p-4 flex items-start gap-3 shadow-sm">
          <div class="p-2 bg-red-100 rounded-lg text-red-600">
             <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path></svg>
          </div>
          <div>
            <h3 class="text-sm font-bold text-red-800">Attention Requise</h3>
            <p class="text-sm text-red-700 mt-1">
              Il y a <span class="font-bold">{{ store.overduePurchaseInvoices() }}</span> factures en retard ou proches de l'échéance TVA (2 mois). Veuillez régulariser la situation.
            </p>
          </div>
        </div>
      }

      <!-- Table Card -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        
        <!-- Filter Bar -->
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

        <!-- Table wrapper with horizontal scroll -->
        @if (invoiceStore.loading()) {
          <app-skeleton-table [columns]="[
            { width: '15%' },
            { width: '20%' },
            { width: '12%' },
            { width: '12%' },
            { width: '15%', align: 'right' },
            { width: '12%', align: 'center' },
            { width: '10%', align: 'center' },
            { width: '4%', align: 'center' }
          ]" [rows]="10"></app-skeleton-table>
        } @else {
        <div class="overflow-x-auto">
          <table class="w-full text-sm text-left text-slate-600 min-w-[600px]">
          <thead class="text-xs text-slate-500 uppercase bg-slate-50 border-b border-slate-200">
            <tr>
              <th class="px-4 md:px-6 py-4 font-semibold">Numéro</th>
              <th class="px-4 md:px-6 py-4 font-semibold">Fournisseur & BC</th>
              <th class="px-4 md:px-6 py-4 font-semibold hidden md:table-cell">Date Facture</th>
              <th class="px-4 md:px-6 py-4 font-semibold">Échéance</th>
              <th class="px-4 md:px-6 py-4 font-semibold text-right">Montant TTC</th>
              <th class="px-4 md:px-6 py-4 font-semibold text-center hidden md:table-cell">Montant Restant</th>
              <th class="px-4 md:px-6 py-4 font-semibold text-center">Statut</th>
              <th class="px-4 md:px-6 py-4"></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            @for (inv of paginatedInvoices(); track inv.id) {
              <tr class="bg-white hover:bg-slate-50 transition-colors group" [class.bg-red-50/30]="inv.estAvoir" [attr.data-item-id]="inv.id">
                <td class="px-4 md:px-6 py-4 font-medium text-slate-900 text-sm md:text-base">
                  <div class="flex items-center gap-2">
                    {{ inv.number }}
                    @if (inv.estAvoir) {
                      <span class="px-2 py-0.5 bg-red-100 text-red-700 rounded text-xs font-bold border border-red-200">
                        AVOIR
                      </span>
                    }
                  </div>
                </td>
                <td class="px-4 md:px-6 py-4">
                   <div class="flex flex-col gap-1">
                     <span class="font-medium text-slate-700 text-sm">{{ store.getSupplierName(inv.partnerId || '') }}</span>
                     @if (inv.bcId) {
                       <span class="text-xs text-blue-500 bg-blue-50 w-fit px-1.5 py-0.5 rounded">{{ store.getBCNumber(inv.bcId) }}</span>
                     }
                     @if (inv.estAvoir && inv.numeroFactureOrigine) {
                       <span class="text-xs text-purple-600 bg-purple-50 w-fit px-1.5 py-0.5 rounded border border-purple-200">
                         Annule: {{ inv.numeroFactureOrigine }}
                       </span>
                     }
                   </div>
                </td>
                <td class="px-4 md:px-6 py-4 hidden md:table-cell">{{ inv.date }}</td>
                <td class="px-4 md:px-6 py-4">
                  <div class="flex items-center gap-2">
                    <span [class]="getDueDateClass(inv.dueDate)" class="text-sm">
                      {{ inv.dueDate }}
                    </span>
                  </div>
                </td>
                <td class="px-4 md:px-6 py-4 text-right font-medium text-sm md:text-base" [class.text-red-600]="inv.estAvoir" [class.text-slate-800]="!inv.estAvoir">
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
                  <div class="flex items-center justify-center gap-2">
                    <span [class]="getStatusClass(inv.status)">
                      {{ getStatusLabel(inv.status) }}
                    </span>
                    @if (hasFile(inv)) {
                      <button (click)="viewFile(inv)" class="p-1 text-emerald-600 hover:text-emerald-700 hover:bg-emerald-50 rounded-full transition-all" title="Voir le fichier">
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.172 7l-6.586 6.586a2 2 0 102.828 2.828l6.414-6.586a4 4 0 00-5.656-5.656l-6.415 6.585a6 6 0 108.486 8.486L20.5 13"></path></svg>
                      </button>
                    }
                  </div>
                </td>
                <td class="px-6 py-4 text-right">
                   <div class="flex items-center justify-end gap-2 opacity-100 md:opacity-0 group-hover:opacity-100 transition-opacity relative">
                      <!-- Actions Directes -->
                      <button (click)="showPaymentModal(inv)" class="p-2 text-slate-400 hover:text-emerald-600 hover:bg-emerald-50 rounded-full transition-all" title="Paiements">
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                      </button>
                      <button (click)="exportPDF(inv)" class="p-2 text-slate-400 hover:text-indigo-600 hover:bg-indigo-50 rounded-full transition-all" title="Exporter PDF">
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
                            <button (click)="openUploadModal(inv); closeDropdown()" class="w-full px-4 py-2 text-left text-sm text-slate-700 hover:bg-slate-50 flex items-center gap-3">
                              <svg class="w-4 h-4 text-emerald-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"></path></svg>
                              <span>{{ hasFile(inv) ? 'Remplacer fichier' : 'Uploader fichier' }}</span>
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
        }
        
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
                    {{ isEditMode() ? 'Modifier' : 'Enregistrer' }} Facture Achat
                  </h2>
                  <button (click)="closeForm()" class="text-slate-400 hover:text-slate-600 transition">
                     <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                  </button>
               </div>
               
               <form [formGroup]="form" (ngSubmit)="onSubmit()" class="flex-1 overflow-y-auto p-4 md:p-6 space-y-6">
                  
                  <!-- Section Link -->
                     <div class="bg-blue-50 p-4 rounded-xl border border-blue-100 space-y-4">
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
                        <label class="block text-xs font-bold text-blue-700 uppercase mb-1">Fournisseur <span class="text-red-500">*</span></label>
                        <select formControlName="partnerId" (change)="onPartnerChange()" 
                                [class.border-red-300]="isFieldInvalid('partnerId')"
                                class="w-full p-2 border border-blue-200 rounded-lg bg-white focus:ring-2 focus:ring-blue-500/20 outline-none">
                           <option value="">Sélectionner un fournisseur</option>
                           @for (s of store.suppliers(); track s.id) {
                              <option [value]="s.id">{{ s.name }}</option>
                           }
                        </select>
                        @if (isFieldInvalid('partnerId')) {
                           <p class="text-xs text-red-500 mt-1">Le fournisseur est requis</p>
                        }
                     </div>
                     <div>
                        <label class="block text-xs font-bold text-blue-700 uppercase mb-1">Lier à un BC (Optionnel)</label>
                        <select formControlName="bcId" (change)="onBCChange()" class="w-full p-2 border border-blue-200 rounded-lg bg-white focus:ring-2 focus:ring-blue-500/20 outline-none">
                           <option value="">Aucun BC</option>
                           @for (bc of availableBCs(); track bc.id) {
                              <option [value]="bc.id">{{ bc.number }} - {{ bc.date }}</option>
                           }
                        </select>
                     </div>
                  </div>

                  <div class="space-y-4">
                     <div>
                       <label class="block text-sm font-semibold text-slate-700 mb-1">Numéro Facture (Fournisseur)</label>
                       <input formControlName="number" type="text" placeholder="Ex: FA-2025-XXX" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition uppercase">
                     </div>

                     <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                          <label class="block text-sm font-semibold text-slate-700 mb-1">Date Facture <span class="text-red-500">*</span></label>
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
                                 class="w-full px-4 py-2 border border-slate-200 rounded-lg bg-slate-50 focus:bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                          <p class="text-[10px] text-slate-400 mt-1">Calcul auto: Date + 60 jours</p>
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

                     <div class="bg-blue-50 p-4 rounded-xl border border-blue-100">
                        <label class="flex items-center gap-3 cursor-pointer">
                           <input type="checkbox" formControlName="ajouterAuStock" class="w-5 h-5 text-blue-600 border-blue-300 rounded focus:ring-blue-500 focus:ring-2">
                           <div>
                              <span class="text-sm font-semibold text-blue-800">Ajouter les quantités au stock</span>
                              <p class="text-xs text-blue-600 mt-0.5">Cochez cette option pour incrémenter automatiquement le stock des produits achetés</p>
                           </div>
                        </label>
                     </div>
                  </div>
               </form>

               <div class="p-4 md:p-6 border-t border-slate-100 bg-slate-50/50 flex gap-3">
                  <button (click)="closeForm()" class="flex-1 px-4 py-2.5 bg-white border border-slate-200 text-slate-700 font-medium rounded-lg hover:bg-slate-50 transition min-h-[44px]">Annuler</button>
                  <button (click)="onSubmit()" class="flex-1 px-4 py-2.5 bg-blue-600 text-white font-bold rounded-lg hover:bg-blue-700 transition shadow-lg shadow-blue-600/20 min-h-[44px]">
                    {{ isEditMode() ? 'Mettre à jour' : 'Enregistrer' }}
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

      <!-- Modal Upload Fichier -->
      @if (uploadModalInvoice()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center" aria-modal="true">
          <div (click)="closeUploadModal()" class="fixed inset-0 bg-slate-900/40 backdrop-blur-sm transition-opacity"></div>
          <div class="relative bg-white rounded-2xl shadow-2xl max-w-md w-full mx-4 overflow-hidden">
            <!-- Header -->
            <div class="flex items-center justify-between p-6 border-b border-slate-100">
              <div>
                <h2 class="text-lg font-bold text-slate-800">Fichier Facture</h2>
                <p class="text-xs text-slate-500 mt-1">{{ uploadModalInvoice()?.number }}</p>
              </div>
              <button (click)="closeUploadModal()" class="text-slate-400 hover:text-slate-600 transition">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                </svg>
              </button>
            </div>
            
            <!-- Content -->
            <div class="p-6 space-y-4">
              <!-- Existing File -->
              @if (uploadedFileId() && !selectedFile()) {
                <div class="bg-slate-50 rounded-lg p-4 border border-slate-200">
                  <div class="flex items-center gap-3 mb-3">
                    <div class="p-2 bg-blue-100 rounded-lg">
                      <svg class="w-5 h-5 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z"></path>
                      </svg>
                    </div>
                    <div class="flex-1 min-w-0">
                      <p class="text-sm font-semibold text-slate-800 truncate">{{ uploadedFileName() }}</p>
                      <p class="text-xs text-slate-500">Fichier actuel</p>
                    </div>
                  </div>
                  <div class="flex gap-2">
                    <button (click)="previewUploadedFile()" class="flex-1 px-3 py-2 text-sm bg-white border border-slate-200 text-slate-700 rounded-lg hover:bg-slate-50 transition font-medium">
                      Voir
                    </button>
                    <button (click)="downloadUploadedFile()" class="flex-1 px-3 py-2 text-sm bg-white border border-slate-200 text-slate-700 rounded-lg hover:bg-slate-50 transition font-medium">
                      Télécharger
                    </button>
                    <button (click)="removeUploadedFile()" class="px-3 py-2 text-sm bg-red-50 border border-red-200 text-red-600 rounded-lg hover:bg-red-50 transition">
                      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                      </svg>
                    </button>
                  </div>
                </div>
              }
              
              <!-- Upload Zone -->
              @if (!uploadedFileId() || selectedFile()) {
                <div (dragover)="onDragOver($event)" (dragleave)="onDragLeave($event)" (drop)="onDrop($event)"
                     class="border-2 border-dashed rounded-xl p-8 text-center transition-all cursor-pointer"
                     [class.border-blue-400]="isDragging"
                     [class.bg-blue-50/50]="isDragging"
                     [class.border-slate-200]="!isDragging"
                     [class.hover:border-blue-300]="!isDragging && !uploadingFile()">
                  <input #uploadFileInput type="file" accept="image/*,.pdf" (change)="onFileSelected($event)" class="hidden">
                  
                  @if (!selectedFile()) {
                    <div class="space-y-4">
                      <div class="mx-auto w-16 h-16 bg-slate-100 rounded-full flex items-center justify-center">
                        <svg class="w-8 h-8 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"></path>
                        </svg>
                      </div>
                      <div>
                        <p class="text-sm font-medium text-slate-700 mb-1">Glissez-déposez votre fichier</p>
                        <p class="text-xs text-slate-500 mb-4">ou</p>
                        <button type="button" (click)="uploadFileInput.click()" [disabled]="uploadingFile()" 
                                class="px-5 py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition font-medium disabled:opacity-50 text-sm">
                          Sélectionner un fichier
                        </button>
                      </div>
                      <p class="text-xs text-slate-400">JPG, PNG ou PDF • Max 10MB</p>
                    </div>
                  }
                  
                  <!-- File Preview -->
                  @if (selectedFile()) {
                    <div class="space-y-4">
                      <div class="flex items-center justify-between">
                        <div class="flex items-center gap-3 flex-1 min-w-0">
                          <div class="p-2 bg-blue-100 rounded-lg shrink-0">
                            @if (selectedFile()?.type.startsWith('image/')) {
                              <svg class="w-5 h-5 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"></path>
                              </svg>
                            } @else {
                              <svg class="w-5 h-5 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z"></path>
                              </svg>
                            }
                          </div>
                          <div class="flex-1 min-w-0">
                            <p class="text-sm font-semibold text-slate-800 truncate">{{ selectedFile()?.name }}</p>
                            <p class="text-xs text-slate-500">{{ formatFileSize(selectedFile()?.size || 0) }}</p>
                          </div>
                        </div>
                        <button type="button" (click)="removeSelectedFile()" class="p-1.5 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition">
                          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                          </svg>
                        </button>
                      </div>
                      
                      @if (selectedFile()?.type.startsWith('image/') && filePreviewUrl()) {
                        <div class="rounded-lg overflow-hidden border border-slate-200 bg-slate-50">
                          <img [src]="filePreviewUrl()!" alt="Preview" class="w-full h-48 object-contain">
                        </div>
                      } @else if (selectedFile()?.type === 'application/pdf') {
                        <div class="rounded-lg border border-slate-200 bg-slate-50 p-4 text-center">
                          <svg class="w-12 h-12 text-blue-600 mx-auto mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z"></path>
                          </svg>
                          <p class="text-sm text-slate-600">Fichier PDF prêt à être uploadé</p>
                        </div>
                      }
                      
                      @if (!uploadingFile()) {
                        <button type="button" (click)="uploadFile()" 
                                class="w-full px-4 py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition font-medium text-sm">
                          Uploader
                        </button>
                      }
                    </div>
                  }
                  
                  <!-- Upload Progress -->
                  @if (uploadingFile()) {
                    <div class="space-y-3">
                      <div class="flex items-center justify-between text-sm">
                        <span class="font-medium text-slate-700">Upload en cours...</span>
                        <span class="font-semibold text-blue-600">{{ uploadProgress() }}%</span>
                      </div>
                      <div class="w-full bg-slate-200 rounded-full h-2 overflow-hidden">
                        <div class="bg-blue-600 h-2 rounded-full transition-all duration-300" [style.width.%]="uploadProgress()"></div>
                      </div>
                    </div>
                  }
                  
                  <!-- Upload Error -->
                  @if (uploadError()) {
                    <div class="bg-red-50 border border-red-200 rounded-lg p-3">
                      <div class="flex items-start gap-2">
                        <svg class="w-4 h-4 text-red-600 shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                        </svg>
                        <p class="text-xs text-red-700 flex-1">{{ uploadError() }}</p>
                      </div>
                    </div>
                  }
                </div>
              }
            </div>
            
            <!-- Footer -->
            <div class="p-6 border-t border-slate-100 bg-slate-50/50">
              <button (click)="closeUploadModal()" class="w-full px-4 py-2.5 bg-white border border-slate-200 text-slate-700 font-medium rounded-lg hover:bg-slate-50 transition">
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
            <div class="flex items-center justify-between p-4 md:p-6 border-b border-slate-100 bg-gradient-to-r from-red-50 to-rose-50">
              <div>
                <h2 class="text-lg md:text-xl font-bold text-slate-800">Facture {{ selectedInvoiceForPayments()?.number }}</h2>
                <p class="text-xs md:text-sm text-slate-600 mt-1">Gestion des paiements et prévisions</p>
              </div>
              <button (click)="closePaymentModal()" class="text-slate-400 hover:text-slate-600 transition min-h-[44px] min-w-[44px] flex items-center justify-center">
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
                            <th class="px-4 py-3 text-right font-semibold text-slate-700">Solde Fournisseur Après</th>
                            <th class="px-4 py-3 text-left font-semibold text-slate-700">Référence</th>
                          </tr>
                        </thead>
                        <tbody class="divide-y divide-slate-100">
                          @for (payment of paymentsForInvoice(); track payment.id) {
                            <tr class="hover:bg-slate-50">
                              <td class="px-4 py-3">{{ payment.date }}</td>
                              <td class="px-4 py-3">
                                <span class="px-2 py-1 bg-red-50 text-red-700 rounded text-xs font-medium">Paiement Fournisseur</span>
                              </td>
                              <td class="px-4 py-3">{{ payment.mode }}</td>
                              <td class="px-4 py-3 text-right font-bold text-red-600">{{ payment.montant | number:'1.2-2' }} MAD</td>
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
                <!-- Onglet Prévisions (identique à sales-invoices) -->
                <div class="space-y-6">
                  <h3 class="text-lg font-bold text-slate-800">Prévisions de Paiement</h3>
                  
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
                              <td class="px-4 py-3 text-right font-bold text-red-600">{{ prev.montantPrevu | number:'1.2-2' }} MAD</td>
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
                        <button type="submit" [disabled]="previsionForm.invalid" class="flex-1 px-4 py-2 bg-red-600 text-white font-bold rounded-lg hover:bg-red-700 transition disabled:opacity-50">
                          {{ editingPrevisionId() ? 'Modifier' : 'Ajouter' }} la Prévision
                        </button>
                        @if (editingPrevisionId()) {
                          <button type="button" (click)="cancelEditPrevision()" class="px-4 py-2 bg-slate-200 text-slate-700 font-medium rounded-lg hover:bg-slate-300 transition">
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

      <!-- File Viewer Modal -->
      @if (viewingFile()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center" aria-modal="true">
          <div (click)="closeFileViewer()" class="fixed inset-0 bg-black/80 backdrop-blur-sm"></div>
          <div class="relative bg-white rounded-2xl shadow-2xl max-w-6xl w-full mx-4 max-h-[90vh] overflow-hidden flex flex-col">
            <div class="flex items-center justify-between p-4 border-b border-slate-200 bg-slate-50">
              <h3 class="text-lg font-bold text-slate-800">{{ viewingFile()?.filename }}</h3>
              <div class="flex gap-2">
                <button (click)="downloadFileFromViewer()" class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition text-sm font-medium">
                  Télécharger
                </button>
                <button (click)="closeFileViewer()" class="text-slate-400 hover:text-slate-600 transition">
                  <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                </button>
              </div>
            </div>
            <div class="flex-1 overflow-auto p-4 bg-slate-100 flex items-center justify-center">
              @if (!fileViewerBlobUrl()) {
                <div class="text-center p-8">
                  <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
                  <p class="text-slate-600">Chargement du fichier...</p>
                </div>
              } @else if (viewingFile()?.type === 'image' || (viewingFile()?.type && viewingFile()!.type.startsWith('image/'))) {
                <img [src]="fileViewerBlobUrl()!" [alt]="viewingFile()?.filename" class="max-w-full max-h-full object-contain rounded-lg shadow-lg" (error)="handleImageError()">
              } @else if (viewingFile()?.type === 'application/pdf') {
                <iframe [src]="fileViewerBlobUrl()!" class="w-full h-full min-h-[600px] border-0 rounded-lg shadow-lg bg-white"></iframe>
              } @else {
                <div class="text-center p-8">
                  <p class="text-slate-600">Type de fichier non supporté pour la prévisualisation</p>
                  <button (click)="downloadFileFromViewer()" class="mt-4 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700">
                    Télécharger le fichier
                  </button>
                </div>
              }
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
export class PurchaseInvoicesComponent implements OnInit {
  Math = Math; // Make Math available in template

  store = inject(StoreService);
  invoiceStore = inject(InvoiceStore);
  navigationRefresh = inject(NavigationRefreshService);
  fb = inject(FormBuilder);
  router = inject(Router);
  route = inject(ActivatedRoute);
  comptabiliteService = inject(ComptabiliteService);
  apiService = inject(ApiService);
  ngZone = inject(NgZone);
  cdr = inject(ChangeDetectorRef);
  
  readonly isRefreshing = computed(() => this.navigationRefresh.isRefreshing() || this.invoiceStore.refreshing());

  isFormOpen = signal(false);
  isEditMode = signal(false);
  editingId: string | null = null;
  availableBCs = signal<BC[]>([]);
  
  // Factures disponibles pour liaison avec avoir
  availableFacturesForAvoir = computed(() => {
    const partnerId = this.form.get('partnerId')?.value;
    if (!partnerId) return [];
    
    // Retourner les factures d'achat normales (non avoirs) du même fournisseur
    return this.store.purchaseInvoices().filter(f => 
      f.partnerId === partnerId && 
      !f.estAvoir &&
      f.type === 'purchase'
    );
  });
  selectedInvoiceForDetails = signal<Invoice | null>(null);
  selectedInvoiceForPayments = signal<Invoice | null>(null);
  selectedInvoiceForEcritures = signal<Invoice | null>(null);
  activeTab = signal<'paiements' | 'previsions'>('paiements');
  activeEcrituresTab = signal<'ecritures' | 'tva'>('ecritures');
  editingPrevisionId = signal<string | null>(null);
  ecrituresComptables = signal<EcritureComptable[]>([]);
  loadingEcritures = signal(false);
  openDropdownId = signal<string | null>(null);
  
  // File upload
  uploadModalInvoice = signal<Invoice | null>(null);
  selectedFile = signal<File | null>(null);
  uploadingFile = signal(false);
  uploadedFileId = signal<string | null>(null);
  uploadedFileName = signal<string | null>(null);
  uploadedFileUrl = signal<string | null>(null);
  filePreviewUrl = signal<string | null>(null);
  uploadProgress = signal(0);
  uploadError = signal<string | null>(null);
  
  // File viewer
  viewingFile = signal<{ fileId: string; filename: string; type: string } | null>(null);
  fileViewerBlobUrl = signal<string | null>(null);

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

  form: FormGroup = this.fb.group({
    number: ['', Validators.required],
    partnerId: ['', Validators.required],
    bcId: [''],
    date: [new Date().toISOString().split('T')[0], Validators.required],
    dueDate: ['', Validators.required],
    amountHT: [0, [Validators.required, Validators.min(0)]],
    amountTTC: [0, [Validators.required, Validators.min(0)]],
    status: ['pending', Validators.required],
    paymentMode: [''],
    ajouterAuStock: [false], // Option pour ajouter au stock
    // Champs pour avoirs
    estAvoir: [false],
    factureOrigineId: ['']
  });

  // Set pour tracker les factures pour lesquelles on a déjà tenté de charger les paiements
  private paymentLoadAttempted = new Set<string>();
  private paymentLoadTimeoutId: any = null;

  constructor() {
    // Auto calculate due date when date changes
    this.form.get('date')?.valueChanges.subscribe(dateVal => {
      if (dateVal && !this.isEditMode()) {
        const d = new Date(dateVal);
        d.setDate(d.getDate() + 60); // Rule: +60 days for Purchase
        this.form.patchValue({ dueDate: d.toISOString().split('T')[0] }, { emitEvent: false });
      }
    });

    // Effet pour charger automatiquement les paiements quand les factures changent
    // Utiliser un debounce pour éviter les appels multiples
    effect(() => {
      const invoices = this.allPurchaseInvoices();
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
              this.store.loadPaymentsForInvoice(inv.id, 'purchase').catch(() => {
                // En cas d'erreur, retirer de la liste pour permettre un nouvel essai plus tard
                this.paymentLoadAttempted.delete(inv.id);
              });
            }
          });
        }, 300); // Debounce de 300ms pour éviter les appels trop fréquents
      }
    });
  }

  // Raw List - Utiliser directement invoiceStore pour de meilleures performances
  allPurchaseInvoices = computed(() => this.invoiceStore.purchaseInvoices());

  // Computed pour recalculer le statut basé sur les paiements réels
  invoicesWithCorrectStatus = computed(() => {
    return this.allPurchaseInvoices().map(inv => {
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

  ngOnInit() {
    // Lire les query params pour bcId
    this.route.queryParams.subscribe(params => {
      if (params['bcId']) {
        this.bcIdFilter.set(params['bcId']);
      }
    });

    // Charger les factures achat si le store est vide
    // Le cache est vérifié automatiquement dans loadPurchaseInvoices()
    // Les paiements seront chargés automatiquement par l'effet réactif dans le constructor
    if (this.invoiceStore.purchaseInvoices().length === 0) {
      this.invoiceStore.loadPurchaseInvoices().catch(err => {
        console.error('Error loading purchase invoices on init:', err);
      });
    }
  }

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
        this.store.getSupplierName(i.partnerId || '').toLowerCase().includes(term)
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

  getDueDateClass(dateStr: string): string {
    const due = new Date(dateStr);
    const today = new Date();
    const diffTime = due.getTime() - today.getTime();
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

    if (diffDays < 0) return 'text-red-600 font-bold flex items-center gap-1'; 
    if (diffDays < 7) return 'text-amber-600 font-medium'; 
    return 'text-emerald-600';
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
      date: new Date().toISOString().split('T')[0],
      status: 'pending',
      amountHT: 0,
      amountTTC: 0,
      ajouterAuStock: false,
      estAvoir: false,
      factureOrigineId: ''
    });
    // Trigger due date calc
    const today = new Date();
    today.setDate(today.getDate() + 60);
    this.form.patchValue({ dueDate: today.toISOString().split('T')[0] });
    
    this.availableBCs.set([]);
    this.selectedFile.set(null);
    this.filePreviewUrl.set(null);
    this.uploadedFileId.set(null);
    this.uploadedFileName.set(null);
    this.uploadedFileUrl.set(null);
    this.uploadProgress.set(0);
    this.uploadError.set(null);
    this.isFormOpen.set(true);
  }

  closeForm() {
    this.isFormOpen.set(false);
  }

  onPartnerChange() {
    const supplierId = this.form.get('partnerId')?.value;
    if (supplierId) {
      // Filter BCs for this supplier
      const bcs = this.store.bcs().filter(b => b.supplierId === supplierId);
      this.availableBCs.set(bcs);
    } else {
      this.availableBCs.set([]);
    }
  }

  onBCChange() {
    const bcId = this.form.get('bcId')?.value;
    const bc = this.store.bcs().find(b => b.id === bcId);
    
    if (bc) {
      let totalHT = 0;
      let totalTva = 0;

      // Nouvelle structure: lignesAchat
      if (bc.lignesAchat && bc.lignesAchat.length > 0) {
        // Utiliser les totaux pré-calculés si disponibles
        if (bc.totalAchatHT !== undefined && bc.totalAchatTTC !== undefined) {
          totalHT = bc.totalAchatHT;
          totalTva = bc.totalAchatTTC - totalHT;
        } else {
          // Calculer à partir des lignes d'achat
          totalHT = bc.lignesAchat.reduce((acc, l) => 
            acc + (l.quantiteAchetee || 0) * (l.prixAchatUnitaireHT || 0), 0);
          totalTva = bc.lignesAchat.reduce((acc, l) => 
            acc + (l.quantiteAchetee || 0) * (l.prixAchatUnitaireHT || 0) * ((l.tva || 0) / 100), 0);
        }
      } 
      // Ancienne structure: items
      else if (bc.items) {
        totalHT = bc.items.reduce((acc, i) => acc + (i.qtyBuy * i.priceBuyHT), 0);
        totalTva = bc.items.reduce((acc, i) => acc + (i.qtyBuy * i.priceBuyHT * (i.tvaRate/100)), 0);
      }
      
      this.form.patchValue({
        amountHT: totalHT,
        amountTTC: totalHT + totalTva
      });
    } else {
      console.warn('🔵 purchase-invoices.onBCChange - BC non trouvé pour id:', bcId);
    }
  }

  editInvoice(inv: Invoice) {
    this.isEditMode.set(true);
    this.editingId = inv.id;
    // S'assurer que tous les champs sont bien remplis, notamment dueDate
    this.form.patchValue({
      number: inv.number || '',
      partnerId: inv.partnerId || '',
      bcId: inv.bcId || '',
      date: inv.date || new Date().toISOString().split('T')[0],
      dueDate: inv.dueDate || inv.date || new Date().toISOString().split('T')[0], // Utiliser date si dueDate manquant
      amountHT: inv.amountHT != null && inv.amountHT !== undefined ? Number(inv.amountHT) : 0,
      amountTTC: inv.amountTTC != null && inv.amountTTC !== undefined ? Number(inv.amountTTC) : 0,
      status: inv.status || 'pending',
      paymentMode: inv.paymentMode || '',
      ajouterAuStock: (inv as any).ajouterAuStock || false,
      estAvoir: inv.estAvoir || false,
      factureOrigineId: inv.factureOrigineId || ''
    });
    // Populate BCs if supplier is set
    if (inv.partnerId) {
      const bcs = this.store.bcs().filter(b => b.supplierId === inv.partnerId);
      this.availableBCs.set(bcs);
    }
    // Charger les informations du fichier si existant
    const factureAchat = inv as any;
    if (factureAchat.fichierFactureId) {
      this.uploadedFileId.set(factureAchat.fichierFactureId);
      this.uploadedFileName.set(factureAchat.fichierFactureNom || 'Fichier joint');
      this.uploadedFileUrl.set(factureAchat.fichierFactureUrl || null);
    } else {
      this.uploadedFileId.set(null);
      this.uploadedFileName.set(null);
      this.uploadedFileUrl.set(null);
    }
    this.selectedFile.set(null);
    this.filePreviewUrl.set(null);
    this.isFormOpen.set(true);
  }

  async exportPDF(inv: Invoice) {
    const facture = inv as any;
    if (!facture.fichierFactureId) {
      this.store.showToast('Aucun fichier uploadé pour cette facture', 'error');
      return;
    }
    
    try {
      // Télécharger le fichier uploadé (image ou PDF)
      await this.downloadUploadedFileForInvoice(inv);
    } catch (error) {
      console.error('Erreur téléchargement fichier:', error);
      this.store.showToast('Erreur lors du téléchargement du fichier', 'error');
    }
  }
  
  async downloadUploadedFileForInvoice(inv: Invoice) {
    const facture = inv as any;
    const fileId = facture.fichierFactureId;
    if (!fileId) return;
    
    try {
      const contentType = facture.fichierFactureType || 
                          (facture.fichierFactureNom?.toLowerCase().endsWith('.pdf') ? 'application/pdf' : 'image/jpeg');
      
      const blob = await this.downloadFactureBlob(fileId, contentType);
      if (blob) {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        
        const filename = this.ensureFileExtension(
          facture.fichierFactureNom || 'fichier',
          contentType || blob.type
        );
        
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);
        this.store.showToast('Fichier téléchargé avec succès', 'success');
      }
    } catch (error) {
      console.error('Erreur téléchargement fichier:', error);
      this.store.showToast('Erreur lors du téléchargement', 'error');
      throw error;
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
        entityType: 'FactureAchat',
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
    this.store.loadPaymentsForInvoice(inv.id, 'purchase').catch(err => {
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
        await this.store.updatePrevision(inv.id, this.editingPrevisionId()!, 'achat', previsionData);
      } else {
        await this.store.addPrevision(inv.id, 'achat', previsionData);
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
      await this.store.deletePrevision(inv.id, id, 'achat');
      // Mettre à jour localement la facture sélectionnée
      const updatedInvoice = this.store.invoices().find(i => i.id === inv.id);
      if (updatedInvoice) {
        this.selectedInvoiceForPayments.set({ ...updatedInvoice });
      }
      // Recharger les factures achat en arrière-plan (non bloquant) pour synchroniser le store
      this.invoiceStore.loadPurchaseInvoices().catch(err => {
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
      notes: '',
      dateRappel: ''
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
      factureAchatId: inv.id,
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
      this.store.loadPaymentsForInvoice(inv.id, 'purchase').catch(err => {
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

  onSubmit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.store.showToast('Veuillez corriger les erreurs dans le formulaire', 'error');
      return;
    }

    const val = this.form.value;
    
    // S'assurer que dueDate est rempli
    if (!val.dueDate) {
      // Calculer automatiquement si manquant
      const date = new Date(val.date);
      date.setDate(date.getDate() + 60);
      val.dueDate = date.toISOString().split('T')[0];
    }
    
    // S'assurer que les montants sont des nombres
    const amountHT = val.amountHT != null && val.amountHT !== undefined ? Number(val.amountHT) : 0;
    const amountTTC = val.amountTTC != null && val.amountTTC !== undefined ? Number(val.amountTTC) : 0;
    
    const invoice: Invoice & { ajouterAuStock?: boolean; fichierFactureId?: string; fichierFactureNom?: string; fichierFactureType?: string } = {
      id: this.editingId || `fa-${Date.now()}`,
      type: 'purchase',
      number: val.number,
      partnerId: val.partnerId,
      bcId: val.bcId || undefined,
      date: val.date,
      dueDate: val.dueDate,
      amountHT: val.estAvoir && amountHT > 0 ? -amountHT : amountHT, // Inverser si avoir
      amountTTC: val.estAvoir && amountTTC > 0 ? -amountTTC : amountTTC, // Inverser si avoir
      status: val.status || 'pending',
      paymentMode: val.paymentMode || undefined,
      ajouterAuStock: val.ajouterAuStock || false,
      // Champs pour avoirs
      estAvoir: val.estAvoir || false,
      typeFacture: val.estAvoir ? 'AVOIR' : 'NORMALE',
      factureOrigineId: val.factureOrigineId || undefined,
      numeroFactureOrigine: val.factureOrigineId ? undefined : undefined // Sera rempli par le backend
    };
    
    // Ajouter les informations du fichier si uploadé
    if (this.uploadedFileId()) {
      invoice.fichierFactureId = this.uploadedFileId()!;
      invoice.fichierFactureNom = this.uploadedFileName() || this.selectedFile()?.name || '';
      invoice.fichierFactureType = this.selectedFile()?.type || '';
      (invoice as any).fichierFactureUrl = this.uploadedFileUrl() || '';
    }

    if (this.isEditMode()) {
      this.store.updateInvoice(invoice);
    } else {
      this.store.addInvoice(invoice);
    }
    this.closeForm();
  }
  
  // File upload methods
  isDragging = false;
  
  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.processFile(input.files[0]);
    }
  }
  
  formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
  }

  processFile(file: File) {
    console.log('📄 [FRONTEND] processFile appelé - Nom:', file.name, 'Type:', file.type, 'Taille:', this.formatFileSize(file.size), 'bytes:', file.size);
    
    // Réinitialiser les erreurs précédentes
    this.uploadError.set(null);
    
    // Vérifier la taille (10MB max)
    if (file.size > 10 * 1024 * 1024) {
      const errorMsg = `Le fichier est trop volumineux (${this.formatFileSize(file.size)}). Taille max: 10MB`;
      console.error('❌ [FRONTEND] Fichier trop volumineux:', errorMsg);
      this.uploadError.set(errorMsg);
      this.store.showToast(errorMsg, 'error');
      return;
    }
    
    // Vérifier le type
    const isImage = file.type.startsWith('image/');
    const isPdf = file.type === 'application/pdf';
    console.log('🔍 [FRONTEND] Validation type - isImage:', isImage, 'isPdf:', isPdf, 'file.type:', file.type);
    
    if (!isImage && !isPdf) {
      const errorMsg = 'Seuls les fichiers images (JPG, PNG) et PDF sont acceptés';
      console.error('❌ [FRONTEND] Type de fichier non accepté:', file.type);
      this.uploadError.set(errorMsg);
      this.store.showToast(errorMsg, 'error');
      return;
    }
    
    console.log('✅ [FRONTEND] Fichier validé, ajout à selectedFile');
    this.selectedFile.set(file);
    this.uploadedFileId.set(null);
    this.uploadedFileName.set(null);
    this.uploadedFileUrl.set(null);
    
    // Créer une prévisualisation pour les images
    if (isImage) {
      console.log('🖼️ [FRONTEND] Création prévisualisation image');
      const reader = new FileReader();
      reader.onload = (e) => {
        this.ngZone.run(() => {
          this.filePreviewUrl.set(e.target?.result as string);
        });
      };
      reader.onerror = () => {
        console.error('❌ [FRONTEND] Erreur lecture fichier image');
        this.ngZone.run(() => {
          this.uploadError.set('Erreur lors de la lecture du fichier');
        });
      };
      reader.readAsDataURL(file);
    } else {
      console.log('📄 [FRONTEND] Fichier PDF, pas de prévisualisation');
      this.filePreviewUrl.set(null);
    }
  }
  
  onDragOver(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging = true;
  }
  
  onDragLeave(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging = false;
  }
  
  onDrop(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging = false;
    
    if (event.dataTransfer?.files && event.dataTransfer.files.length > 0) {
      this.processFile(event.dataTransfer.files[0]);
    }
  }
  
  removeSelectedFile() {
    this.selectedFile.set(null);
    this.filePreviewUrl.set(null);
    this.uploadError.set(null);
  }
  
  removeUploadedFile() {
    if (confirm('Êtes-vous sûr de vouloir supprimer ce fichier ?')) {
      const fileId = this.uploadedFileId();
      if (fileId) {
        const delete$ = this.isGridFsId(fileId)
          ? this.apiService.deleteFileFromGridFS(fileId)
          : this.apiService.deleteFactureAchatFile(fileId, this.editingId || undefined);

        delete$.subscribe({
          next: () => {
            this.ngZone.run(() => {
              this.uploadedFileId.set(null);
              this.uploadedFileName.set(null);
              this.uploadedFileUrl.set(null);
              // Mettre à jour localement la facture dans le modal
              const currentInvoice = this.uploadModalInvoice();
              if (currentInvoice) {
                const updatedInvoice = { ...currentInvoice } as any;
                updatedInvoice.fichierFactureId = undefined;
                updatedInvoice.fichierFactureNom = undefined;
                updatedInvoice.fichierFactureUrl = undefined;
                this.uploadModalInvoice.set(updatedInvoice);
              }
              this.store.showToast('Fichier supprimé avec succès', 'success');
            });
            
            // Recharger les factures en arrière-plan (non bloquant) pour synchroniser le store
            this.store.loadInvoices().catch(err => {
              console.error('Erreur lors du rechargement des factures:', err);
            });
          },
          error: () => {
            this.ngZone.run(() => {
              this.store.showToast('Erreur lors de la suppression', 'error');
            });
          }
        });
      }
    }
  }
  
  async previewUploadedFile() {
    const fileId = this.uploadedFileId();
    const fileName = this.uploadedFileName();
    if (!fileId || !fileName) return;
    
    // Obtenir le type depuis la facture ou le nom de fichier
    const facture = this.uploadModalInvoice();
    const factureAchat = facture as any;
    const contentType = factureAchat?.fichierFactureType || 
                       (fileName.toLowerCase().endsWith('.pdf') ? 'application/pdf' : 'image/jpeg');
    const type = contentType === 'application/pdf' ? 'application/pdf' : 'image';

    // Toujours obtenir une URL signée fraîche pour Cloudinary
    await this.loadFileForViewing(fileId, fileName, type, contentType);
  }
  
  async viewFile(inv: Invoice) {
    const facture = inv as any;
    if (facture.fichierFactureId) {
      const contentType = facture.fichierFactureType || 'application/pdf';
      
      // Pour les PDFs, télécharger directement au lieu d'essayer de les afficher
      if (contentType === 'application/pdf') {
        await this.downloadUploadedFileForInvoice(inv);
        return;
      }
      
      // Pour les images, les afficher dans le modal
      const type = 'image';
      this.loadFileForViewing(
        facture.fichierFactureId,
        facture.fichierFactureNom || 'Fichier',
        type,
        contentType
      );
    }
  }
  
  loadFileForViewing(fileId: string, filename: string, type: string, contentType?: string) {
    // Cette méthode est maintenant uniquement utilisée pour les images
    // Les PDFs sont téléchargés directement via downloadUploadedFileForInvoice
    if (!this.isGridFsId(fileId)) {
      // Cloudinary : obtenir une URL signée fraîche pour l'image
      this.ngZone.runOutsideAngular(() => {
        this.apiService.getFactureAchatFileUrl(fileId, contentType)
          .pipe(take(1))
          .subscribe({
            next: (result) => {
              const url = result.url;
              console.log('🔗 [FRONTEND] URL Cloudinary obtenue pour image:', url);
              
              // Créer un blob URL pour éviter les problèmes CORS
              fetch(url)
                .then(response => {
                  if (!response.ok) throw new Error('Failed to fetch image');
                  return response.blob();
                })
                .then(blob => {
                  const blobUrl = window.URL.createObjectURL(blob);
                  this.ngZone.run(() => {
                    this.fileViewerBlobUrl.set(blobUrl);
                    this.viewingFile.set({ fileId, filename, type });
                  });
                })
                .catch(fetchError => {
                  // Si le fetch échoue, utiliser directement l'URL Cloudinary
                  console.warn('Erreur fetch image, utilisation URL directe:', fetchError);
                  this.ngZone.run(() => {
                    this.fileViewerBlobUrl.set(url);
                    this.viewingFile.set({ fileId, filename, type });
                  });
                });
            },
            error: (error) => {
              console.error('❌ [FRONTEND] Erreur chargement image:', error);
              this.ngZone.run(() => {
                this.store.showToast('Erreur lors du chargement de l\'image', 'error');
              });
            }
          });
      });
      return;
    }

    // GridFS : créer un blob URL pour les images
    this.ngZone.runOutsideAngular(() => {
      this.downloadFactureBlob(fileId, contentType)
        .then(blob => {
          const url = window.URL.createObjectURL(blob);
          this.ngZone.run(() => {
            this.fileViewerBlobUrl.set(url);
            this.viewingFile.set({ fileId, filename, type });
          });
        })
        .catch(error => {
          console.error('❌ [FRONTEND] Erreur chargement image:', error);
          this.ngZone.run(() => {
            this.store.showToast('Erreur lors du chargement de l\'image', 'error');
          });
        });
    });
  }
  
  closeFileViewer() {
    const blobUrl = this.fileViewerBlobUrl();
    if (blobUrl && blobUrl.startsWith('blob:')) {
      window.URL.revokeObjectURL(blobUrl);
    }
    this.viewingFile.set(null);
    this.fileViewerBlobUrl.set(null);
  }
  
  handleImageError() {
    // Si l'image ne peut pas être chargée, essayer de recharger avec une URL signée fraîche
    const file = this.viewingFile();
    if (file) {
      this.loadFileForViewing(file.fileId, file.filename, file.type);
    }
  }
  
  async downloadFileFromViewer() {
    const file = this.viewingFile();
    if (!file) return;
    
    try {
      // Déterminer le contentType depuis le type de fichier
      const contentType = file.type === 'application/pdf' ? 'application/pdf' : 
                         file.type === 'image' ? 'image/jpeg' : 
                         file.type || 'application/octet-stream';
      
      const blob = await this.downloadFactureBlob(file.fileId, contentType);
      if (blob) {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        
        // Utiliser ensureFileExtension pour garantir l'extension
        const filename = this.ensureFileExtension(file.filename, contentType || blob.type);
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);
        this.store.showToast('Fichier téléchargé avec succès', 'success');
      }
    } catch (error) {
      console.error('Erreur téléchargement:', error);
      this.store.showToast('Erreur lors du téléchargement', 'error');
    }
  }
  
  uploadFile() {
    const file = this.selectedFile();
    if (!file) {
      console.error('❌ [FRONTEND] uploadFile: Aucun fichier sélectionné');
      this.store.showToast('Aucun fichier sélectionné', 'error');
      return;
    }
    
    console.log('🚀 [FRONTEND] uploadFile démarré - Nom:', file.name, 'Type:', file.type, 'Taille:', this.formatFileSize(file.size), 'FactureId:', this.editingId);
    
    // Vérifier la taille du fichier
    if (file.size > 10 * 1024 * 1024) {
      console.error('❌ [FRONTEND] Fichier trop volumineux');
      this.store.showToast('Le fichier est trop volumineux (max 10MB)', 'error');
      return;
    }
    
    this.uploadingFile.set(true);
    this.uploadProgress.set(0);
    
    console.log('📤 [FRONTEND] Envoi vers API - URL:', '/factures-achats/files/upload', 'ContentType:', file.type);
    
    // Utiliser subscribe pour gérer la progression
    this.apiService.uploadFactureAchatFile(
      file,
      this.editingId || undefined,
      (progress) => {
        this.ngZone.run(() => {
          this.uploadProgress.set(progress);
        });
        if (progress % 25 === 0 || progress === 100) { // Log tous les 25% et à 100%
          console.log('📊 [FRONTEND] Progression upload:', progress + '%');
        }
      }
    ).subscribe({
      next: async (result: any) => {
        // Ignorer les événements de progression (gérés par le callback)
        if (result && result.type === 'progress') {
          return;
        }
        
        // C'est la réponse finale
        if (result && result.fileId) {
          console.log('✅ Upload réussi:', result);
          
          // Utiliser NgZone pour s'assurer que les modifications de signaux sont dans le contexte Angular
          this.ngZone.run(() => {
            this.uploadProgress.set(100);
            this.uploadedFileId.set(result.fileId);
            this.uploadedFileName.set(result.filename);
            this.uploadedFileUrl.set(result.signedUrl || null);
            this.selectedFile.set(null);
            this.filePreviewUrl.set(null);
            this.uploadError.set(null);
            this.store.showToast(`Fichier "${result.filename}" uploadé avec succès`, 'success');
          });
          
          // Mettre à jour la facture dans le modal localement avec les données de la réponse
          this.ngZone.run(() => {
            const currentInvoice = this.uploadModalInvoice();
            if (currentInvoice) {
              // Mettre à jour localement la facture avec les nouvelles informations de fichier
              const updatedInvoice = { ...currentInvoice } as any;
              updatedInvoice.fichierFactureId = result.fileId;
              updatedInvoice.fichierFactureNom = result.filename;
              updatedInvoice.fichierFactureUrl = result.signedUrl || null;
              this.uploadModalInvoice.set(updatedInvoice);
            }
            
            // Réinitialiser la progression après un court délai
            setTimeout(() => {
              this.uploadProgress.set(0);
            }, 1000);
            this.uploadingFile.set(false);
          });
          
          // Recharger les factures en arrière-plan (non bloquant) pour synchroniser le store
          this.store.loadInvoices().catch(err => {
            console.error('Erreur lors du rechargement des factures:', err);
          });
        } else {
          this.ngZone.run(() => {
            this.uploadingFile.set(false);
          });
          throw new Error('Réponse invalide du serveur: ' + JSON.stringify(result));
        }
      },
      error: (error: any) => {
        this.ngZone.run(() => {
          this.uploadProgress.set(0);
        });
        console.error('❌ [FRONTEND] Erreur upload fichier complète:', error);
        console.error('❌ [FRONTEND] Détails erreur:', {
          status: error?.status,
          statusText: error?.statusText,
          error: error?.error,
          message: error?.message,
          url: error?.url
        });
        
        let errorMessage = 'Erreur lors de l\'upload du fichier';
        if (error?.error?.error) {
          errorMessage = error.error.error;
          console.error('❌ [FRONTEND] Message erreur backend:', errorMessage);
        } else if (error?.error?.message) {
          errorMessage = error.error.message;
        } else if (error?.message) {
          errorMessage = error.message;
        } else if (typeof error === 'string') {
          errorMessage = error;
        }
        
        this.ngZone.run(() => {
          this.uploadError.set(errorMessage);
          this.store.showToast(errorMessage, 'error');
        });
      },
      complete: () => {
        this.ngZone.run(() => {
          this.uploadingFile.set(false);
        });
      }
    });
  }
  
  private ensureFileExtension(filename: string, contentType?: string): string {
    if (!filename) {
      // Déterminer l'extension depuis le contentType
      if (contentType?.startsWith('image/')) {
        const ext = contentType.includes('jpeg') || contentType.includes('jpg') ? '.jpg' :
                   contentType.includes('png') ? '.png' :
                   contentType.includes('gif') ? '.gif' : '.jpg';
        return 'fichier' + ext;
      }
      return contentType?.includes('pdf') ? 'fichier.pdf' : 'fichier';
    }
    
    const lower = filename.toLowerCase();
    // Vérifier si le fichier a déjà une extension
    if (lower.includes('.')) {
      return filename;
    }
    
    // Ajouter l'extension selon le contentType
    if (contentType?.includes('pdf')) {
      return filename + '.pdf';
    } else if (contentType?.startsWith('image/')) {
      const ext = contentType.includes('jpeg') || contentType.includes('jpg') ? '.jpg' :
                 contentType.includes('png') ? '.png' :
                 contentType.includes('gif') ? '.gif' : '.jpg';
      return filename + ext;
    }
    
    return filename;
  }

  async downloadUploadedFile() {
    const fileId = this.uploadedFileId();
    if (!fileId) return;
    
    try {
      // Obtenir le type de fichier depuis la facture du modal ou le store
      const facture = this.uploadModalInvoice();
      const factureAchat = facture as any;
      const contentType = factureAchat?.fichierFactureType || 
                         (this.uploadedFileName()?.toLowerCase().endsWith('.pdf') ? 'application/pdf' : 'image/jpeg');
      
      const blob = await this.downloadFactureBlob(fileId, contentType);
      if (blob) {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        
        const filename = this.ensureFileExtension(
          this.uploadedFileName() || 'fichier',
          contentType || blob.type
        );
        
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);
        this.store.showToast('Fichier téléchargé avec succès', 'success');
      }
    } catch (error) {
      console.error('Erreur téléchargement fichier:', error);
      this.store.showToast('Erreur lors du téléchargement', 'error');
    }
  }

  async showEcrituresModal(inv: Invoice) {
    this.selectedInvoiceForEcritures.set(inv);
    this.activeEcrituresTab.set('ecritures');
    this.loadingEcritures.set(true);
    this.ecrituresComptables.set([]);

    try {
      // Charger les écritures de la facture
      const ecrituresFacture = await firstValueFrom(
        this.comptabiliteService.getEcritures({
          pieceType: 'FACTURE_ACHAT',
          pieceId: inv.id
        })
      );

      // Charger les écritures des paiements associés
      const payments = this.store.payments().get(inv.id) || [];
      const ecrituresPaiements = await Promise.all(
        payments.map(p => 
          firstValueFrom(
            this.comptabiliteService.getEcritures({
              pieceType: 'PAIEMENT',
              pieceId: p.id
            })
          ).catch(() => [])
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
    this.router.navigate(['/comptabilite'], {
      queryParams: {
        pieceType: 'FACTURE_ACHAT',
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

  formatDate(date: string): string {
    return new Date(date).toLocaleDateString('fr-FR');
  }

  private isGridFsId(fileId: string): boolean {
    return /^[a-fA-F0-9]{24}$/.test(fileId);
  }

  private async downloadFactureBlob(fileId: string, contentType?: string): Promise<Blob> {
    if (this.isGridFsId(fileId)) {
      return await firstValueFrom(this.apiService.downloadFileFromGridFS(fileId));
    }
    // Cloudinary : obtenir une URL signée fraîche avec le contentType correct puis récupérer le blob
    const { url } = await firstValueFrom(this.apiService.getFactureAchatFileUrl(fileId, contentType));
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(`Failed to fetch file: ${response.status} ${response.statusText}`);
    }
    return await response.blob();
  }

  hasFile(inv: Invoice): boolean {
    const facture = inv as any;
    return !!(facture.fichierFactureId);
  }
  
  openUploadModal(inv: Invoice) {
    this.uploadModalInvoice.set(inv);
    this.editingId = inv.id;
    
    // Utiliser directement les données de la facture passée en paramètre (déjà à jour dans le store)
    const factureAchat = inv as any;
    if (factureAchat.fichierFactureId) {
      this.uploadedFileId.set(factureAchat.fichierFactureId);
      this.uploadedFileName.set(factureAchat.fichierFactureNom || 'Fichier joint');
      this.uploadedFileUrl.set(factureAchat.fichierFactureUrl || null);
    } else {
      this.uploadedFileId.set(null);
      this.uploadedFileName.set(null);
      this.uploadedFileUrl.set(null);
    }
    
    // Réinitialiser les autres états
    this.selectedFile.set(null);
    this.filePreviewUrl.set(null);
    this.uploadProgress.set(0);
    this.uploadError.set(null);
  }
  
  closeUploadModal() {
    this.uploadModalInvoice.set(null);
    this.selectedFile.set(null);
    this.filePreviewUrl.set(null);
    this.uploadError.set(null);
    // Ne pas réinitialiser uploadedFileId car il peut être utilisé après l'upload
  }

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
