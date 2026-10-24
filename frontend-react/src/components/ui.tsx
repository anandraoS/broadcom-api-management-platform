import type { ReactNode } from 'react';

/** Consistent loading / error / empty presentation; renders children when there is data. */
export function StateView({ loading, error, empty, emptyText = 'Nothing to show.', children }:
  { loading: boolean; error: string | null; empty?: boolean; emptyText?: string; children: ReactNode }) {
  return (
    <>
      {loading && <div className="progress" role="progressbar" aria-label="Loading" />}
      {error ? <div className="error" role="alert">{error}</div>
        : !loading && empty ? <div className="empty" role="status">{emptyText}</div>
        : children}
    </>
  );
}

export function Chip({ value }: { value: string }) {
  return <span className={`chip ${value}`}>{value}</span>;
}

export function Pager({ page, size, total, onPage, label }: { page: number; size: number; total: number; onPage: (p: number) => void; label: string }) {
  const pages = Math.max(1, Math.ceil(total / size));
  return (
    <nav className="pager" aria-label={label}>
      <button type="button" onClick={() => onPage(page - 1)} disabled={page <= 0}>Previous</button>
      <span>Page {page + 1} of {pages} ({total} total)</span>
      <button type="button" onClick={() => onPage(page + 1)} disabled={page + 1 >= pages}>Next</button>
    </nav>
  );
}

export const fmtTs = (s: string) => s.slice(0, 19).replace('T', ' ');
