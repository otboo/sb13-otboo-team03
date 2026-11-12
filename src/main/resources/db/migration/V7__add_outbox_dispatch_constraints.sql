ALTER TABLE outbox_events
    ADD CONSTRAINT ck_outbox_events_status
        CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'));

ALTER TABLE outbox_events
    ADD CONSTRAINT ck_outbox_events_attempt_count
        CHECK (attempt_count >= 0);

CREATE INDEX idx_outbox_events_dispatch
    ON outbox_events (status, available_at, created_at);
