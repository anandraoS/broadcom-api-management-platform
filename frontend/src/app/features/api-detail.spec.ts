import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Api } from '../core/models';
import { fakeToken } from '../testing';
import { ApiDetailPage } from './api-detail';

const API: Api = {
  id: 5, slug: 'orders', name: 'Orders', ownerTeam: 'orders', lifecycle: 'ACTIVE', description: 'd', version: 3,
  createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
  versions: [{ id: 9, label: 'v1', basePath: '/orders/v1', status: 'ACTIVE', hasSpec: false, targets: [] }],
};

describe('ApiDetailPage', () => {
  function create(roles: string[], team: string) {
    sessionStorage.setItem('apiplatform.session', fakeToken({ sub: 'u', roles, team }));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ imports: [ApiDetailPage], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    const f = TestBed.createComponent(ApiDetailPage);
    f.componentRef.setInput('id', '5');
    f.detectChanges();
    const ctl = TestBed.inject(HttpTestingController);
    ctl.expectOne('/api/v1/apis/5').flush(API);
    f.detectChanges();
    return { f, ctl, el: f.nativeElement as HTMLElement };
  }
  afterEach(() => sessionStorage.clear());

  it('renders read-only for an owner of another team (server still enforces)', () => {
    const { el } = create(['API_OWNER'], 'payments');
    expect(el.textContent).toContain('Read-only');
    expect(el.querySelector('form[aria-label="Edit API"] button[type="submit"]')).toBeNull();
  });

  it('offers edit controls to the owning team', () => {
    const { el } = create(['API_OWNER'], 'orders');
    expect(el.textContent).not.toContain('Read-only');
    expect(el.querySelector('form[aria-label="Edit API"] button[type="submit"]')).not.toBeNull();
  });

  it('lists only lifecycle transitions that the state machine allows', () => {
    const { el } = create(['ADMIN'], 'platform');
    const labels = Array.from(el.querySelectorAll('button')).map((b) => b.textContent?.trim());
    expect(labels).toContain('Move to INACTIVE');
    expect(labels).toContain('Move to DEPRECATED');
    expect(labels).not.toContain('Move to ACTIVE'); // already ACTIVE
  });

  it('shows the destination-policy errors when the server rejects a backend target (SSRF)', () => {
    const { f, ctl } = create(['ADMIN'], 'platform');
    // mat-tab renders the content of the active tab only: open "Versions & targets" first
    (f.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('.mdc-tab')[1].click();
    f.detectChanges();
    const c = f.componentInstance as unknown as { targetForm: { setValue: (v: unknown) => void }; addTarget: (a: Api, v: number) => void };
    c.targetForm.setValue({ url: 'http://169.254.169.254', weight: 1 });
    c.addTarget(API, 9);
    ctl.expectOne('/api/v1/apis/5/versions/9/targets').flush(
      { status: 422, code: 'unsafe_destination', detail: 'Backend destination rejected', errors: ['target host is not in the destination allowlist: 169.254.169.254'] },
      { status: 422, statusText: 'Unprocessable Entity' });
    f.detectChanges();
    const alert = (f.nativeElement as HTMLElement).querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('destination allowlist');
  });

  it('blocks submitting an invalid policy form without calling the server', () => {
    const { f, ctl } = create(['ADMIN'], 'platform');
    const c = f.componentInstance as unknown as { policyForm: { patchValue: (v: unknown) => void }; submitPolicy: (a: Api) => void };
    c.policyForm.patchValue({ rateLimitPerMinute: 0, timeoutMs: 5 });
    c.submitPolicy(API);
    ctl.expectNone('/api/v1/apis/5/policies');
    expect((f.componentInstance as unknown as { policyForm: { invalid: boolean } }).policyForm.invalid).toBeTrue();
  });
});
