import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators, FormArray } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { StoreService, BC, LineItem, Product } from '../../services/store.service';

@Component({
  selector: 'app-bc-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="max-w-7xl mx-auto pb-20 fade-in-up">
      
      <!-- Top Action Bar -->
      <div class="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 bg-[#F8FAFC] py-4 border-b border-slate-200 mb-6">
        <div>
           <div class="flex items-center gap-2 text-sm text-slate-500 mb-1">
             <span (click)="goBack()" class="cursor-pointer hover:text-slate-800 transition">Bandes de Commandes</span>
             <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"></path></svg>
             <span class="text-slate-800 font-medium">Édition</span>
           </div>
           <h1 class="text-xl md:text-2xl font-bold text-slate-800 font-display">
             {{ isEditMode ? 'Modifier la commande' : 'Nouvelle Commande' }}
           </h1>
        </div>
        <div class="flex gap-3 w-full md:w-auto">
          <button (click)="goBack()" class="flex-1 md:flex-none px-5 py-2.5 text-slate-600 bg-white border border-slate-200 rounded-lg hover:bg-slate-50 font-medium shadow-sm transition text-center">
            Annuler
          </button>
          <button (click)="save()" class="flex-1 md:flex-none px-6 py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 shadow-lg shadow-blue-600/20 font-medium transition flex items-center justify-center gap-2">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>
            Enregistrer
          </button>
        </div>
      </div>

      <!-- Main Form Area -->
      <form [formGroup]="form" class="mt-6 lg:flex lg:gap-8 items-start">
        
        <!-- Left Column: Inputs -->
        <div class="flex-1 min-w-0 space-y-6">
          
          <!-- Card 1: Info -->
          <div class="bg-white p-4 md:p-6 rounded-xl shadow-sm border border-slate-100">
            <h2 class="text-base font-bold text-slate-800 mb-6 flex items-center gap-2">
              <span class="w-6 h-6 rounded-full bg-blue-50 text-blue-600 flex items-center justify-center text-xs font-bold">1</span>
              Informations Générales
            </h2>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4 md:gap-6">
              <div>
                <label class="block text-xs font-semibold text-slate-500 uppercase mb-1.5">Numéro BC</label>
                <input formControlName="number" type="text" class="w-full px-4 py-2.5 border border-slate-200 rounded-lg bg-slate-50 font-mono text-slate-700 focus:bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all">
              </div>
              <div>
                <label class="block text-xs font-semibold text-slate-500 uppercase mb-1.5">Date d'émission</label>
                <input formControlName="date" type="date" class="w-full px-4 py-2.5 border border-slate-200 rounded-lg bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all text-slate-700">
              </div>
              <div>
                <label class="block text-xs font-semibold text-slate-500 uppercase mb-1.5">Fournisseur</label>
                <select formControlName="supplierId" class="w-full px-4 py-2.5 border border-slate-200 rounded-lg bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all text-slate-700 cursor-pointer">
                  <option value="">Sélectionner...</option>
                  @for (s of store.suppliers(); track s.id) {
                    <option [value]="s.id">{{ s.name }}</option>
                  }
                </select>
              </div>
              <div>
                <label class="block text-xs font-semibold text-slate-500 uppercase mb-1.5">Client</label>
                <select formControlName="clientId" class="w-full px-4 py-2.5 border border-slate-200 rounded-lg bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all text-slate-700 cursor-pointer">
                  <option value="">Sélectionner...</option>
                  @for (c of store.clients(); track c.id) {
                    <option [value]="c.id">{{ c.name }}</option>
                  }
                </select>
              </div>
               <div>
                <label class="block text-xs font-semibold text-slate-500 uppercase mb-1.5">Statut Actuel</label>
                <select formControlName="status" class="w-full px-4 py-2.5 border border-slate-200 rounded-lg bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all text-slate-700">
                  <option value="draft">Brouillon</option>
                  <option value="sent">Envoyée</option>
                  <option value="completed">Validée / Terminée</option>
                </select>
              </div>
              <div>
                <label class="block text-xs font-semibold text-slate-500 uppercase mb-1.5">Mode de Paiement</label>
                <select formControlName="paymentMode" class="w-full px-4 py-2.5 border border-slate-200 rounded-lg bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all text-slate-700">
                  <option value="">Non défini</option>
                  @for (mode of activePaymentModes(); track mode.id) {
                    <option [value]="mode.name">{{ mode.name }}</option>
                  }
                </select>
              </div>
            </div>
          </div>

          <!-- Card 2: Lines -->
          <div class="bg-white rounded-xl shadow-sm border border-slate-100 overflow-hidden">
             <h2 class="text-base font-bold text-slate-800 flex items-center gap-2 p-4 md:p-6 border-b border-slate-100 bg-slate-50/50">
                <span class="w-6 h-6 rounded-full bg-blue-50 text-blue-600 flex items-center justify-center text-xs font-bold">2</span>
                Lignes d'articles
              </h2>
            <div class="overflow-x-auto">
              <table class="w-full text-sm text-left min-w-[900px]">
                <thead class="text-xs text-slate-500 uppercase bg-slate-50 border-b border-slate-200">
                  <tr>
                    <th class="p-3 w-1/3">Produit (Recherche)</th>
                    <th class="p-3 w-20">Qté</th>
                    <th class="p-3 text-right">PU Achat</th>
                    <th class="p-3 text-right">PU Vente</th>
                    <th class="p-3 w-16 text-center">TVA %</th>
                    <th class="p-3 w-20 text-center">Marge</th>
                    <th class="p-3 text-right bg-slate-50/50">Total Vente</th>
                    <th class="p-3 text-center w-10"></th>
                  </tr>
                </thead>
                <tbody formArrayName="items" class="divide-y divide-slate-100">
                  @for (item of itemsArray.controls; track item; let i = $index) {
                    <tr [formGroupName]="i" class="bg-white hover:bg-slate-50/70 transition-colors relative">
                      
                      <!-- Custom Autocomplete Product Selector -->
                      <td class="p-2 align-top">
                         <div class="relative">
                           <input type="text" 
                                  formControlName="productSearch" 
                                  (focus)="openDropdown(i)" 
                                  (blur)="closeDropdownDelayed()"
                                  placeholder="Chercher ref ou nom..."
                                  class="w-full p-2 border border-slate-200 rounded-md text-sm focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none placeholder-slate-400">
                           
                           <!-- Dropdown Results -->
                           @if (activeDropdownIndex() === i) {
                             <div class="absolute z-50 top-full left-0 w-full mt-1 bg-white border border-slate-200 rounded-lg shadow-xl max-h-48 overflow-y-auto">
                               @for (prod of filterProducts(item.value.productSearch); track prod.id) {
                                 <div (click)="selectProduct(i, prod)" class="p-2 hover:bg-blue-50 cursor-pointer border-b border-slate-50 last:border-0 flex flex-col">
                                   <span class="font-medium text-slate-800 text-sm">{{ prod.name }}</span>
                                   <div class="flex justify-between text-xs text-slate-500">
                                     <span>Ref: {{ prod.ref }}</span>
                                     <span class="font-mono">{{ prod.priceSellHT }} Dhs</span>
                                   </div>
                                 </div>
                               }
                               @if (filterProducts(item.value.productSearch).length === 0) {
                                 <div class="p-2 text-xs text-slate-400 text-center">Aucun produit trouvé</div>
                               }
                             </div>
                           }
                           <!-- Hidden ID Control -->
                           <input type="hidden" formControlName="productId">
                         </div>
                      </td>

                      <td class="p-2 align-top">
                        <input type="number" formControlName="qtyBuy" class="w-full p-2 bg-transparent border border-slate-200 rounded-md text-right focus:ring-2 focus:ring-blue-500/20 outline-none">
                      </td>
                      <td class="p-2 align-top">
                        <input type="number" 
                               formControlName="priceBuyHT" 
                               (input)="onPriceBuyChange(i)"
                               class="w-full p-2 bg-slate-50 border border-slate-200 rounded-md text-right text-slate-500 focus:bg-white transition-colors focus:ring-2 focus:ring-blue-500/20 outline-none">
                      </td>
                      <td class="p-2 align-top">
                        <input type="number" 
                               formControlName="priceSellHT" 
                               (input)="onPriceSellChange(i)"
                               class="w-full p-2 bg-transparent border border-slate-200 rounded-md text-right font-medium text-slate-700 focus:ring-2 focus:ring-blue-500/20 outline-none">
                      </td>
                       <td class="p-2 align-top">
                        <input type="number" formControlName="tvaRate" class="w-full p-2 bg-transparent border border-slate-200 rounded-md text-center focus:ring-2 focus:ring-blue-500/20 outline-none">
                      </td>
                      
                      <!-- Line Margin Input/Display -->
                      <td class="p-2 align-top text-center">
                         <input type="number" 
                                formControlName="marginPercent" 
                                (input)="onMarginChange(i)"
                                class="w-full p-2 bg-transparent border border-slate-200 rounded-md text-center focus:ring-2 focus:ring-blue-500/20 outline-none"
                                [class]="getLineMarginInputClass(calculateLineMargin(i))"
                                step="0.1"
                                placeholder="0">
                         <span class="text-xs text-slate-400">%</span>
                      </td>

                      <td class="p-2 align-top text-right font-bold text-slate-800 bg-slate-50/30 pt-4">
                        {{ (item.get('qtyBuy')?.value * item.get('priceSellHT')?.value) | number:'1.0-2' }}
                      </td>
                      <td class="p-2 align-top text-center pt-3">
                        <button type="button" (click)="removeItem(i)" class="text-slate-400 hover:text-red-500 transition-colors">
                          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                        </button>
                      </td>
                    </tr>
                  }
                </tbody>
                <tfoot class="bg-slate-50 border-t border-slate-200">
                    <tr>
                        <td colspan="8" class="p-3">
                            <button type="button" (click)="addItem()" class="w-full text-center py-2 text-sm text-blue-600 hover:bg-blue-50 rounded-lg font-semibold transition flex items-center justify-center gap-1">
                                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path></svg>
                                Ajouter une ligne
                            </button>
                        </td>
                    </tr>
                </tfoot>
              </table>
            </div>
          </div>
        </div>

        <!-- Right Column: Summary -->
        <aside class="w-full lg:w-80 lg:shrink-0 mt-8 lg:mt-0">
          <div class="lg:sticky lg:top-[152px] space-y-6">
           
           <!-- Margin KPI -->
           <div class="bg-gradient-to-br from-slate-800 to-slate-900 p-6 rounded-xl shadow-lg text-white relative overflow-hidden">
               <!-- Decorative blob -->
               <div class="absolute -top-10 -right-10 w-32 h-32 bg-white/10 rounded-full blur-2xl"></div>
               
               <h3 class="text-xs font-bold text-slate-400 uppercase tracking-wider mb-2">Rentabilité estimée</h3>
               <div class="flex items-baseline gap-2 mb-1">
                 <span class="text-4xl font-bold tracking-tight" 
                      [class.text-emerald-400]="marginPercent() >= 15"
                      [class.text-amber-400]="marginPercent() > 0 && marginPercent() < 15"
                      [class.text-red-400]="marginPercent() <= 0">
                   {{ marginPercent() | number:'1.1-1' }}%
                 </span>
                 <span class="text-sm text-slate-400">de marge</span>
               </div>
               <p class="text-sm text-slate-300 border-t border-white/10 pt-3 mt-3">
                 Profit net: <span class="font-bold text-white">{{ (sellTotal() - buyTotal()) | number:'1.2-2' }} MAD</span>
               </p>
           </div>

           <!-- Totals Panel -->
           <div class="bg-white p-6 rounded-xl shadow-sm border border-slate-100 divide-y divide-slate-100">
              
              <!-- Buying -->
              <div class="pb-4">
                 <h3 class="text-xs font-bold text-slate-400 uppercase mb-3 flex items-center gap-2">
                   <span class="w-2 h-2 rounded-full bg-slate-300"></span> Achat Fournisseur
                 </h3>
                 <div class="flex justify-between text-sm mb-1">
                   <span class="text-slate-600">Total HT</span>
                   <span class="font-medium text-slate-900">{{ buyTotal() | number:'1.2-2' }}</span>
                 </div>
                 <div class="flex justify-between text-sm mb-1">
                   <span class="text-slate-600">TVA Recup.</span>
                   <span class="font-medium text-slate-900">{{ buyTva() | number:'1.2-2' }}</span>
                 </div>
                 <div class="flex justify-between text-base font-bold mt-2 pt-2 border-t border-slate-50">
                   <span class="text-slate-800">Total TTC</span>
                   <span class="text-slate-900">{{ (buyTotal() + buyTva()) | number:'1.2-2' }} Dhs</span>
                 </div>
              </div>

              <!-- Selling -->
              <div class="pt-4">
                 <h3 class="text-xs font-bold text-blue-500 uppercase mb-3 flex items-center gap-2">
                   <span class="w-2 h-2 rounded-full bg-blue-500"></span> Vente Client
                 </h3>
                 <div class="flex justify-between text-sm mb-1">
                   <span class="text-slate-600">Total HT</span>
                   <span class="font-medium text-blue-900">{{ sellTotal() | number:'1.2-2' }}</span>
                 </div>
                 <div class="flex justify-between text-sm mb-1">
                   <span class="text-slate-600">TVA Collectée</span>
                   <span class="font-medium text-blue-900">{{ sellTva() | number:'1.2-2' }}</span>
                 </div>
                 <div class="flex justify-between text-lg font-bold mt-2 pt-2 border-t border-slate-50 bg-blue-50/50 -mx-6 px-6 py-3 rounded-b-lg -mb-6">
                   <span class="text-blue-800">Net à Payer</span>
                   <span class="text-blue-900">{{ (sellTotal() + sellTva()) | number:'1.2-2' }} Dhs</span>
                 </div>
              </div>
           </div>

        </div>
       </aside>

      </form>
    </div>
  `
})
export class BcFormComponent implements OnInit {
  fb = inject(FormBuilder);
  store = inject(StoreService);
  router = inject(Router);
  route = inject(ActivatedRoute);

  form!: FormGroup;
  isEditMode = false;
  bcId: string | null = null;

  // Reactivity for totals
  buyTotal = signal(0);
  buyTva = signal(0);
  sellTotal = signal(0);
  sellTva = signal(0);
  marginPercent = signal(0);
  
  // UX State for Autocomplete
  activeDropdownIndex = signal<number | null>(null);

  // Active payment modes
  activePaymentModes = computed(() => this.store.paymentModes().filter(m => m.active));

  constructor() {
    this.form = this.fb.group({
      number: ['BC-2025-' + Math.floor(Math.random() * 1000), Validators.required],
      date: [new Date().toISOString().split('T')[0], Validators.required],
      clientId: ['', Validators.required],
      supplierId: ['', Validators.required],
      status: ['draft', Validators.required],
      paymentMode: [''],
      items: this.fb.array([])
    });

    // Recalculate totals on form change
    this.form.valueChanges.subscribe(() => this.calculateTotals());
  }

  get itemsArray() {
    return this.form.get('items') as FormArray;
  }

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEditMode = true;
      this.bcId = id;
      const bc = this.store.bcs().find(b => b.id === id);
      if (bc) {
        this.form.patchValue({
          number: bc.number,
          date: bc.date,
          clientId: bc.clientId,
          supplierId: bc.supplierId,
          status: bc.status,
          paymentMode: (bc as any).paymentMode || ''
        });
        bc.items.forEach(item => {
          this.itemsArray.push(this.createItemGroup(item));
        });
        this.calculateTotals();
      }
    } else {
      // Add one empty line by default
      this.addItem();
    }
  }

  createItemGroup(data?: any): FormGroup {
    const priceBuy = data?.priceBuyHT || 0;
    const priceSell = data?.priceSellHT || 0;
    const margin = priceBuy > 0 ? ((priceSell - priceBuy) / priceBuy) * 100 : 0;
    
    return this.fb.group({
      productId: [data?.productId || ''],
      productSearch: [data?.name || ''], // Holds the search text
      qtyBuy: [data?.qtyBuy || 1, [Validators.required, Validators.min(1)]],
      qtySell: [data?.qtySell || 1], // Usually linked to qtyBuy
      priceBuyHT: [priceBuy, [Validators.required, Validators.min(0)]],
      priceSellHT: [priceSell, [Validators.required, Validators.min(0)]],
      marginPercent: [margin],
      tvaRate: [data?.tvaRate || 20],
      updatingFromPrice: [false], // Flag to prevent circular updates
      updatingFromMargin: [false] // Flag to prevent circular updates
    });
  }

  addItem() {
    this.itemsArray.push(this.createItemGroup());
  }

  removeItem(index: number) {
    this.itemsArray.removeAt(index);
    this.calculateTotals();
  }

  // --- Autocomplete Logic ---

  openDropdown(index: number) {
    this.activeDropdownIndex.set(index);
  }

  closeDropdownDelayed() {
    // Delay to allow click event on item to fire before closing
    setTimeout(() => {
      this.activeDropdownIndex.set(null);
    }, 200);
  }

  filterProducts(term: string): Product[] {
    if (!term) return this.store.products();
    const t = term.toLowerCase();
    return this.store.products().filter(p => 
      p.name.toLowerCase().includes(t) || p.ref.toLowerCase().includes(t)
    );
  }

  selectProduct(index: number, product: Product) {
    const group = this.itemsArray.at(index);
    group.patchValue({
      productId: product.id,
      productSearch: product.name,
      priceBuyHT: product.priceBuyHT,
      priceSellHT: product.priceSellHT,
      marginPercent: product.priceBuyHT > 0 ? ((product.priceSellHT - product.priceBuyHT) / product.priceBuyHT) * 100 : 0,
      updatingFromPrice: false,
      updatingFromMargin: false
    });
    // activeDropdownIndex is cleared by blur, but we can clear it here too
    this.activeDropdownIndex.set(null);
  }

  // --- Calculation Logic ---

  calculateLineMargin(index: number): number {
    const group = this.itemsArray.at(index);
    const buy = group.get('priceBuyHT')?.value || 0;
    const sell = group.get('priceSellHT')?.value || 0;
    
    if (buy <= 0) return 0;
    return ((sell - buy) / buy) * 100;
  }

  // Calculer le prix de vente à partir de la marge
  onMarginChange(index: number) {
    const group = this.itemsArray.at(index);
    if (group.get('updatingFromMargin')?.value) return;
    
    const margin = parseFloat(group.get('marginPercent')?.value) || 0;
    const priceBuy = parseFloat(group.get('priceBuyHT')?.value) || 0;
    
    if (priceBuy > 0) {
      group.get('updatingFromPrice')?.setValue(true);
      const priceSell = priceBuy * (1 + margin / 100);
      group.get('priceSellHT')?.setValue(parseFloat(priceSell.toFixed(2)), { emitEvent: false });
      group.get('updatingFromPrice')?.setValue(false);
      this.calculateTotals();
    }
  }

  // Calculer la marge à partir du prix de vente
  onPriceSellChange(index: number) {
    const group = this.itemsArray.at(index);
    if (group.get('updatingFromPrice')?.value) return;
    
    const priceBuy = parseFloat(group.get('priceBuyHT')?.value) || 0;
    const priceSell = parseFloat(group.get('priceSellHT')?.value) || 0;
    
    if (priceBuy > 0) {
      group.get('updatingFromMargin')?.setValue(true);
      const margin = ((priceSell - priceBuy) / priceBuy) * 100;
      group.get('marginPercent')?.setValue(parseFloat(margin.toFixed(2)), { emitEvent: false });
      group.get('updatingFromMargin')?.setValue(false);
      this.calculateTotals();
    }
  }

  // Quand le prix d'achat change, recalculer la marge (si le prix de vente existe)
  // ou recalculer le prix de vente (si la marge existe)
  onPriceBuyChange(index: number) {
    const group = this.itemsArray.at(index);
    if (group.get('updatingFromPrice')?.value || group.get('updatingFromMargin')?.value) return;
    
    const priceBuy = parseFloat(group.get('priceBuyHT')?.value) || 0;
    const priceSell = parseFloat(group.get('priceSellHT')?.value) || 0;
    const margin = parseFloat(group.get('marginPercent')?.value) || 0;
    
    if (priceBuy > 0) {
      // Si la marge est définie (non nulle), recalculer le prix de vente
      if (margin !== 0 && margin !== null && !isNaN(margin)) {
        group.get('updatingFromPrice')?.setValue(true);
        const newPriceSell = priceBuy * (1 + margin / 100);
        group.get('priceSellHT')?.setValue(parseFloat(newPriceSell.toFixed(2)), { emitEvent: false });
        group.get('updatingFromPrice')?.setValue(false);
      } 
      // Sinon, si le prix de vente existe, recalculer la marge
      else if (priceSell > 0) {
        group.get('updatingFromMargin')?.setValue(true);
        const newMargin = ((priceSell - priceBuy) / priceBuy) * 100;
        group.get('marginPercent')?.setValue(parseFloat(newMargin.toFixed(2)), { emitEvent: false });
        group.get('updatingFromMargin')?.setValue(false);
      }
      this.calculateTotals();
    }
  }

  getLineMarginInputClass(margin: number): string {
    if (margin >= 15) return 'text-emerald-600 font-semibold';
    if (margin > 0 && margin < 15) return 'text-amber-600 font-medium';
    if (margin <= 0) return 'text-red-600 font-medium';
    return '';
  }

  getLineMarginClass(margin: number): string {
    if (margin >= 15) return 'bg-emerald-100 text-emerald-800 text-xs px-2 py-1 rounded-lg font-bold border border-emerald-200';
    if (margin > 0) return 'bg-amber-100 text-amber-800 text-xs px-2 py-1 rounded-lg font-bold border border-amber-200';
    return 'bg-red-100 text-red-800 text-xs px-2 py-1 rounded-lg font-bold border border-red-200';
  }

  calculateTotals() {
    let bTot = 0, bTva = 0, sTot = 0, sTva = 0;

    this.itemsArray.controls.forEach(control => {
      const val = control.value;
      const q = val.qtyBuy || 0;
      const pb = val.priceBuyHT || 0;
      const ps = val.priceSellHT || 0;
      const tva = (val.tvaRate || 0) / 100;

      bTot += q * pb;
      bTva += q * pb * tva;
      sTot += q * ps;
      sTva += q * ps * tva;
    });

    this.buyTotal.set(bTot);
    this.buyTva.set(bTva);
    this.sellTotal.set(sTot);
    this.sellTva.set(sTva);

    if (bTot > 0) {
      this.marginPercent.set( ((sTot - bTot) / bTot) * 100 );
    } else {
      this.marginPercent.set(0);
    }
  }

  save() {
    // Vérifier les validations de base (nombre, date, client, fournisseur)
    if (!this.form.get('number')?.value || !this.form.get('date')?.value || 
        !this.form.get('clientId')?.value || !this.form.get('supplierId')?.value) {
      alert('Veuillez remplir tous les champs obligatoires (Numéro, Date, Client, Fournisseur)');
      return;
    }

    // Vérifier qu'il y a au moins un article avec les informations essentielles
    const items = this.form.get('items')?.value || [];
    const validItems = items.filter((i: any) => 
      i.productSearch && i.priceBuyHT > 0 && i.priceSellHT > 0 && i.qtyBuy > 0
    );
    
    if (validItems.length === 0) {
      alert('Veuillez ajouter au moins un article avec un produit, quantité et prix valides');
      return;
    }
    
    const formVal = this.form.value;
    const lineItems: LineItem[] = validItems.map((i: any) => ({
      productId: i.productId || `prod-${Date.now()}-${Math.random()}`,
      ref: i.productSearch || 'REF',
      name: i.productSearch || 'Produit sans nom',
      unit: 'U',
      qtyBuy: i.qtyBuy,
      qtySell: i.qtyBuy, 
      priceBuyHT: i.priceBuyHT,
      priceSellHT: i.priceSellHT,
      tvaRate: i.tvaRate || 20
    }));

    const bcData: BC = {
      id: this.bcId || `bc-${Date.now()}`,
      number: formVal.number,
      date: formVal.date,
      clientId: formVal.clientId,
      supplierId: formVal.supplierId,
      status: formVal.status,
      paymentMode: formVal.paymentMode || undefined,
      items: lineItems
    };

    if (this.isEditMode) {
      this.store.updateBC(bcData);
    } else {
      this.store.addBC(bcData);
    }

    this.router.navigate(['/bc']);
  }

  goBack() {
    this.router.navigate(['/bc']);
  }
}