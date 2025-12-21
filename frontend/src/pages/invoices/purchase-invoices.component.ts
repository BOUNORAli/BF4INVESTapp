import { Component, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StoreService, Invoice, BC, PrevisionPaiement } from '../../services/store.service';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators, FormsModule } from '@angular/forms';

@Component({
  selector: 'app-purchase-invoices',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
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
           </div>
           
           <div class="relative w-full md:w-64">
              <span class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path></svg>
              </span>
              <input type="text" [(ngModel)]="searchTerm" placeholder="Rechercher facture..." class="w-full pl-9 pr-3 py-1.5 rounded-full border border-slate-200 text-xs bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all">
           </div>
        </div>

        <!-- Table wrapper with horizontal scroll -->
        <div class="overflow-x-auto">
          <table class="w-full text-sm text-left text-slate-600 min-w-[800px]">
          <thead class="text-xs text-slate-500 uppercase bg-slate-50 border-b border-slate-200">
            <tr>
              <th class="px-6 py-4 font-semibold">Numéro</th>
              <th class="px-6 py-4 font-semibold">Fournisseur & BC</th>
              <th class="px-6 py-4 font-semibold">Date Facture</th>
              <th class="px-6 py-4 font-semibold">Échéance (TVA)</th>
              <th class="px-6 py-4 font-semibold text-right">Montant TTC</th>
              <th class="px-6 py-4 font-semibold text-center">Mode Paiement</th>
              <th class="px-6 py-4 font-semibold text-center">Statut</th>
              <th class="px-6 py-4"></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            @for (inv of paginatedInvoices(); track inv.id) {
              <tr class="bg-white hover:bg-slate-50 transition-colors group" [attr.data-item-id]="inv.id">
                <td class="px-6 py-4 font-medium text-slate-900">{{ inv.number }}</td>
                <td class="px-6 py-4">
                   <div class="flex flex-col">
                     <span class="font-medium text-slate-700">{{ store.getSupplierName(inv.partnerId || '') }}</span>
                     @if (inv.bcId) {
                       <span class="text-xs text-blue-500 bg-blue-50 w-fit px-1.5 py-0.5 rounded mt-0.5">{{ store.getBCNumber(inv.bcId) }}</span>
                     }
                   </div>
                </td>
                <td class="px-6 py-4">{{ inv.date }}</td>
                <td class="px-6 py-4">
                  <div class="flex items-center gap-2">
                    <span [class]="getDueDateClass(inv.dueDate)">
                      {{ inv.dueDate }}
                    </span>
                  </div>
                </td>
                <td class="px-6 py-4 text-right font-medium text-slate-800">{{ inv.amountTTC | number:'1.2-2' }} MAD</td>
                <td class="px-6 py-4 text-center">
                  @if (inv.paymentMode) {
                    <span class="inline-flex items-center bg-blue-50 text-blue-700 text-xs px-2.5 py-1 rounded-full font-medium border border-blue-200">
                      {{ inv.paymentMode }}
                    </span>
                  } @else {
                    <span class="text-xs text-slate-400 italic">Non défini</span>
                  }
                </td>
                <td class="px-6 py-4 text-center">
                  <span [class]="getStatusClass(inv.status)">
                    {{ getStatusLabel(inv.status) }}
                  </span>
                </td>
                <td class="px-6 py-4 text-right">
                   <div class="flex items-center justify-end gap-2 opacity-100 md:opacity-0 group-hover:opacity-100 transition-opacity">
                      <button (click)="showPaymentModal(inv)" class="p-2 text-slate-400 hover:text-emerald-600 hover:bg-emerald-50 rounded-full transition-all" title="Paiements">
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                      </button>
                      <button (click)="showCalculDetails(inv)" class="p-2 text-slate-400 hover:text-purple-600 hover:bg-purple-50 rounded-full transition-all" title="Détails comptables">
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 7h6m0 10v-3m-3 3h.01M9 17h.01M9 14h.01M12 14h.01M15 11h.01M12 11h.01M9 11h.01M7 21h10a2 2 0 002-2V5a2 2 0 00-2-2H7a2 2 0 00-2 2v14a2 2 0 002 2z"></path></svg>
                      </button>
                      <button (click)="exportPDF(inv)" class="p-2 text-slate-400 hover:text-indigo-600 hover:bg-indigo-50 rounded-full transition-all" title="Exporter PDF">
                         <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
                      </button>
                      <button (click)="editInvoice(inv)" class="p-2 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-full transition-all" title="Modifier">
                         <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"></path></svg>
                      </button>
                      <button (click)="deleteInvoice(inv.id)" class="p-2 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-full transition-all" title="Supprimer">
                         <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path></svg>
                      </button>
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

      <!-- SLIDE OVER FORM -->
      @if (isFormOpen()) {
         <div class="fixed inset-0 z-50 flex justify-end" aria-modal="true">
            <!-- Backdrop -->
            <div (click)="closeForm()" class="fixed inset-0 bg-slate-900/40 backdrop-blur-sm transition-opacity"></div>
            
            <!-- Panel -->
            <div class="relative w-full md:w-[600px] bg-white h-full shadow-2xl flex flex-col transform transition-transform animate-[slideInRight_0.3s_ease-out]">
               <div class="flex items-center justify-between p-6 border-b border-slate-100 bg-slate-50/50">
                  <h2 class="text-xl font-bold text-slate-800">
                    {{ isEditMode() ? 'Modifier' : 'Enregistrer' }} Facture Achat
                  </h2>
                  <button (click)="closeForm()" class="text-slate-400 hover:text-slate-600 transition">
                     <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                  </button>
               </div>
               
               <form [formGroup]="form" (ngSubmit)="onSubmit()" class="flex-1 overflow-y-auto p-6 space-y-6">
                  
                  <!-- Section Link -->
                  <div class="bg-blue-50 p-4 rounded-xl border border-blue-100 space-y-4">
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

                     <div class="grid grid-cols-2 gap-4">
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
                     
                     <div class="grid grid-cols-2 gap-4">
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

                     <div class="grid grid-cols-2 gap-4">
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

               <div class="p-6 border-t border-slate-100 bg-slate-50/50 flex gap-3">
                  <button (click)="closeForm()" class="flex-1 px-4 py-2 bg-white border border-slate-200 text-slate-700 font-medium rounded-lg hover:bg-slate-50 transition">Annuler</button>
                  <button (click)="onSubmit()" class="flex-1 px-4 py-2 bg-blue-600 text-white font-bold rounded-lg hover:bg-blue-700 transition shadow-lg shadow-blue-600/20">
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
          <div class="relative bg-white rounded-2xl shadow-2xl max-w-4xl w-full mx-4 max-h-[90vh] overflow-hidden flex flex-col">
            <div class="flex items-center justify-between p-6 border-b border-slate-100 bg-gradient-to-r from-purple-50 to-indigo-50">
              <div>
                <h2 class="text-xl font-bold text-slate-800">Détails Comptables</h2>
                <p class="text-sm text-slate-600 mt-1">Facture: {{ selectedInvoiceForDetails()?.number }}</p>
              </div>
              <button (click)="closeCalculDetails()" class="text-slate-400 hover:text-slate-600 transition">
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
              </button>
            </div>
            
            <div class="flex-1 overflow-y-auto p-6 space-y-6">
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

            <div class="p-6 border-t border-slate-100 bg-slate-50/50">
              <button (click)="closeCalculDetails()" class="w-full px-4 py-2 bg-slate-200 text-slate-700 font-medium rounded-lg hover:bg-slate-300 transition">
                Fermer
              </button>
            </div>
          </div>
        </div>
      }

      <!-- Modal Paiements -->
      @if (selectedInvoiceForPayments()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center" aria-modal="true">
          <div (click)="closePaymentModal()" class="fixed inset-0 bg-slate-900/40 backdrop-blur-sm"></div>
          <div class="relative bg-white rounded-2xl shadow-2xl max-w-4xl w-full mx-4 max-h-[90vh] overflow-hidden flex flex-col">
            <div class="flex items-center justify-between p-6 border-b border-slate-100 bg-gradient-to-r from-red-50 to-rose-50">
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
            
            <div class="flex-1 overflow-y-auto p-6 space-y-6">
              @if (activeTab() === 'paiements') {
              @if (selectedInvoiceForPayments(); as inv) {
                <!-- Résumé de la facture -->
                @if (invoicePaymentSummary(); as summary) {
                  <div class="bg-gradient-to-r from-blue-50 to-indigo-50 p-6 rounded-xl border border-blue-100">
                    <h3 class="text-lg font-bold text-slate-800 mb-4">Résumé de la Facture</h3>
                    <div class="grid grid-cols-3 gap-4 mb-4">
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
                      <table class="w-full text-sm">
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
                <div class="bg-slate-50 p-6 rounded-xl border border-slate-200">
                  <h3 class="text-lg font-bold text-slate-800 mb-4">Ajouter un Paiement</h3>
                  <form [formGroup]="paymentForm" (ngSubmit)="addPayment()" class="space-y-4">
                    <div class="grid grid-cols-2 gap-4">
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
                    <div class="grid grid-cols-2 gap-4">
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
                      <table class="w-full text-sm">
                        <thead class="bg-slate-50 border-b border-slate-200">
                          <tr>
                            <th class="px-4 py-3 text-left font-semibold text-slate-700">Date Prévue</th>
                            <th class="px-4 py-3 text-right font-semibold text-slate-700">Montant</th>
                            <th class="px-4 py-3 text-left font-semibold text-slate-700">Statut</th>
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
                                      [class.bg-blue-50]="prev.statut === 'PREVU'"
                                      [class.text-blue-700]="prev.statut === 'PREVU'"
                                      [class.bg-emerald-50]="prev.statut === 'REALISE'"
                                      [class.text-emerald-700]="prev.statut === 'REALISE'"
                                      [class.bg-red-50]="prev.statut === 'EN_RETARD'"
                                      [class.text-red-700]="prev.statut === 'EN_RETARD'">
                                  {{ prev.statut }}
                                </span>
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

                  <div class="bg-slate-50 p-6 rounded-xl border border-slate-200">
                    <h3 class="text-lg font-bold text-slate-800 mb-4">{{ editingPrevisionId() ? 'Modifier' : 'Ajouter' }} une Prévision</h3>
                    <form [formGroup]="previsionForm" (ngSubmit)="savePrevision()" class="space-y-4">
                      <div class="grid grid-cols-2 gap-4">
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

            <div class="p-6 border-t border-slate-100 bg-slate-50/50">
              <button (click)="closePaymentModal()" class="w-full px-4 py-2 bg-slate-200 text-slate-700 font-medium rounded-lg hover:bg-slate-300 transition">
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
export class PurchaseInvoicesComponent {
  Math = Math; // Make Math available in template

  store = inject(StoreService);
  fb = inject(FormBuilder);

  isFormOpen = signal(false);
  isEditMode = signal(false);
  editingId: string | null = null;
  availableBCs = signal<BC[]>([]);
  selectedInvoiceForDetails = signal<Invoice | null>(null);
  selectedInvoiceForPayments = signal<Invoice | null>(null);
  activeTab = signal<'paiements' | 'previsions'>('paiements');
  editingPrevisionId = signal<string | null>(null);

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
  filterStatus = signal<'all' | 'paid' | 'pending' | 'overdue'>('all');
  searchTerm = signal('');

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
    ajouterAuStock: [false] // Option pour ajouter au stock
  });

  constructor() {
    // Auto calculate due date when date changes
    this.form.get('date')?.valueChanges.subscribe(dateVal => {
      if (dateVal && !this.isEditMode()) {
        const d = new Date(dateVal);
        d.setDate(d.getDate() + 60); // Rule: +60 days for Purchase
        this.form.patchValue({ dueDate: d.toISOString().split('T')[0] }, { emitEvent: false });
      }
    });
  }

  // Raw List
  allPurchaseInvoices = computed(() => this.store.invoices().filter(i => i.type === 'purchase'));

  // Filtered List
  filteredInvoices = computed(() => {
    let list = this.allPurchaseInvoices();

    // Status Filter
    const status = this.filterStatus();
    if (status !== 'all') {
      list = list.filter(i => i.status === status);
    }

    // Search Filter
    const term = this.searchTerm().toLowerCase();
    if (term) {
      list = list.filter(i => 
        i.number.toLowerCase().includes(term) || 
        this.store.getSupplierName(i.partnerId || '').toLowerCase().includes(term)
      );
    }
    
    // Sort by date desc
    return list.sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
  });

  // Paginated List
  paginatedInvoices = computed(() => {
    const list = this.filteredInvoices();
    const start = (this.currentPage() - 1) * this.pageSize();
    const end = start + this.pageSize();
    return list.slice(start, end);
  });

  totalPages = computed(() => Math.ceil(this.filteredInvoices().length / this.pageSize()));

  setFilter(status: 'all' | 'paid' | 'pending' | 'overdue') {
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
      ajouterAuStock: false
    });
    // Trigger due date calc
    const today = new Date();
    today.setDate(today.getDate() + 60);
    this.form.patchValue({ dueDate: today.toISOString().split('T')[0] });
    
    this.availableBCs.set([]);
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
      ajouterAuStock: (inv as any).ajouterAuStock || false
    });
    // Populate BCs if supplier is set
    if (inv.partnerId) {
      const bcs = this.store.bcs().filter(b => b.supplierId === inv.partnerId);
      this.availableBCs.set(bcs);
    }
    this.isFormOpen.set(true);
  }

  async exportPDF(inv: Invoice) {
    try {
      await this.store.downloadFactureAchatPDF(inv.id);
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

  showCalculDetails(inv: Invoice) {
    this.selectedInvoiceForDetails.set(inv);
  }

  closeCalculDetails() {
    this.selectedInvoiceForDetails.set(null);
  }

  async showPaymentModal(inv: Invoice) {
    this.selectedInvoiceForPayments.set(inv);
    this.activeTab.set('paiements');
    // Charger les paiements pour cette facture
    await this.store.loadPaymentsForInvoice(inv.id, 'purchase');
    // Recharger les factures pour avoir les prévisions
    await this.store.loadInvoices();
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
    console.log('[previsionsForInvoice computed ACHAT] Recalcul - inv:', inv?.id, 'prévisions:', inv?.previsionsPaiement?.length || 0);
    if (!inv) return [];
    const previsions = inv.previsionsPaiement || [];
    console.log('[previsionsForInvoice computed ACHAT] Retour:', previsions.length, 'prévisions');
    return previsions;
  });

  async savePrevision() {
    console.log('[savePrevision ACHAT] Début');
    if (this.previsionForm.invalid || !this.selectedInvoiceForPayments()) {
      console.log('[savePrevision ACHAT] Formulaire invalide ou facture non sélectionnée');
      return;
    }

    const inv = this.selectedInvoiceForPayments()!;
    const invoiceId = inv.id; // Sauvegarder l'ID avant le rechargement
    console.log('[savePrevision ACHAT] Facture actuelle:', invoiceId, 'Prévisions avant:', inv.previsionsPaiement?.length || 0);
    
    const previsionData: PrevisionPaiement = {
      datePrevue: this.previsionForm.value.datePrevue!,
      montantPrevu: this.previsionForm.value.montantPrevu!,
      notes: this.previsionForm.value.notes || '',
      dateRappel: this.previsionForm.value.dateRappel || undefined,
      statut: 'PREVU'
    };
    console.log('[savePrevision ACHAT] Données prévision:', previsionData);

    try {
      if (this.editingPrevisionId()) {
        console.log('[savePrevision ACHAT] Mise à jour prévision:', this.editingPrevisionId());
        await this.store.updatePrevision(inv.id, this.editingPrevisionId()!, 'achat', previsionData);
      } else {
        console.log('[savePrevision ACHAT] Ajout nouvelle prévision');
        await this.store.addPrevision(inv.id, 'achat', previsionData);
      }
      console.log('[savePrevision ACHAT] Prévision sauvegardée, loadInvoices() devrait être terminé');
      
      this.previsionForm.reset({
        datePrevue: new Date().toISOString().split('T')[0],
        montantPrevu: 0,
        notes: '',
        dateRappel: ''
      });
      this.editingPrevisionId.set(null);
      
      // Vérifier l'état actuel des factures
      console.log('[savePrevision ACHAT] Nombre de factures dans store:', this.store.invoices().length);
      const invoiceBeforeUpdate = this.store.invoices().find(inv => inv.id === invoiceId);
      console.log('[savePrevision ACHAT] Facture trouvée avant update:', invoiceBeforeUpdate?.id, 'Prévisions:', invoiceBeforeUpdate?.previsionsPaiement?.length || 0);
      
      // addPrevision/updatePrevision appellent déjà loadInvoices()
      // On attend que la mise à jour soit terminée puis on met à jour la facture sélectionnée
      // On force la création d'un nouvel objet pour que Angular détecte le changement
      await new Promise(resolve => setTimeout(resolve, 100));
      
      console.log('[savePrevision ACHAT] Après délai, recherche facture mise à jour');
      const updatedInvoice = this.store.invoices().find(inv => inv.id === invoiceId);
      console.log('[savePrevision ACHAT] Facture mise à jour trouvée:', updatedInvoice?.id, 'Prévisions:', updatedInvoice?.previsionsPaiement?.length || 0);
      if (updatedInvoice) {
        console.log('[savePrevision ACHAT] Détails prévisions:', updatedInvoice.previsionsPaiement);
        // Créer un nouvel objet pour forcer la détection de changement
        this.selectedInvoiceForPayments.set({ ...updatedInvoice });
        console.log('[savePrevision ACHAT] selectedInvoiceForPayments mis à jour');
        console.log('[savePrevision ACHAT] selectedInvoiceForPayments après update:', this.selectedInvoiceForPayments()?.previsionsPaiement?.length || 0);
        console.log('[savePrevision ACHAT] previsionsForInvoice computed:', this.previsionsForInvoice().length);
      } else {
        console.error('[savePrevision ACHAT] ERREUR: Facture mise à jour non trouvée!');
      }
    } catch (error) {
      console.error('[savePrevision ACHAT] Erreur:', error);
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
      await this.store.loadInvoices();
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
      // Recharger les paiements
      await this.store.loadPaymentsForInvoice(inv.id, 'purchase');
      // Recharger les factures pour mettre à jour le statut
      await this.store.loadInvoices();
      // Réinitialiser le formulaire
      this.paymentForm.reset({
        date: new Date().toISOString().split('T')[0],
        montant: 0,
        mode: '',
        reference: '',
        notes: ''
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
    
    const invoice: Invoice & { ajouterAuStock?: boolean } = {
      id: this.editingId || `fa-${Date.now()}`,
      type: 'purchase',
      number: val.number,
      partnerId: val.partnerId,
      bcId: val.bcId || undefined,
      date: val.date,
      dueDate: val.dueDate,
      amountHT: amountHT,
      amountTTC: amountTTC,
      status: val.status || 'pending',
      paymentMode: val.paymentMode || undefined,
      ajouterAuStock: val.ajouterAuStock || false
    };

    if (this.isEditMode()) {
      this.store.updateInvoice(invoice);
    } else {
      this.store.addInvoice(invoice);
    }
    this.closeForm();
  }
}
