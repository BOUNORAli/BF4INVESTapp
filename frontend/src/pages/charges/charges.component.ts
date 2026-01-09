import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { StoreService, Charge } from '../../services/store.service';

@Component({
  selector: 'app-charges',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  template: `
    <div class="space-y-6 fade-in-up pb-10">
      <!-- Header -->
      <div class="flex flex-col md:flex-row justify-between items-start md:items-end gap-4 border-b border-slate-200/60 pb-6">
        <div>
          <h1 class="text-2xl md:text-3xl font-bold text-slate-800 font-display">Charges</h1>
          <p class="text-slate-500 mt-2 text-sm">Gestion des charges (loyer, salaires…) avec statut et classification fiscale.</p>
        </div>
        <div class="flex gap-3 w-full md:w-auto">
          <button (click)="refresh()" class="flex-1 md:flex-none px-4 py-2 bg-white border border-slate-200 text-slate-700 rounded-lg hover:bg-slate-50 transition font-medium">
            Actualiser
          </button>
          <button (click)="openCreate()" class="flex-1 md:flex-none px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition font-medium shadow-lg shadow-blue-600/20">
            Nouvelle Charge
          </button>
        </div>
      </div>

      <!-- Filtres -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
        <div class="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 mb-4">
          <h2 class="text-lg font-bold text-slate-800">Filtres</h2>
          <button (click)="resetFilters()" class="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg hover:bg-slate-200 transition font-medium">
            Réinitialiser
          </button>
        </div>
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <div>
            <label class="block text-sm font-semibold text-slate-700 mb-1">Date début</label>
            <input type="date" [(ngModel)]="dateFrom" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
          </div>
          <div>
            <label class="block text-sm font-semibold text-slate-700 mb-1">Date fin</label>
            <input type="date" [(ngModel)]="dateTo" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
          </div>
          <div>
            <label class="block text-sm font-semibold text-slate-700 mb-1">Statut</label>
            <select [(ngModel)]="statusFilter" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
              <option value="all">Tous</option>
              <option value="PREVUE">Prévue</option>
              <option value="PAYEE">Payée</option>
            </select>
          </div>
          <div>
            <label class="block text-sm font-semibold text-slate-700 mb-1">Fiscalité</label>
            <select [(ngModel)]="imposableFilter" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
              <option value="all">Tout</option>
              <option value="imposable">Imposable</option>
              <option value="non">Non imposable</option>
            </select>
          </div>
        </div>
        <div class="mt-4">
          <label class="block text-sm font-semibold text-slate-700 mb-1">Recherche</label>
          <input type="text" [(ngModel)]="searchTerm" placeholder="Libellé, catégorie, notes..." class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
        </div>
      </div>

      <!-- Table -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div class="p-4 border-b border-slate-100 bg-slate-50/50 flex flex-col md:flex-row justify-between items-center gap-4">
          <div class="text-sm text-slate-600">
            Affichage de {{ (currentPage() - 1) * pageSize() + 1 }} à {{ Math.min(currentPage() * pageSize(), filteredCharges().length) }} sur {{ filteredCharges().length }} résultats
          </div>
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
        </div>

        @if (paginatedCharges().length === 0) {
          <div class="p-12 text-center text-slate-500">
            <p>Aucune charge trouvée.</p>
          </div>
        } @else {
          <div class="overflow-x-auto">
            <table class="w-full text-sm min-w-[900px]">
              <thead class="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th class="px-4 py-3 text-left font-semibold text-slate-700">Libellé</th>
                  <th class="px-4 py-3 text-left font-semibold text-slate-700 hidden md:table-cell">Catégorie</th>
                  <th class="px-4 py-3 text-left font-semibold text-slate-700">Échéance</th>
                  <th class="px-4 py-3 text-right font-semibold text-slate-700">Montant</th>
                  <th class="px-4 py-3 text-left font-semibold text-slate-700">Fiscalité</th>
                  <th class="px-4 py-3 text-left font-semibold text-slate-700">Statut</th>
                  <th class="px-4 py-3 text-left font-semibold text-slate-700 hidden md:table-cell">Paiement</th>
                  <th class="px-4 py-3 text-left font-semibold text-slate-700 hidden md:table-cell">Notes</th>
                  <th class="px-4 py-3 text-right font-semibold text-slate-700">Actions</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                @for (c of paginatedCharges(); track c.id) {
                  <tr class="hover:bg-slate-50">
                    <td class="px-4 py-3">
                      <div class="font-semibold text-slate-800">{{ c.libelle }}</div>
                    </td>
                    <td class="px-4 py-3 hidden md:table-cell text-slate-600">{{ c.categorie || '-' }}</td>
                    <td class="px-4 py-3 text-slate-700">{{ c.dateEcheance }}</td>
                    <td class="px-4 py-3 text-right font-bold text-red-600">{{ c.montant | number:'1.2-2' }} MAD</td>
                    <td class="px-4 py-3">
                      <div class="flex flex-col gap-1">
                        <span class="px-2 py-1 rounded text-xs font-medium"
                              [class.bg-emerald-50]="c.imposable"
                              [class.text-emerald-700]="c.imposable"
                              [class.bg-slate-100]="!c.imposable"
                              [class.text-slate-700]="!c.imposable">
                          {{ c.imposable ? 'Imposable' : 'Non imposable' }}
                        </span>
                        @if (c.imposable && c.tauxImposition) {
                          <span class="text-xs text-slate-600 font-semibold">
                            {{ (c.tauxImposition * 100) | number:'1.0-0' }}%
                          </span>
                        }
                      </div>
                    </td>
                    <td class="px-4 py-3">
                      <span class="px-2 py-1 rounded text-xs font-medium"
                            [class.bg-blue-50]="c.statut === 'PREVUE'"
                            [class.text-blue-700]="c.statut === 'PREVUE'"
                            [class.bg-emerald-50]="c.statut === 'PAYEE'"
                            [class.text-emerald-700]="c.statut === 'PAYEE'">
                        {{ c.statut === 'PAYEE' ? 'Payée' : 'Prévue' }}
                      </span>
                    </td>
                    <td class="px-4 py-3 hidden md:table-cell text-slate-600">
                      {{ c.datePaiement || '-' }}
                    </td>
                    <td class="px-4 py-3 hidden md:table-cell text-slate-500 text-xs">
                      {{ c.notes || '-' }}
                    </td>
                    <td class="px-4 py-3 text-right">
                      <div class="flex items-center justify-end gap-2">
                        @if (c.statut === 'PREVUE') {
                          <button (click)="openPay(c)" class="p-2 text-slate-400 hover:text-emerald-600 hover:bg-emerald-50 rounded-full transition-all" title="Marquer payée">
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
                            </svg>
                          </button>
                        }
                        <button (click)="openEdit(c)" class="p-2 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-full transition-all" title="Modifier">
                          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path>
                          </svg>
                        </button>
                        @if (c.statut === 'PREVUE') {
                          <button (click)="delete(c)" class="p-2 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-full transition-all" title="Supprimer">
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                            </svg>
                          </button>
                        }
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <!-- Pagination -->
          <div class="p-4 border-t border-slate-200 bg-slate-50 flex items-center justify-between">
            <div class="text-xs text-slate-500">
              Page {{ currentPage() }} sur {{ totalPages() || 1 }}
            </div>
            <div class="flex items-center gap-2">
              <button (click)="prevPage()" [disabled]="currentPage() === 1"
                      class="p-2 border border-slate-200 rounded-lg hover:bg-white disabled:opacity-50 disabled:cursor-not-allowed">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"></path>
                </svg>
              </button>
              <button (click)="nextPage()" [disabled]="currentPage() === totalPages() || totalPages() === 0"
                      class="p-2 border border-slate-200 rounded-lg hover:bg-white disabled:opacity-50 disabled:cursor-not-allowed">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"></path>
                </svg>
              </button>
            </div>
          </div>
        }
      </div>

      <!-- Modal Create/Edit -->
      @if (isFormOpen()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center" aria-modal="true">
          <div (click)="closeForm()" class="fixed inset-0 bg-slate-900/40 backdrop-blur-sm"></div>
          <div class="relative bg-white rounded-2xl shadow-2xl max-w-xl w-full mx-2 md:mx-4 overflow-hidden flex flex-col">
            <div class="flex items-center justify-between p-4 md:p-6 border-b border-slate-100 bg-gradient-to-r from-blue-50 to-indigo-50">
              <div>
                <h2 class="text-lg md:text-xl font-bold text-slate-800">{{ isEditMode() ? 'Modifier' : 'Nouvelle' }} Charge</h2>
                <p class="text-xs md:text-sm text-slate-600 mt-1">Renseignez les informations de la charge.</p>
              </div>
              <button (click)="closeForm()" class="text-slate-400 hover:text-slate-600 transition min-h-[44px] min-w-[44px] flex items-center justify-center">
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
              </button>
            </div>

            <form [formGroup]="form" (ngSubmit)="save()" class="p-4 md:p-6 space-y-4">
              <div>
                <label class="block text-sm font-semibold text-slate-700 mb-1">Libellé <span class="text-red-500">*</span></label>
                <input formControlName="libelle" type="text" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
              </div>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label class="block text-sm font-semibold text-slate-700 mb-1">Catégorie (optionnel)</label>
                  <input formControlName="categorie" type="text" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
                </div>
                <div>
                  <label class="block text-sm font-semibold text-slate-700 mb-1">Montant <span class="text-red-500">*</span></label>
                  <input formControlName="montant" type="number" step="0.01" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none text-right">
                </div>
              </div>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label class="block text-sm font-semibold text-slate-700 mb-1">Date échéance <span class="text-red-500">*</span></label>
                  <input formControlName="dateEcheance" type="date" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
                </div>
                <div class="flex items-end">
                  <label class="flex items-center gap-3 cursor-pointer w-full px-4 py-2 border border-slate-200 rounded-lg bg-slate-50">
                    <input type="checkbox" formControlName="imposable" (change)="onImposableChange()" class="w-5 h-5 text-blue-600 border-slate-300 rounded focus:ring-blue-500 focus:ring-2">
                    <div>
                      <div class="text-sm font-semibold text-slate-800">Imposable</div>
                      <div class="text-xs text-slate-500">Déductible fiscalement</div>
                    </div>
                  </label>
                </div>
              </div>
              @if (form.get('imposable')?.value) {
                <div>
                  <label class="block text-sm font-semibold text-slate-700 mb-1">
                    Taux d'imposition <span class="text-red-500">*</span>
                    <span class="text-xs text-slate-500 font-normal ml-2">(ex: 10% = 0.10, 20% = 0.20)</span>
                  </label>
                  <div class="flex items-center gap-2">
                    <input formControlName="tauxImposition" type="number" step="0.01" min="0" max="1" 
                           [class.border-red-300]="isFieldInvalid('tauxImposition')"
                           class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none text-right">
                    <span class="text-sm text-slate-600 font-medium whitespace-nowrap">
                      @if (form.get('tauxImposition')?.value) {
                        ({{ (form.get('tauxImposition')?.value * 100) | number:'1.0-0' }}%)
                      }
                    </span>
                  </div>
                  @if (isFieldInvalid('tauxImposition')) {
                    <p class="text-xs text-red-500 mt-1">Le taux d'imposition est requis si la charge est imposable</p>
                  }
                </div>
              }
              <div>
                <label class="block text-sm font-semibold text-slate-700 mb-1">Notes (optionnel)</label>
                <textarea formControlName="notes" rows="3" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none"></textarea>
              </div>

              <div class="flex gap-3 pt-2">
                <button type="button" (click)="closeForm()" class="flex-1 px-4 py-2.5 bg-white border border-slate-200 text-slate-700 font-medium rounded-lg hover:bg-slate-50 transition min-h-[44px]">
                  Annuler
                </button>
                <button type="submit" [disabled]="form.invalid || isSaving()" class="flex-1 px-4 py-2.5 bg-blue-600 text-white font-bold rounded-lg hover:bg-blue-700 transition shadow-lg shadow-blue-600/20 min-h-[44px] disabled:opacity-50">
                  {{ isEditMode() ? 'Mettre à jour' : 'Créer' }}
                </button>
              </div>
            </form>
          </div>
        </div>
      }

      <!-- Modal Payer -->
      @if (isPayOpen()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center" aria-modal="true">
          <div (click)="closePay()" class="fixed inset-0 bg-slate-900/40 backdrop-blur-sm"></div>
          <div class="relative bg-white rounded-2xl shadow-2xl max-w-md w-full mx-2 md:mx-4 overflow-hidden flex flex-col">
            <div class="flex items-center justify-between p-4 md:p-6 border-b border-slate-100 bg-gradient-to-r from-emerald-50 to-teal-50">
              <div>
                <h2 class="text-lg md:text-xl font-bold text-slate-800">Marquer payée</h2>
                <p class="text-xs md:text-sm text-slate-600 mt-1">{{ payTarget()?.libelle }}</p>
              </div>
              <button (click)="closePay()" class="text-slate-400 hover:text-slate-600 transition min-h-[44px] min-w-[44px] flex items-center justify-center">
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12\"></path></svg>
              </button>
            </div>

            <div class="p-4 md:p-6 space-y-4">
              <div>
                <label class="block text-sm font-semibold text-slate-700 mb-1">Date paiement</label>
                <input type="date" [(ngModel)]="payDate" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-emerald-500/20 outline-none">
              </div>
              <div class="flex gap-3 pt-2">
                <button (click)="closePay()" class="flex-1 px-4 py-2.5 bg-white border border-slate-200 text-slate-700 font-medium rounded-lg hover:bg-slate-50 transition min-h-[44px]">
                  Annuler
                </button>
                <button (click)="confirmPay()" [disabled]="isSaving()" class="flex-1 px-4 py-2.5 bg-emerald-600 text-white font-bold rounded-lg hover:bg-emerald-700 transition shadow-lg shadow-emerald-600/20 min-h-[44px] disabled:opacity-50">
                  Confirmer
                </button>
              </div>
            </div>
          </div>
        </div>
      }
    </div>
  `
})
export class ChargesComponent implements OnInit {
  store = inject(StoreService);
  private fb = inject(FormBuilder);

  // Filtres
  dateFrom = '';
  dateTo = '';
  statusFilter: 'all' | 'PREVUE' | 'PAYEE' = 'all';
  imposableFilter: 'all' | 'imposable' | 'non' = 'all';
  searchTerm = '';

  // Pagination
  currentPage = signal<number>(1);
  pageSize = signal<number>(20);
  Math = Math;

  totalPages = computed(() => Math.ceil(this.filteredCharges().length / this.pageSize()));

  filteredCharges = computed(() => {
    let list = this.store.charges();

    // Date filter
    const from = this.dateFrom ? new Date(this.dateFrom) : null;
    const to = this.dateTo ? new Date(this.dateTo) : null;
    if (from) {
      list = list.filter(c => c.dateEcheance && new Date(c.dateEcheance) >= from);
    }
    if (to) {
      const toEnd = new Date(this.dateTo + 'T23:59:59');
      list = list.filter(c => c.dateEcheance && new Date(c.dateEcheance) <= toEnd);
    }

    // Status
    if (this.statusFilter !== 'all') {
      list = list.filter(c => c.statut === this.statusFilter);
    }

    // Imposable
    if (this.imposableFilter === 'imposable') {
      list = list.filter(c => c.imposable);
    } else if (this.imposableFilter === 'non') {
      list = list.filter(c => !c.imposable);
    }

    // Search
    const q = this.searchTerm.trim().toLowerCase();
    if (q) {
      list = list.filter(c =>
        (c.libelle || '').toLowerCase().includes(q) ||
        (c.categorie || '').toLowerCase().includes(q) ||
        (c.notes || '').toLowerCase().includes(q)
      );
    }

    return list;
  });

  paginatedCharges = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize();
    return this.filteredCharges().slice(start, start + this.pageSize());
  });

  // Form
  isFormOpen = signal<boolean>(false);
  isEditMode = signal<boolean>(false);
  isSaving = signal<boolean>(false);
  editingId = signal<string | null>(null);

  form = this.fb.group({
    libelle: ['', [Validators.required]],
    categorie: [''],
    montant: [0, [Validators.required, Validators.min(0.01)]],
    dateEcheance: ['', [Validators.required]],
    imposable: [true],
    tauxImposition: [0.20, [Validators.min(0.01), Validators.max(1)]],
    notes: ['']
  });

  // Pay modal
  isPayOpen = signal<boolean>(false);
  payTarget = signal<Charge | null>(null);
  payDate = '';

  async ngOnInit() {
    // Charger toutes les charges
    await this.store.loadCharges();
  }

  async refresh() {
    await this.store.loadCharges();
  }

  resetFilters() {
    this.dateFrom = '';
    this.dateTo = '';
    this.statusFilter = 'all';
    this.imposableFilter = 'all';
    this.searchTerm = '';
    this.currentPage.set(1);
  }

  prevPage() {
    if (this.currentPage() > 1) this.currentPage.set(this.currentPage() - 1);
  }

  nextPage() {
    if (this.currentPage() < this.totalPages()) this.currentPage.set(this.currentPage() + 1);
  }

  openCreate() {
    this.isEditMode.set(false);
    this.editingId.set(null);
    this.form.reset({
      libelle: '',
      categorie: '',
      montant: 0,
      dateEcheance: '',
      imposable: true,
      tauxImposition: 0.20,
      notes: ''
    });
    this.updateTauxImpositionValidation();
    this.isFormOpen.set(true);
  }

  openEdit(c: Charge) {
    this.isEditMode.set(true);
    this.editingId.set(c.id || null);
    this.form.reset({
      libelle: c.libelle,
      categorie: c.categorie || '',
      montant: c.montant,
      dateEcheance: c.dateEcheance,
      imposable: c.imposable,
      tauxImposition: c.tauxImposition || (c.imposable ? 0.20 : null),
      notes: c.notes || ''
    });
    this.updateTauxImpositionValidation();
    this.isFormOpen.set(true);
  }

  closeForm() {
    this.isFormOpen.set(false);
  }

  onImposableChange() {
    this.updateTauxImpositionValidation();
  }

  updateTauxImpositionValidation() {
    const imposable = this.form.get('imposable')?.value;
    const tauxImpositionControl = this.form.get('tauxImposition');
    
    if (imposable) {
      tauxImpositionControl?.setValidators([Validators.required, Validators.min(0.01), Validators.max(1)]);
    } else {
      tauxImpositionControl?.clearValidators();
      tauxImpositionControl?.setValue(null);
    }
    tauxImpositionControl?.updateValueAndValidity();
  }

  isFieldInvalid(fieldName: string): boolean {
    const field = this.form.get(fieldName);
    return !!(field && field.invalid && (field.dirty || field.touched));
  }

  async save() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.store.showToast('Veuillez compléter les champs requis', 'error');
      return;
    }

    const v = this.form.value;
    const payload: Charge = {
      libelle: (v.libelle || '').trim(),
      categorie: (v.categorie || '').trim() || undefined,
      montant: Number(v.montant || 0),
      dateEcheance: v.dateEcheance || '',
      statut: 'PREVUE',
      imposable: v.imposable !== false,
      tauxImposition: v.imposable ? (v.tauxImposition ? Number(v.tauxImposition) : undefined) : undefined,
      notes: (v.notes || '').trim() || undefined
    };

    this.isSaving.set(true);
    try {
      const id = this.editingId();
      if (id) {
        await this.store.updateCharge(id, payload);
      } else {
        await this.store.addCharge(payload);
      }
      this.closeForm();
      await this.store.loadCharges();
    } finally {
      this.isSaving.set(false);
    }
  }

  async delete(c: Charge) {
    if (!c.id) return;
    if (!confirm(`Supprimer la charge "${c.libelle}" ?`)) return;
    await this.store.deleteCharge(c.id);
    await this.store.loadCharges();
  }

  openPay(c: Charge) {
    this.payTarget.set(c);
    this.payDate = new Date().toISOString().split('T')[0];
    this.isPayOpen.set(true);
  }

  closePay() {
    this.isPayOpen.set(false);
    this.payTarget.set(null);
  }

  async confirmPay() {
    const target = this.payTarget();
    if (!target?.id) return;

    this.isSaving.set(true);
    try {
      await this.store.payerCharge(target.id, this.payDate || undefined);
      this.closePay();
      await this.store.loadCharges();
    } finally {
      this.isSaving.set(false);
    }
  }
}


