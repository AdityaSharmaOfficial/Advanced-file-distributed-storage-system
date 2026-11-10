package dev.storage.os;

import dev.storage.os.ChunkStore.ChunkExistsException;
import dev.storage.os.ChunkStore.ChunkIntegrityException;
import dev.storage.os.ChunkStore.ChunkNotFoundException;
import dev.storage.os.ChunkStore.ChunkTooLargeException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chunks")
class ChunkController {

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    private final ChunkStore store;

    ChunkController(ChunkStore store) {
        this.store = store;
    }

    @PutMapping("/{id}")
    ResponseEntity<?> put(@PathVariable UUID id,
                          @RequestHeader("X-Sha256") String sha256,
                          HttpServletRequest request) throws IOException {
        if (!SHA256_HEX.matcher(sha256).matches()) {
            return error(HttpStatus.BAD_REQUEST, "X-Sha256 must be 64 lowercase hex characters");
        }
        long bytes = store.write(id, request.getInputStream(), sha256);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", id.toString(), "sizeBytes", bytes));
    }

    @GetMapping("/{id}")
    ResponseEntity<Resource> get(@PathVariable UUID id) throws IOException {
        Path path = store.open(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(path))
                .header("X-Sha256", store.sha256Of(path))
                .body(new FileSystemResource(path));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) throws IOException {
        if (store.delete(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(ChunkNotFoundException.class)
    ResponseEntity<?> notFound(ChunkNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ChunkExistsException.class)
    ResponseEntity<?> exists(ChunkExistsException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(ChunkIntegrityException.class)
    ResponseEntity<?> integrity(ChunkIntegrityException e) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(ChunkTooLargeException.class)
    ResponseEntity<?> tooLarge(ChunkTooLargeException e) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, e.getMessage());
    }

    private static ResponseEntity<?> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
