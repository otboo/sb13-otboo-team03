ALTER TABLE outbox_events
    ADD COLUMN occurred_at TIMESTAMPTZ;

UPDATE outbox_events
SET occurred_at = created_at
WHERE occurred_at IS NULL;

ALTER TABLE outbox_events
    ALTER COLUMN occurred_at SET NOT NULL;
