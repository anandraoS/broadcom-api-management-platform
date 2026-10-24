import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { fakeToken } from '../testing';
import { LoginPage } from './login';

describe('LoginPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ imports: [LoginPage], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
  });

  it('does not call the server when the form is invalid', () => {
    const f = TestBed.createComponent(LoginPage);
    f.detectChanges();
    const ctl = TestBed.inject(HttpTestingController);
    f.componentInstance.submit();
    f.detectChanges();
    ctl.expectNone('/api/v1/auth/token');
    expect(f.nativeElement.querySelectorAll('mat-error').length).toBeGreaterThan(0);
  });

  it('shows one generic message on failure (no user enumeration)', () => {
    const f = TestBed.createComponent(LoginPage);
    f.detectChanges();
    const ctl = TestBed.inject(HttpTestingController);
    const c = f.componentInstance as unknown as { form: { setValue: (v: unknown) => void }; submit: () => void };
    c.form.setValue({ username: 'ghost', password: 'wrong' });
    c.submit();
    ctl.expectOne('/api/v1/auth/token').flush({ code: 'invalid_credentials', detail: 'Invalid username or password' }, { status: 401, statusText: 'Unauthorized' });
    f.detectChanges();
    const alert = f.nativeElement.querySelector('[role="alert"]') as HTMLElement;
    expect(alert.textContent).toContain('Sign-in failed');
    expect(alert.textContent).not.toContain('ghost');
  });

  it('navigates to the dashboard after a successful login', () => {
    const f = TestBed.createComponent(LoginPage);
    f.detectChanges();
    const router = TestBed.inject(Router);
    const nav = spyOn(router, 'navigateByUrl').and.resolveTo(true);
    const ctl = TestBed.inject(HttpTestingController);
    const c = f.componentInstance as unknown as { form: { setValue: (v: unknown) => void }; submit: () => void };
    c.form.setValue({ username: 'admin', password: 'pw' });
    c.submit();
    ctl.expectOne('/api/v1/auth/token').flush({ accessToken: fakeToken({ roles: ['ADMIN'] }), tokenType: 'Bearer', expiresIn: 3600, roles: ['ADMIN'], team: 'platform' });
    expect(nav).toHaveBeenCalledWith('/dashboard');
  });
});
