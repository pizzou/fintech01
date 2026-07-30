


ALTER TABLE IF EXISTS credit_bureau_checks
    ADD COLUMN IF NOT EXISTS external_reference VARCHAR(255);

COMMENT ON COLUMN credit_bureau_checks.external_reference
IS 'External reference or transaction identifier associated with the credit bureau check.';
