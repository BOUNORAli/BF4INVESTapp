import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators, FormArray } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { StoreService, BC, LigneAchat, LigneVente, ClientVente, Product } from '../../services/store.service';

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
          
          <!-- Card 1: Informations Générales -->
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
                <label class="block text-xs font-semibold text-slate-500 uppercase mb-1.5">Statut</label>
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

          <!-- Card 2: Lignes d'Achat (communes) -->
          <div class="bg-white rounded-xl shadow-sm border border-slate-100 overflow-hidden">
            <h2 class="text-base font-bold text-slate-800 flex items-center gap-2 p-4 md:p-6 border-b border-slate-100 bg-orange-50/50">
              <span class="w-6 h-6 rounded-full bg-orange-100 text-orange-600 flex items-center justify-center text-xs font-bold">2</span>
              <span class="text-orange-800">Articles Achetés</span>
              <span class="text-xs text-orange-600 font-normal ml-2">(auprès du fournisseur)</span>
            </h2>
            <div class="overflow-x-auto">
              <table class="w-full text-sm text-left min-w-[700px]">
                <thead class="text-xs text-slate-500 uppercase bg-slate-50 border-b border-slate-200">
                  <tr>
                    <th class="p-3 w-2/5">Produit</th>
                    <th class="p-3 w-20">Qté</th>
                    <th class="p-3 text-right">Prix Achat HT</th>
                    <th class="p-3 w-16 text-center">TVA %</th>
                    <th class="p-3 text-right bg-orange-50/50">Total HT</th>
                    <th class="p-3 text-center w-10"></th>
                  </tr>
                </thead>
                <tbody formArrayName="lignesAchat" class="divide-y divide-slate-100">
                  @for (item of lignesAchatArray.controls; track item; let i = $index) {
                    <tr [formGroupName]="i" class="bg-white hover:bg-slate-50/70 transition-colors">
                      <td class="p-2 align-top">
                        <div class="relative">
                          <input type="text" 
                                 formControlName="productSearch" 
                                 (focus)="openDropdown('achat', i)" 
                                 (blur)="closeDropdownDelayed()"
                                 placeholder="Chercher ref ou nom..."
                                 class="w-full p-2 border border-slate-200 rounded-md text-sm focus:ring-2 focus:ring-orange-500/20 focus:border-orange-500 outline-none placeholder-slate-400">
                          @if (activeDropdownType() === 'achat' && activeDropdownIndex() === i) {
                            <div class="absolute z-50 top-full left-0 w-full mt-1 bg-white border border-slate-200 rounded-lg shadow-xl max-h-48 overflow-y-auto">
                              @for (prod of filterProducts(item.value.productSearch); track prod.id) {
                                <div (mousedown)="selectProductAchat(i, prod)" class="p-2 hover:bg-orange-50 cursor-pointer border-b border-slate-50 last:border-0 flex flex-col">
                                  <span class="font-medium text-slate-800 text-sm">{{ prod.name }}</span>
                                  <div class="flex justify-between text-xs text-slate-500">
                                    <span>Ref: {{ prod.ref }}</span>
                                    <span class="font-mono">{{ prod.priceBuyHT }} Dhs</span>
                                  </div>
                                </div>
                              }
                              @if (filterProducts(item.value.productSearch).length === 0) {
                                <div class="p-2 text-xs text-slate-400 text-center">Aucun produit trouvé</div>
                              }
                            </div>
                          }
                        </div>
                      </td>
                      <td class="p-2 align-top">
                        <input type="number" formControlName="quantiteAchetee" (input)="calculateTotals()" class="w-full p-2 border border-slate-200 rounded-md text-right focus:ring-2 focus:ring-orange-500/20 outline-none">
                      </td>
                      <td class="p-2 align-top">
                        <input type="number" formControlName="prixAchatUnitaireHT" (input)="calculateTotals()" class="w-full p-2 border border-slate-200 rounded-md text-right focus:ring-2 focus:ring-orange-500/20 outline-none">
                      </td>
                      <td class="p-2 align-top">
                        <input type="number" formControlName="tva" (input)="calculateTotals()" class="w-full p-2 border border-slate-200 rounded-md text-center focus:ring-2 focus:ring-orange-500/20 outline-none">
                      </td>
                      <td class="p-2 align-top text-right font-bold text-orange-700 bg-orange-50/30 pt-4">
                        {{ getAchatLineTotal(i) | number:'1.2-2' }}
                      </td>
                      <td class="p-2 align-top text-center pt-3">
                        <button type="button" (click)="removeLigneAchat(i)" class="text-slate-400 hover:text-red-500 transition-colors">
                          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                        </button>
                      </td>
                    </tr>
                  }
                </tbody>
                <tfoot class="bg-slate-50 border-t border-slate-200">
                  <tr>
                    <td colspan="6" class="p-3">
                      <button type="button" (click)="addLigneAchat()" class="w-full text-center py-2 text-sm text-orange-600 hover:bg-orange-50 rounded-lg font-semibold transition flex items-center justify-center gap-1">
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path></svg>
                        Ajouter un article
                      </button>
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </div>

          <!-- Card 3: Clients et Ventes -->
          <div class="bg-white rounded-xl shadow-sm border border-slate-100 overflow-hidden">
            <div class="flex items-center justify-between p-4 md:p-6 border-b border-slate-100 bg-blue-50/50">
              <h2 class="text-base font-bold text-slate-800 flex items-center gap-2">
                <span class="w-6 h-6 rounded-full bg-blue-100 text-blue-600 flex items-center justify-center text-xs font-bold">3</span>
                <span class="text-blue-800">Clients et Ventes</span>
                <span class="px-2 py-0.5 bg-blue-100 text-blue-700 text-xs rounded-full font-bold ml-2">{{ clientsVenteArray.length }} client(s)</span>
              </h2>
              <button type="button" (click)="addClientVente()" class="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-semibold hover:bg-blue-700 transition flex items-center gap-2">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path></svg>
                Ajouter un client
              </button>
            </div>
            
            <div formArrayName="clientsVente" class="divide-y divide-slate-200">
              @for (clientForm of clientsVenteArray.controls; track clientForm; let clientIdx = $index) {
                <div [formGroupName]="clientIdx" class="p-4 md:p-6 bg-white hover:bg-slate-50/30 transition-colors">
                  <!-- Header du bloc client -->
                  <div class="flex items-center justify-between mb-4">
                    <div class="flex items-center gap-3">
                      <div class="w-10 h-10 rounded-full bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center text-white font-bold text-sm shadow-lg shadow-blue-500/30">
                        {{ clientIdx + 1 }}
                      </div>
                      <div class="flex-1">
                        <select formControlName="clientId" class="px-4 py-2.5 border border-slate-200 rounded-lg bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all text-slate-700 cursor-pointer font-medium min-w-[200px]">
                          <option value="">Sélectionner un client...</option>
                          @for (c of store.clients(); track c.id) {
                            <option [value]="c.id">{{ c.name }}</option>
                          }
                        </select>
                      </div>
                    </div>
                    <div class="flex items-center gap-4">
                      <div class="text-right">
                        <div class="text-xs text-slate-500 uppercase">Total Client</div>
                        <div class="text-lg font-bold text-blue-600">{{ getClientTotal(clientIdx) | number:'1.2-2' }} MAD</div>
                      </div>
                      @if (clientsVenteArray.length > 1) {
                        <button type="button" (click)="removeClientVente(clientIdx)" class="p-2 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors">
                          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path></svg>
                        </button>
                      }
                    </div>
                  </div>

                  <!-- Tableau des lignes de vente du client -->
                  <div class="overflow-x-auto border border-slate-200 rounded-lg">
                    <table class="w-full text-sm text-left min-w-[650px]">
                      <thead class="text-xs text-slate-500 uppercase bg-slate-50 border-b border-slate-200">
                        <tr>
                          <th class="p-3 w-2/5">Produit</th>
                          <th class="p-3 w-20">Qté Vendue</th>
                          <th class="p-3 text-right">Prix Vente HT</th>
                          <th class="p-3 w-16 text-center">TVA %</th>
                          <th class="p-3 w-20 text-center">Marge</th>
                          <th class="p-3 text-right bg-blue-50/50">Total HT</th>
                          <th class="p-3 w-10"></th>
                        </tr>
                      </thead>
                      <tbody formArrayName="lignesVente" class="divide-y divide-slate-100">
                        @for (ligneForm of getLignesVenteArray(clientIdx).controls; track ligneForm; let ligneIdx = $index) {
                          <tr [formGroupName]="ligneIdx" class="bg-white hover:bg-slate-50/70 transition-colors">
                            <td class="p-2 align-top">
                              <div class="relative">
                                <input type="text" 
                                       formControlName="productSearch" 
                                       (focus)="openDropdown('vente-' + clientIdx, ligneIdx)" 
                                       (blur)="closeDropdownDelayed()"
                                       placeholder="Chercher produit..."
                                       class="w-full p-2 border border-slate-200 rounded-md text-sm focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none placeholder-slate-400">
                                @if (activeDropdownType() === 'vente-' + clientIdx && activeDropdownIndex() === ligneIdx) {
                                  <div class="absolute z-50 top-full left-0 w-full mt-1 bg-white border border-slate-200 rounded-lg shadow-xl max-h-48 overflow-y-auto">
                                    @for (prod of getProduitsFromAchat(); track prod.produitRef) {
                                      <div (mousedown)="selectProductVente(clientIdx, ligneIdx, prod)" class="p-2 hover:bg-blue-50 cursor-pointer border-b border-slate-50 last:border-0 flex flex-col">
                                        <span class="font-medium text-slate-800 text-sm">{{ prod.designation }}</span>
                                        <span class="text-xs text-slate-500">Ref: {{ prod.produitRef }} - Achat: {{ prod.prixAchatUnitaireHT }} Dhs</span>
                                      </div>
                                    }
                                    @if (getProduitsFromAchat().length === 0) {
                                      <div class="p-2 text-xs text-slate-400 text-center">Ajoutez d'abord des articles dans la section Achat</div>
                                    }
                                  </div>
                                }
                              </div>
                            </td>
                            <td class="p-2 align-top">
                              <input type="number" formControlName="quantiteVendue" (input)="calculateTotals()" class="w-full p-2 border border-slate-200 rounded-md text-right focus:ring-2 focus:ring-blue-500/20 outline-none">
                            </td>
                            <td class="p-2 align-top">
                              <input type="number" formControlName="prixVenteUnitaireHT" (input)="calculateTotals()" class="w-full p-2 border border-slate-200 rounded-md text-right font-medium focus:ring-2 focus:ring-blue-500/20 outline-none">
                            </td>
                            <td class="p-2 align-top">
                              <input type="number" formControlName="tva" (input)="calculateTotals()" class="w-full p-2 border border-slate-200 rounded-md text-center focus:ring-2 focus:ring-blue-500/20 outline-none">
                            </td>
                            <td class="p-2 align-top text-center">
                              <span [class]="getMargeClass(getVenteLigneMarge(clientIdx, ligneIdx))" class="px-2 py-1 rounded text-xs font-bold">
                                {{ getVenteLigneMarge(clientIdx, ligneIdx) | number:'1.1-1' }}%
                              </span>
                            </td>
                            <td class="p-2 align-top text-right font-bold text-blue-700 bg-blue-50/30 pt-4">
                              {{ getVenteLineTotal(clientIdx, ligneIdx) | number:'1.2-2' }}
                            </td>
                            <td class="p-2 align-top text-center pt-3">
                              <button type="button" (click)="removeLigneVente(clientIdx, ligneIdx)" class="text-slate-400 hover:text-red-500 transition-colors">
                                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                              </button>
                            </td>
                          </tr>
                        }
                      </tbody>
                      <tfoot class="bg-slate-50 border-t border-slate-200">
                        <tr>
                          <td colspan="7" class="p-2">
                            <button type="button" (click)="addLigneVente(clientIdx)" class="w-full text-center py-2 text-sm text-blue-600 hover:bg-blue-50 rounded-lg font-semibold transition flex items-center justify-center gap-1">
                              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path></svg>
                              Ajouter une ligne
                            </button>
                          </td>
                        </tr>
                      </tfoot>
                    </table>
                  </div>
                </div>
              }
            </div>
          </div>
        </div>

        <!-- Right Column: Summary -->
        <aside class="w-full lg:w-80 lg:shrink-0 mt-8 lg:mt-0">
          <div class="lg:sticky lg:top-[152px] space-y-6">
           
           <!-- Margin KPI -->
           <div class="bg-gradient-to-br from-slate-800 to-slate-900 p-6 rounded-xl shadow-lg text-white relative overflow-hidden">
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
                 <h3 class="text-xs font-bold text-orange-500 uppercase mb-3 flex items-center gap-2">
                   <span class="w-2 h-2 rounded-full bg-orange-500"></span> Achat Fournisseur
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
                   <span class="text-orange-600">{{ (buyTotal() + buyTva()) | number:'1.2-2' }} Dhs</span>
                 </div>
              </div>

              <!-- Selling -->
              <div class="pt-4">
                 <h3 class="text-xs font-bold text-blue-500 uppercase mb-3 flex items-center gap-2">
                   <span class="w-2 h-2 rounded-full bg-blue-500"></span> Vente Clients ({{ clientsVenteArray.length }})
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
                   <span class="text-blue-800">Total TTC</span>
                   <span class="text-blue-900">{{ (sellTotal() + sellTva()) | number:'1.2-2' }} Dhs</span>
                 </div>
              </div>
           </div>

           <!-- Clients Summary -->
           @if (clientsVenteArray.length > 1) {
             <div class="bg-white p-4 rounded-xl shadow-sm border border-slate-100">
               <h3 class="text-xs font-bold text-slate-500 uppercase mb-3">Répartition par client</h3>
               @for (clientForm of clientsVenteArray.controls; track clientForm; let idx = $index) {
                 <div class="flex justify-between items-center py-2 border-b border-slate-50 last:border-0">
                   <div class="flex items-center gap-2">
                     <span class="w-6 h-6 rounded-full bg-blue-100 text-blue-600 flex items-center justify-center text-xs font-bold">{{ idx + 1 }}</span>
                     <span class="text-sm text-slate-700 truncate max-w-[120px]">{{ getClientName(clientForm.value.clientId) }}</span>
                   </div>
                   <span class="text-sm font-bold text-slate-800">{{ getClientTotal(idx) | number:'1.2-2' }}</span>
                 </div>
               }
             </div>
           }

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

  // Totals signals
  buyTotal = signal(0);
  buyTva = signal(0);
  sellTotal = signal(0);
  sellTva = signal(0);
  marginPercent = signal(0);
  
  // Dropdown state
  activeDropdownType = signal<string | null>(null);
  activeDropdownIndex = signal<number | null>(null);

  // Active payment modes
  activePaymentModes = computed(() => this.store.paymentModes().filter(m => m.active));

  // Map pour stocker les prix d'achat par produit
  prixAchatMap: Map<string, number> = new Map();

  constructor() {
    this.form = this.fb.group({
      number: ['BC-2025-' + Math.floor(Math.random() * 1000), Validators.required],
      date: [new Date().toISOString().split('T')[0], Validators.required],
      supplierId: ['', Validators.required],
      status: ['draft', Validators.required],
      paymentMode: [''],
      lignesAchat: this.fb.array([]),
      clientsVente: this.fb.array([])
    });

    this.form.valueChanges.subscribe(() => this.calculateTotals());
  }

  get lignesAchatArray() {
    return this.form.get('lignesAchat') as FormArray;
  }

  get clientsVenteArray() {
    return this.form.get('clientsVente') as FormArray;
  }

  getLignesVenteArray(clientIdx: number): FormArray {
    return this.clientsVenteArray.at(clientIdx).get('lignesVente') as FormArray;
  }

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEditMode = true;
      this.bcId = id;
      this.loadExistingBC(id);
    } else {
      // Par défaut: 1 ligne achat + 1 client avec 1 ligne vente
      this.addLigneAchat();
      this.addClientVente();
    }
  }

  loadExistingBC(id: string) {
    const bc = this.store.bcs().find(b => b.id === id);
    if (!bc) return;

    this.form.patchValue({
      number: bc.number,
      date: bc.date,
      supplierId: bc.supplierId,
      status: bc.status,
      paymentMode: bc.paymentMode || ''
    });

    // Nouvelle structure multi-clients
    if (bc.lignesAchat && bc.lignesAchat.length > 0) {
      bc.lignesAchat.forEach(ligne => {
        this.lignesAchatArray.push(this.createLigneAchatGroup(ligne));
        this.prixAchatMap.set(ligne.produitRef, ligne.prixAchatUnitaireHT);
      });
    }

    if (bc.clientsVente && bc.clientsVente.length > 0) {
      bc.clientsVente.forEach(client => {
        const clientGroup = this.createClientVenteGroup(client);
        this.clientsVenteArray.push(clientGroup);
      });
    } else if (bc.clientId && bc.items) {
      // Rétrocompatibilité avec ancien format
      // Convertir les anciennes lignes en lignes d'achat
      bc.items.forEach(item => {
        const ligneAchat = {
          produitRef: item.ref,
          designation: item.name,
          unite: item.unit,
          quantiteAchetee: item.qtyBuy,
          prixAchatUnitaireHT: item.priceBuyHT,
          tva: item.tvaRate
        };
        this.lignesAchatArray.push(this.createLigneAchatGroup(ligneAchat));
        this.prixAchatMap.set(item.ref, item.priceBuyHT);
      });

      // Créer un bloc client avec les lignes de vente
      const clientVente = {
        clientId: bc.clientId,
        lignesVente: bc.items.map(item => ({
          produitRef: item.ref,
          designation: item.name,
          unite: item.unit,
          quantiteVendue: item.qtySell,
          prixVenteUnitaireHT: item.priceSellHT,
          tva: item.tvaRate
        }))
      };
      this.clientsVenteArray.push(this.createClientVenteGroup(clientVente));
    }

    // Si aucun client, ajouter un bloc vide
    if (this.clientsVenteArray.length === 0) {
      this.addClientVente();
    }
    if (this.lignesAchatArray.length === 0) {
      this.addLigneAchat();
    }

    this.calculateTotals();
  }

  // === Création des FormGroups ===

  createLigneAchatGroup(data?: any): FormGroup {
    return this.fb.group({
      produitRef: [data?.produitRef || ''],
      productSearch: [data?.designation || ''],
      designation: [data?.designation || ''],
      unite: [data?.unite || 'U'],
      quantiteAchetee: [data?.quantiteAchetee || 1],
      prixAchatUnitaireHT: [data?.prixAchatUnitaireHT || 0],
      tva: [data?.tva || 20]
    });
  }

  createLigneVenteGroup(data?: any): FormGroup {
    return this.fb.group({
      produitRef: [data?.produitRef || ''],
      productSearch: [data?.designation || ''],
      designation: [data?.designation || ''],
      unite: [data?.unite || 'U'],
      quantiteVendue: [data?.quantiteVendue || 1],
      prixVenteUnitaireHT: [data?.prixVenteUnitaireHT || 0],
      tva: [data?.tva || 20]
    });
  }

  createClientVenteGroup(data?: any): FormGroup {
    const group = this.fb.group({
      clientId: [data?.clientId || ''],
      lignesVente: this.fb.array([])
    });

    if (data?.lignesVente && data.lignesVente.length > 0) {
      const lignesArray = group.get('lignesVente') as FormArray;
      data.lignesVente.forEach((ligne: any) => {
        lignesArray.push(this.createLigneVenteGroup(ligne));
      });
    } else {
      // Ajouter une ligne vide par défaut
      (group.get('lignesVente') as FormArray).push(this.createLigneVenteGroup());
    }

    return group;
  }

  // === Ajout/Suppression lignes ===

  addLigneAchat() {
    this.lignesAchatArray.push(this.createLigneAchatGroup());
  }

  removeLigneAchat(index: number) {
    this.lignesAchatArray.removeAt(index);
    this.calculateTotals();
  }

  addClientVente() {
    this.clientsVenteArray.push(this.createClientVenteGroup());
  }

  removeClientVente(index: number) {
    this.clientsVenteArray.removeAt(index);
    this.calculateTotals();
  }

  addLigneVente(clientIdx: number) {
    this.getLignesVenteArray(clientIdx).push(this.createLigneVenteGroup());
  }

  removeLigneVente(clientIdx: number, ligneIdx: number) {
    this.getLignesVenteArray(clientIdx).removeAt(ligneIdx);
    this.calculateTotals();
  }

  // === Dropdown/Autocomplete ===

  openDropdown(type: string, index: number) {
    this.activeDropdownType.set(type);
    this.activeDropdownIndex.set(index);
  }

  closeDropdownDelayed() {
    setTimeout(() => {
      this.activeDropdownType.set(null);
      this.activeDropdownIndex.set(null);
    }, 200);
  }

  filterProducts(term: string): Product[] {
    const products = this.store.products();
    if (!term) return products.slice(0, 10);
    const t = term.toLowerCase();
    return products.filter(p => 
      p.name.toLowerCase().includes(t) || p.ref.toLowerCase().includes(t)
    ).slice(0, 10);
  }

  getProduitsFromAchat(): any[] {
    return this.lignesAchatArray.value.filter((l: any) => l.produitRef || l.designation);
  }

  selectProductAchat(index: number, product: Product) {
    const group = this.lignesAchatArray.at(index);
    group.patchValue({
      produitRef: product.ref,
      productSearch: product.name,
      designation: product.name,
      unite: product.unit,
      prixAchatUnitaireHT: product.priceBuyHT
    });
    this.prixAchatMap.set(product.ref, product.priceBuyHT);
    this.activeDropdownType.set(null);
    this.calculateTotals();
  }

  selectProductVente(clientIdx: number, ligneIdx: number, produitAchat: any) {
    const group = this.getLignesVenteArray(clientIdx).at(ligneIdx);
    const product = this.store.products().find(p => p.ref === produitAchat.produitRef);
    
    group.patchValue({
      produitRef: produitAchat.produitRef,
      productSearch: produitAchat.designation,
      designation: produitAchat.designation,
      unite: produitAchat.unite || 'U',
      quantiteVendue: produitAchat.quantiteAchetee || 1,
      prixVenteUnitaireHT: product?.priceSellHT || produitAchat.prixAchatUnitaireHT * 1.2,
      tva: produitAchat.tva || 20
    });
    this.activeDropdownType.set(null);
    this.calculateTotals();
  }

  // === Calculs ===

  getAchatLineTotal(index: number): number {
    const ligne = this.lignesAchatArray.at(index).value;
    return (ligne.quantiteAchetee || 0) * (ligne.prixAchatUnitaireHT || 0);
  }

  getVenteLineTotal(clientIdx: number, ligneIdx: number): number {
    const ligne = this.getLignesVenteArray(clientIdx).at(ligneIdx).value;
    return (ligne.quantiteVendue || 0) * (ligne.prixVenteUnitaireHT || 0);
  }

  getVenteLigneMarge(clientIdx: number, ligneIdx: number): number {
    const ligne = this.getLignesVenteArray(clientIdx).at(ligneIdx).value;
    const prixVente = ligne.prixVenteUnitaireHT || 0;
    const prixAchat = this.prixAchatMap.get(ligne.produitRef) || 0;
    
    if (prixAchat <= 0) return 0;
    return ((prixVente - prixAchat) / prixAchat) * 100;
  }

  getClientTotal(clientIdx: number): number {
    const lignes = this.getLignesVenteArray(clientIdx).value;
    return lignes.reduce((sum: number, l: any) => 
      sum + (l.quantiteVendue || 0) * (l.prixVenteUnitaireHT || 0), 0);
  }

  getClientName(clientId: string): string {
    if (!clientId) return 'Client non défini';
    const client = this.store.clients().find(c => c.id === clientId);
    return client?.name || 'Client inconnu';
  }

  getMargeClass(marge: number): string {
    if (marge >= 15) return 'bg-emerald-100 text-emerald-700';
    if (marge > 0) return 'bg-amber-100 text-amber-700';
    return 'bg-red-100 text-red-700';
  }

  calculateTotals() {
    let bTot = 0, bTva = 0, sTot = 0, sTva = 0;

    // Totaux achats
    this.lignesAchatArray.controls.forEach(control => {
      const val = control.value;
      const qte = val.quantiteAchetee || 0;
      const prix = val.prixAchatUnitaireHT || 0;
      const tva = (val.tva || 0) / 100;

      bTot += qte * prix;
      bTva += qte * prix * tva;

      // Mettre à jour le map des prix d'achat
      if (val.produitRef) {
        this.prixAchatMap.set(val.produitRef, prix);
      }
    });

    // Totaux ventes (tous les clients)
    this.clientsVenteArray.controls.forEach((clientControl) => {
      const lignesArray = clientControl.get('lignesVente') as FormArray;
      lignesArray.controls.forEach(ligneControl => {
        const val = ligneControl.value;
        const qte = val.quantiteVendue || 0;
        const prix = val.prixVenteUnitaireHT || 0;
        const tva = (val.tva || 0) / 100;

        sTot += qte * prix;
        sTva += qte * prix * tva;
      });
    });

    this.buyTotal.set(bTot);
    this.buyTva.set(bTva);
    this.sellTotal.set(sTot);
    this.sellTva.set(sTva);

    if (bTot > 0) {
      this.marginPercent.set(((sTot - bTot) / bTot) * 100);
    } else {
      this.marginPercent.set(0);
    }
  }

  // === Sauvegarde ===

  save() {
    if (!this.form.get('number')?.value || !this.form.get('date')?.value || !this.form.get('supplierId')?.value) {
      alert('Veuillez remplir tous les champs obligatoires (Numéro, Date, Fournisseur)');
      return;
    }

    // Vérifier qu'il y a au moins un client
    const clientsVente = this.clientsVenteArray.value;
    const validClients = clientsVente.filter((c: any) => c.clientId);
    if (validClients.length === 0) {
      alert('Veuillez sélectionner au moins un client');
      return;
    }

    // Vérifier que la somme des quantités vendues aux clients
    // est égale aux quantités achetées auprès du fournisseur (par produit)
    const achatsMap = new Map<string, number>();
    const ventesMap = new Map<string, number>();

    // 1) Quantités achetées par produit
    this.lignesAchatArray.controls.forEach(control => {
      const val = control.value;
      const ref: string = val.produitRef || val.designation;
      const qte: number = val.quantiteAchetee || 0;
      if (!ref) {
        return;
      }
      achatsMap.set(ref, (achatsMap.get(ref) || 0) + qte);
    });

    // 2) Quantités vendues (tous les clients) par produit
    this.clientsVenteArray.controls.forEach(clientControl => {
      const lignesArray = clientControl.get('lignesVente') as FormArray;
      lignesArray.controls.forEach(ligneControl => {
        const val = ligneControl.value;
        const ref: string = val.produitRef || val.designation;
        const qte: number = val.quantiteVendue || 0;
        if (!ref) {
          return;
        }
        ventesMap.set(ref, (ventesMap.get(ref) || 0) + qte);
      });
    });

    // 3) Comparer pour chaque produit
    for (const [ref, qteAchat] of achatsMap.entries()) {
      const qteVente = ventesMap.get(ref) || 0;
      if (qteVente !== qteAchat) {
        alert(
          `Incohérence sur le produit "${ref}" :\n` +
          `- Quantité achetée fournisseur : ${qteAchat}\n` +
          `- Quantité répartie chez les clients : ${qteVente}\n\n` +
          `La somme des quantités vendues aux clients doit être ÉGALE à la quantité achetée.\n` +
          `Merci d'ajuster les quantités avant d'enregistrer.`
        );
        return;
      }
    }

    const formVal = this.form.value;

    // Construire les lignes d'achat
    const lignesAchat: LigneAchat[] = this.lignesAchatArray.value
      .filter((l: any) => l.designation && l.prixAchatUnitaireHT > 0)
      .map((l: any) => ({
        produitRef: l.produitRef || l.designation,
        designation: l.designation,
        unite: l.unite || 'U',
        quantiteAchetee: l.quantiteAchetee || 1,
        prixAchatUnitaireHT: l.prixAchatUnitaireHT,
        tva: l.tva || 20
      }));

    // Construire les clients avec leurs lignes de vente
    const clientsVenteData: ClientVente[] = validClients.map((client: any) => ({
      clientId: client.clientId,
      lignesVente: (client.lignesVente || [])
        .filter((l: any) => l.designation && l.prixVenteUnitaireHT > 0)
        .map((l: any) => ({
          produitRef: l.produitRef || l.designation,
          designation: l.designation,
          unite: l.unite || 'U',
          quantiteVendue: l.quantiteVendue || 1,
          prixVenteUnitaireHT: l.prixVenteUnitaireHT,
          tva: l.tva || 20
        }))
    }));

    const bcData: BC = {
      id: this.bcId || `bc-${Date.now()}`,
      number: formVal.number,
      date: formVal.date,
      supplierId: formVal.supplierId,
      status: formVal.status,
      paymentMode: formVal.paymentMode || undefined,
      lignesAchat: lignesAchat,
      clientsVente: clientsVenteData,
      // Totaux
      totalAchatHT: this.buyTotal(),
      totalAchatTTC: this.buyTotal() + this.buyTva(),
      totalVenteHT: this.sellTotal(),
      totalVenteTTC: this.sellTotal() + this.sellTva(),
      margeTotale: this.sellTotal() - this.buyTotal(),
      margePourcentage: this.marginPercent()
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
