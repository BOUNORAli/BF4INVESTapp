import { HttpInterceptorFn } from '@angular/common/http';

/**
 * Ensures all outgoing requests send cookies (e.g. httpOnly auth cookies).
 * Required when using cookie-based auth with a cross-origin API.
 */
export const credentialsInterceptor: HttpInterceptorFn = (req, next) => {
  const reqWithCredentials = req.clone({ withCredentials: true });
  return next(reqWithCredentials);
};
