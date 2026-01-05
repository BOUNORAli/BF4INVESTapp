import { Injectable, signal } from '@angular/core';

export interface CacheEntry<T> {
  data: T;
  timestamp: number;
  ttl: number; // Time to live in milliseconds
}

export type CacheKey = 
  | 'products'
  | 'bcs'
  | 'invoices'
  | 'invoices-purchase'
  | 'invoices-sales'
  | 'clients'
  | 'suppliers'
  | 'dashboard'
  | 'settings'
  | 'charges'
  | 'payments'
  | 'solde';

@Injectable({
  providedIn: 'root'
})
export class DataCacheService {
  private cache = new Map<CacheKey, CacheEntry<any>>();
  
  // Configuration TTL par défaut (en millisecondes)
  private defaultTTL = 2 * 60 * 1000; // 2 minutes par défaut
  private ttlConfig: Record<CacheKey, number> = {
    products: 5 * 60 * 1000,      // 5 minutes
    bcs: 2 * 60 * 1000,           // 2 minutes
    invoices: 2 * 60 * 1000,       // 2 minutes (pour compatibilité)
    'invoices-purchase': 2 * 60 * 1000,  // 2 minutes
    'invoices-sales': 2 * 60 * 1000,     // 2 minutes
    clients: 10 * 60 * 1000,       // 10 minutes (rarement modifiés)
    suppliers: 10 * 60 * 1000,     // 10 minutes (rarement modifiés)
    dashboard: 1 * 60 * 1000,     // 1 minute (données critiques)
    settings: 30 * 60 * 1000,      // 30 minutes
    charges: 2 * 60 * 1000,        // 2 minutes
    payments: 2 * 60 * 1000,       // 2 minutes
    solde: 30 * 1000,              // 30 secondes (très dynamique)
  };

  /**
   * Récupère les données du cache si elles sont encore valides
   */
  get<T>(key: CacheKey): T | null {
    const entry = this.cache.get(key);
    if (!entry) {
      return null;
    }

    const age = Date.now() - entry.timestamp;
    if (age > entry.ttl) {
      // Cache expiré
      this.cache.delete(key);
      return null;
    }

    return entry.data as T;
  }

  /**
   * Stocke les données dans le cache
   */
  set<T>(key: CacheKey, data: T, customTTL?: number): void {
    const ttl = customTTL || this.ttlConfig[key] || this.defaultTTL;
    this.cache.set(key, {
      data,
      timestamp: Date.now(),
      ttl
    });
  }

  /**
   * Vérifie si les données sont en cache et valides
   */
  has(key: CacheKey): boolean {
    const entry = this.cache.get(key);
    if (!entry) {
      return false;
    }

    const age = Date.now() - entry.timestamp;
    return age <= entry.ttl;
  }

  /**
   * Vérifie si les données sont expirées (mais toujours en cache)
   */
  isExpired(key: CacheKey): boolean {
    const entry = this.cache.get(key);
    if (!entry) {
      return true;
    }

    const age = Date.now() - entry.timestamp;
    return age > entry.ttl;
  }

  /**
   * Invalide le cache pour une clé spécifique
   */
  invalidate(key: CacheKey): void {
    this.cache.delete(key);
  }

  /**
   * Invalide plusieurs clés à la fois
   */
  invalidateMany(keys: CacheKey[]): void {
    keys.forEach(key => this.cache.delete(key));
  }

  /**
   * Invalide tout le cache
   */
  clear(): void {
    this.cache.clear();
  }

  /**
   * Récupère l'âge des données en cache (en millisecondes)
   */
  getAge(key: CacheKey): number | null {
    const entry = this.cache.get(key);
    if (!entry) {
      return null;
    }
    return Date.now() - entry.timestamp;
  }

  /**
   * Récupère le temps restant avant expiration (en millisecondes)
   */
  getTimeToExpiry(key: CacheKey): number | null {
    const entry = this.cache.get(key);
    if (!entry) {
      return null;
    }

    const age = Date.now() - entry.timestamp;
    const remaining = entry.ttl - age;
    return remaining > 0 ? remaining : 0;
  }

  /**
   * Met à jour le TTL pour une clé spécifique
   */
  setTTL(key: CacheKey, ttl: number): void {
    this.ttlConfig[key] = ttl;
  }
}

