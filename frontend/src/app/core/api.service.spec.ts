import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiService } from './api.service';
import { PolicyConfig } from './models';

describe('ApiService', () => {
  let svc: ApiService;
  let ctl: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    svc = TestBed.inject(ApiService);
    ctl = TestBed.inject(HttpTestingController);
  });
  afterEach(() => ctl.verify());

  it('sends only non-empty search filters as query params', () => {
    svc.searchApis({ q: 'ord', lifecycle: '', owner: undefined, page: 0, size: 20 }).subscribe();
    const r = ctl.expectOne((req) => req.url === '/api/v1/apis');
    expect(r.request.params.get('q')).toBe('ord');
    expect(r.request.params.has('lifecycle')).toBeFalse();
    expect(r.request.params.has('owner')).toBeFalse();
    expect(r.request.params.get('size')).toBe('20');
    r.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('sends an Idempotency-Key when creating an API', () => {
    svc.createApi({ slug: 'orders', name: 'Orders', ownerTeam: 'orders' }, 'key-12345678').subscribe();
    const r = ctl.expectOne('/api/v1/apis');
    expect(r.request.method).toBe('POST');
    expect(r.request.headers.get('Idempotency-Key')).toBe('key-12345678');
    r.flush({});
  });

  it('posts policy config for dry-run validation without a state-changing verb on other resources', () => {
    const cfg: PolicyConfig = { auth: 'API_KEY', requiredRole: null, allowedMethods: ['GET'], rateLimitPerMinute: 10, burst: 5, rateAlgorithm: 'TOKEN_BUCKET', timeoutMs: 1000, maxRequestBytes: 10, ipAllowCidrs: null, retryEnabled: false };
    svc.validatePolicy(cfg).subscribe();
    const r = ctl.expectOne('/api/v1/policies/validate');
    expect(r.request.body).toEqual(cfg);
    r.flush({ valid: true, errors: [] });
  });

  it('uses the expected REST paths for approvals and revert', () => {
    svc.approve(7).subscribe();
    ctl.expectOne('/api/v1/policies/7/approve').flush({});
    svc.revert(3, 1).subscribe();
    const r = ctl.expectOne('/api/v1/apis/3/policies/revert');
    expect(r.request.body).toEqual({ toVersionNo: 1 });
    r.flush({});
  });
});
