import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { fakeToken } from '../testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let svc: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    svc = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('decodes claims for display and starts unauthenticated', () => {
    expect(svc.isAuthenticated()).toBeFalse();
    const u = AuthService.decode(fakeToken({ sub: 'alice', roles: ['ADMIN'], team: 'orders' }));
    expect(u?.username).toBe('alice');
    expect(u?.roles).toEqual(['ADMIN']);
    expect(u?.team).toBe('orders');
  });

  it('rejects malformed tokens instead of throwing', () => {
    expect(AuthService.decode('not-a-jwt')).toBeNull();
    expect(AuthService.decode('a.b.c')).toBeNull();
  });

  it('logs in, exposes roles, and stores the session for the tab only', () => {
    const token = fakeToken({ sub: 'approver', roles: ['POLICY_APPROVER'] });
    svc.login('approver', 'pw').subscribe();
    const req = http.expectOne('/api/v1/auth/token');
    expect(req.request.method).toBe('POST');
    req.flush({ accessToken: token, tokenType: 'Bearer', expiresIn: 3600, roles: ['POLICY_APPROVER'], team: 'platform' });
    expect(svc.isAuthenticated()).toBeTrue();
    expect(svc.hasAnyRole('POLICY_APPROVER')).toBeTrue();
    expect(svc.hasAnyRole('ADMIN')).toBeFalse();
    expect(sessionStorage.getItem('apiplatform.session')).toBe(token);
    expect(localStorage.getItem('apiplatform.session')).toBeNull(); // never persisted across browser sessions
  });

  it('treats an expired token as unauthenticated and hides it', () => {
    svc.login('x', 'y').subscribe();
    http.expectOne('/api/v1/auth/token').flush({ accessToken: fakeToken({ expInSeconds: -10 }), tokenType: 'Bearer', expiresIn: 0, roles: [], team: null });
    expect(svc.isAuthenticated()).toBeFalse();
    expect(svc.token()).toBeNull();
  });

  it('clears the session on logout', () => {
    svc.login('a', 'b').subscribe();
    http.expectOne('/api/v1/auth/token').flush({ accessToken: fakeToken(), tokenType: 'Bearer', expiresIn: 3600, roles: [], team: null });
    svc.logout(false);
    expect(svc.isAuthenticated()).toBeFalse();
    expect(sessionStorage.getItem('apiplatform.session')).toBeNull();
  });
});
