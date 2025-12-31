import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StoreService } from '../../services/store.service';
import { ApiService, CollectionInfo, DeleteDataResponse } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6 fade-in-up pb-10 max-w-3xl mx-auto">
      <div>
        <h1 class="text-2xl font-bold text-slate-800 font-display">Paramètres</h1>
        <p class="text-sm text-slate-500 mt-1">Configuration générale de l'application.</p>
      </div>

      <!-- Payment Modes Card -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div class="p-6 border-b border-slate-100 bg-slate-50/50 flex justify-between items-center">
          <div>
            <h2 class="text-lg font-bold text-slate-800">Modes de Paiement</h2>
            <p class="text-xs text-slate-500">Gérez les méthodes acceptées pour les factures.</p>
          </div>
        </div>
        
        <div class="p-6">
          <!-- Add New -->
           <div class="bg-slate-50 p-6 rounded-xl border border-slate-200 mb-8">
            <h3 class="text-base font-bold text-slate-700 mb-1">Ajouter un mode</h3>
            <p class="text-sm text-slate-500 mb-4">Le nouveau mode sera immédiatement disponible dans les formulaires.</p>
            <div class="flex gap-3">
              <input type="text" [(ngModel)]="newModeName" placeholder="Nouveau mode (ex: PayPal)" 
                     class="flex-1 px-4 py-2 border border-slate-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500/20 outline-none transition"
                     (keyup.enter)="addMode()">
              <button (click)="addMode()" [disabled]="!newModeName()" class="px-5 py-2 bg-blue-600 text-white rounded-lg text-sm font-bold hover:bg-blue-700 transition disabled:opacity-50">
                Ajouter
              </button>
            </div>
          </div>

          <!-- List -->
          <div class="space-y-3">
            @for (mode of store.paymentModes(); track mode.id) {
              <div class="flex items-center justify-between p-3 border border-slate-100 rounded-lg hover:bg-slate-50 transition group bg-white">
                <div class="flex items-center gap-3">
                  <div class="w-8 h-8 rounded-full flex items-center justify-center bg-slate-100 text-slate-500">
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z"></path></svg>
                  </div>
                  <span class="font-medium text-slate-700" [class.line-through]="!mode.active" [class.text-slate-400]="!mode.active">{{ mode.name }}</span>
                  @if (!mode.active) {
                    <span class="text-[10px] bg-slate-100 text-slate-500 px-1.5 py-0.5 rounded">Inactif</span>
                  }
                </div>
                <div class="flex items-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                  <button (click)="toggleMode(mode.id)" class="text-xs font-medium px-2 py-1 rounded transition-colors"
                    [class]="mode.active ? 'text-amber-600 hover:bg-amber-50' : 'text-emerald-600 hover:bg-emerald-50'">
                    {{ mode.active ? 'Désactiver' : 'Activer' }}
                  </button>
                  <button (click)="deleteMode(mode.id)" class="p-1.5 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded transition-colors">
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path></svg>
                  </button>
                </div>
              </div>
            }
          </div>
        </div>
      </div>

      <!-- Solde de Départ Card -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div class="p-6 border-b border-slate-100 bg-slate-50/50 flex justify-between items-center">
          <div>
            <h2 class="text-lg font-bold text-slate-800">Solde de Départ</h2>
            <p class="text-xs text-slate-500">Configurez le solde initial de la trésorerie de l'entreprise.</p>
          </div>
        </div>
        
        <div class="p-6">
          @if (isLoadingSolde()) {
            <div class="text-center py-8 text-slate-500">Chargement...</div>
          } @else {
            <div class="space-y-4">
              <!-- Affichage du solde actuel -->
              @if (soldeGlobal()) {
                <div class="bg-gradient-to-r from-blue-50 to-indigo-50 p-6 rounded-xl border border-blue-100">
                  <div class="flex items-center justify-between">
                    <div>
                      <p class="text-sm text-slate-600 mb-1">Solde Actuel</p>
                      <p class="text-3xl font-bold" [class.text-emerald-600]="soldeGlobal()!.soldeActuel >= 0" [class.text-red-600]="soldeGlobal()!.soldeActuel < 0">
                        {{ soldeGlobal()!.soldeActuel | number:'1.2-2' }} MAD
                      </p>
                      <p class="text-xs text-slate-500 mt-2">Solde initial: {{ soldeGlobal()!.soldeInitial | number:'1.2-2' }} MAD</p>
                      <p class="text-xs text-slate-500">Date de début: {{ soldeGlobal()!.dateDebut | date:'dd/MM/yyyy' }}</p>
                    </div>
                    <div class="w-16 h-16 rounded-full flex items-center justify-center" 
                         [class.bg-emerald-100]="soldeGlobal()!.soldeActuel >= 0" 
                         [class.bg-red-100]="soldeGlobal()!.soldeActuel < 0">
                      <svg class="w-8 h-8" [class.text-emerald-600]="soldeGlobal()!.soldeActuel >= 0" [class.text-red-600]="soldeGlobal()!.soldeActuel < 0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                      </svg>
                    </div>
                  </div>
                </div>
              }

              <!-- Formulaire pour initialiser/modifier le solde -->
              <div class="bg-slate-50 p-6 rounded-xl border border-slate-200">
                <h3 class="text-base font-bold text-slate-700 mb-1">Initialiser / Modifier le Solde</h3>
                <p class="text-sm text-slate-500 mb-4">Définissez le solde de départ de votre trésorerie.</p>
                <div class="space-y-4">
                  <div>
                    <label class="block text-sm font-semibold text-slate-700 mb-1">Montant Initial (MAD)</label>
                    <input type="number" step="0.01" [(ngModel)]="soldeInitial" 
                           placeholder="0.00"
                           class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                  </div>
                  <div>
                    <label class="block text-sm font-semibold text-slate-700 mb-1">Date de Début (optionnel)</label>
                    <input type="date" [(ngModel)]="dateDebut" 
                           class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                  </div>
                  <button (click)="saveSoldeDepart()" [disabled]="!soldeInitial || soldeInitial() <= 0"
                          class="w-full px-4 py-2 bg-blue-600 text-white font-bold rounded-lg hover:bg-blue-700 transition disabled:opacity-50 disabled:cursor-not-allowed shadow-lg shadow-blue-600/20">
                    Enregistrer le Solde de Départ
                  </button>
                </div>
              </div>

              <!-- Formulaire pour ajouter un apport externe -->
              <div class="bg-emerald-50 p-6 rounded-xl border border-emerald-200">
                <h3 class="text-base font-bold text-emerald-800 mb-1">Ajouter un Apport Externe</h3>
                <p class="text-sm text-emerald-700 mb-4">Enregistrez un apport d'argent depuis l'extérieur (augmentation de capital, prêt, etc.).</p>
                <div class="space-y-4">
                  <div>
                    <label class="block text-sm font-semibold text-slate-700 mb-1">Montant (MAD)</label>
                    <input type="number" step="0.01" [(ngModel)]="apportMontant" 
                           placeholder="0.00"
                           class="w-full px-4 py-2 border border-emerald-200 rounded-lg focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500 outline-none transition">
                  </div>
                  <div>
                    <label class="block text-sm font-semibold text-slate-700 mb-1">Motif <span class="text-red-500">*</span></label>
                    <input type="text" [(ngModel)]="apportMotif" 
                           placeholder="Ex: Augmentation de capital, Prêt, Apport associé..."
                           class="w-full px-4 py-2 border border-emerald-200 rounded-lg focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500 outline-none transition">
                    <p class="text-xs text-slate-500 mt-1">Décrivez la raison de cet apport</p>
                  </div>
                  <div>
                    <label class="block text-sm font-semibold text-slate-700 mb-1">Date (optionnel)</label>
                    <input type="date" [(ngModel)]="apportDate" 
                           class="w-full px-4 py-2 border border-emerald-200 rounded-lg focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500 outline-none transition">
                    <p class="text-xs text-slate-500 mt-1">Si non renseignée, la date actuelle sera utilisée</p>
                  </div>
                  <button (click)="ajouterApportExterne()" 
                          [disabled]="!apportMontant() || apportMontant()! <= 0 || !apportMotif() || apportMotif().trim().length === 0"
                          class="w-full px-4 py-2 bg-emerald-600 text-white font-bold rounded-lg hover:bg-emerald-700 transition disabled:opacity-50 disabled:cursor-not-allowed shadow-lg shadow-emerald-600/20">
                    Ajouter l'Apport
                  </button>
                </div>
              </div>
            </div>
          }
        </div>
      </div>

      <!-- Informations de l'Entreprise Card - Configuration des informations pour le footer PDF -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div class="p-6 border-b border-slate-100 bg-slate-50/50 flex justify-between items-center">
          <div>
            <h2 class="text-lg font-bold text-slate-800">Informations de l'Entreprise</h2>
            <p class="text-xs text-slate-500">Ces informations apparaîtront dans le footer de tous les PDFs générés (factures, BC, etc.)</p>
          </div>
        </div>
        
        <div class="p-6">
          @if (isLoadingCompanyInfo()) {
            <div class="text-center py-8 text-slate-500">Chargement...</div>
          } @else {
            <div class="space-y-4">
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label class="block text-sm font-semibold text-slate-700 mb-1">ICE <span class="text-red-500">*</span></label>
                  <input type="text" [(ngModel)]="companyInfo().ice" 
                         placeholder="002889872000062"
                         class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                </div>
                
                <div>
                  <label class="block text-sm font-semibold text-slate-700 mb-1">Capital</label>
                  <input type="text" [(ngModel)]="companyInfo().capital" 
                         placeholder="2.000.000,00 Dhs"
                         class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                </div>
                
                <div>
                  <label class="block text-sm font-semibold text-slate-700 mb-1">Téléphone</label>
                  <input type="text" [(ngModel)]="companyInfo().telephone" 
                         placeholder="06 61 51 11 91"
                         class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                </div>
                
                <div>
                  <label class="block text-sm font-semibold text-slate-700 mb-1">RC (Registre de Commerce)</label>
                  <input type="text" [(ngModel)]="companyInfo().rc" 
                         placeholder="54287"
                         class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                </div>
                
                <div>
                  <label class="block text-sm font-semibold text-slate-700 mb-1">IF (Identifiant Fiscal)</label>
                  <input type="text" [(ngModel)]="companyInfo().ifFiscal" 
                         placeholder="50499801"
                         class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                </div>
                
                <div>
                  <label class="block text-sm font-semibold text-slate-700 mb-1">TP (Patente)</label>
                  <input type="text" [(ngModel)]="companyInfo().tp" 
                         placeholder="17101980"
                         class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                </div>
              </div>
              
              <div class="pt-4 border-t border-slate-100">
                <h3 class="text-sm font-bold text-slate-700 mb-3">Informations Bancaires</h3>
                <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label class="block text-sm font-semibold text-slate-700 mb-1">Banque</label>
                    <input type="text" [(ngModel)]="companyInfo().banque" 
                           placeholder="Ex: ATTIJARI WAFABANK"
                           class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                    <p class="text-xs text-slate-500 mt-1">Nom de la banque principale</p>
                  </div>
                  
                  <div>
                    <label class="block text-sm font-semibold text-slate-700 mb-1">Agence</label>
                    <input type="text" [(ngModel)]="companyInfo().agence" 
                           placeholder="Ex: CENTRE D AFFAIRE MEKNES"
                           class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                    <p class="text-xs text-slate-500 mt-1">Nom de l'agence bancaire</p>
                  </div>
                  
                  <div class="md:col-span-2">
                    <label class="block text-sm font-semibold text-slate-700 mb-1">RIB / Numéro de Compte</label>
                    <input type="text" [(ngModel)]="companyInfo().rib" 
                           placeholder="Ex: 000542H000001759"
                           class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                    <p class="text-xs text-slate-500 mt-1">RIB ou numéro de compte utilisé pour les ordres de virement</p>
                  </div>
                </div>
              </div>
              
              <div class="pt-4 border-t border-slate-100">
                <button (click)="saveCompanyInfo()" 
                        class="w-full px-4 py-2 bg-blue-600 text-white font-bold rounded-lg hover:bg-blue-700 transition shadow-lg shadow-blue-600/20">
                  Enregistrer les Informations
                </button>
              </div>
            </div>
          }
        </div>
      </div>

      <!-- Paramètres de Calcul Card -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div class="p-6 border-b border-slate-100 bg-slate-50/50 flex justify-between items-center">
          <div>
            <h2 class="text-lg font-bold text-slate-800">Paramètres de Calcul Comptable</h2>
            <p class="text-xs text-slate-500">Codes spéciaux utilisés dans les formules Excel (équivalents aux cellules $D$2127, $E$2123, etc.)</p>
          </div>
        </div>
        
        <div class="p-6">
          @if (isLoadingParams()) {
            <div class="text-center py-8 text-slate-500">Chargement...</div>
          } @else {
            <div class="space-y-4">
              <div>
                <label class="block text-sm font-semibold text-slate-700 mb-1">Code D Clôture ($D$2127)</label>
                <input type="text" [(ngModel)]="parametresCalcul().codeDCloture" 
                       placeholder="Code d'exclusion pour la colonne D"
                       class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                <p class="text-xs text-slate-500 mt-1">Les lignes avec ce code dans la colonne D sont exclues du calcul du bilan</p>
              </div>

              <div>
                <label class="block text-sm font-semibold text-slate-700 mb-1">Code E Exclusion 1 ($E$2123)</label>
                <input type="text" [(ngModel)]="parametresCalcul().codeEExclu1" 
                       placeholder="Code d'exclusion 1 pour la colonne E"
                       class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
                <p class="text-xs text-slate-500 mt-1">Les lignes avec ce code dans la colonne E sont exclues du calcul du bilan</p>
              </div>

              <div>
                <label class="block text-sm font-semibold text-slate-700 mb-1">Code E Exclusion 2 ($E$2125)</label>
                <input type="text" [(ngModel)]="parametresCalcul().codeEExclu2" 
                       placeholder="Code d'exclusion 2 pour la colonne E"
                       class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
              </div>

              <div>
                <label class="block text-sm font-semibold text-slate-700 mb-1">Code E Exclusion 3 ($E$2124)</label>
                <input type="text" [(ngModel)]="parametresCalcul().codeEExclu3" 
                       placeholder="Code d'exclusion 3 pour la colonne E"
                       class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
              </div>

              <div class="pt-4 border-t border-slate-100">
                <button (click)="saveParametresCalcul()" 
                        class="w-full px-4 py-2 bg-blue-600 text-white font-bold rounded-lg hover:bg-blue-700 transition shadow-lg shadow-blue-600/20">
                  Enregistrer les Paramètres
                </button>
              </div>
            </div>
          }
        </div>
      </div>

      <!-- Migration des Données Card -->
      @if (auth.currentUser()?.role === 'ADMIN') {
        <div class="bg-blue-50 rounded-xl shadow-sm border-2 border-blue-200 overflow-hidden">
          <div class="p-6 border-b border-blue-100 bg-blue-100/50 flex justify-between items-center">
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-full flex items-center justify-center bg-blue-200 text-blue-700">
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"></path>
                </svg>
              </div>
              <div>
                <h2 class="text-lg font-bold text-blue-800">Migration des Données</h2>
                <p class="text-xs text-blue-600">Corrigez les liaisons entre factures et bons de commande</p>
              </div>
            </div>
          </div>
          
          <div class="p-6">
            <div class="space-y-4">
              <div class="bg-white p-4 rounded-lg border border-blue-200">
                <h3 class="text-base font-bold text-slate-700 mb-2">Synchroniser les références BC</h3>
                <p class="text-sm text-slate-600 mb-4">
                  Cette opération va mettre à jour toutes les factures existantes pour qu'elles aient la référence BC correcte.
                  Cela corrige le problème où les factures ne sont pas trouvées pour un bon de commande.
                </p>
                <p class="text-xs text-slate-500 mb-4">
                  La migration va :
                  <ul class="list-disc list-inside ml-2 mt-2 space-y-1">
                    <li>Récupérer toutes les factures avec un BC lié</li>
                    <li>Définir le champ bcReference basé sur le numéro BC</li>
                    <li>Sauvegarder les modifications</li>
                  </ul>
                </p>
                
                @if (migrationResult()) {
                  <div class="mb-4 p-4 rounded-lg"
                       [class.bg-green-50]="migrationResult()!.success"
                       [class.border-green-200]="migrationResult()!.success"
                       [class.bg-red-50]="!migrationResult()!.success"
                       [class.border-red-200]="!migrationResult()!.success"
                       class="border">
                    <h4 class="text-sm font-bold mb-2"
                        [class.text-green-700]="migrationResult()!.success"
                        [class.text-red-700]="!migrationResult()!.success">
                      {{ migrationResult()!.message }}
                    </h4>
                    @if (migrationResult()!.statistics) {
                      <div class="space-y-1 text-xs text-slate-600">
                        <p><strong>Factures achat mises à jour:</strong> {{ migrationResult()!.statistics.facturesAchatMisesAJour }}</p>
                        <p><strong>Factures vente mises à jour:</strong> {{ migrationResult()!.statistics.facturesVenteMisesAJour }}</p>
                        @if (migrationResult()!.statistics.erreursFacturesAchat > 0 || migrationResult()!.statistics.erreursFacturesVente > 0) {
                          <p class="text-red-600">
                            <strong>Erreurs:</strong> 
                            {{ migrationResult()!.statistics.erreursFacturesAchat + migrationResult()!.statistics.erreursFacturesVente }} facture(s)
                          </p>
                        }
                      </div>
                    }
                  </div>
                }
                
                <button (click)="executeMigration()" 
                        [disabled]="isMigrating()"
                        class="w-full px-4 py-3 bg-blue-600 text-white font-bold rounded-lg hover:bg-blue-700 transition disabled:opacity-50 disabled:cursor-not-allowed shadow-lg shadow-blue-600/20 flex items-center justify-center gap-2">
                  @if (isMigrating()) {
                    <svg class="animate-spin h-5 w-5" fill="none" viewBox="0 0 24 24">
                      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                      <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                    </svg>
                    <span>Migration en cours...</span>
                  } @else {
                    <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"></path>
                    </svg>
                    <span>Exécuter la migration</span>
                  }
                </button>
              </div>
            </div>
          </div>
        </div>
      }

      <!-- Suppression des Données Card -->
      @if (auth.currentUser()?.role === 'ADMIN') {
        <div class="bg-red-50 rounded-xl shadow-sm border-2 border-red-200 overflow-hidden">
          <div class="p-6 border-b border-red-100 bg-red-100/50 flex justify-between items-center">
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-full flex items-center justify-center bg-red-200 text-red-700">
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path>
                </svg>
              </div>
              <div>
                <h2 class="text-lg font-bold text-red-800">Suppression des Données</h2>
                <p class="text-xs text-red-600">ATTENTION: Cette action est irréversible. Supprimez toutes les données sélectionnées.</p>
              </div>
            </div>
          </div>
          
          <div class="p-6">
            @if (isLoadingCollections()) {
              <div class="text-center py-8 text-slate-500">Chargement des collections...</div>
            } @else {
              <div class="space-y-6">
                <!-- Sélection des collections -->
                <div class="space-y-4">
                  <div class="flex items-center justify-between">
                    <h3 class="text-base font-bold text-slate-700">Sélectionner les collections à supprimer</h3>
                    <div class="flex gap-2">
                      <button (click)="selectAllCollections()" 
                              class="text-xs font-medium px-3 py-1.5 bg-slate-100 text-slate-700 rounded-lg hover:bg-slate-200 transition">
                        Tout sélectionner
                      </button>
                      <button (click)="deselectAllCollections()" 
                              class="text-xs font-medium px-3 py-1.5 bg-slate-100 text-slate-700 rounded-lg hover:bg-slate-200 transition">
                        Tout désélectionner
                      </button>
                    </div>
                  </div>

                  <!-- Collections groupées par catégorie -->
                  @for (category of getCategories(); track category) {
                    <div class="bg-white p-4 rounded-lg border border-slate-200">
                      <h4 class="text-sm font-bold text-slate-700 mb-3">{{ category }}</h4>
                      <div class="space-y-2">
                        @for (collection of getCollectionsByCategory(category); track collection.name) {
                          <label class="flex items-center justify-between p-2 hover:bg-slate-50 rounded cursor-pointer group">
                            <div class="flex items-center gap-3 flex-1">
                              <input type="checkbox" 
                                     [checked]="selectedCollections().includes(collection.name)"
                                     (change)="toggleCollection(collection.name)"
                                     class="w-4 h-4 text-red-600 border-slate-300 rounded focus:ring-red-500">
                              <span class="text-sm font-medium text-slate-700">{{ collection.description }}</span>
                              @if (collection.critical) {
                                <span class="text-[10px] bg-red-100 text-red-700 px-2 py-0.5 rounded font-bold">CRITIQUE</span>
                              }
                            </div>
                            <span class="text-xs text-slate-500">{{ collection.count }} élément(s)</span>
                          </label>
                        }
                      </div>
                    </div>
                  }
                </div>

                <!-- Confirmation -->
                <div class="bg-red-100 p-6 rounded-lg border-2 border-red-300">
                  <h3 class="text-base font-bold text-red-800 mb-2">Confirmation requise</h3>
                  <p class="text-sm text-red-700 mb-4">Pour confirmer la suppression, tapez <strong>SUPPRIMER</strong> dans le champ ci-dessous.</p>
                  <input type="text" 
                         [(ngModel)]="confirmationText"
                         placeholder="Tapez SUPPRIMER"
                         class="w-full px-4 py-2 border-2 border-red-300 rounded-lg focus:ring-2 focus:ring-red-500 focus:border-red-500 outline-none transition font-mono"
                         [class.border-red-500]="confirmationText() !== '' && confirmationText() !== 'SUPPRIMER'"
                         [class.border-green-500]="confirmationText() === 'SUPPRIMER'">
                  @if (confirmationText() !== '' && confirmationText() !== 'SUPPRIMER') {
                    <p class="text-xs text-red-600 mt-2">Le texte doit être exactement "SUPPRIMER"</p>
                  }
                </div>

                <!-- Bouton de suppression -->
                <button (click)="openDeleteModal()" 
                        [disabled]="selectedCollections().length === 0 || confirmationText() !== 'SUPPRIMER' || isDeleting()"
                        class="w-full px-4 py-3 bg-red-600 text-white font-bold rounded-lg hover:bg-red-700 transition disabled:opacity-50 disabled:cursor-not-allowed shadow-lg shadow-red-600/20 flex items-center justify-center gap-2">
                  @if (isDeleting()) {
                    <svg class="animate-spin h-5 w-5" fill="none" viewBox="0 0 24 24">
                      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                      <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                    </svg>
                    <span>Suppression en cours...</span>
                  } @else {
                    <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                    </svg>
                    <span>Supprimer les données sélectionnées</span>
                  }
                </button>

                <!-- Résultat de la suppression -->
                @if (deleteResult()) {
                  <div class="bg-white p-6 rounded-lg border border-slate-200">
                    <h3 class="text-base font-bold text-slate-700 mb-4">Résultat de la suppression</h3>
                    <div class="space-y-2">
                      <p class="text-sm text-slate-600">
                        <strong>Total supprimé:</strong> {{ deleteResult()!.totalDeleted }} élément(s)
                      </p>
                      @if (Object.keys(deleteResult()!.deletedCounts).length > 0) {
                        <div class="mt-4">
                          <p class="text-sm font-semibold text-slate-700 mb-2">Détails par collection:</p>
                          <ul class="space-y-1">
                            @for (entry of Object.entries(deleteResult()!.deletedCounts); track entry[0]) {
                              <li class="text-sm text-slate-600">
                                <strong>{{ getCollectionDescription(entry[0]) }}:</strong> {{ entry[1] }} élément(s)
                              </li>
                            }
                          </ul>
                        </div>
                      }
                      @if (deleteResult()!.errors && deleteResult()!.errors.length > 0) {
                        <div class="mt-4 p-3 bg-red-50 border border-red-200 rounded">
                          <p class="text-sm font-semibold text-red-700 mb-2">Erreurs:</p>
                          <ul class="space-y-1">
                            @for (error of deleteResult()!.errors; track error) {
                              <li class="text-sm text-red-600">{{ error }}</li>
                            }
                          </ul>
                        </div>
                      }
                    </div>
                  </div>
                }
              </div>
            }
          </div>
        </div>
      }

      <!-- Modal de confirmation de suppression -->
      @if (showDeleteModal()) {
        <div class="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4" (click)="closeDeleteModal()">
          <div class="bg-white rounded-xl shadow-2xl max-w-2xl w-full p-6" (click)="$event.stopPropagation()">
            <div class="flex items-center gap-3 mb-4">
              <div class="w-12 h-12 rounded-full flex items-center justify-center bg-red-100 text-red-600">
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path>
                </svg>
              </div>
              <div>
                <h3 class="text-xl font-bold text-red-800">Confirmation de suppression</h3>
                <p class="text-sm text-red-600">Cette action est irréversible</p>
              </div>
            </div>

            <div class="bg-red-50 border-2 border-red-200 rounded-lg p-4 mb-6">
              <p class="text-sm font-semibold text-red-800 mb-2">Vous êtes sur le point de supprimer:</p>
              <ul class="list-disc list-inside space-y-1 text-sm text-red-700">
                @for (collectionName of selectedCollections(); track collectionName) {
                  <li>{{ getCollectionDescription(collectionName) }} ({{ getCollectionCount(collectionName) }} élément(s))</li>
                }
              </ul>
              <p class="text-sm font-bold text-red-800 mt-4">
                Total: {{ getTotalSelectedCount() }} élément(s) seront supprimés définitivement.
              </p>
            </div>

            <div class="flex gap-3">
              <button (click)="closeDeleteModal()" 
                      class="flex-1 px-4 py-2 bg-slate-200 text-slate-700 font-bold rounded-lg hover:bg-slate-300 transition">
                Annuler
              </button>
              <button (click)="confirmDelete()" 
                      [disabled]="isDeleting()"
                      class="flex-1 px-4 py-2 bg-red-600 text-white font-bold rounded-lg hover:bg-red-700 transition disabled:opacity-50">
                @if (isDeleting()) {
                  <span class="flex items-center justify-center gap-2">
                    <svg class="animate-spin h-5 w-5" fill="none" viewBox="0 0 24 24">
                      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                      <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                    </svg>
                    Suppression...
                  </span>
                } @else {
                  Confirmer la suppression
                }
              </button>
            </div>
          </div>
        </div>
      }
    </div>
  `
})
export class SettingsComponent implements OnInit {
  store = inject(StoreService);
  api = inject(ApiService);
  auth = inject(AuthService);
  newModeName = signal('');
  
  // Informations de l'entreprise
  companyInfo = signal<any>({
    ice: '',
    capital: '',
    telephone: '',
    rc: '',
    ifFiscal: '',
    tp: '',
    banque: '',
    agence: '',
    rib: ''
  });
  isLoadingCompanyInfo = signal(false);
  
  // Paramètres de calcul
  parametresCalcul = signal<any>({
    codeDCloture: '',
    codeEExclu1: '',
    codeEExclu2: '',
    codeEExclu3: ''
  });
  isLoadingParams = signal(false);
  
  // Gestion du solde
  soldeGlobal = this.store.soldeGlobal;
  soldeInitial = signal<number | null>(null);
  dateDebut = signal<string>('');
  isLoadingSolde = signal(false);
  
  // Gestion des apports externes
  apportMontant = signal<number | null>(null);
  apportMotif = signal<string>('');
  apportDate = signal<string>('');

  // Gestion de la migration de données
  isMigrating = signal(false);
  migrationResult = signal<{
    success: boolean;
    message: string;
    statistics?: {
      facturesAchatMisesAJour: number;
      facturesVenteMisesAJour: number;
      erreursFacturesAchat: number;
      erreursFacturesVente: number;
    };
  } | null>(null);
  
  // Gestion de la suppression de données
  collections = signal<CollectionInfo[]>([]);
  selectedCollections = signal<string[]>([]);
  confirmationText = signal<string>('');
  isLoadingCollections = signal(false);
  isDeleting = signal(false);
  deleteResult = signal<DeleteDataResponse | null>(null);
  showDeleteModal = signal(false);

  async ngOnInit() {
    await this.loadCompanyInfo();
    await this.loadParametresCalcul();
    await this.loadSoldeGlobal();
    if (this.auth.currentUser()?.role === 'ADMIN') {
      await this.loadCollections();
    }
  }
  
  async loadSoldeGlobal() {
    this.isLoadingSolde.set(true);
    try {
      await this.store.loadSoldeGlobal();
      const solde = this.store.soldeGlobal();
      if (solde) {
        this.soldeInitial.set(solde.soldeInitial);
        this.dateDebut.set(solde.dateDebut);
      }
    } catch (error) {
      console.error('Error loading solde global:', error);
    } finally {
      this.isLoadingSolde.set(false);
    }
  }
  
  async saveSoldeDepart() {
    if (!this.soldeInitial() || this.soldeInitial()! <= 0) {
      this.store.showToast('Veuillez entrer un montant valide', 'error');
      return;
    }
    
    try {
      await this.store.initialiserSoldeDepart(this.soldeInitial()!, this.dateDebut() || undefined);
      await this.loadSoldeGlobal();
    } catch (error) {
      console.error('Error saving solde depart:', error);
    }
  }

  async loadParametresCalcul() {
    this.isLoadingParams.set(true);
    try {
      const params = await this.store.loadParametresCalcul();
      if (params) {
        this.parametresCalcul.set(params);
      }
    } catch (error) {
      console.error('Error loading parametres calcul:', error);
    } finally {
      this.isLoadingParams.set(false);
    }
  }

  async loadCompanyInfo() {
    this.isLoadingCompanyInfo.set(true);
    try {
      const info = await this.store.loadCompanyInfo();
      if (info) {
        this.companyInfo.set(info);
      }
    } catch (error) {
      console.error('Error loading company info:', error);
    } finally {
      this.isLoadingCompanyInfo.set(false);
    }
  }

  async saveCompanyInfo() {
    try {
      await this.store.saveCompanyInfo(this.companyInfo());
    } catch (error) {
      console.error('Error saving company info:', error);
    }
  }

  async saveParametresCalcul() {
    try {
      await this.store.saveParametresCalcul(this.parametresCalcul());
    } catch (error) {
      console.error('Error saving parametres calcul:', error);
    }
  }

  async addMode() {
    if (this.newModeName().trim()) {
      try {
        await this.store.addPaymentMode(this.newModeName().trim());
        this.newModeName.set('');
      } catch (error) {
        // Error already handled in store service
      }
    }
  }

  async ajouterApportExterne() {
    if (!this.apportMontant() || this.apportMontant()! <= 0) {
      this.store.showToast('Veuillez entrer un montant valide', 'error');
      return;
    }
    
    if (!this.apportMotif() || this.apportMotif().trim().length === 0) {
      this.store.showToast('Veuillez spécifier un motif', 'error');
      return;
    }
    
    try {
      await this.store.ajouterApportExterne(
        this.apportMontant()!,
        this.apportMotif().trim(),
        this.apportDate() || undefined
      );
      
      // Réinitialiser le formulaire
      this.apportMontant.set(null);
      this.apportMotif.set('');
      this.apportDate.set('');
      
      // Recharger le solde global
      await this.loadSoldeGlobal();
    } catch (error) {
      console.error('Error adding apport externe:', error);
    }
  }

  async toggleMode(id: string) {
    try {
      await this.store.togglePaymentMode(id);
    } catch (error) {
      // Error already handled in store service
    }
  }

  async deleteMode(id: string) {
    if (confirm('Êtes-vous sûr de vouloir supprimer ce mode de paiement ?')) {
      try {
        await this.store.deletePaymentMode(id);
      } catch (error) {
        // Error already handled in store service
      }
    }
  }

  // Méthode pour la migration de données
  async executeMigration() {
    if (this.isMigrating()) {
      return;
    }
    
    if (!confirm('Êtes-vous sûr de vouloir exécuter cette migration ? Cette opération va mettre à jour toutes les factures existantes.')) {
      return;
    }
    
    this.isMigrating.set(true);
    this.migrationResult.set(null);
    
    try {
      const response = await this.api.post<any>('/admin/migration/sync-bc-references', {}).toPromise();
      
      if (response && response.success) {
        this.migrationResult.set({
          success: true,
          message: response.message || 'Migration terminée avec succès',
          statistics: response.statistics
        });
        this.store.showToast('Migration terminée avec succès', 'success');
      } else {
        this.migrationResult.set({
          success: false,
          message: response?.message || 'Erreur lors de la migration'
        });
        this.store.showToast('Erreur lors de la migration', 'error');
      }
    } catch (error: any) {
      console.error('Error executing migration:', error);
      const errorMessage = error?.error?.message || error?.message || 'Erreur inconnue';
      this.migrationResult.set({
        success: false,
        message: 'Erreur lors de la migration: ' + errorMessage
      });
      this.store.showToast('Erreur lors de la migration', 'error');
    } finally {
      this.isMigrating.set(false);
    }
  }
  
  // Méthodes pour la suppression de données
  async loadCollections() {
    this.isLoadingCollections.set(true);
    try {
      const collections = await this.api.getAvailableCollections().toPromise();
      if (collections) {
        this.collections.set(collections);
      }
    } catch (error) {
      console.error('Error loading collections:', error);
      this.store.showToast('Erreur lors du chargement des collections', 'error');
    } finally {
      this.isLoadingCollections.set(false);
    }
  }

  getCategories(): string[] {
    const categories = new Set(this.collections().map(c => c.category));
    return Array.from(categories).sort();
  }

  getCollectionsByCategory(category: string): CollectionInfo[] {
    return this.collections().filter(c => c.category === category);
  }

  toggleCollection(collectionName: string) {
    const current = this.selectedCollections();
    if (current.includes(collectionName)) {
      this.selectedCollections.set(current.filter(c => c !== collectionName));
    } else {
      this.selectedCollections.set([...current, collectionName]);
    }
  }

  selectAllCollections() {
    this.selectedCollections.set(this.collections().map(c => c.name));
  }

  deselectAllCollections() {
    this.selectedCollections.set([]);
  }

  openDeleteModal() {
    this.showDeleteModal.set(true);
  }

  closeDeleteModal() {
    this.showDeleteModal.set(false);
  }

  async confirmDelete() {
    if (this.selectedCollections().length === 0 || this.confirmationText() !== 'SUPPRIMER') {
      return;
    }

    this.isDeleting.set(true);
    this.showDeleteModal.set(false);
    this.deleteResult.set(null);

    try {
      const result = await this.api.deleteAllData(
        this.selectedCollections(),
        this.confirmationText()
      ).toPromise();

      if (result) {
        this.deleteResult.set(result);
        this.store.showToast(
          `Suppression terminée: ${result.totalDeleted} élément(s) supprimé(s)`,
          result.errors && result.errors.length > 0 ? 'info' : 'success'
        );
        
        // Réinitialiser
        this.selectedCollections.set([]);
        this.confirmationText.set('');
        
        // Recharger les collections pour mettre à jour les compteurs
        await this.loadCollections();
      }
    } catch (error: any) {
      console.error('Error deleting data:', error);
      this.store.showToast('Erreur lors de la suppression des données', 'error');
    } finally {
      this.isDeleting.set(false);
    }
  }

  getCollectionDescription(name: string): string {
    const collection = this.collections().find(c => c.name === name);
    return collection ? collection.description : name;
  }

  getCollectionCount(name: string): number {
    const collection = this.collections().find(c => c.name === name);
    return collection ? collection.count : 0;
  }

  getTotalSelectedCount(): number {
    return this.selectedCollections().reduce((total, name) => {
      return total + this.getCollectionCount(name);
    }, 0);
  }
}
