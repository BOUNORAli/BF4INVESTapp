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
  soldeActuelProjete?: number;
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
  readonly refreshing = signal<boolean>(false);
  readonly lastUpdated = signal<Date | null>(null);
  readonly soldeGlobal = signal<SoldeGlobal | null>(null);
  readonly historiqueSolde = signal<HistoriqueSolde[]>([]);
  readonly previsionTresorerie = signal<PrevisionTresorerieResponse | null>(null);

  /**
   * Charge les KPIs du dashboard
   */
  async loadDashboardKPIs(from?: string, to?: string, silent: boolean = false): Promise<void> {
    try {
      if (!silent) {
        this.dashboardLoading.set(true);
      }
      const params: Record<string, any> = {};
      if (from) params.from = from;
      if (to) params.to = to;
      
      const kpis = await this.api.get<DashboardKpiResponse>('/dashboard/kpis', params).toPromise();
      if (kpis) {
        this.dashboardKPIs.set(kpis);
        this.lastUpdated.set(new Date());
      }
    } catch (error: any) {
      // Ne pas logger les erreurs réseau (status: 0) en mode silencieux
      // Le cache interceptor gère déjà le fallback
      if (!silent || (error?.status !== 0 && error?.status !== undefined)) {
        console.error('Error loading dashboard KPIs:', error);
      }
      // Ne pas throw l'erreur en mode silencieux pour ne pas bloquer l'UI
      if (!silent) {
        throw error;
      }
    } finally {
      if (!silent) {
        this.dashboardLoading.set(false);
      }
    }
  }

  /**
   * Charge le solde global
   */
  async loadSoldeGlobal(): Promise<void> {
    try {
      // Utiliser l'endpoint /complet qui retourne l'objet SoldeGlobal complet
      const solde = await this.api.get<SoldeGlobal>('/solde/global/complet').toPromise();
      if (solde) {
        this.soldeGlobal.set(solde);
      } else {
        // Si aucun solde n'existe, créer un objet par défaut
        this.soldeGlobal.set({
          soldeInitial: 0,
          soldeActuel: 0,
          dateDebut: new Date().toISOString().split('T')[0]
        });
      }
    } catch (error) {
      console.error('Error loading solde global:', error);
      // En cas d'erreur, initialiser avec 0
      this.soldeGlobal.set({
        soldeInitial: 0,
        soldeActuel: 0,
        dateDebut: new Date().toISOString().split('T')[0]
      });
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

  /**
   * Rafraîchit toutes les données du dashboard (en arrière-plan)
   */
  async refresh(): Promise<void> {
    try {
      this.refreshing.set(true);
      // Utiliser le mode silencieux pour ne pas spammer les logs en cas d'erreur réseau
      await Promise.allSettled([
        this.loadDashboardKPIs(undefined, undefined, true).catch(() => {
          // Ignorer les erreurs silencieusement - le cache sera utilisé
        }),
        this.loadSoldeGlobal().catch(() => {
          // Ignorer les erreurs silencieusement
        })
      ]);
      this.lastUpdated.set(new Date());
    } catch (error) {
      // Logger seulement les erreurs critiques
      if (error && typeof error === 'object' && 'status' in error && (error as any).status !== 0) {
        console.error('Error refreshing dashboard:', error);
      }
    } finally {
      this.refreshing.set(false);
    }
  }

  /**
   * Force le rafraîchissement (ignore le cache)
   */
  async forceRefresh(): Promise<void> {
    await this.refresh();
  }
}

