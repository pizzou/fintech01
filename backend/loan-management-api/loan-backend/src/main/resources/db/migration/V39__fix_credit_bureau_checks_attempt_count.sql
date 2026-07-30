


ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0;

-- Make sure existing NULL values are normalized if the column
-- existed previously but allowed NULLs.
UPDATE credit_bureau_checks
SET attempt_count = 0
WHERE attempt_count IS NULL;

-- Enforce the expected non-null constraint.
ALTER TABLE IF EXISTS credit_bureau_checks
    ALTER COLUMN attempt_count SET DEFAULT 0;

ALTER TABLE IF EXISTS credit_bureau_checks
    ALTER COLUMN attempt_count SET NOT NULL;

