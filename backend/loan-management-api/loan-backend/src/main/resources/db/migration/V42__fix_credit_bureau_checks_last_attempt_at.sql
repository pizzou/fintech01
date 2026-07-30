
ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMP;

COMMENT ON COLUMN credit_bureau_checks.last_attempt_at
IS 'Timestamp of the most recent credit bureau check attempt.';
