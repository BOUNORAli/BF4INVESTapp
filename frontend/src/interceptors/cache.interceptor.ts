import { HttpInterceptorFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { of, tap } from 'rxjs';

interface CacheEntry {
  response: HttpResponse<any>;
  timestamp: number;
  ttl: number; // Time to live in milliseconds
}

interface CacheConfig {
  ttl: number;
  strategy: 'cache-first' | 'network-first' | 'stale-while-revalidate';
  maxAge?: number; // Maximum age before forcing refresh
}

// Cache en mémoire
const cache = new Map<string, CacheEntry>();

// Configuration par endpoint
const cacheConfig: Record<string, CacheConfig> = {
  '/products': { ttl: 5 * 60 * 1000, strategy: 'stale-while-revalidate' }, // 5 minutes
  '/clients': { ttl: 10 * 60 * 1000, strategy: 'cache-first' }, // 10 minutes
  '/suppliers': { ttl: 10 * 60 * 1000, strategy: 'cache-first' }, // 10 minutes
  '/bcs': { ttl: 2 * 60 * 1000, strategy: 'network-first' }, // 2 minutes
  '/factures-achats': { ttl: 2 * 60 * 1000, strategy: 'network-first' }, // 2 minutes
  '/factures-ventes': { ttl: 2 * 60 * 1000, strategy: 'network-first' }, // 2 minutes
  '/dashboard/kpis': { ttl: 1 * 60 * 1000, strategy: 'stale-while-revalidate' }, // 1 minute
  '/settings': { ttl: 30 * 60 * 1000, strategy: 'cache-first' }, // 30 minutes
  '/solde/global': { ttl: 30 * 1000, strategy: 'network-first' }, // 30 seconds
  '/charges': { ttl: 2 * 60 * 1000, strategy: 'network-first' }, // 2 minutes
};

// Endpoints qui ne doivent jamais être mis en cache
const noCacheEndpoints = [
  '/auth/',
  '/files/upload',
  '/import/',
  '/settings/data/delete',
  '/admin/migration/',
];

// Vérifier si une requête doit être mise en cache
function shouldCache(req: HttpRequest<any>): boolean {
  // Ne pas mettre en cache les requêtes non-GET
  if (req.method !== 'GET') {
    return false;
  }

  // Ne pas mettre en cache les endpoints spécifiques
  if (noCacheEndpoints.some(endpoint => req.url.includes(endpoint))) {
    return false;
  }

  return true;
}

// Obtenir la configuration de cache pour une URL
function getCacheConfig(url: string): CacheConfig | null {
  for (const [endpoint, config] of Object.entries(cacheConfig)) {
    if (url.includes(endpoint)) {
      return config;
    }
  }
  return null;
}

// Générer une clé de cache unique pour une requête
function getCacheKey(req: HttpRequest<any>): string {
  const url = req.urlWithParams;
  const method = req.method;
  return `${method}:${url}`;
}

// Vérifier si une entrée de cache est valide
function isCacheValid(entry: CacheEntry, config: CacheConfig): boolean {
  const age = Date.now() - entry.timestamp;
  return age < entry.ttl;
}

// Vérifier si une entrée de cache est stale (mais utilisable)
function isCacheStale(entry: CacheEntry, config: CacheConfig): boolean {
  const age = Date.now() - entry.timestamp;
  const maxAge = config.maxAge || config.ttl * 2; // Par défaut, 2x le TTL
  return age > config.ttl && age < maxAge;
}

// Nettoyer le cache périodiquement
function cleanCache(): void {
  const now = Date.now();
  for (const [key, entry] of cache.entries()) {
    // Supprimer les entrées expirées (plus de 2x le TTL)
    const maxAge = 2 * 60 * 60 * 1000; // 2 heures max
    if (now - entry.timestamp > maxAge) {
      cache.delete(key);
    }
  }
}

// Nettoyer le cache toutes les 5 minutes
setInterval(cleanCache, 5 * 60 * 1000);

export const cacheInterceptor: HttpInterceptorFn = (req, next) => {
  // Si la requête ne doit pas être mise en cache, passer directement
  if (!shouldCache(req)) {
    return next(req);
  }

  const cacheKey = getCacheKey(req);
  const config = getCacheConfig(req.url);

  // Si pas de configuration de cache pour cet endpoint, passer directement
  if (!config) {
    return next(req);
  }

  const cachedEntry = cache.get(cacheKey);

  // Stratégie: Cache-First
  if (config.strategy === 'cache-first') {
    if (cachedEntry && isCacheValid(cachedEntry, config)) {
      return of(cachedEntry.response.clone());
    }

    // Si pas de cache valide, faire la requête et mettre en cache
    return next(req).pipe(
      tap(event => {
        if (event instanceof HttpResponse) {
          cache.set(cacheKey, {
            response: event.clone(),
            timestamp: Date.now(),
            ttl: config.ttl
          });
        }
      })
    );
  }

  // Stratégie: Network-First
  if (config.strategy === 'network-first') {
    return next(req).pipe(
      tap(event => {
        if (event instanceof HttpResponse) {
          cache.set(cacheKey, {
            response: event.clone(),
            timestamp: Date.now(),
            ttl: config.ttl
          });
        }
      })
    );
  }

  // Stratégie: Stale-While-Revalidate
  if (config.strategy === 'stale-while-revalidate') {
    // Si on a un cache valide, retourner immédiatement
    if (cachedEntry && isCacheValid(cachedEntry, config)) {
      return of(cachedEntry.response.clone());
    }

    // Si on a un cache stale, le retourner immédiatement et rafraîchir en arrière-plan
    if (cachedEntry && isCacheStale(cachedEntry, config)) {
      // Rafraîchir en arrière-plan
      next(req).pipe(
        tap(event => {
          if (event instanceof HttpResponse) {
            cache.set(cacheKey, {
              response: event.clone(),
              timestamp: Date.now(),
              ttl: config.ttl
            });
          }
        })
      ).subscribe();

      // Retourner le cache stale immédiatement
      return of(cachedEntry.response.clone());
    }

    // Pas de cache, faire la requête normalement
    return next(req).pipe(
      tap(event => {
        if (event instanceof HttpResponse) {
          cache.set(cacheKey, {
            response: event.clone(),
            timestamp: Date.now(),
            ttl: config.ttl
          });
        }
      })
    );
  }

  // Fallback: pas de cache
  return next(req);
};

// Fonction utilitaire pour invalider le cache
export function invalidateCache(pattern?: string): void {
  if (!pattern) {
    cache.clear();
    return;
  }

  for (const [key] of cache.entries()) {
    if (key.includes(pattern)) {
      cache.delete(key);
    }
  }
}

// Fonction utilitaire pour obtenir les statistiques du cache
export function getCacheStats(): { size: number; keys: string[] } {
  return {
    size: cache.size,
    keys: Array.from(cache.keys())
  };
}
