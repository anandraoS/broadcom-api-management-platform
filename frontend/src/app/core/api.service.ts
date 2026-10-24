import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Api, AuditEvent, Consumer, IncidentReport, KeyIssued, KeyView, OpEvent, PageView, Policy, PolicyConfig, Subscription,
  TrafficSummary, ValidationResult,
} from './models';

function params(o: Record<string, string | number | undefined | null>): HttpParams {
  let p = new HttpParams();
  for (const [k, v] of Object.entries(o)) if (v !== undefined && v !== null && v !== '') p = p.set(k, String(v));
  return p;
}

/** Thin typed wrapper over the control-plane REST API (all authorization is enforced server-side). */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  // --- registry
  searchApis(q: { q?: string; lifecycle?: string; owner?: string; page?: number; size?: number }): Observable<PageView<Api>> {
    return this.http.get<PageView<Api>>('/api/v1/apis', { params: params(q) });
  }
  getApi(id: number): Observable<Api> { return this.http.get<Api>(`/api/v1/apis/${id}`); }
  createApi(body: { slug: string; name: string; ownerTeam: string; description?: string }, idemKey: string): Observable<Api> {
    return this.http.post<Api>('/api/v1/apis', body, { headers: { 'Idempotency-Key': idemKey } });
  }
  updateApi(id: number, body: { name: string; description?: string; version: number }): Observable<Api> {
    return this.http.put<Api>(`/api/v1/apis/${id}`, body);
  }
  transition(id: number, target: string): Observable<Api> { return this.http.post<Api>(`/api/v1/apis/${id}/lifecycle`, { target }); }
  addVersion(id: number, body: { label: string; basePath: string; openapiSpec?: string }): Observable<Api> {
    return this.http.post<Api>(`/api/v1/apis/${id}/versions`, body);
  }
  addTarget(id: number, versionId: number, body: { url: string; weight: number; healthPath?: string }): Observable<Api> {
    return this.http.post<Api>(`/api/v1/apis/${id}/versions/${versionId}/targets`, body);
  }
  removeTarget(id: number, versionId: number, targetId: number): Observable<Api> {
    return this.http.delete<Api>(`/api/v1/apis/${id}/versions/${versionId}/targets/${targetId}`);
  }

  // --- policies
  validatePolicy(c: PolicyConfig): Observable<ValidationResult> { return this.http.post<ValidationResult>('/api/v1/policies/validate', c); }
  policyHistory(apiId: number): Observable<Policy[]> { return this.http.get<Policy[]>(`/api/v1/apis/${apiId}/policies`); }
  submitPolicy(apiId: number, config: PolicyConfig, comment: string): Observable<Policy> {
    return this.http.post<Policy>(`/api/v1/apis/${apiId}/policies`, { config, comment });
  }
  pendingPolicies(): Observable<Policy[]> { return this.http.get<Policy[]>('/api/v1/policies/pending'); }
  approve(id: number): Observable<Policy> { return this.http.post<Policy>(`/api/v1/policies/${id}/approve`, {}); }
  reject(id: number): Observable<Policy> { return this.http.post<Policy>(`/api/v1/policies/${id}/reject`, {}); }
  revert(apiId: number, toVersionNo: number): Observable<Policy> {
    return this.http.post<Policy>(`/api/v1/apis/${apiId}/policies/revert`, { toVersionNo });
  }

  // --- consumers
  searchConsumers(q: { q?: string; page?: number; size?: number }): Observable<PageView<Consumer>> {
    return this.http.get<PageView<Consumer>>('/api/v1/consumers', { params: params(q) });
  }
  createConsumer(body: { name: string; clientId: string; ownerEmail: string }): Observable<Consumer> {
    return this.http.post<Consumer>('/api/v1/consumers', body);
  }
  setConsumerActive(id: number, active: boolean): Observable<Consumer> {
    return this.http.post<Consumer>(`/api/v1/consumers/${id}/${active ? 'enable' : 'disable'}`, {});
  }
  subscriptions(id: number): Observable<Subscription[]> { return this.http.get<Subscription[]>(`/api/v1/consumers/${id}/subscriptions`); }
  subscribe(id: number, apiId: number): Observable<Subscription> {
    return this.http.post<Subscription>(`/api/v1/consumers/${id}/subscriptions`, { apiId });
  }
  cancelSubscription(id: number, subId: number): Observable<Subscription> {
    return this.http.delete<Subscription>(`/api/v1/consumers/${id}/subscriptions/${subId}`);
  }
  keys(id: number): Observable<KeyView[]> { return this.http.get<KeyView[]>(`/api/v1/consumers/${id}/keys`); }
  issueKey(id: number, validDays: number): Observable<KeyIssued> { return this.http.post<KeyIssued>(`/api/v1/consumers/${id}/keys`, { validDays }); }
  revokeKey(id: number, keyId: number): Observable<void> { return this.http.delete<void>(`/api/v1/consumers/${id}/keys/${keyId}`); }

  // --- operations
  audit(q: { entityType?: string; entityId?: string; actor?: string; page?: number; size?: number }): Observable<PageView<AuditEvent>> {
    return this.http.get<PageView<AuditEvent>>('/api/v1/audit', { params: params(q) });
  }
  events(q: { type?: string; route?: string; severity?: string; page?: number; size?: number }): Observable<PageView<OpEvent>> {
    return this.http.get<PageView<OpEvent>>('/api/v1/events', { params: params(q) });
  }
  traffic(windowMinutes: number): Observable<TrafficSummary> {
    return this.http.get<TrafficSummary>('/api/v1/traffic/summary', { params: params({ windowMinutes }) });
  }
  incidents(windowMinutes: number): Observable<IncidentReport> {
    return this.http.get<IncidentReport>('/api/v1/incidents', { params: params({ windowMinutes }) });
  }
}
