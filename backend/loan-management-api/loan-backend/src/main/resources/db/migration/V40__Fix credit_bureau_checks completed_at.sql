

ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;

-- Existing records are intentionally left NULL because
-- completed_at represents the time a credit bureau check
-- actually completed. Existing historical records cannot
-- safely be assigned a fabricated completion timestamp.

COMMENT ON COLUMN credit_bureau_checks.completed_at
IS 'Timestamp when the credit bureau check completed; NULL when not completed.';
