DROP INDEX IF EXISTS idx_direct_message_1;

CREATE INDEX idx_direct_message_cursor
    ON direct_message (dm_key, created_at DESC, id DESC);
