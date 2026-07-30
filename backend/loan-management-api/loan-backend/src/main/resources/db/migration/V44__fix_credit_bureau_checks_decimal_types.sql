-- Fix credit bureau monetary columns.
-- Hibernate expects BigDecimal -> NUMERIC(19,2).

ALTER TABLE credit_bureau_checks
    ALTER COLUMN total_monthly_obligations
    TYPE NUMERIC(19,2)
    USING ROUND(total_monthly_obligations::numeric, 2);

ALTER TABLE credit_bureau_checks
    ALTER COLUMN total_outstanding_debt
    TYPE NUMERIC(19,2)
    USING ROUND(total_outstanding_debt::numeric, 2);