import { Component, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StoreService, Invoice, BC } from '../../services/store.service';
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
            @for (inv of filteredInvoices(); track inv.id) {
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
                        <label class="block text-xs font-bold text-blue-700 uppercase mb-1">Fournisseur</label>
                        <select formControlName="partnerId" (change)="onPartnerChange()" class="w-full p-2 border border-blue-200 rounded-lg bg-white focus:ring-2 focus:ring-blue-500/20 outline-none">
                           <option value="">Sélectionner un fournisseur</option>
                           @for (s of store.suppliers(); track s.id) {
                              <option [value]="s.id">{{ s.name }}</option>
                           }
                        </select>
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
                          <label class="block text-sm font-semibold text-slate-700 mb-1">Date Facture</label>
                          <input formControlName="date" type="date" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                        </div>
                        <div>
                          <label class="block text-sm font-semibold text-slate-700 mb-1">Date Échéance</label>
                          <input formControlName="dueDate" type="date" class="w-full px-4 py-2 border border-slate-200 rounded-lg bg-slate-50 focus:bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                          <p class="text-[10px] text-slate-400 mt-1">Calcul auto: Date + 60 jours</p>
                        </div>
                     </div>
                     
                     <div class="grid grid-cols-2 gap-4">
                        <div>
                           <label class="block text-sm font-semibold text-slate-700 mb-1">Montant HT</label>
                           <input formControlName="amountHT" type="number" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition text-right">
                        </div>
                        <div>
                           <label class="block text-sm font-semibold text-slate-700 mb-1">Montant TTC</label>
                           <input formControlName="amountTTC" type="number" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition text-right font-bold text-slate-800">
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
  store = inject(StoreService);
  fb = inject(FormBuilder);

  isFormOpen = signal(false);
  isEditMode = signal(false);
  editingId: string | null = null;
  availableBCs = signal<BC[]>([]);

  // Filters
  filterStatus = signal<'all' | 'paid' | 'pending' | 'overdue'>('all');
  searchTerm = signal('');

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
    paymentMode: ['']
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
    
    return list;
  });

  setFilter(status: 'all' | 'paid' | 'pending' | 'overdue') {
    this.filterStatus.set(status);
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
      amountTTC: 0
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
      // Auto fill amounts from BC Buying values
      const totalHT = bc.items.reduce((acc, i) => acc + (i.qtyBuy * i.priceBuyHT), 0);
      const totalTva = bc.items.reduce((acc, i) => acc + (i.qtyBuy * i.priceBuyHT * (i.tvaRate/100)), 0);
      
      this.form.patchValue({
        amountHT: totalHT,
        amountTTC: totalHT + totalTva
      });
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
      paymentMode: inv.paymentMode || ''
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

  onSubmit() {
    const val = this.form.value;
    
    // Validation manuelle des champs essentiels
    if (!val.number || !val.date || !val.partnerId) {
      this.store.showToast('Veuillez remplir tous les champs obligatoires (Numéro, Date, Fournisseur).', 'error');
      this.form.markAllAsTouched();
      return;
    }
    
    // S'assurer que dueDate est rempli
    if (!val.dueDate) {
      // Calculer automatiquement si manquant
      const date = new Date(val.date);
      date.setDate(date.getDate() + 60);
      val.dueDate = date.toISOString().split('T')[0];
    }
    
    const invoice: Invoice = {
      id: this.editingId || `fa-${Date.now()}`,
      type: 'purchase',
      ...val
    };

    if (this.isEditMode()) {
      this.store.updateInvoice(invoice);
    } else {
      this.store.addInvoice(invoice);
    }
    this.closeForm();
  }
}
