/**
 * Multi-tenant domain resolution.
 *
 * In production, which organization this frontend renders is resolved by
 * the BACKEND from the request's own hostname (growthfinance.rw,
 * abcsacco.rw, ...) — see TenantResolutionFilter on the backend. This file
 * exists to tell the backend which hostname the browser is actually on
 * (via the X-Tenant-Domain header set in services/api.ts, and the plain
 * fetch() calls in app/(site)/layout.tsx) — nothing here decides the
 * tenant itself, it just reports it.
 *
 * TENANT_SLUG below is ONLY a local-development fallback for when there's
 * no real domain to resolve from (localhost) — see isLocalDev(). It is
 * never consulted on a real customer domain in production; on
 * growthfinance.rw the backend resolves the org from the domain itself and
 * this slug is ignored.
 */

/** The hostname the browser is currently on, with a leading "www." stripped. Null on the server. */
export function currentTenantDomain(): string | null {
  if (typeof window === 'undefined') return null;
  const host = window.location.hostname.toLowerCase();
  return host.startsWith('www.') ? host.slice(4) : host;
}

/** True on localhost / *.local — anywhere there's no real customer domain for the backend to resolve. */
export function isLocalDev(): boolean {
  const host = currentTenantDomain();
  if (host === null) return true; // server-side render before hydration — treat as dev-safe default
  return host === 'localhost' || host === '127.0.0.1' || host.endsWith('.local');
}

/** Local-dev-only fallback org slug — see isLocalDev(). Change via NEXT_PUBLIC_TENANT_SLUG for local testing. */
export const TENANT_SLUG =
  process.env.NEXT_PUBLIC_TENANT_SLUG || 'growthfinance';