import { Injectable, inject, signal } from '@angular/core';
import { ApiService } from '../services/api.service';
import type { PaymentMode, CompanyInfo } from '../services/store.service';

/**
 * Store spécialisé pour les paramètres (modes de paiement, infos société)
 */
@Injectable({
  providedIn: 'root'
})
export class SettingsStore {
  private api = inject(ApiService);

  // État
  readonly paymentModes = signal<PaymentMode[]>([]);
  readonly companyInfo = signal<CompanyInfo | null>(null);
  readonly loading = signal<boolean>(false);

  /**
   * Charge les modes de paiement
   */
  async loadPaymentModes(): Promise<void> {
    try {
      this.loading.set(true);
      const modes = await this.api.get<PaymentMode[]>('/settings/payment-modes').toPromise() || [];
      this.paymentModes.set(modes);
    } catch (error) {
      console.error('Error loading payment modes:', error);
      throw error;
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * Charge les informations de la société
   */
  async loadCompanyInfo(): Promise<void> {
    try {
      this.loading.set(true);
      const info = await this.api.get<CompanyInfo>('/company-info').toPromise();
      if (info) {
        this.companyInfo.set(info);
      }
    } catch (error) {
      console.error('Error loading company info:', error);
      throw error;
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * Met à jour les modes de paiement
   */
  setPaymentModes(modes: PaymentMode[]): void {
    this.paymentModes.set(modes);
  }

  /**
   * Met à jour les infos société
   */
  setCompanyInfo(info: CompanyInfo): void {
    this.companyInfo.set(info);
  }

  /**
   * Trouve un mode de paiement par ID
   */
  findPaymentModeById(id: string): PaymentMode | undefined {
    return this.paymentModes().find(m => m.id === id);
  }

  /**
   * Trouve un mode de paiement par nom
   */
  findPaymentModeByName(name: string): PaymentMode | undefined {
    return this.paymentModes().find(m => m.name === name);
  }

  /**
   * Récupère les modes de paiement actifs
   */
  getActivePaymentModes(): PaymentMode[] {
    return this.paymentModes().filter(m => m.active);
  }

  /**
   * Ajoute un mode de paiement
   */
  addPaymentMode(mode: PaymentMode): void {
    this.paymentModes.update(modes => [...modes, mode]);
  }

  /**
   * Met à jour un mode de paiement
   */
  updatePaymentMode(mode: PaymentMode): void {
    this.paymentModes.update(modes => modes.map(m => m.id === mode.id ? mode : m));
  }

  /**
   * Supprime un mode de paiement
   */
  removePaymentMode(id: string): void {
    this.paymentModes.update(modes => modes.filter(m => m.id !== id));
  }
}

