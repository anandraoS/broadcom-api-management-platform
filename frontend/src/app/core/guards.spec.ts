import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { fakeToken } from '../testing';
import { AuthService } from './auth.service';
import { authGuard, roleGuard } from './guards';

describe('guards (UX only - the server is authoritative)', () => {
  let auth: AuthService;
  const run = (g: () => unknown) => TestBed.runInInjectionContext(g as () => unknown);

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthService);
  });

  function setUser(roles: string[]): void {
    sessionStorage.setItem('apiplatform.session', fakeToken({ roles }));
    // re-create the service so it restores the session
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthService);
  }

  it('redirects anonymous users to /login', () => {
    const r = run(() => authGuard({} as never, {} as never)) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(r)).toBe('/login');
    expect(auth.isAuthenticated()).toBeFalse();
  });

  it('allows authenticated users and enforces roles for navigation', () => {
    setUser(['VIEWER']);
    expect(run(() => authGuard({} as never, {} as never))).toBeTrue();
    const denied = run(() => roleGuard('ADMIN')({} as never, {} as never)) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(denied)).toBe('/forbidden');
    setUser(['ADMIN']);
    expect(run(() => roleGuard('ADMIN')({} as never, {} as never))).toBeTrue();
  });
});
