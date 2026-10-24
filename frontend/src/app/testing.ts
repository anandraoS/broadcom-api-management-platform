/** Test helpers: build unsigned JWT-shaped tokens (the UI never verifies signatures; the server does). */
function b64url(o: object): string {
  return btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function fakeToken(opts: { sub?: string; roles?: string[]; team?: string; expInSeconds?: number } = {}): string {
  const exp = Math.floor(Date.now() / 1000) + (opts.expInSeconds ?? 3600);
  return `${b64url({ alg: 'HS256', typ: 'JWT' })}.${b64url({ sub: opts.sub ?? 'tester', roles: opts.roles ?? ['VIEWER'], team: opts.team ?? 'platform', exp })}.sig`;
}
