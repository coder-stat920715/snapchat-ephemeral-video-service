CREATE TABLE video_metadata (
    id               UUID                     PRIMARY KEY,
    owner_id         VARCHAR(64)              NOT NULL,
    title            VARCHAR(200)             NOT NULL,
    duration_seconds INTEGER                  NOT NULL,
    s3_key           VARCHAR(512)             NOT NULL UNIQUE,
    retention_type   VARCHAR(32)              NOT NULL,
    status           VARCHAR(16)              NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    version          BIGINT                   NOT NULL DEFAULT 0
);

CREATE INDEX idx_video_status_expires ON video_metadata (status, expires_at);
CREATE INDEX idx_video_owner ON video_metadata (owner_id);
