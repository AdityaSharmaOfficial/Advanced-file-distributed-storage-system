# Distributed Storage

A local chunk-based storage system. A coordinator tracks storage nodes and metadata, storage nodes keep chunk bytes on their own volumes, and a Next.js dashboard shows the nodes from the coordinator API.

This drop covers Phase 3 (node registration and metadata APIs) and Phase 4 (chunk write, read, and delete on a storage node with SHA-256 checks).

## Layout

```
.
├── docker-compose.yml
├── .env.example
├── backend
│   ├── pom.xml
│   ├── Dockerfile
│   ├── os          storage node: chunk files on disk, checksums, HTTP chunk API, node registration
│   └── dbms        coordinator: SQL schema, constraint tests, raw JDBC queries, node registry API
└── frontend        Next.js dashboard (reads only real coordinator data)
```

Inside `backend/os`, `java/` holds the code and `test/` holds the unit test. Inside `backend/dbms`, `java/` holds the code, `sql/` holds the schema that is applied at startup, and `constraint_tests.sql` holds the database checks.

## Run

```
cp .env.example .env        # then set POSTGRES_PASSWORD
docker compose up --build
```

Open http://localhost:3000. The three storage nodes register themselves with the coordinator at http://localhost:8080/api/nodes. Each node is reachable only inside the compose network.

## What works

- Storage node: `PUT /chunks/{id}` streams the body to a temporary file, checks it against the `X-Sha256` header, fsyncs, and renames it into place. A bad checksum or an oversized body leaves no file. `GET /chunks/{id}` re-hashes the file and returns the result in `X-Sha256`. `DELETE /chunks/{id}` removes a chunk.
- Storage node: each chunk is stored under its UUID. The UUID comes from the coordinator, never from a user-supplied path.
- Storage node: registers with the coordinator on startup and retries up to 10 times.
- Coordinator: `POST /api/nodes`, `GET /api/nodes`, and `GET /api/nodes/{id}`. Re-registering a node name updates its row.
- Coordinator: applies `sql/V1__schema.sql` at startup in plain JDBC and records it in `schema_migrations`.
- Dashboard: lists registered nodes from the API and refreshes every 5 seconds.

## Not built yet

- Chunked upload and download with file reconstruction (Phase 5)
- Replication and capacity-aware placement (Phase 6). The `recovery_job` table and its unique index exist, but no recovery logic is written.
- Heartbeats, failure detection, and self-healing (Phase 7). Node status is set to ONLINE on registration and nothing changes it afterwards.
- Testcontainers integration tests and failure simulation (Phase 9)

## Design notes

- Spring is used only for HTTP, validation, and bean wiring. There is no JPA, no Spring Data, and no Flyway. Every SQL statement is in `backend/dbms` and runs through JDBC.
- Chunk bytes never go into PostgreSQL. Metadata only.
- Each storage node has its own Docker volume.
- `chunk_replica` has one row per physical copy. A chunk can have at most one copy per node. The replication factor is the count of `VERIFIED` rows, exposed through the `chunk_health` view.
- A partial unique index on `recovery_job (chunk_id, destination_node_id) WHERE status IN ('PENDING', 'RUNNING')` stops duplicate active recovery jobs.

## Limits

- The coordinator is a single instance and a single point of failure.
- There is no authentication and no TLS. Anyone who can reach the coordinator can register a node. This setup is for local development only.
- Node capacity is the total size of the volume, not free space.
- Every chunk read re-hashes the file, which costs disk I/O on large chunks.
- The coordinator opens a new JDBC connection per request. A connection pool is planned.

## Verification status

- PostgreSQL 16: `sql/V1__schema.sql` applied with no errors. `constraint_tests.sql` ran with `ON_ERROR_STOP=1`: 16 PASS notices, 0 failures, exit code 0, and no rows left behind after the rollback.
- Frontend: `npm ci` and `npm run build` completed. The TypeScript check and the production build passed.
- Backend (Java): **not compiled or tested in the build environment.** Maven Central was unreachable from there, so Spring Boot dependencies could not be downloaded. The Java code has not been run. Verify it with:

  ```
  cd backend && mvn -B test && mvn -B -DskipTests package
  ```

- Docker Compose: **not run.** There was no Docker in the build environment.
