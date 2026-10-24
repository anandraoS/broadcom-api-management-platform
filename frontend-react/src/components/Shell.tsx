import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../core/auth';

interface NavItem { label: string; path: string; roles?: string[] }

export const NAV: NavItem[] = [
  { label: 'Traffic dashboard', path: '/dashboard' },
  { label: 'API registry', path: '/apis' },
  { label: 'Policy approvals', path: '/approvals', roles: ['ADMIN', 'POLICY_APPROVER'] },
  { label: 'Consumers', path: '/consumers' },
  { label: 'Incident investigation', path: '/incidents' },
  { label: 'Operational events', path: '/events' },
  { label: 'Audit history', path: '/audit', roles: ['ADMIN', 'OPERATOR', 'POLICY_APPROVER'] },
];

/** Application shell: role-aware navigation (hiding an item never grants or removes server-side access) + responsive drawer. */
export function Shell() {
  const { user, logout, hasAnyRole } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const items = NAV.filter((i) => !i.roles || hasAnyRole(...i.roles));
  return (
    <>
      <header className="topbar" role="banner">
        <button type="button" className="menu-btn" aria-label="Toggle navigation" aria-expanded={open} onClick={() => setOpen((o) => !o)}>Menu</button>
        <span className="brand">API Control Plane</span>
        <span className="spacer" />
        <span className="who" data-testid="whoami">{user?.username} ({user?.roles.join(', ')})</span>
        <button type="button" onClick={() => { logout(); navigate('/login'); }}>Sign out</button>
      </header>
      <div className="layout">
        <nav className={`sidenav${open ? ' open' : ''}`} aria-label="Main">
          {items.map((i) => (
            <NavLink key={i.path} to={i.path} className={({ isActive }) => (isActive ? 'active' : '')} onClick={() => setOpen(false)}>{i.label}</NavLink>
          ))}
        </nav>
        <main className="content"><Outlet /></main>
      </div>
    </>
  );
}
