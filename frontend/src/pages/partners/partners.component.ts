import { Component, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators, FormsModule } from '@angular/forms';
import { StoreService, Client, Supplier } from '../../services/store.service';

@Component({
  selector: 'app-partners',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  template: `
    <div class="space-y-8 fade-in-up pb-10 relative">
      
      <!-- Header -->
      <div class="flex flex-col md:flex-row justify-between items-center gap-4 bg-white p-6 rounded-2xl shadow-sm border border-slate-100">
        <div>
           <h1 class="text-2xl font-bold text-slate-800 font-display">Partenaires</h1>
           <p class="text-slate-500 text-sm mt-1">Gérez votre base de clients et fournisseurs.</p>
        </div>
        
        <div class="flex flex-col md:flex-row gap-4 items-center w-full md:w-auto">
          
          <!-- Search -->
          <div class="relative w-full md:w-56">
             <span class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
               <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path></svg>
             </span>
             <input type="text" [(ngModel)]="searchTerm" placeholder="Nom, ICE..." class="w-full pl-9 pr-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
          </div>

          <!-- Tab Switcher -->
          <div class="bg-slate-100 p-1 rounded-xl flex gap-1 w-full md:w-auto">
            <button (click)="switchTab('clients')" 
              [class.bg-white]="activeTab() === 'clients'" 
              [class.text-blue-600]="activeTab() === 'clients'"
              [class.shadow-sm]="activeTab() === 'clients'"
              class="flex-1 md:flex-none px-6 py-2 rounded-lg text-sm font-semibold text-slate-500 transition-all duration-200">
              Clients
            </button>
            <button (click)="switchTab('suppliers')" 
              [class.bg-white]="activeTab() === 'suppliers'" 
              [class.text-blue-600]="activeTab() === 'suppliers'"
              [class.shadow-sm]="activeTab() === 'suppliers'"
              class="flex-1 md:flex-none px-6 py-2 rounded-lg text-sm font-semibold text-slate-500 transition-all duration-200">
              Fournisseurs
            </button>
          </div>

          <button (click)="openForm()" class="w-full md:w-auto px-5 py-2.5 bg-slate-800 text-white rounded-lg hover:bg-slate-900 shadow-lg shadow-slate-800/20 font-medium transition flex items-center justify-center gap-2">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path></svg>
            <span class="hidden md:inline">Nouveau</span>
            <span class="md:hidden">Ajouter</span>
          </button>
        </div>
      </div>

      <!-- Clients Grid -->
      @if (activeTab() === 'clients') {
        <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6 animate-[fadeIn_0.3s_ease-out]">
          @for (client of filteredClients(); track client.id) {
            <div class="bg-white rounded-2xl p-6 shadow-sm border border-slate-100 hover-card group relative" [attr.data-item-id]="client.id">
               
               <div class="absolute top-4 right-4 opacity-0 group-hover:opacity-100 transition-opacity flex gap-2">
                 <button (click)="editClient(client)" class="p-1.5 text-blue-600 bg-blue-50 rounded hover:bg-blue-100">
                   <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"></path></svg>
                 </button>
                 <button (click)="deleteClient(client.id)" class="p-1.5 text-red-600 bg-red-50 rounded hover:bg-red-100">
                   <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path></svg>
                 </button>
               </div>

               <div class="flex justify-between items-start mb-4">
                 <div class="w-12 h-12 rounded-xl bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center text-white font-bold text-xl shadow-lg shadow-blue-500/20">
                   {{ client.name.charAt(0) }}
                 </div>
               </div>
               
               <h3 class="text-lg font-bold text-slate-800 mb-1">{{ client.name }}</h3>
               <div class="flex items-center gap-2 text-xs text-slate-500 mb-4">
                 <span class="bg-slate-100 px-2 py-1 rounded">ICE: {{ client.ice }}</span>
               </div>

               <div class="space-y-3 pt-4 border-t border-slate-100">
                  <div class="flex items-start gap-3">
                    <svg class="w-4 h-4 text-slate-400 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"></path></svg>
                    <span class="text-sm text-slate-600">{{ client.contact }}</span>
                  </div>
                  <div class="flex items-start gap-3">
                    <svg class="w-4 h-4 text-slate-400 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z"></path></svg>
                    <span class="text-sm text-slate-600">{{ client.phone }}</span>
                  </div>
                   <div class="flex items-start gap-3">
                    <svg class="w-4 h-4 text-slate-400 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z"></path><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z"></path></svg>
                    <span class="text-sm text-slate-600 truncate">{{ client.address }}</span>
                  </div>
               </div>
            </div>
          }
          @if (filteredClients().length === 0) {
            <div class="col-span-full py-12 text-center text-slate-500">
               Aucun client trouvé.
            </div>
          }
        </div>
      }

      <!-- Suppliers Grid -->
      @if (activeTab() === 'suppliers') {
        <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6 animate-[fadeIn_0.3s_ease-out]">
          @for (sup of filteredSuppliers(); track sup.id) {
            <div class="bg-white rounded-2xl p-6 shadow-sm border border-slate-100 hover-card group relative" [attr.data-item-id]="sup.id">
               
               <div class="absolute top-4 right-4 opacity-0 group-hover:opacity-100 transition-opacity flex gap-2">
                 <button (click)="editSupplier(sup)" class="p-1.5 text-blue-600 bg-blue-50 rounded hover:bg-blue-100">
                   <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"></path></svg>
                 </button>
                 <button (click)="deleteSupplier(sup.id)" class="p-1.5 text-red-600 bg-red-50 rounded hover:bg-red-100">
                   <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path></svg>
                 </button>
               </div>

               <div class="flex justify-between items-start mb-4">
                 <div class="w-12 h-12 rounded-xl bg-gradient-to-br from-emerald-500 to-teal-600 flex items-center justify-center text-white font-bold text-xl shadow-lg shadow-emerald-500/20">
                   {{ sup.name.charAt(0) }}
                 </div>
               </div>
               
               <h3 class="text-lg font-bold text-slate-800 mb-1">{{ sup.name }}</h3>
               <div class="flex items-center gap-2 text-xs text-slate-500 mb-4">
                 <span class="bg-slate-100 px-2 py-1 rounded">ICE: {{ sup.ice }}</span>
               </div>
               @if (isRegulariteFiscaleExpired(sup)) {
                 <div class="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg flex items-center gap-2">
                   <svg class="w-5 h-5 text-red-600 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                     <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path>
                   </svg>
                   <div class="flex-1">
                     <p class="text-sm font-semibold text-red-800">Régularité fiscale expirée</p>
                     <p class="text-xs text-red-600">Date de régularité fiscale de plus de 6 mois</p>
                   </div>
                 </div>
               }

               <div class="space-y-3 pt-4 border-t border-slate-100">
                  <div class="flex items-start gap-3">
                    <svg class="w-4 h-4 text-slate-400 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"></path></svg>
                    <span class="text-sm text-slate-600">{{ sup.contact }}</span>
                  </div>
                  <div class="flex items-start gap-3">
                    <svg class="w-4 h-4 text-slate-400 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z"></path></svg>
                    <span class="text-sm text-slate-600">{{ sup.phone }}</span>
                  </div>
                  <div class="flex items-start gap-3">
                    <svg class="w-4 h-4 text-slate-400 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z"></path><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z"></path></svg>
                    <span class="text-sm text-slate-600 truncate">{{ sup.address }}</span>
                  </div>
                  @if (sup.dateRegulariteFiscale) {
                    <div class="flex items-start gap-3">
                      <svg class="w-4 h-4 text-slate-400 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"></path></svg>
                      <div class="flex-1">
                        <span class="text-sm text-slate-600">Régularité fiscale: </span>
                        <span class="text-sm font-medium" [class.text-red-600]="isRegulariteFiscaleExpired(sup)" [class.text-slate-700]="!isRegulariteFiscaleExpired(sup)">
                          {{ formatDate(sup.dateRegulariteFiscale) }}
                        </span>
                      </div>
                    </div>
                  }
               </div>
            </div>
          }
          @if (filteredSuppliers().length === 0) {
            <div class="col-span-full py-12 text-center text-slate-500">
               Aucun fournisseur trouvé.
            </div>
          }
        </div>
      }

      <!-- SLIDE OVER FORM -->
      @if (isFormOpen()) {
         <div class="fixed inset-0 z-50 flex justify-end" aria-modal="true">
            <!-- Backdrop -->
            <div (click)="closeForm()" class="fixed inset-0 bg-slate-900/40 backdrop-blur-sm transition-opacity"></div>
            
            <!-- Panel -->
            <div class="relative w-full md:w-[500px] bg-white h-full shadow-2xl flex flex-col transform transition-transform animate-[slideInRight_0.3s_ease-out]">
               <div class="flex items-center justify-between p-6 border-b border-slate-100 bg-slate-50/50">
                  <h2 class="text-xl font-bold text-slate-800">
                    {{ isEditMode() ? 'Modifier' : 'Nouveau' }} {{ activeTab() === 'clients' ? 'Client' : 'Fournisseur' }}
                  </h2>
                  <button (click)="closeForm()" class="text-slate-400 hover:text-slate-600 transition">
                     <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                  </button>
               </div>
               
               <form [formGroup]="form" (ngSubmit)="onSubmit()" class="flex-1 overflow-y-auto p-6 space-y-5">
                  <div>
                    <label class="block text-sm font-semibold text-slate-700 mb-1">Nom de l'entreprise</label>
                    <input formControlName="name" type="text" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                  </div>
                  <div>
                    <label class="block text-sm font-semibold text-slate-700 mb-1">ICE (Identifiant Fiscal)</label>
                    <input formControlName="ice" type="text" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                  </div>
                  @if (activeTab() === 'suppliers') {
                    <div>
                      <label class="block text-sm font-semibold text-slate-700 mb-1">Date de régularité fiscale</label>
                      <input formControlName="dateRegulariteFiscale" type="date" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                      <p class="text-xs text-slate-500 mt-1">Date de la dernière régularité fiscale du fournisseur</p>
                    </div>
                  }
                  <div>
                    <label class="block text-sm font-semibold text-slate-700 mb-1">Contact Principal</label>
                    <input formControlName="contact" type="text" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                  </div>
                  <div class="grid grid-cols-2 gap-4">
                    <div>
                        <label class="block text-sm font-semibold text-slate-700 mb-1">Téléphone</label>
                        <input formControlName="phone" type="text" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                    </div>
                    <div>
                        <label class="block text-sm font-semibold text-slate-700 mb-1">Email</label>
                        <input formControlName="email" type="email" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                    </div>
                  </div>
                  <div>
                    <label class="block text-sm font-semibold text-slate-700 mb-1">Adresse</label>
                    <textarea formControlName="address" rows="3" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition"></textarea>
                  </div>
               </form>

               <div class="p-6 border-t border-slate-100 bg-slate-50/50 flex gap-3">
                  <button (click)="closeForm()" class="flex-1 px-4 py-2 bg-white border border-slate-200 text-slate-700 font-medium rounded-lg hover:bg-slate-50 transition">Annuler</button>
                  <button (click)="onSubmit()" [disabled]="form.invalid" class="flex-1 px-4 py-2 bg-slate-800 text-white font-bold rounded-lg hover:bg-slate-900 transition shadow-lg disabled:opacity-50">
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
export class PartnersComponent {
  store = inject(StoreService);
  fb = inject(FormBuilder);
  
  activeTab = signal<'clients' | 'suppliers'>('clients');
  isFormOpen = signal(false);
  isEditMode = signal(false);
  editingId: string | null = null;
  searchTerm = signal('');

  form: FormGroup = this.fb.group({
    name: ['', Validators.required],
    ice: ['', Validators.required],
    contact: ['', Validators.required],
    phone: [''],
    email: ['', [Validators.email]],
    address: [''],
    dateRegulariteFiscale: [''] // Uniquement pour les fournisseurs
  });

  filteredClients = computed(() => {
    const term = this.searchTerm().toLowerCase();
    return this.store.clients().filter(c => 
      c.name.toLowerCase().includes(term) || 
      c.ice.includes(term) || 
      c.contact.toLowerCase().includes(term)
    );
  });

  filteredSuppliers = computed(() => {
    const term = this.searchTerm().toLowerCase();
    return this.store.suppliers().filter(s => 
      s.name.toLowerCase().includes(term) || 
      s.ice.includes(term) || 
      s.contact.toLowerCase().includes(term)
    );
  });

  switchTab(tab: 'clients' | 'suppliers') {
    this.activeTab.set(tab);
    this.closeForm();
  }

  openForm() {
    this.isEditMode.set(false);
    this.editingId = null;
    this.form.reset();
    this.isFormOpen.set(true);
  }

  closeForm() {
    this.isFormOpen.set(false);
  }

  editClient(client: Client) {
    this.activeTab.set('clients');
    this.isEditMode.set(true);
    this.editingId = client.id;
    this.form.patchValue(client);
    this.isFormOpen.set(true);
  }

  async deleteClient(id: string) {
    if (confirm('Êtes-vous sûr de vouloir supprimer ce client ? Cette action est irréversible.')) {
      try {
        await this.store.deleteClient(id);
      } catch (error) {
        // Error already handled in store
      }
    }
  }

  editSupplier(supplier: Supplier) {
    this.activeTab.set('suppliers');
    this.isEditMode.set(true);
    this.editingId = supplier.id;
    this.form.patchValue(supplier);
    this.isFormOpen.set(true);
  }

  async deleteSupplier(id: string) {
    if (confirm('Êtes-vous sûr de vouloir supprimer ce fournisseur ? Cette action est irréversible.')) {
      try {
        await this.store.deleteSupplier(id);
      } catch (error) {
        // Error already handled in store
      }
    }
  }

  onSubmit() {
    if (this.form.invalid) return;

    const val = this.form.value;
    
    if (this.activeTab() === 'clients') {
      const client: Client = {
        id: this.editingId || `c-${Date.now()}`,
        ...val
      };
      if (this.isEditMode()) {
        this.store.updateClient(client);
      } else {
        this.store.addClient(client);
      }
    } else {
      const supplier: Supplier = {
        id: this.editingId || `s-${Date.now()}`,
        ...val
      };
      if (this.isEditMode()) {
        this.store.updateSupplier(supplier);
      } else {
        this.store.addSupplier(supplier);
      }
    }
    
    this.closeForm();
  }

  // Vérifie si la date de régularité fiscale est expirée (plus de 6 mois)
  isRegulariteFiscaleExpired(supplier: Supplier): boolean {
    if (!supplier.dateRegulariteFiscale) {
      return false; // Pas de date = pas d'alerte
    }

    const dateRegularite = new Date(supplier.dateRegulariteFiscale);
    const now = new Date();
    
    // Calculer la différence en millisecondes
    const diffMs = now.getTime() - dateRegularite.getTime();
    
    // Convertir en jours
    const diffDays = diffMs / (1000 * 60 * 60 * 24);
    
    // 6 mois = environ 180 jours (on utilise 180 pour être précis)
    return diffDays > 180;
  }

  // Formate une date au format français
  formatDate(dateString: string): string {
    if (!dateString) return '';
    const date = new Date(dateString);
    return date.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' });
  }
}
