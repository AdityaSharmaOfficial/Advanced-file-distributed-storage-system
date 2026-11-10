-- Constraint tests for backend/dbms/sql/V1__schema.sql.
-- Run after the schema is applied:
--   psql -v ON_ERROR_STOP=1 -d <database> -f constraint_tests.sql
-- Everything runs in one transaction that is rolled back at the end.

\set ON_ERROR_STOP on
BEGIN;

CREATE OR REPLACE FUNCTION pg_temp.expect_rejected(stmt TEXT, label TEXT) RETURNS VOID AS $fn$
BEGIN
    EXECUTE stmt;
    RAISE EXCEPTION 'FAIL: accepted bad data (%)', label;
EXCEPTION
    WHEN check_violation OR unique_violation OR foreign_key_violation OR not_null_violation THEN
        RAISE NOTICE 'PASS: rejected (%)', label;
END;
$fn$ LANGUAGE plpgsql;

-- Fixture rows
INSERT INTO storage_node (id, name, base_url, capacity_bytes, status) VALUES
    ('00000000-0000-0000-0000-000000000001', 'node-a', 'http://node-a:8080', 1024, 'ONLINE'),
    ('00000000-0000-0000-0000-000000000002', 'node-b', 'http://node-b:8080', 1024, 'ONLINE');

INSERT INTO stored_file (id, file_name, size_bytes, chunk_size_bytes, chunk_count, sha256, status) VALUES
    ('00000000-0000-0000-0000-000000000011', 'sample.bin', 8, 8, 1, repeat('a', 64), 'COMPLETE');

INSERT INTO file_chunk (id, file_id, chunk_index, size_bytes, sha256) VALUES
    ('00000000-0000-0000-0000-000000000021', '00000000-0000-0000-0000-000000000011', 0, 8, repeat('b', 64));

INSERT INTO chunk_replica (id, chunk_id, node_id, storage_key, state, verified_at) VALUES
    ('00000000-0000-0000-0000-000000000031', '00000000-0000-0000-0000-000000000021',
     '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000041', 'VERIFIED', now());

-- storage_node rules
SELECT pg_temp.expect_rejected($q$INSERT INTO storage_node (id, name, base_url, capacity_bytes, status)
    VALUES (gen_random_uuid(), 'Bad_Name', 'http://x:1', 1, 'ONLINE')$q$, 'node name with uppercase and underscore');
SELECT pg_temp.expect_rejected($q$INSERT INTO storage_node (id, name, base_url, capacity_bytes, status)
    VALUES (gen_random_uuid(), 'node-z', 'http://z:1', 0, 'ONLINE')$q$, 'zero capacity');
SELECT pg_temp.expect_rejected($q$INSERT INTO storage_node (id, name, base_url, capacity_bytes, status)
    VALUES (gen_random_uuid(), 'node-y', 'http://y:1', 1, 'BROKEN')$q$, 'unknown node status');
SELECT pg_temp.expect_rejected($q$INSERT INTO storage_node (id, name, base_url, capacity_bytes, status)
    VALUES (gen_random_uuid(), 'node-a', 'http://new:1', 1, 'ONLINE')$q$, 'duplicate node name');
SELECT pg_temp.expect_rejected($q$INSERT INTO storage_node (id, name, base_url, capacity_bytes, status)
    VALUES (gen_random_uuid(), 'node-c', 'http://node-a:8080', 1, 'ONLINE')$q$, 'duplicate base URL');

-- stored_file rules
SELECT pg_temp.expect_rejected($q$INSERT INTO stored_file (id, file_name, size_bytes, chunk_size_bytes, chunk_count, sha256, status)
    VALUES (gen_random_uuid(), 'x', 1, 1, 1, repeat('A', 64), 'COMPLETE')$q$, 'uppercase sha256');
SELECT pg_temp.expect_rejected($q$INSERT INTO stored_file (id, file_name, size_bytes, chunk_size_bytes, chunk_count, sha256, status)
    VALUES (gen_random_uuid(), 'x', 1, 1, 1, repeat('a', 64), 'PENDING')$q$, 'unknown file status');

-- file_chunk rules
SELECT pg_temp.expect_rejected($q$INSERT INTO file_chunk (id, file_id, chunk_index, size_bytes, sha256)
    VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000011', -1, 8, repeat('c', 64))$q$, 'negative chunk index');
SELECT pg_temp.expect_rejected($q$INSERT INTO file_chunk (id, file_id, chunk_index, size_bytes, sha256)
    VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000011', 0, 8, repeat('c', 64))$q$, 'duplicate chunk index within a file');

-- chunk_replica rules
SELECT pg_temp.expect_rejected($q$INSERT INTO chunk_replica (id, chunk_id, node_id, storage_key, state)
    VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000021',
            '00000000-0000-0000-0000-000000000001', gen_random_uuid(), 'PENDING')$q$, 'second physical copy on the same node');
SELECT pg_temp.expect_rejected($q$INSERT INTO chunk_replica (id, chunk_id, node_id, storage_key, state)
    VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000021',
            '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000041', 'PENDING')$q$, 'reused storage key');
SELECT pg_temp.expect_rejected($q$INSERT INTO chunk_replica (id, chunk_id, node_id, storage_key, state)
    VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000021',
            '00000000-0000-0000-0000-000000000002', gen_random_uuid(), 'VERIFIED')$q$, 'VERIFIED without verified_at');
SELECT pg_temp.expect_rejected($q$INSERT INTO chunk_replica (id, chunk_id, node_id, storage_key, state)
    VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000099',
            '00000000-0000-0000-0000-000000000002', gen_random_uuid(), 'PENDING')$q$, 'replica of an unknown chunk');

-- Only VERIFIED rows count toward replication.
INSERT INTO chunk_replica (id, chunk_id, node_id, storage_key, state) VALUES
    ('00000000-0000-0000-0000-000000000032', '00000000-0000-0000-0000-000000000021',
     '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000043', 'PENDING');

-- recovery_job rules
INSERT INTO recovery_job (id, chunk_id, destination_node_id, status) VALUES
    ('00000000-0000-0000-0000-000000000051', '00000000-0000-0000-0000-000000000021',
     '00000000-0000-0000-0000-000000000002', 'PENDING');
SELECT pg_temp.expect_rejected($q$INSERT INTO recovery_job (id, chunk_id, destination_node_id, status)
    VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000021',
            '00000000-0000-0000-0000-000000000002', 'RUNNING')$q$, 'second active recovery for same chunk and destination');
INSERT INTO recovery_job (id, chunk_id, destination_node_id, status) VALUES
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000021',
     '00000000-0000-0000-0000-000000000002', 'DONE');
DO $$
BEGIN
    IF (SELECT count(*) FROM recovery_job
        WHERE chunk_id = '00000000-0000-0000-0000-000000000021') <> 2 THEN
        RAISE EXCEPTION 'FAIL: expected one PENDING and one DONE recovery row';
    END IF;
    RAISE NOTICE 'PASS: a DONE recovery row can coexist with an active one';
END
$$;

-- Replication factor counts VERIFIED rows only (expected 1 here).
DO $$
BEGIN
    IF (SELECT verified_replicas FROM chunk_health
        WHERE chunk_id = '00000000-0000-0000-0000-000000000021') <> 1 THEN
        RAISE EXCEPTION 'FAIL: chunk_health should count exactly one VERIFIED replica';
    END IF;
    RAISE NOTICE 'PASS: chunk_health counts only VERIFIED replicas';
END
$$;

ROLLBACK;
