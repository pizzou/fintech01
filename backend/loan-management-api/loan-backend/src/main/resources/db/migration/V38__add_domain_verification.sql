-- Self-service custom domains: a client submits their own domain from their
-- dashboard (no super-admin involvement needed, so this scales past a
-- handful of manually-onboarded orgs), but the domain only becomes LIVE
-- (i.e. actually resolvable by TenantResolutionFilter, and allowed by CORS)
-- after they prove ownership via a DNS TXT record. Otherwise org A could
-- type in org B's real domain and silently start receiving/serving traffic
-- meant for a domain they don't control.
ALTER TABLE organizations ADD COLUMN domain_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE organizations ADD COLUMN domain_verification_token VARCHAR(64);

-- Domains assigned before this migration (via the earlier manual/super-admin
-- flow, or the .local dev backfill) were already trusted assignments, not
-- self-service claims — grandfather them in as verified so nothing that
-- already worked stops working.
UPDATE organizations SET domain_verified = TRUE WHERE domain IS NOT NULL;
