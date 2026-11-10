package dev.storage.os;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.storage.os.ChunkStore.ChunkExistsException;
import dev.storage.os.ChunkStore.ChunkIntegrityException;
import dev.storage.os.ChunkStore.ChunkTooLargeException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChunkStoreTest {

    @TempDir
    Path tmp;

    @Test
    void storesVerifiedBytesAndReadsThemBack() throws Exception {
        ChunkStore store = new ChunkStore(tmp, 1024);
        byte[] data = "hello chunk".getBytes(StandardCharsets.UTF_8);
        UUID key = UUID.randomUUID();

        long written = store.write(key, new ByteArrayInputStream(data), sha256(data));

        assertEquals(data.length, written);
        Path stored = store.open(key);
        assertArrayEquals(data, Files.readAllBytes(stored));
        assertEquals(sha256(data), store.sha256Of(stored));
    }

    @Test
    void rejectsChecksumMismatchAndLeavesNoFileBehind() {
        ChunkStore store = assertDoesNotThrow(() -> new ChunkStore(tmp, 1024));
        UUID key = UUID.randomUUID();
        byte[] data = "payload".getBytes(StandardCharsets.UTF_8);

        assertThrows(ChunkIntegrityException.class,
                () -> store.write(key, new ByteArrayInputStream(data), "0".repeat(64)));

        assertFalse(Files.exists(tmp.resolve(key.toString())));
        assertEquals(0, countEntries());
    }

    @Test
    void rejectsChunksAboveTheLimit() throws Exception {
        ChunkStore store = new ChunkStore(tmp, 4);
        byte[] data = "too long".getBytes(StandardCharsets.UTF_8);

        assertThrows(ChunkTooLargeException.class,
                () -> store.write(UUID.randomUUID(), new ByteArrayInputStream(data), sha256(data)));
        assertEquals(0, countEntries());
    }

    @Test
    void refusesToOverwriteAnExistingChunk() throws Exception {
        ChunkStore store = new ChunkStore(tmp, 1024);
        UUID key = UUID.randomUUID();
        byte[] data = "once".getBytes(StandardCharsets.UTF_8);
        store.write(key, new ByteArrayInputStream(data), sha256(data));

        assertThrows(ChunkExistsException.class,
                () -> store.write(key, new ByteArrayInputStream(data), sha256(data)));
    }

    @Test
    void deleteReportsWhetherTheChunkExisted() throws Exception {
        ChunkStore store = new ChunkStore(tmp, 1024);
        UUID key = UUID.randomUUID();
        byte[] data = "bye".getBytes(StandardCharsets.UTF_8);
        store.write(key, new ByteArrayInputStream(data), sha256(data));

        assertTrue(store.delete(key));
        assertFalse(store.delete(key));
    }

    private long countEntries() throws Exception {
        try (Stream<Path> entries = Files.list(tmp)) {
            return entries.count();
        }
    }

    private static ChunkStore assertDoesNotThrow(ThrowingSupplier<ChunkStore> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            throw new AssertionError("unexpected exception", e);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
