import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { fakeToken } from '../testing';
import { AuthService } from './auth.service';
import { authInterceptor, describeError, unauthorizedInterceptor } from './interceptors';

describe('interceptors', () => {
  let http: HttpClient;
  let ctl: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor, unauthorizedInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    ctl = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => ctl.verify());

  function signIn(): void {
    auth.login('u', 'p').subscribe();
    ctl.expectOne('/api/v1/auth/token').flush({ accessToken: fakeToken(), tokenType: 'Bearer', expiresIn: 3600, roles: [], team: null });
  }

  it('adds bearer token and correlation id to same-origin API calls', () => {
    signIn();
    http.get('/api/v1/apis').subscribe();
    const r = ctl.expectOne('/api/v1/apis');
    expect(r.request.headers.get('Authorization')).toMatch(/^Bearer /);
    expect(r.request.headers.get('X-Correlation-Id')).toMatch(/^[0-9a-f-]{36}$/);
    r.flush({});
  });

  it('never attaches the token to third-party URLs', () => {
    signIn();
    http.get('https://example.org/data').subscribe();
    const r = ctl.expectOne('https://example.org/data');
    expect(r.request.headers.has('Authorization')).toBeFalse();
    r.flush({});
  });

  it('does not send a token with the login request itself', () => {
    http.post('/api/v1/auth/token', {}).subscribe();
    const r = ctl.expectOne('/api/v1/auth/token');
    expect(r.request.headers.has('Authorization')).toBeFalse();
    r.flush({});
  });

  it('clears the session when the API answers 401', () => {
    signIn();
    spyOn(auth, 'logout').and.callThrough();
    http.get('/api/v1/apis').subscribe({ error: () => undefined });
    ctl.expectOne('/api/v1/apis').flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });
    expect(auth.logout).toHaveBeenCalled();
    expect(auth.isAuthenticated()).toBeFalse();
  });

  it('describeError summarises problem bodies and hides internals', () => {
    const e = new HttpErrorResponse({ status: 422, error: { status: 422, detail: 'Policy validation failed', errors: ['burst must be >= 1'], correlationId: 'abc-123' } });
    expect(describeError(e)).toContain('Policy validation failed');
    expect(describeError(e)).toContain('burst must be >= 1');
    expect(describeError(e)).toContain('ref: abc-123');
    expect(describeError(new HttpErrorResponse({ status: 0 }))).toContain('Cannot reach the server');
    expect(describeError(new Error('boom <script>'))).toBe('Unexpected error');
  });
});
