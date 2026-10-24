import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/guards';

/** All feature areas are lazy-loaded: the initial bundle contains only the shell and login. */
export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/login').then((m) => m.LoginPage), title: 'Sign in' },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', loadComponent: () => import('./features/dashboard').then((m) => m.DashboardPage), title: 'Traffic dashboard' },
      { path: 'apis', loadComponent: () => import('./features/api-list').then((m) => m.ApiListPage), title: 'API registry' },
      { path: 'apis/:id', loadComponent: () => import('./features/api-detail').then((m) => m.ApiDetailPage), title: 'API detail' },
      {
        path: 'approvals',
        canActivate: [roleGuard('ADMIN', 'POLICY_APPROVER')],
        loadComponent: () => import('./features/approvals').then((m) => m.ApprovalsPage),
        title: 'Policy approvals',
      },
      { path: 'consumers', loadComponent: () => import('./features/consumer-list').then((m) => m.ConsumerListPage), title: 'Consumers' },
      { path: 'consumers/:id', loadComponent: () => import('./features/consumer-detail').then((m) => m.ConsumerDetailPage), title: 'Consumer detail' },
      { path: 'events', loadComponent: () => import('./features/events').then((m) => m.EventsPage), title: 'Operational events' },
      { path: 'incidents', loadComponent: () => import('./features/incidents').then((m) => m.IncidentsPage), title: 'Incident investigation' },
      {
        path: 'audit',
        canActivate: [roleGuard('ADMIN', 'OPERATOR', 'POLICY_APPROVER')],
        loadComponent: () => import('./features/audit').then((m) => m.AuditPage),
        title: 'Audit history',
      },
      { path: 'forbidden', loadComponent: () => import('./features/forbidden').then((m) => m.ForbiddenPage), title: 'Forbidden' },
    ],
  },
  { path: '**', redirectTo: '' },
];
