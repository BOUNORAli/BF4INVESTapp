import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError, switchMap, BehaviorSubject, filter, take } from 'rxjs';
import { ToastService } from '../services/toast.service';
import { AuthService } from '../services/auth.service';

let isRefreshing = false;
const refreshTokenSubject = new BehaviorSubject<string | null>(null);

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toastService = inject(ToastService);
  const authService = inject(AuthService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // Ignorer les erreurs si la requête a été annulée (AbortError)
      if (error.error instanceof Error && error.error.name === 'AbortError') {
        return throwError(() => error);
      }

      // Gestion du Refresh Token pour les erreurs 401
      if (error.status === 401 && !req.url.includes('/auth/login') && !req.url.includes('/auth/refresh')) {
        if (!isRefreshing) {
          isRefreshing = true;
          refreshTokenSubject.next(null);

          return authService.refreshToken().pipe(
            switchMap(() => {
              isRefreshing = false;
              refreshTokenSubject.next('ok');
              return next(req);
            }),
            catchError((refreshError) => {
              isRefreshing = false;
              authService.logout();
              toastService.showToast('Session expirée. Veuillez vous reconnecter.', 'error');
              return throwError(() => refreshError);
            })
          );
        } else {
          return refreshTokenSubject.pipe(
            filter(v => v !== null),
            take(1),
            switchMap(() => next(req))
          );
        }
      }

      let errorMessage = 'Une erreur est survenue';

      if (error.error instanceof ErrorEvent) {
        // Erreur côté client
        errorMessage = error.error.message;
      } else {
        // Erreur côté serveur
        if (error.status === 401) {
          // Si on arrive ici, c'est que le refresh a échoué ou c'était une requête login/refresh
          errorMessage = 'Session expirée ou identifiants incorrects.';
          // Logout handled elsewhere if it was a protected route, otherwise login failed
        } else if (error.status === 403) {
          errorMessage = 'Accès refusé. Vous n\'avez pas les droits nécessaires.';
        } else if (error.status === 404) {
          errorMessage = 'Ressource non trouvée.';
        } else if (error.status === 500) {
          errorMessage = 'Erreur serveur interne. Veuillez réessayer plus tard.';
        } else if (error.error?.error) {
          // Backend retourne {error: "message"}
          errorMessage = error.error.error;
        } else if (error.error?.message) {
          // Backend retourne {message: "message"}
          errorMessage = error.error.message;
        } else if (typeof error.error === 'string') {
          errorMessage = error.error;
        }
      }

      // Ne pas afficher de toast pour les erreurs réseau (status: 0) - ce sont souvent des problèmes temporaires
      // Le cache interceptor gère déjà le fallback sur le cache
      const isNetworkError = error.status === 0;
      const isBackgroundRequest = req.url.includes('/dashboard/kpis') && 
                                  (req.headers.get('X-Background-Request') === 'true' || 
                                   req.headers.get('X-Silent-Request') === 'true');
      
      // Afficher le toast seulement pour les erreurs importantes (pas les erreurs réseau ou les requêtes en arrière-plan)
      if (!isNetworkError && !isBackgroundRequest) {
        // Afficher le toast (sauf si 401 géré par refresh qui a échoué - déjà affiché)
        // Si c'est une 401 sur login, on affiche.
        // Si c'est une 401 sur refresh, on affiche.
        // Si c'est une 401 sur api classique et refresh a fail, le catchError du refresh l'a affiché.
        // Donc ici on affiche si ce n'est PAS une requête qui a tenté le refresh (login ou refresh endpoint)
        if ((req.url.includes('/auth/login') || req.url.includes('/auth/refresh')) || error.status !== 401) {
           // Avoid duplicate toasts if possible, but better safe than sorry for login errors
           if (!req.url.includes('/auth/refresh')) { 
              toastService.showToast(errorMessage, 'error');
           }
        }
      }
      
      // Logger seulement les erreurs non-réseau
      // Les erreurs réseau (status: 0) sont gérées silencieusement avec fallback sur le cache
      if (!isNetworkError) {
        // Erreur non-réseau : toujours logger
        console.error('API Error (Interceptor):', error);
      } else {
        // Erreur réseau : logger en mode debug seulement (le cache interceptor gère le fallback)
        console.debug('Network error (will use cache if available):', req.url);
      }
      
      return throwError(() => error);
    })
  );
};
