package dev.storage.dbms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/** Raw SQL access to storage_node. Every statement is visible here. */
@Component
class NodeRepository {

    private static final String UPSERT = """
            INSERT INTO storage_node (id, name, base_url, capacity_bytes, status, last_seen_at)
            VALUES (?, ?, ?, ?, 'ONLINE', now())
            ON CONFLICT (name) DO UPDATE SET
                base_url       = EXCLUDED.base_url,
                capacity_bytes = EXCLUDED.capacity_bytes,
                status         = 'ONLINE',
                last_seen_at   = now()
            RETURNING id, name, base_url, capacity_bytes, status, registered_at, last_seen_at
            """;

    private static final String SELECT_NODES =
            "SELECT id, name, base_url, capacity_bytes, status, registered_at, last_seen_at FROM storage_node";

    private final DataSource dataSource;

    NodeRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Inserts a node, or refreshes the existing row with the same name.
     * A base URL already used by a different node raises SQLState 23505.
     */
    StorageNodeView upsert(UUID id, String name, String baseUrl, long capacityBytes) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT)) {
            ps.setObject(1, id);
            ps.setString(2, name);
            ps.setString(3, baseUrl);
            ps.setLong(4, capacityBytes);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return map(rs);
            }
        }
    }

    List<StorageNodeView> findAll() throws SQLException {
        List<StorageNodeView> nodes = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_NODES + " ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                nodes.add(map(rs));
            }
        }
        return nodes;
    }

    Optional<StorageNodeView> findById(UUID id) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_NODES + " WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private static StorageNodeView map(ResultSet rs) throws SQLException {
        return new StorageNodeView(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("base_url"),
                rs.getLong("capacity_bytes"),
                rs.getString("status"),
                rs.getObject("registered_at", OffsetDateTime.class),
                rs.getObject("last_seen_at", OffsetDateTime.class));
    }
}
