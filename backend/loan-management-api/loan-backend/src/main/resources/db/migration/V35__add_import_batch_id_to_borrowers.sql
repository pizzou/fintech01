-- Support for bulk-importing a client's pre-existing manual (e.g. Excel) loan ledger.

CREATE TABLE IF NOT EXISTS import_batches (
    id                BIGSERIAL PRIMARY KEY,
    organization_id   BIGINT NOT NULL REFERENCES organizations(id),
    imported_by       BIGINT REFERENCES app_users(id),
    file_name         VARCHAR(255),
    total_rows        INTEGER NOT NULL DEFAULT 0,
    success_count     INTEGER NOT NULL DEFAULT 0,
    failure_count     INTEGER NOT NULL DEFAULT 0,
    status            VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    row_results       TEXT,   -- JSON array: per-row outcome, for staff review after commit
    created_at        TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_import_batches_org ON import_batches(organization_id);

-- Tag which loans/borrowers came from a bulk import rather than being originated on-platform —
-- keeps them out of flows that only make sense for a loan going through this system's own
-- lifecycle (approval chain, credit-bureau "loan approved" reporting), and makes them visible
-- in reporting/audit as pre-existing history rather than new business.
ALTER TABLE loans     ADD COLUMN IF NOT EXISTS imported BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE loans     ADD COLUMN IF NOT EXISTS import_batch_id BIGINT REFERENCES import_batches(id);
ALTER TABLE borrowers ADD COLUMN IF NOT EXISTS imported BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_loans_import_batch ON loans(import_batch_id);