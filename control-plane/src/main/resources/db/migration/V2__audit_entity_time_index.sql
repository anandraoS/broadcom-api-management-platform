-- Query-plan review finding (see docs/database-performance.md): the audit history query filters by (entity_type, entity_id)
-- and sorts by ts DESC. idx_audit_entity serves the filter but forces a sort of every matching row; a composite index that
-- ends in ts lets the database read the newest rows first and stop at LIMIT.
-- Expand/contract: the new index is created first, the superseded one dropped afterwards (both are online-safe in MySQL 8
-- with ALGORITHM=INPLACE; at scale prefer pt-online-schema-change / gh-ost for very large tables).
CREATE INDEX idx_audit_entity_ts ON audit_event (entity_type, entity_id, ts);
DROP INDEX idx_audit_entity ON audit_event;
