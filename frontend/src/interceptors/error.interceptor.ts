import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError, switchMap, BehaviorSubject, filter, take } from 'rxjs';
import { ToastService } from '../services/toast.service';
import { AuthService } from '../services/auth.service';
import { Router } from '@angular/router';

let isRefreshing = false;
const refreshTokenSubject = new BehaviorSubject<string | null>(null);

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toastService = inject(ToastService);
  const authService = inject(AuthService);
  const router = inject(Router);

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
            switchMap((response: any) => {
              isRefreshing = false;
              const newToken = response.token;
              refreshTokenSubject.next(newToken);
              
              // Cloner la requête avec le nouveau token
              const authReq = req.clone({
                setHeaders: { Authorization: `Bearer ${newToken}` }
              });
              return next(authReq);
            }),
            catchError((refreshError) => {
              isRefreshing = false;
              authService.logout();
              toastService.showToast('Session expirée. Veuillez vous reconnecter.', 'error');
              return throwError(() => refreshError);
            })
          );
        } else {
          // Si un refresh est déjà en cours, attendre qu'il se termine
          return refreshTokenSubject.pipe(
            filter(token => token !== null),
            take(1),
            switchMap(token => {
              const authReq = req.clone({
                setHeaders: { Authorization: `Bearer ${token}` }
              });
              return next(authReq);
            })
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
        } else if (error.error?.message) {
          errorMessage = error.error.message;
        } else if (typeof error.error === 'string') {
            errorMessage = error.error;
        }
      }

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
      
      console.error('API Error (Interceptor):', error);
      return throwError(() => error);
    })
  );
};
