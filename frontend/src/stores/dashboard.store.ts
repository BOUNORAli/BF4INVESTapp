import { Injectable, inject, signal } from '@angular/core';
import { ApiService } from '../services/api.service';

export interface DashboardKpiResponse {
  caHT: number;
  caTTC: number;
  totalAchatsHT: number;
  totalAchatsTTC: number;
  margeTotale: number;
  margeMoyenne: number;
  tvaCollectee: number;
  tvaDeductible: number;
  impayes: {
    totalImpayes: number;
    impayes0_30: number;
    impayes31_60: number;
    impayesPlus60: number;
  };
  facturesEnRetard: number;
  caMensuel: Array<{
    mois: string;
    caHT: number;
    marge: number;
  }>;
  topFournisseurs: Array<{
    id: string;
    nom: string;
    montant: number;
  }>;
  topClients: Array<{
    id: string;
    nom: string;
    montant: number;
  }>;
}

export interface SoldeGlobal {
  id?: string;
  soldeInitial: number;
  soldeActuel: number;
  dateDebut: string;
}

export interface HistoriqueSolde {
  id: string;
  type: string;
  montant: number;
  soldeGlobalAvant: number;
  soldeGlobalApres: number;
  soldePartenaireAvant: number | null;
  soldePartenaireApres: number | null;
  partenaireId?: string;
  partenaireType?: string;
  partenaireNom?: string;
  referenceId?: string;
  referenceNumero?: string;
  date: string;
  description?: string;
}

export interface PrevisionJournaliere {
  date: string;
  entreesPrevisionnelles: number;
  sortiesPrevisionnelles: number;
  soldePrevu: number;
}

export interface EcheanceDetail {
  date: string;
  type: 'VENTE' | 'ACHAT' | 'CHARGE';
  numeroFacture: string;
  partenaire: string;
  montant: number;
  statut: string;
  factureId: string;
}

export interface PrevisionTresorerieResponse {
  soldeActuel: number;
  previsions: PrevisionJournaliere[];
  echeances: EcheanceDetail[];
}

/**
 * Store spécialisé pour le dashboard et la trésorerie
 */
@Injectable({
  providedIn: 'root'
})
export class DashboardStore {
  private api = inject(ApiService);

  // État
  readonly dashboardKPIs = signal<DashboardKpiResponse | null>(null);
  readonly dashboardLoading = signal<boolean>(false);
  readonly soldeGlobal = signal<SoldeGlobal | null>(null);
  readonly historiqueSolde = signal<HistoriqueSolde[]>([]);
  readonly previsionTresorerie = signal<PrevisionTresorerieResponse | null>(null);

  /**
   * Charge les KPIs du dashboard
   */
  async loadDashboardKPIs(from?: string, to?: string): Promise<void> {
    try {
      this.dashboardLoading.set(true);
      const params: Record<string, any> = {};
      if (from) params.from = from;
      if (to) params.to = to;
      
      const kpis = await this.api.get<DashboardKpiResponse>('/dashboard/kpis', params).toPromise();
      if (kpis) {
        this.dashboardKPIs.set(kpis);
      }
    } catch (error) {
      console.error('Error loading dashboard KPIs:', error);
      throw error;
    } finally {
      this.dashboardLoading.set(false);
    }
  }

  /**
   * Charge le solde global
   */
  async loadSoldeGlobal(): Promise<void> {
    try {
      const solde = await this.api.get<SoldeGlobal>('/solde/global').toPromise();
      if (solde) {
        this.soldeGlobal.set(solde);
      }
    } catch (error) {
      console.error('Error loading solde global:', error);
      throw error;
    }
  }

  /**
   * Charge l'historique des soldes
   */
  async loadHistoriqueSolde(): Promise<void> {
    try {
      const historique = await this.api.get<HistoriqueSolde[]>('/solde/historique').toPromise() || [];
      this.historiqueSolde.set(historique);
    } catch (error) {
      console.error('Error loading historique solde:', error);
      throw error;
    }
  }

  /**
   * Charge la prévision de trésorerie
   */
  async loadPrevisionTresorerie(): Promise<void> {
    try {
      const prevision = await this.api.get<PrevisionTresorerieResponse>('/prevision/tresorerie').toPromise();
      if (prevision) {
        this.previsionTresorerie.set(prevision);
      }
    } catch (error) {
      console.error('Error loading prevision tresorerie:', error);
      throw error;
    }
  }

  /**
   * Met à jour le solde global
   */
  setSoldeGlobal(solde: SoldeGlobal): void {
    this.soldeGlobal.set(solde);
  }

  /**
   * Met à jour l'historique des soldes
   */
  setHistoriqueSolde(historique: HistoriqueSolde[]): void {
    this.historiqueSolde.set(historique);
  }

  /**
   * Met à jour la prévision de trésorerie
   */
  setPrevisionTresorerie(prevision: PrevisionTresorerieResponse): void {
    this.previsionTresorerie.set(prevision);
  }
}

