-- ================================================================
-- V28: Borrower blacklisting workflow
--
-- Borrower.status already had a BLACKLISTED enum value, but nothing in
-- the system ever set it, checked it, or recorded why/when/who -- an
-- unused label, not a real feature. This adds the actual workflow:
-- a reason, a timestamp, and who made the call, so it's an auditable
-- compliance action rather than a silent status flip. Loan creation
-- is blocked for blacklisted borrowers in the application layer
-- (LoanService), not just hidden in the UI.
-- ================================================================
ALTER TABLE borrowers ADD COLUMN IF NOT EXISTS blacklist_reason VARCHAR(500);
ALTER TABLE borrowers ADD COLUMN IF NOT EXISTS blacklisted_at TIMESTAMP;
ALTER TABLE borrowers ADD COLUMN IF NOT EXISTS blacklisted_by BIGINT REFERENCES users(id);