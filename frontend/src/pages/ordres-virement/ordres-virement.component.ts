import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StoreService, Invoice, Supplier } from '../../services/store.service';
import { OrdreVirement, FactureMontant } from '../../models/types';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators, FormsModule } from '@angular/forms';

@Component({
  selector: 'app-ordres-virement',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  template: `
    <div class="space-y-6 fade-in-up pb-10">
      
      <!-- Header & Actions -->
      <div class="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h1 class="text-2xl font-bold text-slate-800 font-display">Ordres de Virements</h1>
          <p class="text-slate-500 text-sm mt-1">Gérez vos ordres de virement et leur historique.</p>
        </div>
        <button (click)="openForm()" class="px-5 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 shadow-lg shadow-blue-600/20 font-medium transition flex items-center gap-2">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path></svg>
          Créer Ordre de Virement
        </button>
      </div>

      <!-- Filters -->
      <div class="bg-white p-5 rounded-xl shadow-sm border border-slate-100 grid grid-cols-1 md:grid-cols-12 gap-4 items-end">
        
        <div class="md:col-span-3 space-y-1">
          <label class="text-xs font-semibold text-slate-500 uppercase">Recherche</label>
          <div class="relative">
            <span class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
              <svg class="h-4 w-4 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
              </svg>
            </span>
            <input type="text" [(ngModel)]="searchTerm" placeholder="N° OV..." class="w-full pl-9 pr-3 py-2.5 border border-slate-200 rounded-lg text-sm bg-slate-50 focus:bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all outline-none">
          </div>
        </div>

        <div class="md:col-span-2 space-y-1">
          <label class="text-xs font-semibold text-slate-500 uppercase">Bénéficiaire</label>
          <select [(ngModel)]="filterBeneficiaire" class="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm bg-slate-50 focus:bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all appearance-none cursor-pointer outline-none">
            <option value="">Tous</option>
            <option value="OTHER">Personnes Physiques</option>
            @for (sup of store.suppliers(); track sup.id) {
              <option [value]="sup.id">{{ sup.name }}</option>
            }
          </select>
        </div>

        <div class="md:col-span-2 space-y-1">
          <label class="text-xs font-semibold text-slate-500 uppercase">Statut</label>
          <select [(ngModel)]="filterStatut" class="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm bg-slate-50 focus:bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all appearance-none cursor-pointer outline-none">
            <option value="">Tous</option>
            <option value="EN_ATTENTE">En attente</option>
            <option value="EXECUTE">Exécuté</option>
            <option value="ANNULE">Annulé</option>
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
                <th class="px-6 py-4 font-semibold tracking-wider">N° OV</th>
                <th class="px-6 py-4 font-semibold tracking-wider">Bénéficiaire</th>
                <th class="px-6 py-4 font-semibold tracking-wider">Date</th>
                <th class="px-6 py-4 font-semibold tracking-wider text-right">Montant</th>
                <th class="px-6 py-4 font-semibold tracking-wider">Factures</th>
                <th class="px-6 py-4 font-semibold tracking-wider text-center">Statut</th>
                <th class="px-6 py-4 text-center w-32">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              @for (ov of paginatedOrdres(); track ov.id) {
                <tr class="bg-white hover:bg-slate-50 transition-colors group">
                  <td class="px-6 py-4">
                    <div class="flex items-center gap-2">
                      <span class="font-bold text-blue-600">{{ ov.numeroOV }}</span>
                      @if (ov.type === 'EXPRESS') {
                        <span class="px-2 py-0.5 bg-red-50 text-red-600 rounded text-xs font-bold border border-red-200">EXPRESS</span>
                      }
                    </div>
                  </td>
                  <td class="px-6 py-4">
                    <div class="flex flex-col">
                      <span class="font-medium text-slate-700">{{ ov.nomBeneficiaire || store.getSupplierName(ov.beneficiaireId) }}</span>
                      <span class="text-xs text-slate-400">{{ ov.ribBeneficiaire }}</span>
                    </div>
                  </td>
                  <td class="px-6 py-4">
                    <div class="flex flex-col">
                      <span class="text-sm">{{ ov.dateOV }}</span>
                      @if (ov.dateExecution) {
                        <span class="text-xs text-slate-400">Exécution: {{ ov.dateExecution }}</span>
                      }
                    </div>
                  </td>
                  <td class="px-6 py-4 text-right font-bold text-slate-800">{{ formatCurrency(ov.montant) }}</td>
                  <td class="px-6 py-4">
                    @if (ov.facturesIds && ov.facturesIds.length > 0) {
                      <div class="flex flex-wrap gap-1">
                        @for (factureId of ov.facturesIds; track factureId; let i = $index) {
                          @if (i < 2) {
                            <span class="inline-flex items-center gap-1 px-2 py-0.5 bg-blue-50 text-blue-700 rounded-full text-xs font-medium">
                              {{ getFactureNumber(factureId) }}
                            </span>
                          }
                        }
                        @if (ov.facturesIds.length > 2) {
                          <span class="px-2 py-0.5 bg-slate-100 text-slate-600 rounded-full text-xs font-medium">
                            +{{ ov.facturesIds.length - 2 }}
                          </span>
                        }
                      </div>
                    } @else {
                      <span class="text-xs text-slate-400 italic">Aucune facture</span>
                    }
                  </td>
                  <td class="px-6 py-4 text-center">
                    <span [class]="getStatutClass(ov.statut)">
                      {{ getStatutLabel(ov.statut) }}
                    </span>
                  </td>
                  <td class="px-6 py-4 text-right">
                    <div class="flex items-center justify-end gap-2 opacity-100 md:opacity-0 group-hover:opacity-100 transition-opacity">
                      @if (ov.statut === 'EN_ATTENTE') {
                        <button (click)="executerOV(ov.id!)" class="p-2 text-slate-400 hover:text-emerald-600 hover:bg-emerald-50 rounded-full transition-all" title="Exécuter">
                          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>
                        </button>
                        <button (click)="annulerOV(ov.id!)" class="p-2 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-full transition-all" title="Annuler">
                          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                        </button>
                      }
                      <button (click)="downloadPDF(ov.id!)" class="p-2 text-slate-400 hover:text-purple-600 hover:bg-purple-50 rounded-full transition-all" title="Télécharger PDF">
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
                      </button>
                      <button (click)="editOV(ov)" class="p-2 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-full transition-all" title="Modifier">
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"></path></svg>
                      </button>
                      <button (click)="deleteOV(ov.id!)" class="p-2 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-full transition-all" title="Supprimer">
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path></svg>
                      </button>
                    </div>
                  </td>
                </tr>
              }
              @if (paginatedOrdres().length === 0) {
                <tr>
                  <td colspan="7" class="px-6 py-12 text-center text-slate-500">
                    Aucun ordre de virement trouvé.
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <!-- Pagination -->
        <div class="p-4 border-t border-slate-200 bg-slate-50 flex items-center justify-between">
          <div class="text-xs text-slate-500">
            Affichage de {{ (currentPage() - 1) * pageSize() + 1 }} à {{ Math.min(currentPage() * pageSize(), filteredOrdres().length) }} sur {{ filteredOrdres().length }} résultats
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

    <!-- Modal Formulaire -->
    @if (isFormOpen()) {
      <div class="fixed inset-0 z-50 flex items-center justify-center" aria-modal="true">
        <div (click)="closeForm()" class="fixed inset-0 bg-slate-900/40 backdrop-blur-sm"></div>
        
        <div class="relative w-full md:w-[800px] max-h-[90vh] bg-white rounded-xl shadow-2xl flex flex-col transform transition-transform mx-2 md:mx-4">
          <div class="flex items-center justify-between p-4 md:p-6 border-b border-slate-100 bg-slate-50/50">
            <h2 class="text-lg md:text-xl font-bold text-slate-800">
              {{ isEditMode() ? 'Modifier' : 'Créer' }} Ordre de Virement
            </h2>
            <button (click)="closeForm()" class="text-slate-400 hover:text-slate-600 transition">
              <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
            </button>
          </div>
          
          <form [formGroup]="form" (ngSubmit)="onSubmit()" class="flex-1 overflow-y-auto p-4 md:p-6 space-y-6">
            
            <!-- Informations Générales -->
            <div class="space-y-4">
              <h3 class="text-sm font-bold text-slate-700 uppercase border-b border-slate-200 pb-2">Informations Générales</h3>
              
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label class="block text-xs font-semibold text-slate-600 mb-1">Date OV <span class="text-red-500">*</span></label>
                  <input type="date" formControlName="dateOV" class="w-full p-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
                </div>
                
                <div>
                  <label class="block text-xs font-semibold text-slate-600 mb-1">Date d'exécution</label>
                  <input type="date" formControlName="dateExecution" class="w-full p-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
                </div>
              </div>

              <!-- Type de Bénéficiaire -->
              <div class="bg-slate-50 p-4 rounded-lg border border-slate-200">
                <label class="block text-xs font-semibold text-slate-600 mb-3">Type de Bénéficiaire <span class="text-red-500">*</span></label>
                <div class="flex gap-4">
                  <label class="flex items-center gap-2 cursor-pointer">
                    <input type="radio" [checked]="beneficiaryType() === 'SUPPLIER'" (change)="beneficiaryType.set('SUPPLIER'); onBeneficiaryTypeChange()" class="w-4 h-4 text-blue-600 border-slate-300 focus:ring-blue-500">
                    <span class="text-sm font-medium text-slate-700">Fournisseur</span>
                  </label>
                  <label class="flex items-center gap-2 cursor-pointer">
                    <input type="radio" [checked]="beneficiaryType() === 'OTHER'" (change)="beneficiaryType.set('OTHER'); onBeneficiaryTypeChange()" class="w-4 h-4 text-blue-600 border-slate-300 focus:ring-blue-500">
                    <span class="text-sm font-medium text-slate-700">Autre / Personne Physique</span>
                  </label>
                </div>
              </div>

              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <!-- Bénéficiaire selon le type -->
                @if (beneficiaryType() === 'SUPPLIER') {
                  <div>
                    <label class="block text-xs font-semibold text-slate-600 mb-1">Fournisseur <span class="text-red-500">*</span></label>
                    <select formControlName="beneficiaireId" (change)="onBeneficiaireChange()" 
                            [class.border-red-300]="isFieldInvalid('beneficiaireId')"
                            class="w-full p-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
                      <option value="">Sélectionner un fournisseur</option>
                      @for (sup of store.suppliers(); track sup.id) {
                        <option [value]="sup.id">{{ sup.name }}</option>
                      }
                    </select>
                    @if (isFieldInvalid('beneficiaireId')) {
                      <p class="text-xs text-red-500 mt-1">Le fournisseur est requis</p>
                    }
                  </div>
                } @else {
                  <div>
                    <label class="block text-xs font-semibold text-slate-600 mb-1">Nom du Bénéficiaire <span class="text-red-500">*</span></label>
                    <input type="text" formControlName="nomBeneficiaire" 
                           placeholder="Ex: Prénom NOM"
                           [class.border-red-300]="isFieldInvalid('nomBeneficiaire')"
                           class="w-full p-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
                    @if (isFieldInvalid('nomBeneficiaire')) {
                      <p class="text-xs text-red-500 mt-1">Le nom du bénéficiaire est requis</p>
                    }
                  </div>
                }

                <div>
                  <label class="block text-xs font-semibold text-slate-600 mb-1">Type <span class="text-red-500">*</span></label>
                  <select formControlName="type" class="w-full p-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
                    <option value="NORMAL">Normal</option>
                    <option value="EXPRESS">Express</option>
                  </select>
                </div>
              </div>

              <div>
                <label class="block text-xs font-semibold text-slate-600 mb-1">RIB Bénéficiaire <span class="text-red-500">*</span></label>
                <input type="text" formControlName="ribBeneficiaire" placeholder="RIB du bénéficiaire (auto-rempli depuis le fournisseur)" class="w-full p-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
              </div>

              <div>
                <label class="block text-xs font-semibold text-slate-600 mb-1">Banque Bénéficiaire <span class="text-red-500">*</span></label>
                <input type="text" formControlName="banqueBeneficiaire" placeholder="Banque du bénéficiaire (auto-rempli depuis le fournisseur)" class="w-full p-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
              </div>

              <div>
                <label class="block text-xs font-semibold text-slate-600 mb-1">Banque Émettrice <span class="text-red-500">*</span></label>
                <input type="text" formControlName="banqueEmettrice" placeholder="Nom de la banque" class="w-full p-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
              </div>

              <div>
                <label class="block text-xs font-semibold text-slate-600 mb-1">Motif <span class="text-red-500">*</span></label>
                <textarea formControlName="motif" rows="3" placeholder="Libellé/Motif du virement" class="w-full p-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none"></textarea>
              </div>
            </div>

            <!-- Factures ou Montant Manuel -->
            <div class="space-y-4">
              <h3 class="text-sm font-bold text-slate-700 uppercase border-b border-slate-200 pb-2">
                Factures Concernées 
                <span class="text-xs font-normal text-slate-500 normal-case">(optionnel - sinon saisissez un montant manuel ci-dessous)</span>
              </h3>
              
              @if (availableFactures().length > 0) {
                <div class="space-y-2 max-h-60 overflow-y-auto border border-slate-200 rounded-lg p-3">
                  @for (facture of availableFactures(); track facture.id) {
                    <div class="p-2 hover:bg-slate-50 rounded border border-slate-100">
                      <label class="flex items-center gap-3 cursor-pointer mb-2">
                        <input type="checkbox" [value]="facture.id" (change)="onFactureToggleWithValidation(facture.id)" [checked]="isFactureSelected(facture.id)" class="rounded border-slate-300 text-blue-600 focus:ring-blue-500">
                        <div class="flex-1">
                          <div class="font-medium text-sm">{{ facture.number }}</div>
                          <div class="text-xs text-slate-500">{{ store.getSupplierName(facture.partnerId || '') }} - Total: {{ formatCurrency(facture.amountTTC) }}</div>
                        </div>
                      </label>
                      @if (isFactureSelected(facture.id)) {
                        <div class="ml-7 mt-2">
                          <label class="block text-xs font-semibold text-slate-600 mb-1">
                            Montant partiel (laisser vide pour total)
                          </label>
                          <input type="number" step="0.01" [value]="getFactureMontant(facture.id)" (input)="onFactureMontantChange(facture.id, $event)" placeholder="{{ formatCurrency(facture.amountTTC) }}" class="w-full p-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none text-sm">
                        </div>
                      }
                    </div>
                  }
                </div>
              } @else {
                <div class="bg-amber-50 border border-amber-200 rounded-lg p-3">
                  <p class="text-sm text-amber-700">Aucune facture disponible. Utilisez le montant manuel ci-dessous.</p>
                </div>
              }

              <!-- Montant Manuel (si aucune facture sélectionnée) -->
              @if (selectedFactures().length === 0) {
                <div class="bg-emerald-50 border border-emerald-200 rounded-lg p-4">
                  <label class="block text-xs font-semibold text-emerald-800 mb-1">
                    Montant Manuel <span class="text-red-500">*</span>
                  </label>
                  <input type="number" step="0.01" formControlName="montant" 
                         (input)="onMontantChange($event)"
                         [class.border-red-300]="isFieldInvalid('montant')"
                         placeholder="0.00" 
                         class="w-full p-2 border border-emerald-300 rounded-lg focus:ring-2 focus:ring-emerald-500/20 outline-none text-right font-semibold">
                  <p class="text-xs text-emerald-700 mt-1">Saisissez le montant du virement si aucune facture n'est sélectionnée</p>
                  @if (isFieldInvalid('montant')) {
                    <p class="text-xs text-red-500 mt-1">Le montant doit être supérieur à 0</p>
                  }
                </div>
              }

              <div class="bg-blue-50 p-3 rounded-lg border border-blue-100">
                <div class="text-sm font-semibold text-blue-800">Montant Total: {{ formatCurrency(calculatedMontant()) }}</div>
              </div>
            </div>

            <!-- Actions -->
            <div class="flex gap-3 pt-4 border-t border-slate-200">
              <button type="button" (click)="closeForm()" class="flex-1 px-4 py-2.5 bg-slate-100 text-slate-700 rounded-lg hover:bg-slate-200 font-medium transition">
                Annuler
              </button>
              <button type="submit" class="flex-1 px-4 py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 font-medium transition">
                {{ isEditMode() ? 'Modifier' : 'Créer' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    }
  `
})
export class OrdresVirementComponent implements OnInit {
  store = inject(StoreService);
  fb = inject(FormBuilder);

  // State
  searchTerm = signal('');
  filterBeneficiaire = signal('');
  filterStatut = signal('');
  dateMin = signal('');
  dateMax = signal('');
  currentPage = signal(1);
  pageSize = signal(10);
  isFormOpen = signal(false);
  isEditMode = signal(false);
  selectedFactures = signal<string[]>([]);
  facturesMontants = signal<Map<string, number>>(new Map());
  editingOV = signal<OrdreVirement | null>(null);
  beneficiaryType = signal<'SUPPLIER' | 'OTHER'>('SUPPLIER'); // Type de bénéficiaire

  form: FormGroup;

  constructor() {
    this.form = this.fb.group({
      dateOV: ['', Validators.required],
      dateExecution: [''],
      beneficiaireId: [''], // Plus obligatoire par défaut
      nomBeneficiaire: [''], // Pour saisie manuelle
      ribBeneficiaire: ['', Validators.required],
      banqueBeneficiaire: ['', Validators.required],
      banqueEmettrice: ['', Validators.required],
      motif: ['', Validators.required],
      type: ['NORMAL', Validators.required],
      statut: ['EN_ATTENTE'],
      montant: [0] // Montant manuel si pas de factures
    });

    // Écouter les changements du montant pour mettre à jour le signal
    this.form.get('montant')?.valueChanges.subscribe(value => {
      this.montantManuel.set(value || 0);
    });
  }

  ngOnInit() {
    this.store.loadOrdresVirement();
    this.store.loadInvoices();
    this.store.loadSuppliers();
  }

  // Computed
  filteredOrdres = computed(() => {
    let ordres = this.store.ordresVirement();
    
    if (this.searchTerm()) {
      const term = this.searchTerm().toLowerCase();
      ordres = ordres.filter(ov => 
        ov.numeroOV.toLowerCase().includes(term) ||
        (ov.nomBeneficiaire && ov.nomBeneficiaire.toLowerCase().includes(term))
      );
    }
    
    if (this.filterBeneficiaire()) {
      ordres = ordres.filter(ov => ov.beneficiaireId === this.filterBeneficiaire() || 
                                  (ov.beneficiaireId === undefined && this.filterBeneficiaire() === 'OTHER'));
    }
    
    if (this.filterStatut()) {
      ordres = ordres.filter(ov => ov.statut === this.filterStatut());
    }
    
    if (this.dateMin()) {
      ordres = ordres.filter(ov => ov.dateOV >= this.dateMin());
    }
    
    if (this.dateMax()) {
      ordres = ordres.filter(ov => ov.dateOV <= this.dateMax());
    }
    
    return ordres;
  });

  totalPages = computed(() => Math.ceil(this.filteredOrdres().length / this.pageSize()));

  paginatedOrdres = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize();
    const end = start + this.pageSize();
    return this.filteredOrdres().slice(start, end);
  });

  availableFactures = computed(() => {
    return this.store.invoices().filter(inv => inv.type === 'purchase' && inv.status !== 'paid');
  });

  // Signal pour suivre le montant manuel
  montantManuel = signal<number>(0);

  calculatedMontant = computed(() => {
    // Si des factures sont sélectionnées, calculer à partir des factures
    if (this.selectedFactures().length > 0) {
      const factures = this.availableFactures().filter(f => this.selectedFactures().includes(f.id));
      return factures.reduce((sum, f) => {
        const montantPartiel = this.facturesMontants().get(f.id);
        return sum + (montantPartiel !== undefined && montantPartiel > 0 ? montantPartiel : f.amountTTC);
      }, 0);
    }
    // Sinon, utiliser le montant manuel depuis le signal
    return this.montantManuel();
  });

  // Methods
  resetFilters() {
    this.searchTerm.set('');
    this.filterBeneficiaire.set('');
    this.filterStatut.set('');
    this.dateMin.set('');
    this.dateMax.set('');
    this.currentPage.set(1);
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

  openForm() {
    this.isFormOpen.set(true);
    this.isEditMode.set(false);
    this.selectedFactures.set([]);
    this.facturesMontants.set(new Map());
    this.montantManuel.set(0);
    this.beneficiaryType.set('SUPPLIER');
    this.form.reset({ 
      statut: 'EN_ATTENTE', 
      type: 'NORMAL',
      montant: 0
    });
    this.updateBeneficiaryTypeValidation();
  }

  closeForm() {
    this.isFormOpen.set(false);
    this.isEditMode.set(false);
    this.editingOV.set(null);
    this.selectedFactures.set([]);
    this.facturesMontants.set(new Map());
    this.form.reset();
  }

  editOV(ov: OrdreVirement) {
    this.isFormOpen.set(true);
    this.isEditMode.set(true);
    this.editingOV.set(ov);
    this.selectedFactures.set(ov.facturesIds || []);
    
    // Déterminer le type de bénéficiaire
    if (ov.beneficiaireId) {
      this.beneficiaryType.set('SUPPLIER');
    } else {
      this.beneficiaryType.set('OTHER');
    }
    
    // Récupérer les montants partiels
    const montantsMap = new Map<string, number>();
    if (ov.facturesMontants && ov.facturesMontants.length > 0) {
      ov.facturesMontants.forEach(fm => {
        montantsMap.set(fm.factureId, fm.montant);
      });
    }
    this.facturesMontants.set(montantsMap);
    
    const montantInitial = ov.facturesIds && ov.facturesIds.length > 0 ? 0 : (ov.montant || 0);
    this.montantManuel.set(montantInitial);
    
    this.form.patchValue({
      dateOV: ov.dateOV,
      dateExecution: ov.dateExecution || '',
      beneficiaireId: ov.beneficiaireId || '',
      nomBeneficiaire: ov.nomBeneficiaire || '',
      ribBeneficiaire: ov.ribBeneficiaire,
      banqueBeneficiaire: ov.banqueBeneficiaire || '',
      banqueEmettrice: ov.banqueEmettrice,
      motif: ov.motif,
      type: ov.type || 'NORMAL',
      statut: ov.statut,
      montant: montantInitial
    });
    
    this.updateBeneficiaryTypeValidation();
  }

  onBeneficiaryTypeChange() {
    this.updateBeneficiaryTypeValidation();
    
    // Réinitialiser les champs selon le type
    if (this.beneficiaryType() === 'SUPPLIER') {
      this.form.patchValue({
        nomBeneficiaire: '',
        beneficiaireId: ''
      });
    } else {
      this.form.patchValue({
        beneficiaireId: '',
        nomBeneficiaire: ''
      });
    }
  }

  updateBeneficiaryTypeValidation() {
    const beneficiaireIdControl = this.form.get('beneficiaireId');
    const nomBeneficiaireControl = this.form.get('nomBeneficiaire');
    const montantControl = this.form.get('montant');
    
    if (this.beneficiaryType() === 'SUPPLIER') {
      beneficiaireIdControl?.setValidators([Validators.required]);
      nomBeneficiaireControl?.clearValidators();
    } else {
      beneficiaireIdControl?.clearValidators();
      nomBeneficiaireControl?.setValidators([Validators.required]);
    }
    
    // Validation du montant : requis si aucune facture n'est sélectionnée
    if (this.selectedFactures().length === 0) {
      montantControl?.setValidators([Validators.required, Validators.min(0.01)]);
    } else {
      montantControl?.clearValidators();
    }
    
    beneficiaireIdControl?.updateValueAndValidity();
    nomBeneficiaireControl?.updateValueAndValidity();
    montantControl?.updateValueAndValidity();
  }

  onFactureToggleWithValidation(factureId: string) {
    this.onFactureToggle(factureId);
    // Mettre à jour la validation du montant après avoir changé la sélection
    setTimeout(() => {
      this.updateBeneficiaryTypeValidation();
    }, 0);
  }

  onBeneficiaireChange() {
    const beneficiaireId = this.form.get('beneficiaireId')?.value;
    if (beneficiaireId) {
      const supplier = this.store.suppliers().find(s => s.id === beneficiaireId);
      if (supplier) {
        const updates: any = {
          nomBeneficiaire: supplier.name
        };
        if (supplier.rib) {
          updates.ribBeneficiaire = supplier.rib;
        }
        if (supplier.banque) {
          updates.banqueBeneficiaire = supplier.banque;
        }
        this.form.patchValue(updates);
      }
    }
  }

  onFactureMontantChange(factureId: string, event: any) {
    const value = parseFloat(event.target.value);
    const montants = new Map(this.facturesMontants());
    if (value && value > 0) {
      montants.set(factureId, value);
    } else {
      montants.delete(factureId);
    }
    this.facturesMontants.set(montants);
  }

  getFactureMontant(factureId: string): number | undefined {
    return this.facturesMontants().get(factureId);
  }

  onMontantChange(event: any) {
    const value = parseFloat(event.target.value) || 0;
    this.montantManuel.set(value);
    // Mettre à jour aussi la valeur du formulaire
    this.form.patchValue({ montant: value }, { emitEvent: false });
  }

  onFactureToggle(factureId: string) {
    const current = this.selectedFactures();
    if (current.includes(factureId)) {
      this.selectedFactures.set(current.filter(id => id !== factureId));
    } else {
      this.selectedFactures.set([...current, factureId]);
    }
  }

  isFactureSelected(factureId: string): boolean {
    return this.selectedFactures().includes(factureId);
  }

  async onSubmit() {
    // Validation selon le type de bénéficiaire
    if (this.beneficiaryType() === 'SUPPLIER' && !this.form.get('beneficiaireId')?.value) {
      this.store.showToast('Veuillez sélectionner un fournisseur', 'error');
      return;
    }
    
    if (this.beneficiaryType() === 'OTHER' && !this.form.get('nomBeneficiaire')?.value?.trim()) {
      this.store.showToast('Veuillez saisir le nom du bénéficiaire', 'error');
      return;
    }
    
    if (this.form.invalid) {
      this.store.showToast('Veuillez remplir tous les champs requis', 'error');
      return;
    }
    
    // Validation : factures ou montant manuel requis
    if (this.selectedFactures().length === 0 && (!this.form.get('montant')?.value || this.form.get('montant')?.value <= 0)) {
      this.store.showToast('Veuillez sélectionner au moins une facture ou saisir un montant manuel', 'error');
      return;
    }

    const formValue = this.form.value;
    
    // Construire la liste des factures avec montants (si des factures sont sélectionnées)
    let facturesMontants: FactureMontant[] = [];
    let facturesIds: string[] = [];
    
    if (this.selectedFactures().length > 0) {
      facturesMontants = this.selectedFactures().map(factureId => {
        const montantPartiel = this.facturesMontants().get(factureId);
        const facture = this.availableFactures().find(f => f.id === factureId);
        return {
          factureId,
          montant: montantPartiel !== undefined && montantPartiel > 0 ? montantPartiel : (facture?.amountTTC || 0)
        };
      });
      facturesIds = this.selectedFactures();
    }

    // Préparer les valeurs selon le type de bénéficiaire
    let beneficiaireIdValue: string | undefined;
    let nomBeneficiaireValue: string | undefined;

    if (this.beneficiaryType() === 'SUPPLIER') {
      // Pour un fournisseur, utiliser beneficiaireId et nomBeneficiaire sera rempli par le backend
      beneficiaireIdValue = formValue.beneficiaireId && formValue.beneficiaireId.trim() ? formValue.beneficiaireId.trim() : undefined;
      nomBeneficiaireValue = undefined; // Sera rempli par le backend depuis le fournisseur
    } else {
      // Pour une personne physique, beneficiaireId doit être undefined et nomBeneficiaire doit être rempli
      beneficiaireIdValue = undefined;
      
      // Récupérer la valeur du formulaire
      const nomBeneficiaireRaw = this.form.get('nomBeneficiaire')?.value;
      const nomBeneficiaireInput = nomBeneficiaireRaw ? String(nomBeneficiaireRaw).trim() : '';
      
      if (!nomBeneficiaireInput || nomBeneficiaireInput.length === 0) {
        // Si le nom n'est pas rempli, essayer de récupérer celui de l'ordre existant (en cas d'édition)
        if (this.isEditMode() && this.editingOV()?.nomBeneficiaire) {
          nomBeneficiaireValue = this.editingOV()!.nomBeneficiaire.trim();
        } else {
          // Si toujours pas de nom, générer une erreur avant d'envoyer
          this.store.showToast('Veuillez saisir le nom du bénéficiaire', 'error');
          return;
        }
      } else {
        nomBeneficiaireValue = nomBeneficiaireInput;
      }
    }

    // Construire l'objet sans utiliser spread pour éviter les conflits
    const ov: OrdreVirement = {
      // Pour une création, le backend génère le numeroOV automatiquement
      // Pour une mise à jour, on doit fournir le numeroOV existant
      numeroOV: this.isEditMode() && this.editingOV()?.numeroOV ? this.editingOV()!.numeroOV : '',
      dateOV: formValue.dateOV,
      dateExecution: formValue.dateExecution || undefined,
      beneficiaireId: beneficiaireIdValue === undefined || beneficiaireIdValue === null || beneficiaireIdValue === '' ? undefined : beneficiaireIdValue,
      nomBeneficiaire: nomBeneficiaireValue, // Toujours défini pour une personne physique (déjà validé plus haut)
      ribBeneficiaire: formValue.ribBeneficiaire,
      banqueBeneficiaire: formValue.banqueBeneficiaire,
      banqueEmettrice: formValue.banqueEmettrice,
      motif: formValue.motif,
      type: formValue.type || 'NORMAL',
      statut: formValue.statut || 'EN_ATTENTE',
      montant: this.calculatedMontant(),
      facturesIds: facturesIds.length > 0 ? facturesIds : undefined,
      facturesMontants: facturesMontants.length > 0 ? facturesMontants : undefined
    };

    // Debug: vérifier ce qui est envoyé
    console.log('Envoi OrdreVirement:', {
      beneficiaryType: this.beneficiaryType(),
      beneficiaireId: ov.beneficiaireId,
      nomBeneficiaire: ov.nomBeneficiaire,
      montant: ov.montant,
      formValue_nomBeneficiaire: formValue.nomBeneficiaire,
      nomBeneficiaireValue: nomBeneficiaireValue
    });

    try {
      if (this.isEditMode() && this.editingOV()?.id) {
        await this.store.updateOrdreVirement(this.editingOV()!.id!, ov);
        this.store.showToast('Ordre de virement modifié avec succès', 'success');
      } else {
        await this.store.addOrdreVirement(ov);
        this.store.showToast('Ordre de virement créé avec succès', 'success');
      }
      this.closeForm();
      await this.store.loadOrdresVirement();
    } catch (error: any) {
      console.error('Error saving ordre virement:', error);
      // Extraire le message d'erreur du backend
      let errorMessage = 'Erreur lors de la sauvegarde';
      if (error?.error?.error) {
        errorMessage = error.error.error;
      } else if (error?.error?.message) {
        errorMessage = error.error.message;
      } else if (error?.message) {
        errorMessage = error.message;
      }
      this.store.showToast(errorMessage, 'error');
    }
  }

  async executerOV(id: string) {
    if (confirm('Confirmer l\'exécution de cet ordre de virement ?')) {
      try {
        await this.store.executerOrdreVirement(id);
        await this.store.loadOrdresVirement();
      } catch (error) {
        console.error('Error executing ordre virement:', error);
      }
    }
  }

  async annulerOV(id: string) {
    if (confirm('Confirmer l\'annulation de cet ordre de virement ?')) {
      try {
        await this.store.annulerOrdreVirement(id);
        await this.store.loadOrdresVirement();
      } catch (error) {
        console.error('Error cancelling ordre virement:', error);
      }
    }
  }

  async deleteOV(id: string) {
    if (confirm('Confirmer la suppression de cet ordre de virement ?')) {
      try {
        await this.store.deleteOrdreVirement(id);
        await this.store.loadOrdresVirement();
      } catch (error) {
        console.error('Error deleting ordre virement:', error);
      }
    }
  }

  async downloadPDF(id: string) {
    try {
      await this.store.downloadOrdreVirementPDF(id);
    } catch (error) {
      console.error('Error downloading PDF:', error);
    }
  }

  getStatutClass(statut: string): string {
    switch (statut) {
      case 'EXECUTE':
        return 'inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-700 border border-emerald-200';
      case 'ANNULE':
        return 'inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold bg-red-100 text-red-700 border border-red-200';
      default:
        return 'inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold bg-amber-100 text-amber-700 border border-amber-200';
    }
  }

  getStatutLabel(statut: string): string {
    switch (statut) {
      case 'EXECUTE':
        return 'Exécuté';
      case 'ANNULE':
        return 'Annulé';
      default:
        return 'En attente';
    }
  }

  getFactureNumber(factureId: string): string {
    const facture = this.store.invoices().find(f => f.id === factureId);
    return facture?.number || factureId;
  }

  formatCurrency(amount: number): string {
    return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'MAD' }).format(amount);
  }

  isFieldInvalid(fieldName: string): boolean {
    const field = this.form.get(fieldName);
    return !!(field && field.invalid && (field.dirty || field.touched));
  }

  Math = Math;
}

