import type {
  Api, AuditEvent, Consumer, IncidentReport, KeyIssued, KeyView, OpEvent, PageView, Policy, PolicyConfig, Problem,
  Subscription, TokenResponse, TrafficSummary, ValidationResult,
} from './models';

/** Error carrying the RFC 7807 problem body; its message is already safe to show to users. */
export class ApiError extends Error {
  constructor(public status: number, public problem: Partial<Problem> | null, message: string) {
    super(message);
  }
  /** Rule/field messages (e.g. SSRF destination reasons). */
  get details(): string[] { return this.problem?.errors ?? []; }
}

export function describeError(err: unknown): string {
  if (err instanceof ApiError) return err.message;
  return 'Unexpected error';
}

/** Session token lives in a module variable + sessionStorage (cleared with the tab). See docs/security.md for the XSS trade-off. */
export const session = {
  key: 'apiplatform.session',
  get token(): string | null { return sessionStorage.getItem(this.key); },
  set(t: string): void { sessionStorage.setItem(this.key, t); },
  clear(): void { sessionStorage.removeItem(this.key); },
};

let onUnauthorized: () => void = () => undefined;
export function setUnauthorizedHandler(fn: () => void): void { onUnauthorized = fn; }

function qs(o: Record<string, string | number | undefined | null>): string {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(o)) if (v !== undefined && v !== null && v !== '') p.set(k, String(v));
  const s = p.toString();
  return s ? `?${s}` : '';
}

async function request<T>(method: string, url: string, body?: unknown, headers: Record<string, string> = {}, signal?: AbortSignal): Promise<T> {
  // token is attached to same-origin API calls only (this client only ever calls /api/ paths)
  const h: Record<string, string> = { 'X-Correlation-Id': crypto.randomUUID(), ...headers };
  const token = session.token;
  if (token && !url.endsWith('/auth/token')) h['Authorization'] = `Bearer ${token}`;
  if (body !== undefined) h['Content-Type'] = 'application/json';
  let res: Response;
  try {
    res = await fetch(url, { method, headers: h, body: body === undefined ? undefined : JSON.stringify(body), signal });
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e;
    throw new ApiError(0, null, 'Cannot reach the server. Check your connection.');
  }
  if (res.ok) {
    if (res.status === 204) return undefined as T;
    const text = await res.text();
    return (text ? JSON.parse(text) : undefined) as T;
  }
  if (res.status === 401 && !url.endsWith('/auth/token')) onUnauthorized();
  let problem: Partial<Problem> | null = null;
  try { problem = (await res.json()) as Partial<Problem>; } catch { /* non-JSON body */ }
  const lines = [problem?.detail ?? `Request failed (${res.status})`];
  if (problem?.errors?.length) lines.push(...problem.errors.map(String));
  if (problem?.correlationId) lines.push(`ref: ${problem.correlationId}`);
  throw new ApiError(res.status, problem, lines.join(' - '));
}

const get = <T,>(url: string, signal?: AbortSignal) => request<T>('GET', url, undefined, {}, signal);
const post = <T,>(url: string, body?: unknown, headers?: Record<string, string>) => request<T>('POST', url, body ?? {}, headers);

/** Typed wrapper over the control-plane REST API. All authorization is enforced server-side. */
export const api = {
  login: (username: string, password: string) => post<TokenResponse>('/api/v1/auth/token', { username, password }),

  searchApis: (q: { q?: string; lifecycle?: string; owner?: string; page?: number; size?: number }, signal?: AbortSignal) =>
    get<PageView<Api>>(`/api/v1/apis${qs(q)}`, signal),
  getApi: (id: number, signal?: AbortSignal) => get<Api>(`/api/v1/apis/${id}`, signal),
  createApi: (body: { slug: string; name: string; ownerTeam: string; description?: string }, idemKey: string) =>
    post<Api>('/api/v1/apis', body, { 'Idempotency-Key': idemKey }),
  updateApi: (id: number, body: { name: string; description?: string; version: number }) => request<Api>('PUT', `/api/v1/apis/${id}`, body),
  transition: (id: number, target: string) => post<Api>(`/api/v1/apis/${id}/lifecycle`, { target }),
  addVersion: (id: number, body: { label: string; basePath: string }) => post<Api>(`/api/v1/apis/${id}/versions`, body),
  addTarget: (id: number, versionId: number, body: { url: string; weight: number }) => post<Api>(`/api/v1/apis/${id}/versions/${versionId}/targets`, body),
  removeTarget: (id: number, versionId: number, targetId: number) => request<Api>('DELETE', `/api/v1/apis/${id}/versions/${versionId}/targets/${targetId}`),

  validatePolicy: (c: PolicyConfig) => post<ValidationResult>('/api/v1/policies/validate', c),
  policyHistory: (apiId: number, signal?: AbortSignal) => get<Policy[]>(`/api/v1/apis/${apiId}/policies`, signal),
  submitPolicy: (apiId: number, config: PolicyConfig, comment: string) => post<Policy>(`/api/v1/apis/${apiId}/policies`, { config, comment }),
  pendingPolicies: (signal?: AbortSignal) => get<Policy[]>('/api/v1/policies/pending', signal),
  approve: (id: number) => post<Policy>(`/api/v1/policies/${id}/approve`),
  reject: (id: number) => post<Policy>(`/api/v1/policies/${id}/reject`),
  revert: (apiId: number, toVersionNo: number) => post<Policy>(`/api/v1/apis/${apiId}/policies/revert`, { toVersionNo }),

  searchConsumers: (q: { q?: string; page?: number; size?: number }, signal?: AbortSignal) => get<PageView<Consumer>>(`/api/v1/consumers${qs(q)}`, signal),
  createConsumer: (body: { name: string; clientId: string; ownerEmail: string }) => post<Consumer>('/api/v1/consumers', body),
  subscriptions: (id: number, signal?: AbortSignal) => get<Subscription[]>(`/api/v1/consumers/${id}/subscriptions`, signal),
  subscribe: (id: number, apiId: number) => post<Subscription>(`/api/v1/consumers/${id}/subscriptions`, { apiId }),
  cancelSubscription: (id: number, subId: number) => request<Subscription>('DELETE', `/api/v1/consumers/${id}/subscriptions/${subId}`),
  keys: (id: number, signal?: AbortSignal) => get<KeyView[]>(`/api/v1/consumers/${id}/keys`, signal),
  issueKey: (id: number, validDays: number) => post<KeyIssued>(`/api/v1/consumers/${id}/keys`, { validDays }),
  revokeKey: (id: number, keyId: number) => request<void>('DELETE', `/api/v1/consumers/${id}/keys/${keyId}`),

  audit: (q: { entityType?: string; entityId?: string; actor?: string; page?: number; size?: number }, signal?: AbortSignal) =>
    get<PageView<AuditEvent>>(`/api/v1/audit${qs(q)}`, signal),
  events: (q: { type?: string; route?: string; severity?: string; page?: number; size?: number }, signal?: AbortSignal) =>
    get<PageView<OpEvent>>(`/api/v1/events${qs(q)}`, signal),
  traffic: (windowMinutes: number, signal?: AbortSignal) => get<TrafficSummary>(`/api/v1/traffic/summary${qs({ windowMinutes })}`, signal),
  incidents: (windowMinutes: number, signal?: AbortSignal) => get<IncidentReport>(`/api/v1/incidents${qs({ windowMinutes })}`, signal),
};
