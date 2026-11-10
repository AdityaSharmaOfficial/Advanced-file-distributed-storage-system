package dev.storage.dbms;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nodes")
class NodeController {

    private static final String UNIQUE_VIOLATION = "23505";

    private final NodeRepository nodes;

    NodeController(NodeRepository nodes) {
        this.nodes = nodes;
    }

    @PostMapping
    ResponseEntity<?> register(@Valid @RequestBody RegisterNodeRequest request) throws SQLException {
        try {
            StorageNodeView saved = nodes.upsert(
                    UUID.randomUUID(), request.name(), request.baseUrl(), request.capacityBytes());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "base URL is already used by another node"));
            }
            throw e;
        }
    }

    @GetMapping
    List<StorageNodeView> list() throws SQLException {
        return nodes.findAll();
    }

    @GetMapping("/{id}")
    ResponseEntity<?> get(@PathVariable UUID id) throws SQLException {
        Optional<StorageNodeView> node = nodes.findById(id);
        if (node.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "node not found"));
        }
        return ResponseEntity.ok(node.get());
    }

    public record RegisterNodeRequest(
            @NotBlank
            @jakarta.validation.constraints.Pattern(regexp = "^[a-z0-9-]{3,64}$",
                    message = "name must be 3-64 characters of lowercase letters, digits, or hyphens")
            String name,

            @NotBlank
            @Size(max = 255)
            @jakarta.validation.constraints.Pattern(regexp = "^https?://[^\\s]+$",
                    message = "baseUrl must be an http or https URL")
            String baseUrl,

            @Positive
            long capacityBytes) {
    }
}
