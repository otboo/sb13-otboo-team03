ALTER TABLE users ALTER COLUMN password DROP NOT NULL;

CREATE TABLE social_accounts
(
    user_id          UUID         NOT NULL,
    provider         VARCHAR(20)  NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    CONSTRAINT pk_social_accounts PRIMARY KEY (user_id),
    CONSTRAINT fk_social_accounts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_social_accounts_provider_identity UNIQUE (provider, provider_user_id)
);