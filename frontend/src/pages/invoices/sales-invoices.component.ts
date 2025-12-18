import { Component, inject, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StoreService, Invoice, BC } from '../../services/store.service';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators, FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

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
           </div>
           
           <div class="relative w-full md:w-64">
              <span class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path></svg>
              </span>
              <input type="text" [(ngModel)]="searchTerm" placeholder="Rechercher facture..." class="w-full pl-9 pr-3 py-1.5 rounded-full border border-slate-200 text-xs bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all">
           </div>
        </div>

        <div class="overflow-x-auto">
          <table class="w-full text-sm text-left min-w-[700px]">
            <thead class="text-xs text-slate-500 uppercase bg-slate-50 border-b border-slate-200">
              <tr>
                <th class="px-6 py-4 font-semibold">Facture</th>
                <th class="px-6 py-4 font-semibold">Client & BC</th>
                <th class="px-6 py-4 font-semibold">Date Émission</th>
                <th class="px-6 py-4 font-semibold">Échéance</th>
                <th class="px-6 py-4 font-semibold text-right">Montant TTC</th>
                <th class="px-6 py-4 font-semibold text-center">Mode Paiement</th>
                <th class="px-6 py-4 font-semibold text-center">État</th>
                <th class="px-6 py-4 text-center">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              @for (inv of filteredInvoices(); track inv.id) {
                <tr class="bg-white hover:bg-slate-50 transition-colors group" [attr.data-item-id]="inv.id">
                  <td class="px-6 py-4">
                    <div class="font-bold text-slate-800 text-base mb-0.5">{{ inv.number }}</div>
                    <span class="text-xs text-slate-400">Ref interne</span>
                  </td>
                  <td class="px-6 py-4">
                    <div class="flex flex-col">
                       <span class="font-medium text-slate-700 flex items-center gap-1.5">
                         <div class="w-1.5 h-1.5 rounded-full bg-indigo-500"></div>
                         {{ store.getClientName(inv.partnerId || '') }}
                       </span>
                       @if (inv.bcId) {
                         <a [routerLink]="['/bc/edit', inv.bcId]" class="text-xs text-blue-500 hover:underline mt-0.5 pl-3">BC: {{ store.getBCNumber(inv.bcId) }}</a>
                       }
                    </div>
                  </td>
                  <td class="px-6 py-4 text-slate-600">{{ inv.date }}</td>
                  <td class="px-6 py-4">
                    <span class="text-slate-600 font-medium">{{ inv.dueDate }}</span>
                  </td>
                  <td class="px-6 py-4 text-right font-bold text-slate-800">
                    {{ inv.amountTTC | number:'1.2-2' }} MAD
                  </td>
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
                  <td class="px-6 py-4 text-center">
                    <div class="flex items-center justify-center gap-2 opacity-100 md:opacity-0 group-hover:opacity-100 transition-opacity">
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
              @if (filteredInvoices().length === 0) {
                <tr>
                  <td colspan="8" class="px-6 py-12 text-center text-slate-500">
                    Aucune facture trouvée.
                  </td>
                </tr>
              }
            </tbody>
          </table>
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
                    {{ isEditMode() ? 'Modifier' : 'Créer' }} Facture Vente
                  </h2>
                  <button (click)="closeForm()" class="text-slate-400 hover:text-slate-600 transition">
                     <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                  </button>
               </div>
               
               <form [formGroup]="form" (ngSubmit)="onSubmit()" class="flex-1 overflow-y-auto p-6 space-y-6">
                  
                  <!-- Section Link -->
                  <div class="bg-indigo-50 p-4 rounded-xl border border-indigo-100 space-y-4">
                     <div>
                        <label class="block text-xs font-bold text-indigo-700 uppercase mb-1">Client</label>
                        <select formControlName="partnerId" (change)="onPartnerChange()" class="w-full p-2 border border-indigo-200 rounded-lg bg-white focus:ring-2 focus:ring-blue-500/20 outline-none">
                           <option value="">Sélectionner un client</option>
                           @for (c of store.clients(); track c.id) {
                              <option [value]="c.id">{{ c.name }}</option>
                           }
                        </select>
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

                     <div class="grid grid-cols-2 gap-4">
                        <div>
                          <label class="block text-sm font-semibold text-slate-700 mb-1">Date Émission</label>
                          <input formControlName="date" type="date" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                        </div>
                        <div>
                          <label class="block text-sm font-semibold text-slate-700 mb-1">Date Échéance</label>
                          <input formControlName="dueDate" type="date" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                        </div>
                     </div>
                     
                     <div class="grid grid-cols-2 gap-4">
                        <div>
                           <label class="block text-sm font-semibold text-slate-700 mb-1">Montant HT</label>
                           <input formControlName="amountHT" type="number" step="0.01" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition text-right">
                        </div>
                        <div>
                           <label class="block text-sm font-semibold text-slate-700 mb-1">Montant TTC</label>
                           <input formControlName="amountTTC" type="number" step="0.01" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition text-right font-bold text-slate-800">
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

               <div class="p-6 border-t border-slate-100 bg-slate-50/50 flex gap-3">
                  <button (click)="closeForm()" class="flex-1 px-4 py-2 bg-white border border-slate-200 text-slate-700 font-medium rounded-lg hover:bg-slate-50 transition">Annuler</button>
                  <button (click)="onSubmit()" [disabled]="form.invalid" class="flex-1 px-4 py-2 bg-blue-600 text-white font-bold rounded-lg hover:bg-blue-700 transition shadow-lg disabled:opacity-50 shadow-blue-600/20">
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

    </div>
  `,
  styles: [`
    @keyframes slideInRight {
      from { transform: translateX(100%); }
      to { transform: translateX(0); }
    }
  `]
})
export class SalesInvoicesComponent {
  store = inject(StoreService);
  fb = inject(FormBuilder);

  isFormOpen = signal(false);
  isEditMode = signal(false);
  editingId: string | null = null;
  originalInvoice: Invoice | null = null; // Stocker la facture originale pour préserver les valeurs
  availableBCs = signal<BC[]>([]);
  selectedInvoiceForDetails = signal<Invoice | null>(null);

  // Filters
  filterStatus = signal<'all' | 'paid' | 'pending' | 'overdue'>('all');
  searchTerm = signal('');

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
    paymentMode: ['']
  });

  constructor() {
    // Auto calculate due date default (+30 days for sales)
    this.form.get('date')?.valueChanges.subscribe(dateVal => {
      if (dateVal && !this.isEditMode()) {
        const d = new Date(dateVal);
        d.setDate(d.getDate() + 30);
        this.form.patchValue({ dueDate: d.toISOString().split('T')[0] }, { emitEvent: false });
      }
    });
  }

  // Raw list
  allSalesInvoices = computed(() => this.store.invoices().filter(i => i.type === 'sale'));

  // Filtered List
  filteredInvoices = computed(() => {
    let list = this.allSalesInvoices();
    
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
        this.store.getClientName(i.partnerId || '').toLowerCase().includes(term)
      );
    }
    
    return list;
  });

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

  setFilter(status: 'all' | 'paid' | 'pending' | 'overdue') {
    this.filterStatus.set(status);
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
      number: '', // Laisser vide - sera généré par le backend
      date: new Date().toISOString().split('T')[0],
      status: 'pending',
      amountHT: 0,
      amountTTC: 0
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
    console.log('🔵 sales-invoices.onBCChange - bcId:', bcId, 'clientId:', clientId);
    const bc = this.store.bcs().find(b => b.id === bcId);
    console.log('🔵 sales-invoices.onBCChange - BC trouvé:', bc);
    
    if (bc) {
      let totalHT = 0;
      let totalTva = 0;

      // Nouvelle structure multi-clients
      if (bc.clientsVente && bc.clientsVente.length > 0) {
        console.log('🔵 sales-invoices.onBCChange - Utilisation nouvelle structure (clientsVente)');
        // Trouver le client spécifique dans le BC
        const clientVente = bc.clientsVente.find(cv => cv.clientId === clientId);
        console.log('🔵 sales-invoices.onBCChange - ClientVente trouvé:', clientVente);
        if (clientVente && clientVente.lignesVente) {
          // Utiliser les totaux pré-calculés si disponibles
          if (clientVente.totalVenteHT !== undefined && clientVente.totalVenteTTC !== undefined) {
            console.log('🔵 sales-invoices.onBCChange - Utilisation totaux pré-calculés:', {
              totalVenteHT: clientVente.totalVenteHT,
              totalVenteTTC: clientVente.totalVenteTTC
            });
            totalHT = clientVente.totalVenteHT;
            totalTva = (clientVente.totalVenteTTC || 0) - totalHT;
          } else {
            console.log('🔵 sales-invoices.onBCChange - Calcul depuis lignesVente');
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
        console.log('🔵 sales-invoices.onBCChange - Utilisation ancienne structure (items)');
        totalHT = bc.items.reduce((acc, i) => acc + (i.qtySell * i.priceSellHT), 0);
        totalTva = bc.items.reduce((acc, i) => acc + (i.qtySell * i.priceSellHT * (i.tvaRate/100)), 0);
      }
      
      console.log('🔵 sales-invoices.onBCChange - Montants calculés:', {
        totalHT,
        totalTva,
        totalTTC: totalHT + totalTva
      });
      
      this.form.patchValue({
        amountHT: totalHT,
        amountTTC: totalHT + totalTva
      });
      
      console.log('🔵 sales-invoices.onBCChange - Formulaire mis à jour, valeurs actuelles:', {
        amountHT: this.form.get('amountHT')?.value,
        amountTTC: this.form.get('amountTTC')?.value
      });
    } else {
      console.warn('🔵 sales-invoices.onBCChange - BC non trouvé pour id:', bcId);
    }
  }

  editInvoice(inv: Invoice) {
    console.log('🔵 editInvoice - Facture reçue:', inv);
    console.log('🔵 editInvoice - Montants:', { amountHT: inv.amountHT, amountTTC: inv.amountTTC });
    
    this.isEditMode.set(true);
    this.editingId = inv.id;
    // Stocker la facture originale pour préserver les valeurs
    this.originalInvoice = { ...inv };
    console.log('🔵 editInvoice - originalInvoice sauvegardé:', this.originalInvoice);
    console.log('🔵 editInvoice - Montants dans originalInvoice:', { 
      amountHT: this.originalInvoice.amountHT, 
      amountTTC: this.originalInvoice.amountTTC 
    });
    
    // S'assurer que tous les champs sont bien remplis, notamment les montants
    const formValues = {
      number: inv.number || '',
      partnerId: inv.partnerId || '',
      bcId: inv.bcId || '',
      date: inv.date || '',
      dueDate: inv.dueDate || '',
      amountHT: inv.amountHT != null && inv.amountHT !== undefined ? Number(inv.amountHT) : 0,
      amountTTC: inv.amountTTC != null && inv.amountTTC !== undefined ? Number(inv.amountTTC) : 0,
      status: inv.status || 'pending',
      paymentMode: inv.paymentMode || ''
    };
    console.log('🔵 editInvoice - Valeurs du formulaire:', formValues);
    this.form.patchValue(formValues);
    
    // Vérifier les valeurs après patchValue
    setTimeout(() => {
      console.log('🔵 editInvoice - Valeurs du formulaire APRÈS patchValue:', this.form.value);
    }, 100);
    
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

  async onSubmit() {
    console.log('🟢 onSubmit - Début');
    console.log('🟢 onSubmit - isEditMode:', this.isEditMode());
    console.log('🟢 onSubmit - editingId:', this.editingId);
    console.log('🟢 onSubmit - originalInvoice:', this.originalInvoice);
    
    if (this.form.invalid) {
      console.warn('🟡 onSubmit - Formulaire invalide');
      return;
    }
    
    const val = this.form.value;
    console.log('🟢 onSubmit - Valeurs du formulaire:', val);
    console.log('🟢 onSubmit - Montants du formulaire:', { 
      amountHT: val.amountHT, 
      amountTTC: val.amountTTC,
      amountHTType: typeof val.amountHT,
      amountTTCType: typeof val.amountTTC
    });
    
    // En mode édition, toujours préserver les montants et autres valeurs de la facture originale
    if (this.isEditMode() && this.editingId && this.originalInvoice) {
      console.log('🟢 onSubmit - Mode édition détecté');
      console.log('🟢 onSubmit - Montants dans originalInvoice:', { 
        amountHT: this.originalInvoice.amountHT, 
        amountTTC: this.originalInvoice.amountTTC 
      });
      
      // TOUJOURS utiliser les montants de la facture originale, jamais ceux du formulaire
      // Le formulaire peut avoir des valeurs vides ou 0 à cause du formatage
      const originalHT = this.originalInvoice.amountHT;
      const originalTTC = this.originalInvoice.amountTTC;
      
      console.log('🟢 onSubmit - Utilisation des montants originaux:', { 
        originalHT, 
        originalTTC,
        originalHTType: typeof originalHT,
        originalTTCType: typeof originalTTC
      });
      
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
      
      console.log('🟢 onSubmit - Valeurs finales après préservation:', val);
      console.log('🟢 onSubmit - Montants finaux:', { 
        amountHT: val.amountHT, 
        amountTTC: val.amountTTC 
      });
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
      amountHT: amountHT,
      amountTTC: amountTTC,
      status: val.status || 'pending',
      paymentMode: val.paymentMode || undefined
    };
    
    console.log('🟢 onSubmit - Invoice final à envoyer:', invoice);
    console.log('🟢 onSubmit - Montants dans invoice final:', { 
      amountHT: invoice.amountHT, 
      amountTTC: invoice.amountTTC 
    });

    try {
      if (this.isEditMode()) {
        console.log('🟢 onSubmit - Appel à store.updateInvoice');
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
}
