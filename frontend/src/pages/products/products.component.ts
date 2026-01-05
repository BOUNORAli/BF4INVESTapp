import { Component, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators, FormsModule } from '@angular/forms';
import { StoreService, Product } from '../../services/store.service';
import { matchesFlexibleSearch } from '../../utils/product-search.util';

@Component({
  selector: 'app-products',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  template: `
    <div class="space-y-6 fade-in-up relative pb-10">
      <div class="flex flex-col md:flex-row justify-between items-center bg-white p-6 rounded-xl shadow-sm border border-slate-100 gap-4">
        <div>
           <h1 class="text-2xl font-bold text-slate-800 font-display">Catalogue Produits</h1>
           <p class="text-sm text-slate-500 mt-1">Gérez votre base de données articles.</p>
        </div>
        <div class="flex flex-col md:flex-row gap-3 w-full md:w-auto">
          <div class="relative">
            <span class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
               <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path></svg>
            </span>
            <input type="text" [(ngModel)]="searchTerm" placeholder="Chercher produit..." class="w-full md:w-64 pl-9 pr-4 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all outline-none">
          </div>
          <button (click)="openForm()" class="px-5 py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 shadow-lg shadow-blue-600/20 font-medium transition flex items-center justify-center gap-2">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6"></path></svg>
            Nouveau Produit
          </button>
        </div>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
        @for (prod of filteredProducts(); track prod.id) {
          <div class="bg-white rounded-xl shadow-sm hover:shadow-xl transition-all border border-slate-100 overflow-hidden group hover:-translate-y-1 relative" [attr.data-item-id]="prod.id">
            
             <!-- Delete Action (Hover) -->
             <button (click)="deleteProduct(prod.id)" class="absolute top-2 right-2 z-10 p-1.5 bg-white/90 text-red-500 rounded-full shadow-sm opacity-0 group-hover:opacity-100 transition-opacity hover:bg-red-50">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
             </button>

            <!-- Product Image Area -->
            <div class="h-40 bg-slate-50 relative overflow-hidden">
               @if (prod.imageUrl) {
                 <img [src]="prod.imageUrl" [alt]="prod.name" class="w-full h-full object-cover">
               } @else {
                 <div class="h-full flex items-center justify-center">
                   <svg class="w-16 h-16 text-slate-200 group-hover:text-blue-200 transition-colors transform group-hover:scale-110 duration-500" fill="currentColor" viewBox="0 0 24 24"><path d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" /></svg>
                 </div>
               }
               <div class="absolute inset-0 bg-gradient-to-t from-black/10 to-transparent pointer-events-none"></div>
               <span class="absolute top-3 left-3 bg-white/90 backdrop-blur px-2 py-1 text-xs font-bold text-slate-700 rounded shadow-sm border border-slate-100 uppercase tracking-wide">
                 {{ prod.ref }}
               </span>
            </div>

            <div class="p-5">
              <div class="mb-4">
                 <h3 class="text-lg font-bold text-slate-800 leading-tight mb-1 truncate">{{ prod.name }}</h3>
                 <p class="text-xs text-slate-500 uppercase font-semibold">Unité : {{ prod.unit }}</p>
              </div>
              
              <div class="space-y-3 pt-3 border-t border-slate-100">
                <div class="flex justify-between items-center text-sm">
                  <span class="text-slate-500">Prix Achat</span>
                  <span class="font-medium text-slate-700">{{ prod.priceBuyHT }} MAD</span>
                </div>
                <div class="flex justify-between items-center text-sm">
                  <span class="text-slate-500">Prix Vente</span>
                  <span class="font-bold text-blue-600">{{ prod.priceSellHT }} MAD</span>
                </div>
                <div class="flex justify-between items-center text-sm pt-2 border-t border-slate-100">
                  <span class="text-slate-500">Stock</span>
                  <span [class]="getStockClass(prod.stock ?? 0)" class="font-bold">
                    {{ prod.stock ?? 0 }} {{ prod.unit }}
                  </span>
                </div>
              </div>

              <!-- Margin Bar -->
              <div class="mt-4 pt-3 border-t border-slate-100">
                 <div class="flex justify-between items-center mb-1">
                    <span class="text-xs font-semibold text-slate-400">Marge</span>
                    <span class="text-xs font-bold text-emerald-600">
                      {{ getMargin(prod) | number:'1.0-1' }}%
                    </span>
                 </div>
                 <div class="w-full bg-slate-100 rounded-full h-1.5">
                    <div class="bg-emerald-500 h-1.5 rounded-full" [style.width.%]="getMargin(prod) > 100 ? 100 : getMargin(prod)"></div>
                 </div>
              </div>

              <div class="mt-5 flex gap-2">
                <button (click)="editProduct(prod)" class="flex-1 py-2 text-sm text-slate-600 bg-slate-50 border border-slate-200 rounded-lg hover:bg-white hover:text-blue-600 hover:border-blue-200 transition-all font-medium">Éditer</button>
              </div>
            </div>
          </div>
        }
        @if (filteredProducts().length === 0) {
          <div class="col-span-full py-12 text-center text-slate-500">
            Aucun produit ne correspond à votre recherche.
          </div>
        }
      </div>

      <!-- SLIDE OVER FORM -->
      @if (isFormOpen()) {
         <div class="fixed inset-0 z-50 flex justify-end" aria-modal="true">
            <!-- Backdrop -->
            <div (click)="closeForm()" class="fixed inset-0 bg-slate-900/40 backdrop-blur-sm transition-opacity"></div>
            
            <!-- Panel -->
            <div class="relative w-full md:w-[500px] bg-white h-full shadow-2xl flex flex-col transform transition-transform animate-[slideInRight_0.3s_ease-out]">
               <div class="flex items-center justify-between p-6 border-b border-slate-100 bg-slate-50/50">
                  <h2 class="text-xl font-bold text-slate-800">
                    {{ isEditMode() ? 'Modifier le' : 'Nouveau' }} Produit
                  </h2>
                  <button (click)="closeForm()" class="text-slate-400 hover:text-slate-600 transition">
                     <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                  </button>
               </div>
               
               <form [formGroup]="form" (ngSubmit)="onSubmit()" class="flex-1 overflow-y-auto p-6 space-y-5">
                  <!-- Image Upload Section -->
                  <div>
                    <label class="block text-sm font-semibold text-slate-700 mb-2">Image du Produit</label>
                    @if (imagePreview()) {
                      <div class="relative mb-3">
                        <img [src]="imagePreview()!" alt="Aperçu" class="w-full h-48 object-cover rounded-lg border border-slate-200">
                        <button type="button" (click)="removeImage()" class="absolute top-2 right-2 p-2 bg-red-500 text-white rounded-full hover:bg-red-600 transition shadow-lg">
                          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                        </button>
                      </div>
                    }
                    <label for="product-image-input" class="flex flex-col items-center justify-center w-full h-32 border-2 border-dashed border-slate-300 rounded-lg cursor-pointer hover:bg-slate-50 transition-colors">
                      <div class="flex flex-col items-center justify-center pt-5 pb-6">
                        <svg class="w-10 h-10 mb-3 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"></path>
                        </svg>
                        <p class="mb-2 text-sm text-slate-500">
                          <span class="font-semibold">Cliquez pour uploader</span> ou glissez-déposez
                        </p>
                        <p class="text-xs text-slate-500">PNG, JPG, WEBP (MAX. 2MB)</p>
                      </div>
                      <input id="product-image-input" type="file" accept="image/*" (change)="onImageSelect($event)" class="hidden">
                    </label>
                  </div>

                  <div>
                    <label class="block text-sm font-semibold text-slate-700 mb-1">Référence Article</label>
                    <input formControlName="ref" type="text" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition uppercase placeholder-slate-400" placeholder="EX: CIM-001">
                  </div>
                  <div>
                    <label class="block text-sm font-semibold text-slate-700 mb-1">Désignation</label>
                    <input formControlName="name" type="text" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                  </div>
                  <div>
                    <label class="block text-sm font-semibold text-slate-700 mb-1">Unité</label>
                    <select formControlName="unit" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition bg-white">
                      <option value="U">Unité (U)</option>
                      <option value="KG">Kilogramme (KG)</option>
                      <option value="M2">Mètre Carré (M2)</option>
                      <option value="M3">Mètre Cube (M3)</option>
                      <option value="Sac">Sac</option>
                      <option value="Palette">Palette</option>
                      <option value="Litre">Litre</option>
                    </select>
                  </div>
                  <div class="grid grid-cols-2 gap-4 pt-4 border-t border-slate-100">
                    <div>
                        <label class="block text-sm font-semibold text-slate-700 mb-1">Prix Achat HT</label>
                        <input formControlName="priceBuyHT" type="number" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition text-right">
                    </div>
                    <div>
                        <label class="block text-sm font-semibold text-slate-700 mb-1">Prix Vente HT</label>
                        <input formControlName="priceSellHT" type="number" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition text-right font-bold text-slate-800">
                    </div>
                  </div>
                  <div>
                    <label class="block text-sm font-semibold text-slate-700 mb-1">Quantité en Stock</label>
                    <input formControlName="stock" type="number" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition text-right" placeholder="0">
                    <p class="text-xs text-slate-400 mt-1">Quantité actuellement disponible en stock</p>
                  </div>
                  <div class="bg-blue-50 p-4 rounded-lg">
                    <div class="flex justify-between text-sm">
                       <span class="text-blue-700 font-medium">Marge estimée:</span>
                       <span class="font-bold text-blue-800">{{ calculateFormMargin() | number:'1.1-1' }}%</span>
                    </div>
                  </div>
               </form>

               <div class="p-6 border-t border-slate-100 bg-slate-50/50 flex gap-3">
                  <button (click)="closeForm()" class="flex-1 px-4 py-2 bg-white border border-slate-200 text-slate-700 font-medium rounded-lg hover:bg-slate-50 transition">Annuler</button>
                  <button (click)="onSubmit()" [disabled]="form.invalid" class="flex-1 px-4 py-2 bg-blue-600 text-white font-bold rounded-lg hover:bg-blue-700 transition shadow-lg disabled:opacity-50 shadow-blue-600/20">
                    {{ isEditMode() ? 'Mettre à jour' : 'Ajouter Produit' }}
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
export class ProductsComponent {
  store = inject(StoreService);
  fb = inject(FormBuilder);
  
  isFormOpen = signal(false);
  isEditMode = signal(false);
  editingId: string | null = null;
  searchTerm = signal('');
  selectedImageFile: File | null = null;
  imagePreview = signal<string | null>(null);

  form: FormGroup = this.fb.group({
    ref: ['', Validators.required],
    name: ['', Validators.required],
    unit: ['U', Validators.required],
    priceBuyHT: [0, [Validators.required, Validators.min(0)]],
    priceSellHT: [0, [Validators.required, Validators.min(0)]],
    stock: [0, [Validators.min(0)]]
  });

  filteredProducts = computed(() => {
    const term = this.searchTerm();
    if (!term || term.trim() === '') {
      return this.store.products();
    }
    return this.store.products().filter(p => 
      matchesFlexibleSearch({ name: p.name, ref: p.ref }, term)
    );
  });

  getMargin(prod: Product) {
     if (prod.priceBuyHT === 0) return 0;
     return ((prod.priceSellHT - prod.priceBuyHT) / prod.priceBuyHT) * 100;
  }

  getStockClass(stock: number): string {
    if (stock < 0) return 'text-red-600';
    if (stock === 0) return 'text-red-500';
    if (stock < 10) return 'text-amber-600';
    return 'text-emerald-600';
  }

  calculateFormMargin() {
    const buy = this.form.get('priceBuyHT')?.value || 0;
    const sell = this.form.get('priceSellHT')?.value || 0;
    if (buy <= 0) return 0;
    return ((sell - buy) / buy) * 100;
  }

  openForm() {
    this.isEditMode.set(false);
    this.editingId = null;
    this.form.reset({ unit: 'U', priceBuyHT: 0, priceSellHT: 0, stock: 0 });
    this.selectedImageFile = null;
    this.imagePreview.set(null);
    this.isFormOpen.set(true);
  }

  closeForm() {
    this.isFormOpen.set(false);
  }

  editProduct(prod: Product) {
    this.isEditMode.set(true);
    this.editingId = prod.id;
    this.form.patchValue(prod);
    this.selectedImageFile = null;
    this.imagePreview.set(prod.imageUrl || null);
    this.isFormOpen.set(true);
  }

  async deleteProduct(id: string) {
    if (confirm('Êtes-vous sûr de vouloir supprimer ce produit du catalogue ? Cette action est irréversible.')) {
      try {
        await this.store.deleteProduct(id);
      } catch (error) {
        // Error already handled in store
      }
    }
  }

  async onImageSelect(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      const file = input.files[0];
      
      // Valider le type de fichier
      if (!file.type.startsWith('image/')) {
        alert('Veuillez sélectionner une image valide');
        return;
      }
      
      // Valider la taille (max 2MB)
      if (file.size > 2 * 1024 * 1024) {
        alert('L\'image ne doit pas dépasser 2MB');
        return;
      }
      
      this.selectedImageFile = file;
      
      // Créer la prévisualisation
      const reader = new FileReader();
      reader.onload = (e) => {
        this.imagePreview.set(e.target?.result as string);
      };
      reader.readAsDataURL(file);
    }
  }

  removeImage() {
    this.selectedImageFile = null;
    this.imagePreview.set(null);
    // Réinitialiser l'input file
    const fileInput = document.getElementById('product-image-input') as HTMLInputElement;
    if (fileInput) {
      fileInput.value = '';
    }
  }

  private async fileToDataUrl(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.readAsDataURL(file);
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = error => reject(error);
    });
  }

  async onSubmit() {
    if (this.form.invalid) return;

    const val = this.form.value;
    const product: Product = {
      id: this.editingId || `p-${Date.now()}`,
      ...val
    };

    // Ajouter l'image si un fichier a été sélectionné
    if (this.selectedImageFile) {
      try {
        product.imageUrl = await this.fileToDataUrl(this.selectedImageFile);
      } catch (error) {
        console.error('Erreur lors de la conversion de l\'image:', error);
        alert('Erreur lors du traitement de l\'image');
        return;
      }
    } else if (this.imagePreview()) {
      // Conserver l'image existante si aucune nouvelle image n'est sélectionnée
      product.imageUrl = this.imagePreview() || undefined;
    }

    if (this.isEditMode()) {
      await this.store.updateProduct(product);
    } else {
      await this.store.addProduct(product);
    }
    this.closeForm();
  }
}
