import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * UX guards only: they avoid showing screens the user cannot use. They are NOT a security control - anyone can bypass
 * client code. Every endpoint re-checks authentication and authorization on the server.
 */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isAuthenticated() ? true : inject(Router).createUrlTree(['/login']);
};

export const roleGuard = (...roles: string[]): CanActivateFn => () => {
  const auth = inject(AuthService);
  if (!auth.isAuthenticated()) return inject(Router).createUrlTree(['/login']);
  return auth.hasAnyRole(...roles) ? true : inject(Router).createUrlTree(['/forbidden']);
};
