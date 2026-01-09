import { Component, inject, signal, computed, OnInit, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { StoreService, Client, Supplier } from '../../services/store.service';

export interface PartnerSituation {
  partnerInfo: {
    id: string;
    nom: string;
    ice: string;
    reference?: string;
    adresse?: string;
    telephone?: string;
    email?: string;
    rib?: string;
    banque?: string;
    type: 'CLIENT' | 'FOURNISSEUR';
  };
  dateFrom?: string;
  dateTo?: string;
  factures: FactureDetail[];
  previsions: PrevisionDetail[];
  totaux: {
    totalFactureTTC: number;
    totalFactureHT: number;
    totalTVA: number;
    totalPaye: number;
    totalRestant: number;
    solde: number;
    nombreFactures: number;
    nombreFacturesPayees: number;
    nombreFacturesEnAttente: number;
    nombreFacturesEnRetard: number;
    nombrePrevisions: number;
    nombrePrevisionsRealisees: number;
    nombrePrevisionsEnRetard: number;
  };
}

export interface MultiPartnerSituation {
  partners: Array<{
    id: string;
    nom: string;
    ice: string;
    reference?: string;
    adresse?: string;
    telephone?: string;
    email?: string;
    rib?: string;
    banque?: string;
    type: 'CLIENT' | 'FOURNISSEUR';
  }>;
  dateFrom?: string;
  dateTo?: string;
  facturesConsolidees: Array<{
    partnerId: string;
    partnerNom: string;
    partnerType: string;
    facture: FactureDetail;
  }>;
  previsionsConsolidees: Array<{
    partnerId: string;
    partnerNom: string;
    partnerType: string;
    prevision: PrevisionDetail;
  }>;
  totauxGlobaux: {
    totalFactureTTC: number;
    totalFactureHT: number;
    totalTVA: number;
    totalPaye: number;
    totalRestant: number;
    soldeGlobal: number;
    nombreFactures: number;
    nombreFacturesPayees: number;
    nombreFacturesEnAttente: number;
    nombreFacturesEnRetard: number;
    nombrePrevisions: number;
    nombrePrevisionsRealisees: number;
    nombrePrevisionsEnRetard: number;
    nombrePartenaires: number;
  };
  totauxParPartenaire: { [key: string]: PartnerSituation['totaux'] };
  situationsParPartenaire: PartnerSituation[];
}

export interface FactureDetail {
  id: string;
  numeroFacture: string;
  dateFacture?: string;
  dateEcheance?: string;
  montantTTC: number;
  montantHT: number;
  montantTVA: number;
  montantPaye: number;
  montantRestant: number;
  statut: string;
  estAvoir?: boolean;
  numeroFactureOrigine?: string;
  bcReference?: string;
}

export interface PrevisionDetail {
  id: string;
  factureId: string;
  numeroFacture: string;
  datePrevue?: string;
  montantPrevu: number;
  montantPaye: number;
  montantRestant: number;
  statut: string;
  notes?: string;
}

@Component({
  selector: 'app-partner-situation',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6 fade-in-up pb-10">
      
      <!-- Header -->
      <div class="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h1 class="text-2xl font-bold text-slate-800 font-display">Situation Financière</h1>
          <p class="text-slate-500 text-sm mt-1">Avec prévisions de paiement</p>
        </div>
        <div class="flex gap-3 w-full md:w-auto">
          <button (click)="exportPDF()" [disabled]="loading() || (!selectedPartnerId() && selectedPartnerIds().length === 0)" class="flex-1 md:flex-none px-4 py-2 bg-white border border-slate-200 text-slate-700 rounded-lg text-sm font-medium hover:bg-slate-50 transition shadow-sm flex items-center justify-center gap-2 disabled:opacity-50">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
            Export PDF
          </button>
          <button (click)="exportExcel()" [disabled]="loading() || (!selectedPartnerId() && selectedPartnerIds().length === 0)" class="flex-1 md:flex-none px-4 py-2 bg-white border border-slate-200 text-slate-700 rounded-lg text-sm font-medium hover:bg-slate-50 transition shadow-sm flex items-center justify-center gap-2 disabled:opacity-50">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
            Export Excel
          </button>
        </div>
      </div>

      <!-- Mode Toggle -->
      <div class="bg-white p-4 rounded-xl shadow-sm border border-slate-100">
        <div class="flex items-center justify-between">
          <label class="text-sm font-semibold text-slate-700">Mode de sélection:</label>
          <div class="flex gap-2">
            <button 
              (click)="multiMode.set(false)" 
              [class.bg-blue-600]="!multiMode()"
              [class.text-white]="!multiMode()"
              [class.bg-slate-100]="multiMode()"
              [class.text-slate-700]="multiMode()"
              class="px-4 py-2 rounded-lg text-sm font-medium transition">
              Un seul partenaire
            </button>
            <button 
              (click)="multiMode.set(true)" 
              [class.bg-blue-600]="multiMode()"
              [class.text-white]="multiMode()"
              [class.bg-slate-100]="!multiMode()"
              [class.text-slate-700]="!multiMode()"
              class="px-4 py-2 rounded-lg text-sm font-medium transition">
              Plusieurs partenaires
            </button>
          </div>
        </div>
      </div>

      <!-- Filters -->
      <div class="bg-white p-5 rounded-xl shadow-sm border border-slate-100">
        @if (!multiMode()) {
          <!-- Single Partner Selection -->
          <div class="grid grid-cols-1 md:grid-cols-4 gap-4 items-end">
            <div class="md:col-span-2 space-y-1">
              <label class="text-xs font-semibold text-slate-500 uppercase">{{ partnerType() === 'CLIENT' ? 'Client' : 'Fournisseur' }}</label>
              <select [(ngModel)]="selectedPartnerId" (ngModelChange)="loadSituation()" class="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm bg-slate-50 focus:bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all appearance-none cursor-pointer outline-none">
                <option value="">Sélectionner un {{ partnerType() === 'CLIENT' ? 'client' : 'fournisseur' }}</option>
                @if (partnerType() === 'CLIENT') {
                  @for (client of store.clients(); track client.id) {
                    <option [value]="client.id">{{ client.name }}</option>
                  }
                } @else {
                  @for (supplier of store.suppliers(); track supplier.id) {
                    <option [value]="supplier.id">{{ supplier.name }}</option>
                  }
                }
              </select>
            </div>
            <div class="space-y-1">
              <label class="text-xs font-semibold text-slate-500 uppercase">Date Début</label>
              <input type="date" [(ngModel)]="dateFrom" (ngModelChange)="loadSituation()" class="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm bg-slate-50 focus:bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all outline-none text-slate-600">
            </div>
            <div class="space-y-1">
              <label class="text-xs font-semibold text-slate-500 uppercase">Date Fin</label>
              <input type="date" [(ngModel)]="dateTo" (ngModelChange)="loadSituation()" class="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm bg-slate-50 focus:bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all outline-none text-slate-600">
            </div>
          </div>
        } @else {
          <!-- Multi Partner Selection -->
          <div class="space-y-4">
            <div class="grid grid-cols-1 md:grid-cols-3 gap-4 items-end">
              <div class="md:col-span-2 space-y-1">
                <label class="text-xs font-semibold text-slate-500 uppercase">Sélection multiple {{ partnerType() === 'CLIENT' ? 'Clients' : 'Fournisseurs' }}</label>
                <div class="relative">
                  <div 
                    (click)="multiSelectOpen.set(!multiSelectOpen())"
                    class="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm bg-slate-50 focus:bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all cursor-pointer flex items-center justify-between">
                    <span class="text-slate-600">
                      @if (selectedPartnerIds().length === 0) {
                        Sélectionner des {{ partnerType() === 'CLIENT' ? 'clients' : 'fournisseurs' }}
                      } @else {
                        {{ selectedPartnerIds().length }} {{ partnerType() === 'CLIENT' ? 'client(s)' : 'fournisseur(s)' }} sélectionné(s)
                      }
                    </span>
                    <svg class="w-4 h-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"></path>
                    </svg>
                  </div>
                  @if (multiSelectOpen()) {
                    <div class="absolute z-50 w-full mt-1 bg-white border border-slate-200 rounded-lg shadow-lg max-h-60 overflow-y-auto">
                      <div class="p-2 border-b border-slate-100 flex items-center justify-between">
                        <button (click)="selectAll()" class="text-xs text-blue-600 hover:text-blue-700 font-medium">Tout sélectionner</button>
                        <button (click)="deselectAll()" class="text-xs text-slate-600 hover:text-slate-700 font-medium">Tout désélectionner</button>
                      </div>
                      <div class="p-2">
                        @if (partnerType() === 'CLIENT') {
                          @for (client of store.clients(); track client.id) {
                            <label class="flex items-center p-2 hover:bg-slate-50 rounded cursor-pointer">
                              <input type="checkbox" [checked]="selectedPartnerIds().includes(client.id)" (change)="togglePartner(client.id)" class="mr-2 rounded border-slate-300 text-blue-600 focus:ring-blue-500">
                              <span class="text-sm text-slate-700">{{ client.name }}</span>
                            </label>
                          }
                        } @else {
                          @for (supplier of store.suppliers(); track supplier.id) {
                            <label class="flex items-center p-2 hover:bg-slate-50 rounded cursor-pointer">
                              <input type="checkbox" [checked]="selectedPartnerIds().includes(supplier.id)" (change)="togglePartner(supplier.id)" class="mr-2 rounded border-slate-300 text-blue-600 focus:ring-blue-500">
                              <span class="text-sm text-slate-700">{{ supplier.name }}</span>
                            </label>
                          }
                        }
                      </div>
                    </div>
                  }
                </div>
              </div>
              <div class="space-y-1">
                <label class="text-xs font-semibold text-slate-500 uppercase">Date Début</label>
                <input type="date" [(ngModel)]="dateFrom" (ngModelChange)="loadSituation()" class="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm bg-slate-50 focus:bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all outline-none text-slate-600">
              </div>
              <div class="space-y-1">
                <label class="text-xs font-semibold text-slate-500 uppercase">Date Fin</label>
                <input type="date" [(ngModel)]="dateTo" (ngModelChange)="loadSituation()" class="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm bg-slate-50 focus:bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all outline-none text-slate-600">
              </div>
            </div>
            
            <!-- Selected Partners Badges -->
            @if (selectedPartnerIds().length > 0) {
              <div class="flex flex-wrap gap-2">
                @for (partnerId of selectedPartnerIds(); track partnerId) {
                  <span class="inline-flex items-center gap-1 px-3 py-1 bg-blue-100 text-blue-800 rounded-full text-xs font-medium">
                    {{ getPartnerName(partnerId) }}
                    <button (click)="removePartner(partnerId)" class="hover:text-blue-900">
                      <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                      </svg>
                    </button>
                  </span>
                }
              </div>
            }

            <!-- Display Mode Toggle (only for multi-mode) -->
            @if (selectedPartnerIds().length > 0) {
              <div class="flex items-center justify-between pt-2 border-t border-slate-100">
                <label class="text-sm font-semibold text-slate-700">Mode d'affichage:</label>
                <div class="flex gap-2">
                  <button 
                    (click)="displayMode.set('consolidated')" 
                    [class.bg-blue-600]="displayMode() === 'consolidated'"
                    [class.text-white]="displayMode() === 'consolidated'"
                    [class.bg-slate-100]="displayMode() !== 'consolidated'"
                    [class.text-slate-700]="displayMode() !== 'consolidated'"
                    class="px-4 py-2 rounded-lg text-sm font-medium transition">
                    Consolidé
                  </button>
                  <button 
                    (click)="displayMode.set('grouped')" 
                    [class.bg-blue-600]="displayMode() === 'grouped'"
                    [class.text-white]="displayMode() === 'grouped'"
                    [class.bg-slate-100]="displayMode() !== 'grouped'"
                    [class.text-slate-700]="displayMode() !== 'grouped'"
                    class="px-4 py-2 rounded-lg text-sm font-medium transition">
                    Groupé
                  </button>
                </div>
              </div>
            }
          </div>
        }
      </div>

      @if (loading()) {
        <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-12 text-center">
          <div class="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
          <p class="mt-4 text-slate-500">Chargement de la situation...</p>
        </div>
      } @else if (situation() && !multiMode()) {
        <!-- Single Partner Display -->
        <ng-container *ngTemplateOutlet="singlePartnerTemplate; context: { $implicit: situation() }"></ng-container>
      } @else if (multiSituation() && multiMode()) {
        <!-- Multi Partner Display -->
        <ng-container *ngTemplateOutlet="multiPartnerTemplate; context: { $implicit: multiSituation() }"></ng-container>
      } @else if (!loading() && !selectedPartnerId() && selectedPartnerIds().length === 0) {
        <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-12 text-center">
          <svg class="w-16 h-16 text-slate-300 mx-auto mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
          </svg>
          <h3 class="text-slate-900 font-medium text-lg mb-2">Sélectionnez {{ multiMode() ? 'des' : 'un' }} {{ partnerType() === 'CLIENT' ? 'client' : 'fournisseur' }}{{ multiMode() ? 's' : '' }}</h3>
          <p class="text-slate-500">Choisissez {{ multiMode() ? 'des' : 'un' }} {{ partnerType() === 'CLIENT' ? 'client' : 'fournisseur' }}{{ multiMode() ? 's' : '' }} dans le filtre ci-dessus pour afficher {{ multiMode() ? 'leurs' : 'sa' }} situation{{ multiMode() ? 's' : '' }} financière{{ multiMode() ? 's' : '' }}.</p>
        </div>
      }
    </div>

    <!-- Single Partner Template -->
    <ng-template #singlePartnerTemplate let-sit>
      <!-- Summary Cards -->
      <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div class="bg-white p-5 rounded-xl shadow-sm border border-slate-100">
          <div class="text-xs font-bold text-slate-500 uppercase tracking-wider mb-1">Total Facturé TTC</div>
          <div class="text-2xl font-extrabold text-slate-800">{{ sit.totaux.totalFactureTTC | number:'1.2-2' }} MAD</div>
        </div>
        <div class="bg-white p-5 rounded-xl shadow-sm border border-slate-100">
          <div class="text-xs font-bold text-emerald-600 uppercase tracking-wider mb-1">Total Payé</div>
          <div class="text-2xl font-extrabold text-emerald-700">{{ sit.totaux.totalPaye | number:'1.2-2' }} MAD</div>
        </div>
        <div class="bg-white p-5 rounded-xl shadow-sm border border-slate-100">
          <div class="text-xs font-bold text-amber-600 uppercase tracking-wider mb-1">Total Restant</div>
          <div class="text-2xl font-extrabold text-amber-700">{{ sit.totaux.totalRestant | number:'1.2-2' }} MAD</div>
        </div>
        <div class="bg-white p-5 rounded-xl shadow-sm border border-slate-100" [class.border-emerald-200]="sit.totaux.solde >= 0" [class.border-red-200]="sit.totaux.solde < 0">
          <div class="text-xs font-bold uppercase tracking-wider mb-1" [class.text-emerald-600]="sit.totaux.solde >= 0" [class.text-red-600]="sit.totaux.solde < 0">Solde</div>
          <div class="text-2xl font-extrabold" [class.text-emerald-700]="sit.totaux.solde >= 0" [class.text-red-700]="sit.totaux.solde < 0">{{ sit.totaux.solde | number:'1.2-2' }} MAD</div>
        </div>
      </div>

      <!-- Partner Info -->
      <div class="bg-white p-5 rounded-xl shadow-sm border border-slate-100">
        <h3 class="text-lg font-bold text-slate-800 mb-4">Informations du {{ partnerType() === 'CLIENT' ? 'Client' : 'Fournisseur' }}</h3>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
          <div><span class="font-semibold text-slate-600">Nom:</span> <span class="text-slate-800">{{ sit.partnerInfo.nom }}</span></div>
          @if (sit.partnerInfo.ice) {
            <div><span class="font-semibold text-slate-600">ICE:</span> <span class="text-slate-800">{{ sit.partnerInfo.ice }}</span></div>
          }
          @if (sit.partnerInfo.reference) {
            <div><span class="font-semibold text-slate-600">Référence:</span> <span class="text-slate-800">{{ sit.partnerInfo.reference }}</span></div>
          }
          @if (sit.partnerInfo.telephone) {
            <div><span class="font-semibold text-slate-600">Téléphone:</span> <span class="text-slate-800">{{ sit.partnerInfo.telephone }}</span></div>
          }
          @if (sit.partnerInfo.email) {
            <div><span class="font-semibold text-slate-600">Email:</span> <span class="text-slate-800">{{ sit.partnerInfo.email }}</span></div>
          }
          @if (sit.partnerInfo.rib) {
            <div><span class="font-semibold text-slate-600">RIB:</span> <span class="text-slate-800 font-mono">{{ sit.partnerInfo.rib }}</span></div>
          }
          @if (sit.partnerInfo.banque) {
            <div><span class="font-semibold text-slate-600">Banque:</span> <span class="text-slate-800">{{ sit.partnerInfo.banque }}</span></div>
          }
        </div>
      </div>

      <!-- Factures Table -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div class="p-4 border-b border-slate-100 bg-slate-50/50">
          <h3 class="text-lg font-bold text-slate-800">Factures ({{ sit.factures.length }})</h3>
        </div>
        <div class="overflow-x-auto">
          <table class="w-full text-sm text-left min-w-[1000px]">
            <thead class="text-xs text-slate-500 uppercase bg-slate-50/80 border-b border-slate-200">
              <tr>
                <th class="px-6 py-4 font-semibold tracking-wider">N° Facture</th>
                <th class="px-6 py-4 font-semibold tracking-wider">Date</th>
                <th class="px-6 py-4 font-semibold tracking-wider">Échéance</th>
                <th class="px-6 py-4 font-semibold tracking-wider text-right">Montant TTC</th>
                <th class="px-6 py-4 font-semibold tracking-wider text-right">Payé</th>
                <th class="px-6 py-4 font-semibold tracking-wider text-right">Restant</th>
                <th class="px-6 py-4 font-semibold tracking-wider text-center">Statut</th>
                <th class="px-6 py-4 font-semibold tracking-wider text-center">Avoir</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              @for (facture of sit.factures; track facture.id) {
                <tr class="bg-white hover:bg-slate-50 transition-colors">
                  <td class="px-6 py-4 font-medium text-slate-800">{{ facture.numeroFacture }}</td>
                  <td class="px-6 py-4 text-slate-600">{{ facture.dateFacture | date:'dd/MM/yyyy' }}</td>
                  <td class="px-6 py-4 text-slate-600">{{ facture.dateEcheance | date:'dd/MM/yyyy' }}</td>
                  <td class="px-6 py-4 text-right font-medium text-slate-800">{{ facture.montantTTC | number:'1.2-2' }} MAD</td>
                  <td class="px-6 py-4 text-right font-medium text-emerald-700">{{ facture.montantPaye | number:'1.2-2' }} MAD</td>
                  <td class="px-6 py-4 text-right font-medium text-amber-700">{{ facture.montantRestant | number:'1.2-2' }} MAD</td>
                  <td class="px-6 py-4 text-center">
                    <span [class]="getStatutClass(facture.statut)">{{ formatStatut(facture.statut) }}</span>
                  </td>
                  <td class="px-6 py-4 text-center">
                    @if (facture.estAvoir) {
                      <span class="inline-flex items-center bg-purple-100 text-purple-800 text-xs px-2 py-1 rounded-full font-medium">Oui</span>
                    } @else {
                      <span class="text-slate-400">-</span>
                    }
                  </td>
                </tr>
              }
              @if (sit.factures.length === 0) {
                <tr>
                  <td colspan="8" class="px-6 py-12 text-center text-slate-500">Aucune facture trouvée</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>

      <!-- Previsions Table -->
      @if (sit.previsions.length > 0) {
        <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
          <div class="p-4 border-b border-slate-100 bg-slate-50/50">
            <h3 class="text-lg font-bold text-slate-800">Prévisions de Paiement ({{ sit.previsions.length }})</h3>
          </div>
          <div class="overflow-x-auto">
            <table class="w-full text-sm text-left min-w-[800px]">
              <thead class="text-xs text-slate-500 uppercase bg-slate-50/80 border-b border-slate-200">
                <tr>
                  <th class="px-6 py-4 font-semibold tracking-wider">N° Facture</th>
                  <th class="px-6 py-4 font-semibold tracking-wider">Date Prévue</th>
                  <th class="px-6 py-4 font-semibold tracking-wider text-right">Montant Prévu</th>
                  <th class="px-6 py-4 font-semibold tracking-wider text-right">Payé</th>
                  <th class="px-6 py-4 font-semibold tracking-wider text-right">Restant</th>
                  <th class="px-6 py-4 font-semibold tracking-wider text-center">Statut</th>
                  <th class="px-6 py-4 font-semibold tracking-wider">Notes</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                @for (prevision of sit.previsions; track prevision.id) {
                  <tr class="bg-white hover:bg-slate-50 transition-colors">
                    <td class="px-6 py-4 font-medium text-slate-800">{{ prevision.numeroFacture }}</td>
                    <td class="px-6 py-4 text-slate-600">{{ prevision.datePrevue | date:'dd/MM/yyyy' }}</td>
                    <td class="px-6 py-4 text-right font-medium text-slate-800">{{ prevision.montantPrevu | number:'1.2-2' }} MAD</td>
                    <td class="px-6 py-4 text-right font-medium text-emerald-700">{{ prevision.montantPaye | number:'1.2-2' }} MAD</td>
                    <td class="px-6 py-4 text-right font-medium text-amber-700">{{ prevision.montantRestant | number:'1.2-2' }} MAD</td>
                    <td class="px-6 py-4 text-center">
                      <span [class]="getStatutClass(prevision.statut)">{{ formatStatut(prevision.statut) }}</span>
                    </td>
                    <td class="px-6 py-4 text-slate-600">{{ prevision.notes || '-' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      }
    </ng-template>

    <!-- Multi Partner Template -->
    <ng-template #multiPartnerTemplate let-multiSit>
      <!-- Global Summary Cards -->
      <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div class="bg-white p-5 rounded-xl shadow-sm border border-slate-100">
          <div class="text-xs font-bold text-slate-500 uppercase tracking-wider mb-1">Total Facturé TTC</div>
          <div class="text-2xl font-extrabold text-slate-800">{{ multiSit.totauxGlobaux.totalFactureTTC | number:'1.2-2' }} MAD</div>
        </div>
        <div class="bg-white p-5 rounded-xl shadow-sm border border-slate-100">
          <div class="text-xs font-bold text-emerald-600 uppercase tracking-wider mb-1">Total Payé</div>
          <div class="text-2xl font-extrabold text-emerald-700">{{ multiSit.totauxGlobaux.totalPaye | number:'1.2-2' }} MAD</div>
        </div>
        <div class="bg-white p-5 rounded-xl shadow-sm border border-slate-100">
          <div class="text-xs font-bold text-amber-600 uppercase tracking-wider mb-1">Total Restant</div>
          <div class="text-2xl font-extrabold text-amber-700">{{ multiSit.totauxGlobaux.totalRestant | number:'1.2-2' }} MAD</div>
        </div>
        <div class="bg-white p-5 rounded-xl shadow-sm border border-slate-100" [class.border-emerald-200]="multiSit.totauxGlobaux.soldeGlobal >= 0" [class.border-red-200]="multiSit.totauxGlobaux.soldeGlobal < 0">
          <div class="text-xs font-bold uppercase tracking-wider mb-1" [class.text-emerald-600]="multiSit.totauxGlobaux.soldeGlobal >= 0" [class.text-red-600]="multiSit.totauxGlobaux.soldeGlobal < 0">Solde Global</div>
          <div class="text-2xl font-extrabold" [class.text-emerald-700]="multiSit.totauxGlobaux.soldeGlobal >= 0" [class.text-red-700]="multiSit.totauxGlobaux.soldeGlobal < 0">{{ multiSit.totauxGlobaux.soldeGlobal | number:'1.2-2' }} MAD</div>
        </div>
      </div>

      @if (displayMode() === 'consolidated') {
        <!-- Consolidated View -->
        <!-- Factures Table (Consolidated) -->
        <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
          <div class="p-4 border-b border-slate-100 bg-slate-50/50">
            <h3 class="text-lg font-bold text-slate-800">Factures Consolidées ({{ multiSit.facturesConsolidees.length }}) - Tri Chronologique</h3>
          </div>
          <div class="overflow-x-auto">
            <table class="w-full text-sm text-left min-w-[1200px]">
              <thead class="text-xs text-slate-500 uppercase bg-slate-50/80 border-b border-slate-200">
                <tr>
                  <th class="px-6 py-4 font-semibold tracking-wider">Partenaire</th>
                  <th class="px-6 py-4 font-semibold tracking-wider">N° Facture</th>
                  <th class="px-6 py-4 font-semibold tracking-wider">Date</th>
                  <th class="px-6 py-4 font-semibold tracking-wider">Échéance</th>
                  <th class="px-6 py-4 font-semibold tracking-wider text-right">Montant TTC</th>
                  <th class="px-6 py-4 font-semibold tracking-wider text-right">Payé</th>
                  <th class="px-6 py-4 font-semibold tracking-wider text-right">Restant</th>
                  <th class="px-6 py-4 font-semibold tracking-wider text-center">Statut</th>
                  <th class="px-6 py-4 font-semibold tracking-wider text-center">Avoir</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                @for (item of multiSit.facturesConsolidees; track item.facture.id) {
                  <tr class="bg-white hover:bg-slate-50 transition-colors">
                    <td class="px-6 py-4 font-medium text-slate-800">{{ item.partnerNom }}</td>
                    <td class="px-6 py-4 font-medium text-slate-800">{{ item.facture.numeroFacture }}</td>
                    <td class="px-6 py-4 text-slate-600">{{ item.facture.dateFacture | date:'dd/MM/yyyy' }}</td>
                    <td class="px-6 py-4 text-slate-600">{{ item.facture.dateEcheance | date:'dd/MM/yyyy' }}</td>
                    <td class="px-6 py-4 text-right font-medium text-slate-800">{{ item.facture.montantTTC | number:'1.2-2' }} MAD</td>
                    <td class="px-6 py-4 text-right font-medium text-emerald-700">{{ item.facture.montantPaye | number:'1.2-2' }} MAD</td>
                    <td class="px-6 py-4 text-right font-medium text-amber-700">{{ item.facture.montantRestant | number:'1.2-2' }} MAD</td>
                    <td class="px-6 py-4 text-center">
                      <span [class]="getStatutClass(item.facture.statut)">{{ formatStatut(item.facture.statut) }}</span>
                    </td>
                    <td class="px-6 py-4 text-center">
                      @if (item.facture.estAvoir) {
                        <span class="inline-flex items-center bg-purple-100 text-purple-800 text-xs px-2 py-1 rounded-full font-medium">Oui</span>
                      } @else {
                        <span class="text-slate-400">-</span>
                      }
                    </td>
                  </tr>
                }
                @if (multiSit.facturesConsolidees.length === 0) {
                  <tr>
                    <td colspan="9" class="px-6 py-12 text-center text-slate-500">Aucune facture trouvée</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>

        <!-- Previsions Table (Consolidated) -->
        @if (multiSit.previsionsConsolidees.length > 0) {
          <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
            <div class="p-4 border-b border-slate-100 bg-slate-50/50">
              <h3 class="text-lg font-bold text-slate-800">Prévisions Consolidées ({{ multiSit.previsionsConsolidees.length }}) - Tri Chronologique</h3>
            </div>
            <div class="overflow-x-auto">
              <table class="w-full text-sm text-left min-w-[1000px]">
                <thead class="text-xs text-slate-500 uppercase bg-slate-50/80 border-b border-slate-200">
                  <tr>
                    <th class="px-6 py-4 font-semibold tracking-wider">Partenaire</th>
                    <th class="px-6 py-4 font-semibold tracking-wider">N° Facture</th>
                    <th class="px-6 py-4 font-semibold tracking-wider">Date Prévue</th>
                    <th class="px-6 py-4 font-semibold tracking-wider text-right">Montant Prévu</th>
                    <th class="px-6 py-4 font-semibold tracking-wider text-right">Payé</th>
                    <th class="px-6 py-4 font-semibold tracking-wider text-right">Restant</th>
                    <th class="px-6 py-4 font-semibold tracking-wider text-center">Statut</th>
                    <th class="px-6 py-4 font-semibold tracking-wider">Notes</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-slate-100">
                  @for (item of multiSit.previsionsConsolidees; track item.prevision.id) {
                    <tr class="bg-white hover:bg-slate-50 transition-colors">
                      <td class="px-6 py-4 font-medium text-slate-800">{{ item.partnerNom }}</td>
                      <td class="px-6 py-4 font-medium text-slate-800">{{ item.prevision.numeroFacture }}</td>
                      <td class="px-6 py-4 text-slate-600">{{ item.prevision.datePrevue | date:'dd/MM/yyyy' }}</td>
                      <td class="px-6 py-4 text-right font-medium text-slate-800">{{ item.prevision.montantPrevu | number:'1.2-2' }} MAD</td>
                      <td class="px-6 py-4 text-right font-medium text-emerald-700">{{ item.prevision.montantPaye | number:'1.2-2' }} MAD</td>
                      <td class="px-6 py-4 text-right font-medium text-amber-700">{{ item.prevision.montantRestant | number:'1.2-2' }} MAD</td>
                      <td class="px-6 py-4 text-center">
                        <span [class]="getStatutClass(item.prevision.statut)">{{ formatStatut(item.prevision.statut) }}</span>
                      </td>
                      <td class="px-6 py-4 text-slate-600">{{ item.prevision.notes || '-' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        }
      } @else {
        <!-- Grouped View -->
        @for (partnerSit of multiSit.situationsParPartenaire; track partnerSit.partnerInfo.id) {
          <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
            <div class="p-4 border-b border-slate-100 bg-slate-50/50">
              <h3 class="text-lg font-bold text-slate-800">{{ partnerSit.partnerInfo.nom }}</h3>
            </div>
            <div class="p-4">
              <div class="grid grid-cols-1 md:grid-cols-4 gap-4 mb-4">
                <div class="text-sm">
                  <div class="text-xs font-semibold text-slate-500 uppercase">Total Facturé</div>
                  <div class="text-lg font-bold text-slate-800">{{ partnerSit.totaux.totalFactureTTC | number:'1.2-2' }} MAD</div>
                </div>
                <div class="text-sm">
                  <div class="text-xs font-semibold text-slate-500 uppercase">Total Payé</div>
                  <div class="text-lg font-bold text-emerald-700">{{ partnerSit.totaux.totalPaye | number:'1.2-2' }} MAD</div>
                </div>
                <div class="text-sm">
                  <div class="text-xs font-semibold text-slate-500 uppercase">Total Restant</div>
                  <div class="text-lg font-bold text-amber-700">{{ partnerSit.totaux.totalRestant | number:'1.2-2' }} MAD</div>
                </div>
                <div class="text-sm">
                  <div class="text-xs font-semibold text-slate-500 uppercase">Solde</div>
                  <div class="text-lg font-bold" [class.text-emerald-700]="partnerSit.totaux.solde >= 0" [class.text-red-700]="partnerSit.totaux.solde < 0">{{ partnerSit.totaux.solde | number:'1.2-2' }} MAD</div>
                </div>
              </div>
              <div class="text-xs text-slate-500 mb-2">Factures: {{ partnerSit.factures.length }} | Prévisions: {{ partnerSit.previsions.length }}</div>
            </div>
          </div>
        }
      }
    </ng-template>
  `
})
export class PartnerSituationComponent implements OnInit {
  store = inject(StoreService);
  router = inject(Router);
  route = inject(ActivatedRoute);
  
  partnerType = signal<'CLIENT' | 'FOURNISSEUR'>('CLIENT');
  multiMode = signal<boolean>(false);
  displayMode = signal<'consolidated' | 'grouped'>('consolidated');
  selectedPartnerId = signal<string>('');
  selectedPartnerIds = signal<string[]>([]);
  dateFrom = signal<string>('');
  dateTo = signal<string>('');
  situation = signal<PartnerSituation | null>(null);
  multiSituation = signal<MultiPartnerSituation | null>(null);
  loading = signal<boolean>(false);
  multiSelectOpen = signal<boolean>(false);
  
  ngOnInit() {
    this.route.params.subscribe(params => {
      const type = params['type'];
      const id = params['id'];
      
      if (type === 'client' || type === 'supplier') {
        this.partnerType.set(type === 'client' ? 'CLIENT' : 'FOURNISSEUR');
      }
      
      if (id) {
        this.selectedPartnerId.set(id);
        this.multiMode.set(false);
        this.loadSituation();
      }
    });
  }
  
  async loadSituation() {
    if (this.multiMode()) {
      if (this.selectedPartnerIds().length === 0) {
        this.multiSituation.set(null);
        return;
      }
      
      this.loading.set(true);
      try {
        const situation = await this.store.getMultiPartnerSituation(
          this.partnerType(),
          this.selectedPartnerIds(),
          this.dateFrom() || undefined,
          this.dateTo() || undefined
        );
        this.multiSituation.set(situation);
      } catch (error) {
        console.error('Erreur lors du chargement de la situation multi:', error);
        this.store.showToast('Erreur lors du chargement de la situation', 'error');
      } finally {
        this.loading.set(false);
      }
    } else {
      if (!this.selectedPartnerId()) {
        this.situation.set(null);
        return;
      }
      
      this.loading.set(true);
      try {
        const situation = await this.store.getPartnerSituation(
          this.partnerType(),
          this.selectedPartnerId(),
          this.dateFrom() || undefined,
          this.dateTo() || undefined
        );
        this.situation.set(situation);
      } catch (error) {
        console.error('Erreur lors du chargement de la situation:', error);
        this.store.showToast('Erreur lors du chargement de la situation', 'error');
      } finally {
        this.loading.set(false);
      }
    }
  }
  
  togglePartner(partnerId: string) {
    const current = this.selectedPartnerIds();
    if (current.includes(partnerId)) {
      this.selectedPartnerIds.set(current.filter(id => id !== partnerId));
    } else {
      this.selectedPartnerIds.set([...current, partnerId]);
    }
    this.loadSituation();
  }
  
  removePartner(partnerId: string) {
    this.selectedPartnerIds.set(this.selectedPartnerIds().filter(id => id !== partnerId));
    this.loadSituation();
  }
  
  selectAll() {
    const allIds = this.partnerType() === 'CLIENT'
      ? this.store.clients().map(c => c.id)
      : this.store.suppliers().map(s => s.id);
    this.selectedPartnerIds.set(allIds);
    this.loadSituation();
  }
  
  deselectAll() {
    this.selectedPartnerIds.set([]);
    this.loadSituation();
  }
  
  getPartnerName(partnerId: string): string {
    if (this.partnerType() === 'CLIENT') {
      const client = this.store.clients().find(c => c.id === partnerId);
      return client?.name || partnerId;
    } else {
      const supplier = this.store.suppliers().find(s => s.id === partnerId);
      return supplier?.name || partnerId;
    }
  }
  
  async exportPDF() {
    if (this.multiMode()) {
      if (this.selectedPartnerIds().length === 0) {
        this.store.showToast('Veuillez sélectionner au moins un partenaire', 'error');
        return;
      }
      
      try {
        await this.store.exportMultiPartnerSituationPDF(
          this.partnerType(),
          this.selectedPartnerIds(),
          this.dateFrom() || undefined,
          this.dateTo() || undefined
        );
        this.store.showToast('Export PDF téléchargé avec succès', 'success');
      } catch (error) {
        console.error('Erreur lors de l\'export PDF:', error);
        this.store.showToast('Erreur lors de l\'export PDF', 'error');
      }
    } else {
      if (!this.selectedPartnerId()) {
        this.store.showToast('Veuillez sélectionner un partenaire', 'error');
        return;
      }
      
      try {
        await this.store.exportPartnerSituationPDF(
          this.partnerType(),
          this.selectedPartnerId(),
          this.dateFrom() || undefined,
          this.dateTo() || undefined
        );
        this.store.showToast('Export PDF téléchargé avec succès', 'success');
      } catch (error) {
        console.error('Erreur lors de l\'export PDF:', error);
        this.store.showToast('Erreur lors de l\'export PDF', 'error');
      }
    }
  }
  
  async exportExcel() {
    if (this.multiMode()) {
      if (this.selectedPartnerIds().length === 0) {
        this.store.showToast('Veuillez sélectionner au moins un partenaire', 'error');
        return;
      }
      
      try {
        await this.store.exportMultiPartnerSituationExcel(
          this.partnerType(),
          this.selectedPartnerIds(),
          this.dateFrom() || undefined,
          this.dateTo() || undefined
        );
        this.store.showToast('Export Excel téléchargé avec succès', 'success');
      } catch (error) {
        console.error('Erreur lors de l\'export Excel:', error);
        this.store.showToast('Erreur lors de l\'export Excel', 'error');
      }
    } else {
      if (!this.selectedPartnerId()) {
        this.store.showToast('Veuillez sélectionner un partenaire', 'error');
        return;
      }
      
      try {
        await this.store.exportPartnerSituationExcel(
          this.partnerType(),
          this.selectedPartnerId(),
          this.dateFrom() || undefined,
          this.dateTo() || undefined
        );
        this.store.showToast('Export Excel téléchargé avec succès', 'success');
      } catch (error) {
        console.error('Erreur lors de l\'export Excel:', error);
        this.store.showToast('Erreur lors de l\'export Excel', 'error');
      }
    }
  }
  
  getStatutClass(statut: string): string {
    switch(statut) {
      case 'PAYEE': return 'inline-flex items-center bg-emerald-100 text-emerald-800 text-xs px-3 py-1 rounded-full font-bold border border-emerald-200';
      case 'PARTIELLE': return 'inline-flex items-center bg-amber-100 text-amber-800 text-xs px-3 py-1 rounded-full font-bold border border-amber-200';
      case 'EN_ATTENTE': return 'inline-flex items-center bg-blue-100 text-blue-800 text-xs px-3 py-1 rounded-full font-bold border border-blue-200';
      case 'EN_RETARD': return 'inline-flex items-center bg-red-100 text-red-800 text-xs px-3 py-1 rounded-full font-bold border border-red-200';
      case 'PREVU': return 'inline-flex items-center bg-slate-100 text-slate-800 text-xs px-3 py-1 rounded-full font-bold border border-slate-200';
      case 'REALISE': return 'inline-flex items-center bg-emerald-100 text-emerald-800 text-xs px-3 py-1 rounded-full font-bold border border-emerald-200';
      default: return 'inline-flex items-center bg-slate-100 text-slate-800 text-xs px-3 py-1 rounded-full font-bold border border-slate-200';
    }
  }
  
  formatStatut(statut: string): string {
    switch(statut) {
      case 'PAYEE': return 'Payée';
      case 'PARTIELLE': return 'Partielle';
      case 'EN_ATTENTE': return 'En Attente';
      case 'EN_RETARD': return 'En Retard';
      case 'PREVU': return 'Prévu';
      case 'REALISE': return 'Réalisé';
      default: return statut;
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    const target = event.target as HTMLElement;
    if (!target.closest('.relative')) {
      this.multiSelectOpen.set(false);
    }
  }
}
