import { Injectable, inject, signal } from '@angular/core';
import { BcService } from '../services/bc.service';
import type { BC } from '../services/store.service';

/**
 * Store spécialisé pour la gestion des bandes de commandes
 */
@Injectable({
  providedIn: 'root'
})
export class BCStore {
  private bcService = inject(BcService);

  // État
  readonly bcs = signal<BC[]>([]);
  readonly loading = signal<boolean>(false);
  readonly refreshing = signal<boolean>(false);
  readonly lastUpdated = signal<Date | null>(null);

  // Computed
  readonly bcsCount = () => this.bcs().length;
  readonly draftBCs = () => this.bcs().filter(bc => bc.status === 'draft');
  readonly sentBCs = () => this.bcs().filter(bc => bc.status === 'sent');
  readonly completedBCs = () => this.bcs().filter(bc => bc.status === 'completed');

  /**
   * Charge toutes les BCs
   */
  async loadBCs(): Promise<void> {
    try {
      this.loading.set(true);
      const bcs = await this.bcService.getBCs();
      this.bcs.set(bcs);
      this.lastUpdated.set(new Date());
    } catch (error) {
      console.error('Error loading BCs:', error);
      throw error;
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * Rafraîchit les BCs (en arrière-plan, ne bloque pas l'UI)
   */
  async refresh(): Promise<void> {
    try {
      this.refreshing.set(true);
      const bcs = await this.bcService.getBCs();
      this.bcs.set(bcs);
      this.lastUpdated.set(new Date());
    } catch (error) {
      console.error('Error refreshing BCs:', error);
      throw error;
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

  /**
   * Ajoute ou met à jour une BC
   */
  upsertBC(bc: BC): void {
    this.bcs.update(list => {
      const index = list.findIndex(b => b.id === bc.id);
      if (index >= 0) {
        const updated = [...list];
        updated[index] = bc;
        return updated;
      }
      return [...list, bc];
    });
  }

  /**
   * Supprime une BC
   */
  removeBC(bcId: string): void {
    this.bcs.update(list => list.filter(b => b.id !== bcId));
  }

  /**
   * Trouve une BC par ID
   */
  findBCById(id: string): BC | undefined {
    return this.bcs().find(b => b.id === id);
  }

  /**
   * Trouve une BC par numéro
   */
  findBCByNumber(number: string): BC | undefined {
    return this.bcs().find(b => b.number === number);
  }
}

