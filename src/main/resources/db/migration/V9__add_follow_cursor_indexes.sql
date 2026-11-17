CREATE INDEX idx_follows_followings_cursor
    ON follows (follower_id, created_at DESC, id DESC);

CREATE INDEX idx_follows_followers_cursor
    ON follows (followee_id, created_at DESC, id DESC);
