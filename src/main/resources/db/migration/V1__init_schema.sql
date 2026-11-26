CREATE TABLE users
(
    id         UUID         NOT NULL,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    name       VARCHAR(100) NOT NULL,
    role       VARCHAR(20)  NOT NULL DEFAULT 'USER',
    locked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE weathers
(
    id                        UUID          NOT NULL,
    latitude                  DOUBLE PRECISION NOT NULL,
    longitude                 DOUBLE PRECISION NOT NULL,
    forecasted_at             TIMESTAMPTZ   NOT NULL,
    forecast_at               TIMESTAMPTZ   NOT NULL,
    sky_status                VARCHAR(30)   NOT NULL,
    precipitation_type        VARCHAR(30)   NOT NULL,
    precipitation_amount      NUMERIC(7, 2) NOT NULL,
    precipitation_probability NUMERIC(5, 2) NOT NULL,
    temperature               NUMERIC(5, 2) NOT NULL,
    humidity                  NUMERIC(5, 2) NOT NULL,
    wind_speed                NUMERIC(6, 2) NOT NULL,
    created_at                TIMESTAMPTZ   NOT NULL,
    updated_at                TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_weathers PRIMARY KEY (id)
);

CREATE TABLE clothes_attribute_definitions
(
    id                UUID         NOT NULL,
    name              VARCHAR(255) NOT NULL,
    selectable_values TEXT[] NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_clothes_attribute_definitions PRIMARY KEY (id)
);

CREATE TABLE outbox_events
(
    event_id       UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    schema_version SMALLINT     NOT NULL DEFAULT 1,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    partition_key  VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempt_count  INTEGER      NOT NULL DEFAULT 0,
    available_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at   TIMESTAMPTZ NULL,
    last_error     TEXT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_outbox_events PRIMARY KEY (event_id)
);

CREATE TABLE processed_events
(
    consumer_name VARCHAR(100) NOT NULL,
    event_id      UUID         NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_processed_events PRIMARY KEY (consumer_name, event_id)
);

CREATE TABLE profiles
(
    user_id                 UUID             NOT NULL,
    profile_image_url       VARCHAR(2048)    NULL,
    gender                  VARCHAR(10)      NULL,
    birth_date              DATE             NULL,
    latitude                DOUBLE PRECISION NULL,
    longitude               DOUBLE PRECISION NULL,
    temperature_sensitivity INTEGER          NULL,
    location_names          JSONB            NULL,
    created_at              TIMESTAMPTZ      NOT NULL,
    updated_at              TIMESTAMPTZ      NOT NULL,
    CONSTRAINT pk_profiles PRIMARY KEY (user_id),
    CONSTRAINT fk_users_to_profiles FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_profiles_temperature_sensitivity CHECK (temperature_sensitivity BETWEEN 1 AND 5)
);

CREATE TABLE clothes
(
    id        UUID         NOT NULL,
    name      VARCHAR(255) NOT NULL,
    type      VARCHAR(30)  NOT NULL,
    image_url VARCHAR(2048) NULL,
    owner_id  UUID         NOT NULL,
    CONSTRAINT pk_clothes PRIMARY KEY (id),
    CONSTRAINT fk_users_to_clothes FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE TABLE clothes_attribute
(
    clothes_id    UUID         NOT NULL,
    definition_id UUID         NOT NULL,
    value         VARCHAR(255) NOT NULL,
    CONSTRAINT pk_clothes_attribute PRIMARY KEY (clothes_id, definition_id),
    CONSTRAINT fk_clothes_to_clothes_attribute FOREIGN KEY (clothes_id) REFERENCES clothes (id),
    CONSTRAINT fk_clothes_attribute_definitions_to_clothes_attribute FOREIGN KEY (definition_id) REFERENCES clothes_attribute_definitions (id)
);

CREATE TABLE feeds
(
    id         UUID        NOT NULL,
    author_id  UUID        NOT NULL,
    weather_id UUID        NOT NULL,
    content    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_feeds PRIMARY KEY (id),
    CONSTRAINT fk_users_to_feeds FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT fk_weathers_to_feeds FOREIGN KEY (weather_id) REFERENCES weathers (id)
);

CREATE TABLE feed_likes
(
    id         UUID        NOT NULL,
    feed_id    UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_feed_likes PRIMARY KEY (id),
    CONSTRAINT uq_feed_likes_feed_user UNIQUE (feed_id, user_id),
    CONSTRAINT fk_feeds_to_feed_likes FOREIGN KEY (feed_id) REFERENCES feeds (id),
    CONSTRAINT fk_users_to_feed_likes FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE feed_comments
(
    id         UUID        NOT NULL,
    feed_id    UUID        NOT NULL,
    author_id  UUID        NOT NULL,
    content    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_feed_comments PRIMARY KEY (id),
    CONSTRAINT fk_feeds_to_feed_comments FOREIGN KEY (feed_id) REFERENCES feeds (id),
    CONSTRAINT fk_users_to_feed_comments FOREIGN KEY (author_id) REFERENCES users (id)
);

CREATE TABLE feed_clothes
(
    feed_id    UUID NOT NULL,
    clothes_id UUID NOT NULL,
    CONSTRAINT pk_feed_clothes PRIMARY KEY (feed_id, clothes_id),
    CONSTRAINT fk_feeds_to_feed_clothes FOREIGN KEY (feed_id) REFERENCES feeds (id),
    CONSTRAINT fk_clothes_to_feed_clothes FOREIGN KEY (clothes_id) REFERENCES clothes (id)
);

CREATE TABLE follows
(
    id          UUID        NOT NULL,
    follower_id UUID        NOT NULL,
    followee_id UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_follows PRIMARY KEY (id),
    CONSTRAINT uq_follows_follower_followee UNIQUE (follower_id, followee_id),
    CONSTRAINT ck_follows_not_self CHECK (follower_id <> followee_id),
    CONSTRAINT fk_users_to_follows_follower FOREIGN KEY (follower_id) REFERENCES users (id),
    CONSTRAINT fk_users_to_follows_followee FOREIGN KEY (followee_id) REFERENCES users (id)
);

CREATE TABLE notifications
(
    id               UUID         NOT NULL,
    receiver_id      UUID         NOT NULL,
    actor_id         UUID NULL,
    notification_type VARCHAR(50)  NOT NULL,
    related_id       UUID NULL,
    title            VARCHAR(100) NOT NULL,
    content          VARCHAR(500) NOT NULL,
    level            VARCHAR(20)  NOT NULL DEFAULT 'INFO',
    read_at          TIMESTAMPTZ NULL,
    source_event_id  UUID         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT uq_notifications_receiver_type_event UNIQUE (receiver_id, notification_type, source_event_id),
    CONSTRAINT fk_users_to_notifications_receiver FOREIGN KEY (receiver_id) REFERENCES users (id),
    CONSTRAINT fk_users_to_notifications_actor FOREIGN KEY (actor_id) REFERENCES users (id)
);

CREATE TABLE direct_message
(
    id          UUID          NOT NULL,
    receiver_id UUID          NOT NULL,
    sender_id   UUID          NOT NULL,
    dm_key      VARCHAR(73)   NOT NULL,
    content     VARCHAR(1000) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_direct_message PRIMARY KEY (id),
    CONSTRAINT fk_users_to_direct_message_receiver FOREIGN KEY (receiver_id) REFERENCES users (id),
    CONSTRAINT fk_users_to_direct_message_sender FOREIGN KEY (sender_id) REFERENCES users (id)
);


CREATE INDEX idx_direct_message_1 ON direct_message (id, dm_key, created_at);
CREATE INDEX idx_feeds_1 ON feeds (created_at, id);
CREATE INDEX idx_feeds_2 ON feeds (author_id, created_at, id);
CREATE INDEX idx_notifications_1 ON notifications (id, receiver_id, created_at);
CREATE INDEX idx_feed_comments_1 ON feed_comments (feed_id, created_at, id);
