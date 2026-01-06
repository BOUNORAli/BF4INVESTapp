import { Injectable, inject, signal } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { DataCacheService, CacheKey } from './data-cache.service';
import { ProductStore } from '../stores/product.store';
import { BCStore } from '../stores/bc.store';
import { InvoiceStore } from '../stores/invoice.store';
import { PartnerStore } from '../stores/partner.store';
import { DashboardStore } from '../stores/dashboard.store';

type StoreName = 'productStore' | 'bcStore' | 'invoiceStore' | 'partnerStore' | 'dashboardStore';

interface StoreRefreshMap {
  [key: string]: StoreName[];
}

@Injectable({
  providedIn: 'root'
})
export class NavigationRefreshService {
  private router = inject(Router);
  private cache = inject(DataCacheService);
  
  // Stores
  private productStore = inject(ProductStore);
  private bcStore = inject(BCStore);
  private invoiceStore = inject(InvoiceStore);
  private partnerStore = inject(PartnerStore);
  private dashboardStore = inject(DashboardStore);

  // État de rafraîchissement global
  readonly isRefreshing = signal<boolean>(false);
  readonly lastRefreshTime = signal<Date | null>(null);

  // Mapping routes → stores à rafraîchir
  private routeStoreMap: StoreRefreshMap = {
    '/dashboard': ['dashboardStore', 'bcStore', 'invoiceStore'],
    '/bc': ['bcStore', 'partnerStore'],
    '/bc/new': ['bcStore', 'partnerStore'],
    '/bc/edit': ['bcStore', 'partnerStore'],
    '/invoices/purchase': ['invoiceStore', 'bcStore', 'partnerStore'],
    '/invoices/sales': ['invoiceStore', 'bcStore', 'partnerStore'],
    '/products': ['productStore'],
    '/partners': ['partnerStore'],
    '/charges': ['invoiceStore'],
    '/tva': ['invoiceStore'],
    '/comptabilite': ['invoiceStore', 'bcStore'],
    '/prevision': ['dashboardStore', 'invoiceStore'],
    '/historique-tresorerie': ['dashboardStore', 'invoiceStore'],
  };

  // Mapping store → cache key
  private storeCacheMap: Record<StoreName, CacheKey> = {
    productStore: 'products',
    bcStore: 'bcs',
    invoiceStore: 'invoices',
    partnerStore: 'clients', // Utilise 'clients' comme clé principale (suppliers partage le même TTL)
    dashboardStore: 'dashboard',
  };

  constructor() {
    this.setupNavigationListener();
  }

  /**
   * Configure l'écoute des événements de navigation
   */
  private setupNavigationListener(): void {
    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe((event: any) => {
        const url = event.urlAfterRedirects || event.url;
        this.refreshForRoute(url);
      });
  }

  /**
   * Rafraîchit les stores nécessaires pour une route donnée
   */
  async refreshForRoute(url: string): Promise<void> {
    // Extraire le chemin de base (sans paramètres)
    const basePath = this.extractBasePath(url);
    
    // Trouver les stores à rafraîchir
    const storesToRefresh = this.getStoresForRoute(basePath);
    
    if (storesToRefresh.length === 0) {
      return; // Aucun store à rafraîchir pour cette route
    }

    // Rafraîchir en arrière-plan (ne bloque pas l'UI) en passant la route pour le chargement sélectif
    this.refreshStores(storesToRefresh, false, basePath);
  }

  /**
   * Extrait le chemin de base depuis une URL complète
   */
  private extractBasePath(url: string): string {
    // Enlever les query params et hash
    const path = url.split('?')[0].split('#')[0];
    
    // Gérer les routes avec paramètres (ex: /bc/edit/:id)
    if (path.includes('/edit/') || path.includes('/new')) {
      const parts = path.split('/');
      if (parts.length >= 3) {
        return `/${parts[1]}/${parts[2]}`;
      }
    }
    
    return path;
  }

  /**
   * Récupère les stores à rafraîchir pour une route
   */
  private getStoresForRoute(route: string): StoreName[] {
    // Recherche exacte
    if (this.routeStoreMap[route]) {
      return this.routeStoreMap[route];
    }

    // Recherche par préfixe (pour les routes avec paramètres)
    for (const [routePattern, stores] of Object.entries(this.routeStoreMap)) {
      if (route.startsWith(routePattern)) {
        return stores;
      }
    }

    return [];
  }

  /**
   * Rafraîchit une liste de stores
   */
  async refreshStores(stores: StoreName[], force: boolean = false, currentRoute?: string): Promise<void> {
    if (stores.length === 0) {
      return;
    }

    this.isRefreshing.set(true);

    try {
      // Filtrer les stores qui ont besoin d'être rafraîchis
      const storesToRefresh = force 
        ? stores 
        : stores.filter(store => {
            // Pour invoiceStore, vérifier le cache approprié selon la route
            if (store === 'invoiceStore' && currentRoute) {
              const purchaseCacheKey = 'invoices-purchase';
              const salesCacheKey = 'invoices-sales';
              
              if (currentRoute.includes('/invoices/purchase')) {
                return !this.cache.has(purchaseCacheKey) || this.cache.isExpired(purchaseCacheKey);
              } else if (currentRoute.includes('/invoices/sales')) {
                return !this.cache.has(salesCacheKey) || this.cache.isExpired(salesCacheKey);
              } else {
                // Pour les autres routes, vérifier les deux caches
                const purchaseExpired = !this.cache.has(purchaseCacheKey) || this.cache.isExpired(purchaseCacheKey);
                const salesExpired = !this.cache.has(salesCacheKey) || this.cache.isExpired(salesCacheKey);
                return purchaseExpired || salesExpired;
              }
            } else {
              const cacheKey = this.storeCacheMap[store];
              return !this.cache.has(cacheKey) || this.cache.isExpired(cacheKey);
            }
          });

      if (storesToRefresh.length === 0) {
        // Tous les stores sont à jour
        this.isRefreshing.set(false);
        return;
      }

      // Exécuter les rafraîchissements en parallèle avec gestion d'erreur silencieuse
      const refreshPromises = storesToRefresh.map(store => 
        this.refreshStore(store, currentRoute).catch((error: any) => {
          // Logger seulement les erreurs non-réseau
          if (error?.status !== 0 && error?.status !== undefined) {
            console.error(`Error refreshing ${store}:`, error);
          }
          // Ne pas propager l'erreur pour ne pas bloquer les autres stores
        })
      );
      await Promise.allSettled(refreshPromises);

      this.lastRefreshTime.set(new Date());
    } catch (error) {
      console.error('Error refreshing stores:', error);
    } finally {
      this.isRefreshing.set(false);
    }
  }

  /**
   * Rafraîchit un store spécifique
   */
  private async refreshStore(storeName: StoreName, currentRoute?: string): Promise<void> {
    const cacheKey = this.storeCacheMap[storeName];

    try {
      switch (storeName) {
        case 'productStore':
          await this.productStore.loadProducts();
          this.cache.set(cacheKey, this.productStore.products());
          break;

        case 'bcStore':
          await this.bcStore.loadBCs();
          this.cache.set(cacheKey, this.bcStore.bcs());
          break;

        case 'invoiceStore':
          // Charger sélectivement selon la route
          if (currentRoute?.includes('/invoices/purchase')) {
            // Charger uniquement les factures achat
            await this.invoiceStore.loadPurchaseInvoices();
            const purchaseInvoices = this.invoiceStore.purchaseInvoices();
            this.cache.set('invoices-purchase', purchaseInvoices);
          } else if (currentRoute?.includes('/invoices/sales')) {
            // Charger uniquement les factures ventes
            await this.invoiceStore.loadSalesInvoices();
            const salesInvoices = this.invoiceStore.salesInvoices();
            this.cache.set('invoices-sales', salesInvoices);
          } else {
            // Pour les autres routes, charger toutes les factures
            await this.invoiceStore.loadInvoices();
            this.cache.set(cacheKey, this.invoiceStore.invoices());
          }
          break;

        case 'partnerStore':
          await this.partnerStore.loadAll();
          // Mettre en cache clients et suppliers séparément
          this.cache.set('clients', this.partnerStore.clients());
          this.cache.set('suppliers', this.partnerStore.suppliers());
          break;

        case 'dashboardStore':
          // Utiliser refresh qui gère déjà les erreurs silencieusement
          await this.dashboardStore.refresh().catch(() => {
            // Ignorer les erreurs - le cache sera utilisé si disponible
          });
          const kpis = this.dashboardStore.dashboardKPIs();
          if (kpis) {
            this.cache.set(cacheKey, kpis);
          }
          break;
      }
    } catch (error) {
      console.error(`Error refreshing ${storeName}:`, error);
      throw error;
    }
  }

  /**
   * Force le rafraîchissement de stores spécifiques (ignore le cache)
   */
  async forceRefresh(stores: StoreName[]): Promise<void> {
    // Invalider le cache pour ces stores
    stores.forEach(store => {
      const cacheKey = this.storeCacheMap[store];
      this.cache.invalidate(cacheKey);
    });

    await this.refreshStores(stores, true);
  }

  /**
   * Force le rafraîchissement pour la route actuelle
   */
  async forceRefreshCurrentRoute(): Promise<void> {
    const url = this.router.url;
    const basePath = this.extractBasePath(url);
    const stores = this.getStoresForRoute(basePath);
    await this.forceRefresh(stores);
  }

  /**
   * Invalide le cache pour des stores spécifiques
   */
  invalidateCache(stores: StoreName[]): void {
    stores.forEach(store => {
      const cacheKey = this.storeCacheMap[store];
      this.cache.invalidate(cacheKey);
    });
  }
}

