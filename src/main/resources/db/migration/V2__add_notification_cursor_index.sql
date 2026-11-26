DROP INDEX IF EXISTS idx_notifications_1;

CREATE INDEX idx_notifications_cursor
    ON notifications (receiver_id, created_at DESC, id DESC);
