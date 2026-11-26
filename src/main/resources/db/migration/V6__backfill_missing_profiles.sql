INSERT INTO profiles (user_id, location_names, created_at, updated_at)
SELECT u.id, '[]'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM users u
WHERE NOT EXISTS (
    SELECT 1
    FROM profiles p
    WHERE p.user_id = u.id
);