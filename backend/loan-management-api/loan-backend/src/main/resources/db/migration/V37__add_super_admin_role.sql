-- Platform-level role for the SaaS owner (you), not tied to any single
-- organization. Users with this role have organization_id = NULL (already
-- supported — see AuthController's null-organization handling) and access
-- only the /api/super-admin/** endpoints: creating/suspending organizations,
-- assigning their public domain, viewing subscription/billing status.
-- This is deliberately NOT assignable from any per-org /me endpoint.
INSERT INTO roles (name, description)
SELECT 'SUPER_ADMIN', 'Platform owner — manages all organizations, billing, and domains across the whole SaaS'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'SUPER_ADMIN');
