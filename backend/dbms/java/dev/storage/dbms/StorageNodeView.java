package dev.storage.dbms;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Read model for a registered storage node, as returned by the API. */
public record StorageNodeView(
        UUID id,
        String name,
        String baseUrl,
        long capacityBytes,
        String status,
        OffsetDateTime registeredAt,
        OffsetDateTime lastSeenAt) {
}
