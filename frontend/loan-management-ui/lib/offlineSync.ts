import { getPendingActions, removePendingAction, bumpAttempt, PendingAction } from './offlineDb';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

export interface SyncResult {
  succeeded: PendingAction[];
  failed: { action: PendingAction; error: string }[];
}

/**
 * Replays every queued offline action against the real API, in the order
 * they were created. Stops trying a given action after 5 failed attempts
 * (so one permanently-broken request can't block the queue forever) but
 * keeps trying everything else. Safe to call repeatedly — it's a no-op
 * when the queue is empty, and already-synced actions are removed as
 * they succeed so a second call never double-submits them.
 */
export async function drainOfflineQueue(
  authHeader: () => Record<string, string>,
): Promise<SyncResult> {
  const actions = await getPendingActions();
  const result: SyncResult = { succeeded: [], failed: [] };

  for (const action of actions) {
    if (action.attempts >= 5) continue; // give up quietly on this one, keep going

    try {
      const res = await fetch(`${API_BASE}${action.url}`, {
        method: action.method,
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': action.id, // stable across retries — a repeated sync attempt can't double-submit
          ...authHeader(),
        },
        body: action.body ? JSON.stringify(action.body) : undefined,
      });
      if (!res.ok) {
        const json = await res.json().catch(() => ({}));
        throw new Error(json.error || json.message || `Sync failed (${res.status})`);
      }
      await removePendingAction(action.id);
      result.succeeded.push(action);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Network error';
      await bumpAttempt(action.id, message);
      result.failed.push({ action, error: message });
    }
  }

  return result;
}
