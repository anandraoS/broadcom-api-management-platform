import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { fakeToken } from './testing';
import { Shell } from './shell';

describe('Shell navigation', () => {
  function render(roles: string[]): HTMLElement {
    sessionStorage.setItem('apiplatform.session', fakeToken({ sub: 'u', roles }));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ imports: [Shell], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    const f = TestBed.createComponent(Shell);
    f.detectChanges();
    return f.nativeElement as HTMLElement;
  }

  afterEach(() => sessionStorage.clear());

  it('shows approvals and audit only to privileged roles', () => {
    const viewer = render(['VIEWER']).textContent ?? '';
    expect(viewer).toContain('API registry');
    expect(viewer).not.toContain('Policy approvals');
    expect(viewer).not.toContain('Audit history');

    const approver = render(['POLICY_APPROVER']).textContent ?? '';
    expect(approver).toContain('Policy approvals');
    expect(approver).toContain('Audit history');
  });

  it('shows the signed-in user and a sign-out control', () => {
    const el = render(['OPERATOR']);
    expect(el.querySelector('[data-testid="whoami"]')?.textContent).toContain('u (OPERATOR)');
    expect(Array.from(el.querySelectorAll('button')).some((b) => b.textContent?.includes('Sign out'))).toBeTrue();
  });
});
