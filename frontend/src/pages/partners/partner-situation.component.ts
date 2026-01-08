import { Component, inject, signal, computed, OnInit } from '@angular/core';
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
          <button (click)="exportPDF()" [disabled]="loading()" class="flex-1 md:flex-none px-4 py-2 bg-white border border-slate-200 text-slate-700 rounded-lg text-sm font-medium hover:bg-slate-50 transition shadow-sm flex items-center justify-center gap-2 disabled:opacity-50">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
            Export PDF
          </button>
          <button (click)="exportExcel()" [disabled]="loading()" class="flex-1 md:flex-none px-4 py-2 bg-white border border-slate-200 text-slate-700 rounded-lg text-sm font-medium hover:bg-slate-50 transition shadow-sm flex items-center justify-center gap-2 disabled:opacity-50">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
            Export Excel
          </button>
        </div>
      </div>

      <!-- Filters -->
      <div class="bg-white p-5 rounded-xl shadow-sm border border-slate-100 grid grid-cols-1 md:grid-cols-4 gap-4 items-end">
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

      @if (loading()) {
        <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-12 text-center">
          <div class="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
          <p class="mt-4 text-slate-500">Chargement de la situation...</p>
        </div>
      } @else if (situation()) {
        <!-- Summary Cards -->
        <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div class="bg-white p-5 rounded-xl shadow-sm border border-slate-100">
            <div class="text-xs font-bold text-slate-500 uppercase tracking-wider mb-1">Total Facturé TTC</div>
            <div class="text-2xl font-extrabold text-slate-800">{{ situation()!.totaux.totalFactureTTC | number:'1.2-2' }} MAD</div>
          </div>
          <div class="bg-white p-5 rounded-xl shadow-sm border border-slate-100">
            <div class="text-xs font-bold text-emerald-600 uppercase tracking-wider mb-1">Total Payé</div>
            <div class="text-2xl font-extrabold text-emerald-700">{{ situation()!.totaux.totalPaye | number:'1.2-2' }} MAD</div>
          </div>
          <div class="bg-white p-5 rounded-xl shadow-sm border border-slate-100">
            <div class="text-xs font-bold text-amber-600 uppercase tracking-wider mb-1">Total Restant</div>
            <div class="text-2xl font-extrabold text-amber-700">{{ situation()!.totaux.totalRestant | number:'1.2-2' }} MAD</div>
          </div>
          <div class="bg-white p-5 rounded-xl shadow-sm border border-slate-100" [class.border-emerald-200]="situation()!.totaux.solde >= 0" [class.border-red-200]="situation()!.totaux.solde < 0">
            <div class="text-xs font-bold uppercase tracking-wider mb-1" [class.text-emerald-600]="situation()!.totaux.solde >= 0" [class.text-red-600]="situation()!.totaux.solde < 0">Solde</div>
            <div class="text-2xl font-extrabold" [class.text-emerald-700]="situation()!.totaux.solde >= 0" [class.text-red-700]="situation()!.totaux.solde < 0">{{ situation()!.totaux.solde | number:'1.2-2' }} MAD</div>
          </div>
        </div>

        <!-- Partner Info -->
        <div class="bg-white p-5 rounded-xl shadow-sm border border-slate-100">
          <h3 class="text-lg font-bold text-slate-800 mb-4">Informations du {{ partnerType() === 'CLIENT' ? 'Client' : 'Fournisseur' }}</h3>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
            <div><span class="font-semibold text-slate-600">Nom:</span> <span class="text-slate-800">{{ situation()!.partnerInfo.nom }}</span></div>
            @if (situation()!.partnerInfo.ice) {
              <div><span class="font-semibold text-slate-600">ICE:</span> <span class="text-slate-800">{{ situation()!.partnerInfo.ice }}</span></div>
            }
            @if (situation()!.partnerInfo.reference) {
              <div><span class="font-semibold text-slate-600">Référence:</span> <span class="text-slate-800">{{ situation()!.partnerInfo.reference }}</span></div>
            }
            @if (situation()!.partnerInfo.telephone) {
              <div><span class="font-semibold text-slate-600">Téléphone:</span> <span class="text-slate-800">{{ situation()!.partnerInfo.telephone }}</span></div>
            }
            @if (situation()!.partnerInfo.email) {
              <div><span class="font-semibold text-slate-600">Email:</span> <span class="text-slate-800">{{ situation()!.partnerInfo.email }}</span></div>
            }
            @if (situation()!.partnerInfo.rib) {
              <div><span class="font-semibold text-slate-600">RIB:</span> <span class="text-slate-800 font-mono">{{ situation()!.partnerInfo.rib }}</span></div>
            }
            @if (situation()!.partnerInfo.banque) {
              <div><span class="font-semibold text-slate-600">Banque:</span> <span class="text-slate-800">{{ situation()!.partnerInfo.banque }}</span></div>
            }
          </div>
        </div>

        <!-- Factures Table -->
        <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
          <div class="p-4 border-b border-slate-100 bg-slate-50/50">
            <h3 class="text-lg font-bold text-slate-800">Factures ({{ situation()!.factures.length }})</h3>
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
                @for (facture of situation()!.factures; track facture.id) {
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
                @if (situation()!.factures.length === 0) {
                  <tr>
                    <td colspan="8" class="px-6 py-12 text-center text-slate-500">Aucune facture trouvée</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>

        <!-- Previsions Table -->
        @if (situation()!.previsions.length > 0) {
          <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
            <div class="p-4 border-b border-slate-100 bg-slate-50/50">
              <h3 class="text-lg font-bold text-slate-800">Prévisions de Paiement ({{ situation()!.previsions.length }})</h3>
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
                  @for (prevision of situation()!.previsions; track prevision.id) {
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
      } @else if (!loading() && !selectedPartnerId) {
        <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-12 text-center">
          <svg class="w-16 h-16 text-slate-300 mx-auto mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
          </svg>
          <h3 class="text-slate-900 font-medium text-lg mb-2">Sélectionnez un {{ partnerType() === 'CLIENT' ? 'client' : 'fournisseur' }}</h3>
          <p class="text-slate-500">Choisissez un {{ partnerType() === 'CLIENT' ? 'client' : 'fournisseur' }} dans le filtre ci-dessus pour afficher sa situation financière.</p>
        </div>
      }
    </div>
  `
})
export class PartnerSituationComponent implements OnInit {
  store = inject(StoreService);
  router = inject(Router);
  route = inject(ActivatedRoute);
  
  partnerType = signal<'CLIENT' | 'FOURNISSEUR'>('CLIENT');
  selectedPartnerId = signal<string>('');
  dateFrom = signal<string>('');
  dateTo = signal<string>('');
  situation = signal<PartnerSituation | null>(null);
  loading = signal<boolean>(false);
  
  ngOnInit() {
    this.route.params.subscribe(params => {
      const type = params['type'];
      const id = params['id'];
      
      if (type === 'client' || type === 'supplier') {
        this.partnerType.set(type === 'client' ? 'CLIENT' : 'FOURNISSEUR');
      }
      
      if (id) {
        this.selectedPartnerId.set(id);
        this.loadSituation();
      }
    });
  }
  
  async loadSituation() {
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
  
  async exportPDF() {
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
  
  async exportExcel() {
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
}

