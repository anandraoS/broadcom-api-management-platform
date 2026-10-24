import { useCallback, useEffect, useRef, useState } from 'react';
import { describeError } from './api';

export interface AsyncState<T> { data: T | null; loading: boolean; error: string | null; reload: () => void }

/**
 * Loads data when `deps` change. A newer request aborts the previous one (no stale response can overwrite a newer one)
 * and unmounting aborts in-flight requests (no state updates after unmount).
 */
export function useAsync<T>(fn: (signal: AbortSignal) => Promise<T>, deps: unknown[]): AsyncState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);
  const fnRef = useRef(fn);
  fnRef.current = fn;

  useEffect(() => {
    const ctl = new AbortController();
    setLoading(true);
    setError(null);
    fnRef.current(ctl.signal)
      .then((v) => { if (!ctl.signal.aborted) { setData(v); setLoading(false); } })
      .catch((e: unknown) => {
        if (ctl.signal.aborted) return;
        setError(describeError(e));
        setLoading(false);
      });
    return () => ctl.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  return { data, loading, error, reload };
}

/** Debounces a fast-changing value (text filters). */
export function useDebounced<T>(value: T, ms = 300): T {
  const [v, setV] = useState(value);
  useEffect(() => { const t = setTimeout(() => setV(value), ms); return () => clearTimeout(t); }, [value, ms]);
  return v;
}
