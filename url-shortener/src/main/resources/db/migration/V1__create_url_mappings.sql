CREATE TABLE url_mappings (
    id              BIGSERIAL    PRIMARY KEY,
    short_code      VARCHAR(10)  NOT NULL UNIQUE,
    long_url        TEXT         NOT NULL,
    url_hash        VARCHAR(64)  NOT NULL,
    is_custom_alias BOOLEAN      NOT NULL DEFAULT false,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_url_mappings_url_hash ON url_mappings (url_hash);
