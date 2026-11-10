-- Storage metadata schema for PostgreSQL 16.
-- Chunk bytes are never stored here. Only metadata lives in the database.

CREATE TABLE storage_node (
    id              UUID PRIMARY KEY,
    name            VARCHAR(64)  NOT NULL UNIQUE,
    base_url        VARCHAR(255) NOT NULL UNIQUE,
    capacity_bytes  BIGINT       NOT NULL CHECK (capacity_bytes > 0),
    status          VARCHAR(16)  NOT NULL CHECK (status IN ('ONLINE', 'SUSPECT', 'OFFLINE', 'DRAINING')),
    registered_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at    TIMESTAMPTZ,
    CHECK (name ~ '^[a-z0-9-]{3,64}$')
);

CREATE TABLE stored_file (
    id                UUID PRIMARY KEY,
    file_name         VARCHAR(255) NOT NULL CHECK (length(btrim(file_name)) > 0),
    size_bytes        BIGINT       NOT NULL CHECK (size_bytes >= 0),
    chunk_size_bytes  INTEGER      NOT NULL CHECK (chunk_size_bytes > 0),
    chunk_count       INTEGER      NOT NULL CHECK (chunk_count >= 0),
    sha256            CHAR(64)     NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    status            VARCHAR(16)  NOT NULL CHECK (status IN ('UPLOADING', 'COMPLETE', 'FAILED', 'DELETING')),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE file_chunk (
    id           UUID PRIMARY KEY,
    file_id      UUID     NOT NULL REFERENCES stored_file (id) ON DELETE CASCADE,
    chunk_index  INTEGER  NOT NULL CHECK (chunk_index >= 0),
    size_bytes   INTEGER  NOT NULL CHECK (size_bytes > 0),
    sha256       CHAR(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    UNIQUE (file_id, chunk_index)
);

-- One row per physical copy. Replication factor is derived from VERIFIED rows (see chunk_health).
CREATE TABLE chunk_replica (
    id           UUID PRIMARY KEY,
    chunk_id     UUID        NOT NULL REFERENCES file_chunk (id) ON DELETE CASCADE,
    node_id      UUID        NOT NULL REFERENCES storage_node (id),
    storage_key  UUID        NOT NULL UNIQUE,
    state        VARCHAR(16) NOT NULL CHECK (state IN ('PENDING', 'VERIFIED', 'CORRUPT', 'DELETING')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    verified_at  TIMESTAMPTZ,
    UNIQUE (chunk_id, node_id),
    CHECK (state <> 'VERIFIED' OR verified_at IS NOT NULL)
);

CREATE INDEX idx_chunk_replica_node ON chunk_replica (node_id);

CREATE TABLE upload_job (
    id          UUID PRIMARY KEY,
    file_id     UUID        NOT NULL UNIQUE REFERENCES stored_file (id) ON DELETE CASCADE,
    status      VARCHAR(16) NOT NULL CHECK (status IN ('OPEN', 'COMPLETED', 'ABORTED', 'EXPIRED')),
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE recovery_job (
    id                   UUID PRIMARY KEY,
    chunk_id             UUID        NOT NULL REFERENCES file_chunk (id) ON DELETE CASCADE,
    destination_node_id  UUID        NOT NULL REFERENCES storage_node (id),
    status               VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')),
    attempts             INTEGER     NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- At most one active recovery per chunk and destination node.
CREATE UNIQUE INDEX uq_recovery_active
    ON recovery_job (chunk_id, destination_node_id)
    WHERE status IN ('PENDING', 'RUNNING');

CREATE VIEW chunk_health AS
SELECT c.id AS chunk_id,
       c.file_id,
       c.chunk_index,
       COUNT(r.id) FILTER (WHERE r.state = 'VERIFIED') AS verified_replicas
FROM file_chunk c
LEFT JOIN chunk_replica r ON r.chunk_id = c.id
GROUP BY c.id, c.file_id, c.chunk_index;
