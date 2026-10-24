import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { Problem } from './models';

/** Adds the bearer token to same-origin API calls only (never to third-party URLs) plus a correlation id. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const isApi = req.url.startsWith('/api/') || req.url.startsWith('/graphql');
  if (!isApi) return next(req);
  const headers: Record<string, string> = { 'X-Correlation-Id': crypto.randomUUID() };
  const token = auth.token();
  if (token && !req.url.endsWith('/auth/token')) headers['Authorization'] = `Bearer ${token}`;
  return next(req.clone({ setHeaders: headers }));
};

/** A 401 means the session is no longer valid: clear it and go to the login page. */
export const unauthorizedInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401 && !req.url.endsWith('/auth/token')) {
        auth.logout();
      }
      return throwError(() => err);
    }),
  );
};

/** Turns any HTTP failure into a short, safe message (never renders raw server bodies as HTML). */
export function describeError(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    const p = err.error as Partial<Problem> | null;
    if (p && typeof p === 'object') {
      const lines = [p.detail ?? `Request failed (${err.status})`];
      if (Array.isArray(p.errors) && p.errors.length) lines.push(...p.errors.map(String));
      if (p.correlationId) lines.push(`ref: ${p.correlationId}`);
      return lines.join(' - ');
    }
    if (err.status === 0) return 'Cannot reach the server. Check your connection.';
    return `Request failed (${err.status})`;
  }
  return 'Unexpected error';
}
