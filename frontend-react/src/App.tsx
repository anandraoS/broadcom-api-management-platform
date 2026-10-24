import { lazy, Suspense, type ReactNode } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './core/auth';
import { Shell } from './components/Shell';
import { LoginPage } from './pages/LoginPage';

// Feature pages are code-split: the initial bundle contains only the shell and login.
const DashboardPage = lazy(() => import('./pages/DashboardPage'));
const ApiListPage = lazy(() => import('./pages/ApiListPage'));
const ApiDetailPage = lazy(() => import('./pages/ApiDetailPage'));
const ApprovalsPage = lazy(() => import('./pages/ApprovalsPage'));
const ConsumerListPage = lazy(() => import('./pages/ConsumerListPage'));
const ConsumerDetailPage = lazy(() => import('./pages/ConsumerDetailPage'));
const EventsPage = lazy(() => import('./pages/EventsPage'));
const IncidentsPage = lazy(() => import('./pages/IncidentsPage'));
const AuditPage = lazy(() => import('./pages/AuditPage'));
const ForbiddenPage = lazy(() => import('./pages/ForbiddenPage'));

/** UX guard only - never a security control; every endpoint authorizes on the server. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  return user && user.expiresAt > Date.now() ? <>{children}</> : <Navigate to="/login" replace />;
}

export function RequireRole({ roles, children }: { roles: string[]; children: ReactNode }) {
  const { hasAnyRole } = useAuth();
  return hasAnyRole(...roles) ? <>{children}</> : <Navigate to="/forbidden" replace />;
}

export function App() {
  return (
    <Suspense fallback={<div className="progress" role="progressbar" aria-label="Loading" />}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<RequireAuth><Shell /></RequireAuth>}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="apis" element={<ApiListPage />} />
          <Route path="apis/:id" element={<ApiDetailPage />} />
          <Route path="approvals" element={<RequireRole roles={['ADMIN', 'POLICY_APPROVER']}><ApprovalsPage /></RequireRole>} />
          <Route path="consumers" element={<ConsumerListPage />} />
          <Route path="consumers/:id" element={<ConsumerDetailPage />} />
          <Route path="events" element={<EventsPage />} />
          <Route path="incidents" element={<IncidentsPage />} />
          <Route path="audit" element={<RequireRole roles={['ADMIN', 'OPERATOR', 'POLICY_APPROVER']}><AuditPage /></RequireRole>} />
          <Route path="forbidden" element={<ForbiddenPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}
