-- V28: Loan.java (nextInstallmentAmount / nextPaymentDate) and LoanService — which sets both
-- when a loan is created, so the borrower dashboard can show "next payment: X on <date>" without
-- recomputing the amortization schedule on every read — were added without the matching schema
-- migration. Flyway's schema-validation on startup (spring.jpa.hibernate.ddl-auto=validate)
-- correctly rejected this mismatch: "missing column [next_installment_amount] in table [loans]".
-- This migration adds the two columns the entity has expected all along.
--
-- Both nullable: existing loans created before this migration have no value here until they're
-- next touched by the app (e.g. a payment recorded, or a future backfill job) — NULL is the
-- correct "not yet computed" state, not 0 or a fabricated date.

ALTER TABLE loans ADD COLUMN IF NOT EXISTS next_installment_amount DOUBLE PRECISION;
ALTER TABLE loans ADD COLUMN IF NOT EXISTS next_payment_date       DATE;

COMMENT ON COLUMN loans.next_installment_amount IS
  'Amount due at the next scheduled installment. Set at loan creation and kept current by '
  'PaymentService on every payment recorded (0 once the loan is fully paid).';
COMMENT ON COLUMN loans.next_payment_date IS
  'Date the next installment is due. Set at loan creation and kept current by PaymentService '
  'on every payment recorded (NULL once the loan is fully paid).';