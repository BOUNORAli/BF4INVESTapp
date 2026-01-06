import { Injectable, inject } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';

interface RoutePrefetchConfig {
  route: string;
  priority: 'high' | 'medium' | 'low';
  preloadOnHover?: boolean;
  preloadDelay?: number; // ms
}

/**
 * Service de prefetching intelligent des routes
 * Précharge les routes probables pour navigation instantanée
 */
@Injectable({
  providedIn: 'root'
})
export class RoutePrefetchService {
  private router = inject(Router);
  
  // Routes fréquemment visitées avec priorité
  private routeConfigs: RoutePrefetchConfig[] = [
    { route: '/dashboard', priority: 'high' },
    { route: '/bc', priority: 'high' },
    { route: '/invoices/purchase', priority: 'high' },
    { route: '/invoices/sales', priority: 'high' },
    { route: '/products', priority: 'high' },
    { route: '/clients', priority: 'medium' },
    { route: '/settings', priority: 'low' },
    { route: '/prevision', priority: 'medium' },
    { route: '/charges', priority: 'medium' },
  ];

  // Routes déjà préchargées
  private prefetchedRoutes = new Set<string>();
  
  // Timer pour prefetch différé
  private prefetchTimers = new Map<string, any>();

  constructor() {
    // Précharger les routes critiques au démarrage (après un délai)
    setTimeout(() => {
      this.prefetchHighPriorityRoutes();
    }, 2000);

    // Précharger les routes liées à la navigation actuelle
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe((event: any) => {
      this.prefetchRelatedRoutes(event.urlAfterRedirects);
    });
  }

  /**
   * Précharge les routes haute priorité
   */
  private prefetchHighPriorityRoutes(): void {
    const highPriorityRoutes = this.routeConfigs
      .filter(config => config.priority === 'high')
      .map(config => config.route);

    highPriorityRoutes.forEach(route => {
      this.prefetchRoute(route);
    });
  }

  /**
   * Précharge les routes liées à la route actuelle
   */
  private prefetchRelatedRoutes(currentRoute: string): void {
    // Routes liées par contexte
    const relatedRoutes: Record<string, string[]> = {
      '/dashboard': ['/bc', '/invoices/purchase', '/invoices/sales'],
      '/bc': ['/bc/new', '/products', '/clients'],
      '/invoices/purchase': ['/bc', '/clients'],
      '/invoices/sales': ['/bc', '/clients'],
      '/products': ['/bc/new'],
      '/clients': ['/bc/new', '/invoices/purchase', '/invoices/sales'],
    };

    const related = relatedRoutes[currentRoute];
    if (related) {
      related.forEach(route => {
        setTimeout(() => this.prefetchRoute(route), 500);
      });
    }
  }

  /**
   * Précharge une route spécifique
   */
  prefetchRoute(route: string): void {
    if (this.prefetchedRoutes.has(route)) {
      return; // Déjà préchargée
    }

    // Trouver la configuration
    const config = this.routeConfigs.find(c => c.route === route);
    if (!config) {
      return;
    }

    // Précharger en utilisant le router
    this.router.navigate([route], { skipLocationChange: true }).then(() => {
      this.prefetchedRoutes.add(route);
    }).catch(() => {
      // Ignorer les erreurs de navigation (route peut ne pas exister)
    });
  }

  /**
   * Précharge une route au hover (avec délai)
   */
  prefetchOnHover(route: string, delay: number = 200): void {
    // Annuler le timer précédent si existe
    const existingTimer = this.prefetchTimers.get(route);
    if (existingTimer) {
      clearTimeout(existingTimer);
    }

    // Créer un nouveau timer
    const timer = setTimeout(() => {
      this.prefetchRoute(route);
      this.prefetchTimers.delete(route);
    }, delay);

    this.prefetchTimers.set(route, timer);
  }

  /**
   * Annule un prefetch en attente
   */
  cancelPrefetch(route: string): void {
    const timer = this.prefetchTimers.get(route);
    if (timer) {
      clearTimeout(timer);
      this.prefetchTimers.delete(route);
    }
  }

  /**
   * Vérifie si une route est déjà préchargée
   */
  isPrefetched(route: string): boolean {
    return this.prefetchedRoutes.has(route);
  }

  /**
   * Obtient les statistiques de prefetch
   */
  getStats(): { prefetched: number; total: number; routes: string[] } {
    return {
      prefetched: this.prefetchedRoutes.size,
      total: this.routeConfigs.length,
      routes: Array.from(this.prefetchedRoutes)
    };
  }
}

