-- Adds a custom domain to each organization so the backend can resolve the
-- tenant from the request's Host header (growthfinance.rw -> org id 7),
-- instead of relying on the frontend to send a tenantSlug.
ALTER TABLE organizations ADD COLUMN domain VARCHAR(255);

-- One domain can only ever point at one organization.
CREATE UNIQUE INDEX idx_organizations_domain ON organizations (domain) WHERE domain IS NOT NULL;

-- Backfill existing demo org(s) with a domain derived from their slug so
-- local/dev environments keep working without manual data entry.
-- Adjust/remove this for real tenants — set the real domain per org instead.
UPDATE organizations
SET domain = LOWER(REPLACE(name, ' ', '')) || '.local'
WHERE domain IS NULL;
