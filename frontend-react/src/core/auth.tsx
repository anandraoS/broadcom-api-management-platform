import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, session, setUnauthorizedHandler } from './api';

export interface SessionUser { username: string; roles: string[]; team: string | null; expiresAt: number }

/** Reads claims for DISPLAY only. The signature is verified by the backend, never here. */
export function decodeToken(token: string): SessionUser | null {
  try {
    const payload = token.split('.')[1];
    const json = decodeURIComponent(
      atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join(''),
    );
    const c = JSON.parse(json) as { sub?: string; roles?: string[]; team?: string; exp?: number };
    if (!c.sub || !c.exp) return null;
    return { username: c.sub, roles: c.roles ?? [], team: c.team ?? null, expiresAt: c.exp * 1000 };
  } catch {
    return null;
  }
}

function restore(): SessionUser | null {
  const t = session.token;
  const u = t ? decodeToken(t) : null;
  if (u && u.expiresAt > Date.now()) return u;
  session.clear();
  return null;
}

interface AuthState {
  user: SessionUser | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  /** UX only: the navigation reflects roles, the server enforces access. */
  hasAnyRole: (...roles: string[]) => boolean;
}

const Ctx = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(restore);
  const logout = useCallback(() => { session.clear(); setUser(null); }, []);

  useEffect(() => { setUnauthorizedHandler(logout); }, [logout]);

  const value = useMemo<AuthState>(() => ({
    user,
    logout,
    hasAnyRole: (...roles) => !!user && roles.some((r) => user.roles.includes(r)),
    login: async (username, password) => {
      const r = await api.login(username, password);
      const u = decodeToken(r.accessToken);
      if (!u) throw new Error('invalid token');
      session.set(r.accessToken);
      setUser(u);
    },
  }), [user, logout]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth(): AuthState {
  const v = useContext(Ctx);
  if (!v) throw new Error('useAuth outside AuthProvider');
  return v;
}
