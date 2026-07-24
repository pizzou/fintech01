-- V31: Holiday.java and HolidayRepository were added (for computing business-day loan/payment
-- schedules around org-specific non-working days) without the matching schema migration —
-- the same class of gap V29 fixed for loans.next_installment_amount/next_payment_date.
-- Hibernate's schema-validation on startup (spring.jpa.hibernate.ddl-auto=validate)
-- correctly rejected this: the "holidays" table doesn't exist at all, so entityManagerFactory
-- failed to initialize, which cascaded through UserRepository -> CustomUserDetailsService ->
-- JwtAuthFilter (every repository bean lives in the same persistence unit, so one bad entity
-- blocks all of them). This migration creates the table the entity has expected all along.

CREATE TABLE IF NOT EXISTS holidays (
    id                   BIGSERIAL PRIMARY KEY,
    organization_id      BIGINT REFERENCES organizations(id) ON DELETE CASCADE,
    holiday_date         DATE         NOT NULL,
    name                 VARCHAR(255) NOT NULL,
    recurring_annually   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_holidays_org ON holidays(organization_id);

-- Matches HolidayRepository.findByOrganization_IdAndHolidayDate — one entry per org per date.
CREATE UNIQUE INDEX idx_holidays_org_date ON holidays(organization_id, holiday_date);

COMMENT ON TABLE holidays IS
  'Org-specific non-working days (e.g. public holidays) used when computing business-day loan '
  'and payment schedules. recurring_annually = true means the month/day repeats every year '
  'regardless of the stored year (see HolidayRepository.findRecurringForMonthDay).';