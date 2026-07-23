ALTER TABLE borrowers
ADD COLUMN IF NOT EXISTS blacklisted_by BIGINT;

ALTER TABLE borrowers
ADD CONSTRAINT IF NOT EXISTS fk_borrowers_blacklisted_by
FOREIGN KEY (blacklisted_by)
REFERENCES app_users(id);