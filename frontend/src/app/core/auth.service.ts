import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { TokenResponse } from './models';

export interface SessionUser { username: string; roles: string[]; team: string | null; expiresAt: number }

const STORAGE_KEY = 'apiplatform.session';

/**
 * UI-side session state. The access token is a short-lived bearer token issued by the identity provider.
 * It is kept in sessionStorage (cleared when the tab closes). Trade-off: any XSS could read it - the production design
 * is an OIDC authorization-code flow with PKCE and an in-memory token (see docs/security.md). Roles here only shape
 * the navigation; the backend authorizes every request and is the single source of truth.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly _token = signal<string | null>(null);
  readonly user = signal<SessionUser | null>(null);
  readonly isAuthenticated = computed(() => {
    const u = this.user();
    return !!u && u.expiresAt > Date.now();
  });

  constructor() {
    this.restore();
  }

  token(): string | null {
    return this.isAuthenticated() ? this._token() : null;
  }

  login(username: string, password: string): Observable<TokenResponse> {
    return this.http.post<TokenResponse>('/api/v1/auth/token', { username, password }).pipe(
      tap((r) => {
        const user = AuthService.decode(r.accessToken);
        if (user) this.setSession(r.accessToken, user);
      }),
    );
  }

  logout(redirect = true): void {
    this._token.set(null);
    this.user.set(null);
    sessionStorage.removeItem(STORAGE_KEY);
    if (redirect) void this.router.navigate(['/login']);
  }

  hasAnyRole(...roles: string[]): boolean {
    const u = this.user();
    return !!u && roles.some((r) => u.roles.includes(r));
  }

  private setSession(token: string, user: SessionUser): void {
    this._token.set(token);
    this.user.set(user);
    sessionStorage.setItem(STORAGE_KEY, token);
  }

  private restore(): void {
    const t = sessionStorage.getItem(STORAGE_KEY);
    const u = t ? AuthService.decode(t) : null;
    if (t && u && u.expiresAt > Date.now()) {
      this._token.set(t);
      this.user.set(u);
    } else {
      sessionStorage.removeItem(STORAGE_KEY);
    }
  }

  /** Reads claims for display only. The signature is verified by the backend, never here. */
  static decode(token: string): SessionUser | null {
    try {
      const payload = token.split('.')[1];
      const json = decodeURIComponent(
        atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
          .split('')
          .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join(''),
      );
      const c = JSON.parse(json) as { sub?: string; roles?: string[]; team?: string; exp?: number };
      if (!c.sub || !c.exp) return null;
      return { username: c.sub, roles: c.roles ?? [], team: c.team ?? null, expiresAt: c.exp * 1000 };
    } catch {
      return null;
    }
  }
}
