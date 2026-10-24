export type Lifecycle = 'DRAFT' | 'ACTIVE' | 'INACTIVE' | 'DEPRECATED';
export type PolicyStatus = 'PENDING_APPROVAL' | 'ACTIVE' | 'SUPERSEDED' | 'REJECTED';
export type AuthType = 'NONE' | 'API_KEY' | 'JWT';
export type RateAlgorithm = 'FIXED_WINDOW' | 'SLIDING_WINDOW' | 'TOKEN_BUCKET';

export interface PageView<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Target { id: number; url: string; weight: number; healthPath: string }
export interface ApiVersion { id: number; label: string; basePath: string; status: Lifecycle; hasSpec: boolean; targets: Target[] }
export interface Api {
  id: number; slug: string; name: string; ownerTeam: string; lifecycle: Lifecycle; description: string | null;
  version: number; createdAt: string; updatedAt: string; versions: ApiVersion[];
}

export interface PolicyConfig {
  auth: AuthType;
  requiredRole: string | null;
  allowedMethods: string[];
  rateLimitPerMinute: number;
  burst: number;
  rateAlgorithm: RateAlgorithm;
  timeoutMs: number;
  maxRequestBytes: number;
  ipAllowCidrs: string[] | null;
  retryEnabled: boolean;
}

export interface Policy {
  id: number; apiId: number; versionNo: number; status: PolicyStatus; config: PolicyConfig;
  submittedBy: string; approvedBy: string | null; comment: string | null; createdAt: string; decidedAt: string | null;
}

export interface ValidationResult { valid: boolean; errors: string[] }

export interface Consumer { id: number; name: string; clientId: string; ownerEmail: string; active: boolean; createdAt: string }
export interface Subscription { id: number; consumerId: number; apiId: number; apiSlug: string; status: string }
export interface KeyView { id: number; keyPrefix: string; expiresAt: string; revoked: boolean; createdAt: string }
export interface KeyIssued { id: number; keyPrefix: string; apiKey: string; expiresAt: string }

export interface AuditEvent { id: number; ts: string; actor: string; action: string; entityType: string; entityId: string; detail: string | null; correlationId: string | null }
export interface OpEvent { id: number; ts: string; type: string; severity: string; routeSlug: string | null; message: string; correlationId: string | null }

export interface RouteSummary {
  route: string; requests: number; errors4xx: number; errors5xx: number; errorRate: number;
  avgLatencyMs: number; maxLatencyMs: number; rateLimited: number; authFailures: number; timeouts: number;
}
export interface TrafficSummary { windowMinutes: number; totalRequests: number; overallErrorRate: number; routes: RouteSummary[] }

export interface Finding { rule: string; severity: string; route: string | null; summary: string; evidence: Record<string, unknown>; suggestedSteps: string[] }
export interface IncidentReport {
  generatedAt: string; windowMinutes: number; findings: Finding[]; recentConfigChanges: AuditEvent[]; recentEvents: OpEvent[]; note: string;
}

export interface TokenResponse { accessToken: string; tokenType: string; expiresIn: number; roles: string[]; team: string | null }

/** RFC 7807 problem body returned by the backend. */
export interface Problem { status: number; code?: string; detail?: string; correlationId?: string; errors?: string[] }
