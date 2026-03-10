import { HttpInterceptorFn } from '@angular/common/http';

/**
 * Ensures all outgoing requests:
 * - envoient les cookies (httpOnly) vers l'API
 * - ajoutent un header Authorization Bearer si un token fallback est disponible
 *   dans localStorage (utile pour Safari / mobiles qui bloquent les cookies tiers).
 */
export const credentialsInterceptor: HttpInterceptorFn = (req, next) => {
  // Toujours envoyer les cookies
  let updatedReq = req.clone({ withCredentials: true });

  try {
    const accessToken = typeof window !== 'undefined'
      ? window.localStorage.getItem('bf4_token_access')
      : null;

    if (accessToken && !updatedReq.headers.has('Authorization')) {
      updatedReq = updatedReq.clone({
        setHeaders: {
          Authorization: `Bearer ${accessToken}`
        }
      });
    }
  } catch {
    // En cas d'erreur d'accès à localStorage (SSR / mode privé strict), on ignore simplement
  }

  return next(updatedReq);
};
