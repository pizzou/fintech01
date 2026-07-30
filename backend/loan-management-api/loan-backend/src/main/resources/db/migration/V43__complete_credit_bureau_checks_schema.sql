


ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS borrower_id BIGINT;

ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS organization_id BIGINT;




ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS reference VARCHAR(100);

ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS external_reference VARCHAR(150);




ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS provider VARCHAR(100);

ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS national_id_checked VARCHAR(100);

ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS provider_request_id VARCHAR(150);




ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS status VARCHAR(30);

ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS credit_score INTEGER;

ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS risk_grade VARCHAR(50);

ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS active_facilities INTEGER;

ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS delinquent_accounts INTEGER;




ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS total_outstanding_debt NUMERIC(19,2);

ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS total_monthly_obligations NUMERIC(19,2);

ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS has_default_history BOOLEAN;

ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS has_active_listing BOOLEAN;

ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS listing_reason VARCHAR(500);




ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS raw_response TEXT;

ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS requested_by VARCHAR(150);

ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(1000);




ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER;

ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMP;

ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;




ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;

ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;



ALTER TABLE IF EXISTS credit_bureau_checks
    ALTER COLUMN attempt_count SET DEFAULT 0;

ALTER TABLE IF EXISTS credit_bureau_checks
    ALTER COLUMN status SET DEFAULT 'PENDING';



UPDATE credit_bureau_checks
SET attempt_count = 0
WHERE attempt_count IS NULL;

UPDATE credit_bureau_checks
SET status = 'PENDING'
WHERE status IS NULL;

UPDATE credit_bureau_checks
SET created_at = CURRENT_TIMESTAMP
WHERE created_at IS NULL;




ALTER TABLE IF EXISTS credit_bureau_checks
    ALTER COLUMN attempt_count SET NOT NULL;

ALTER TABLE IF EXISTS credit_bureau_checks
    ALTER COLUMN status SET NOT NULL;

ALTER TABLE IF EXISTS credit_bureau_checks
    ALTER COLUMN created_at SET NOT NULL;



DO $$
BEGIN

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_credit_bureau_checks_reference'
    ) THEN

        ALTER TABLE credit_bureau_checks
            ADD CONSTRAINT uk_credit_bureau_checks_reference
            UNIQUE (reference);

    END IF;

END $$;


DO $$
BEGIN

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_credit_bureau_checks_external_reference'
    ) THEN

        ALTER TABLE credit_bureau_checks
            ADD CONSTRAINT uk_credit_bureau_checks_external_reference
            UNIQUE (external_reference);

    END IF;

END $$;




DO $$
BEGIN

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_credit_bureau_checks_borrower'
    ) THEN

        ALTER TABLE credit_bureau_checks
            ADD CONSTRAINT fk_credit_bureau_checks_borrower
            FOREIGN KEY (borrower_id)
            REFERENCES borrowers(id);

    END IF;

END $$;


DO $$
BEGIN

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_credit_bureau_checks_organization'
    ) THEN

        ALTER TABLE credit_bureau_checks
            ADD CONSTRAINT fk_credit_bureau_checks_organization
            FOREIGN KEY (organization_id)
            REFERENCES organizations(id);

    END IF;

END $$;




CREATE INDEX IF NOT EXISTS idx_cbc_borrower
    ON credit_bureau_checks (borrower_id);

CREATE INDEX IF NOT EXISTS idx_cbc_org
    ON credit_bureau_checks (organization_id);

CREATE INDEX IF NOT EXISTS idx_cbc_reference
    ON credit_bureau_checks (reference);

CREATE INDEX IF NOT EXISTS idx_cbc_external_reference
    ON credit_bureau_checks (external_reference);

CREATE INDEX IF NOT EXISTS idx_cbc_created_at
    ON credit_bureau_checks (created_at);





COMMENT ON TABLE credit_bureau_checks
IS 'Credit bureau inquiry and credit assessment records.';

COMMENT ON COLUMN credit_bureau_checks.reference
IS 'Internal unique reference for the credit bureau operation.';

COMMENT ON COLUMN credit_bureau_checks.external_reference
IS 'External idempotency/reference identifier.';

COMMENT ON COLUMN credit_bureau_checks.attempt_count
IS 'Number of provider request attempts.';

COMMENT ON COLUMN credit_bureau_checks.last_attempt_at
IS 'Timestamp of the most recent provider request attempt.';

COMMENT ON COLUMN credit_bureau_checks.completed_at
IS 'Timestamp when the credit bureau operation completed.';

COMMENT ON COLUMN credit_bureau_checks.created_at
IS 'Timestamp when the credit bureau check was created.';

COMMENT ON COLUMN credit_bureau_checks.expires_at
IS 'Timestamp after which the bureau result is considered expired.';

