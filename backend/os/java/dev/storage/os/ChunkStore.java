package dev.storage.os;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Stores chunk bytes as plain files named by UUID under one directory per node.
 * A write goes to a temporary file, is checked against the expected SHA-256,
 * is fsynced, and is then atomically renamed into place. A failed write leaves no file behind.
 */
public final class ChunkStore {

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final HexFormat HEX = HexFormat.of();

    private final Path root;
    private final long maxChunkBytes;

    public ChunkStore(Path root, long maxChunkBytes) throws IOException {
        if (maxChunkBytes <= 0) {
            throw new IllegalArgumentException("maxChunkBytes must be positive");
        }
        this.root = root.toAbsolutePath().normalize();
        this.maxChunkBytes = maxChunkBytes;
        Files.createDirectories(this.root);
    }

    public long maxChunkBytes() {
        return maxChunkBytes;
    }

    /** Total size of the volume that holds this store, in bytes. */
    public long capacityBytes() throws IOException {
        return Files.getFileStore(root).getTotalSpace();
    }

    /** Writes the stream under the given key and returns the number of bytes stored. */
    public long write(UUID key, InputStream body, String expectedSha256) throws IOException {
        Path target = pathFor(key);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new ChunkExistsException(key);
        }
        Path temp = Files.createTempFile(root, ".incoming-", ".part");
        try {
            MessageDigest digest = newDigest();
            long total = 0;
            try (OutputStream out = Files.newOutputStream(temp,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = body.read(buffer)) != -1) {
                    total += read;
                    if (total > maxChunkBytes) {
                        throw new ChunkTooLargeException(maxChunkBytes);
                    }
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            String actual = HEX.formatHex(digest.digest());
            if (!actual.equals(expectedSha256)) {
                throw new ChunkIntegrityException(expectedSha256, actual);
            }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            return total;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Returns the path of a stored chunk or throws if it does not exist. */
    public Path open(UUID key) throws IOException {
        Path path = pathFor(key);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new ChunkNotFoundException(key);
        }
        return path;
    }

    /** Re-hashes the bytes on disk, so corruption is detected on every read. */
    public String sha256Of(Path path) throws IOException {
        MessageDigest digest = newDigest();
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HEX.formatHex(digest.digest());
    }

    /** Deletes a chunk. Returns false when nothing was stored under that key. */
    public boolean delete(UUID key) throws IOException {
        return Files.deleteIfExists(pathFor(key));
    }

    private Path pathFor(UUID key) {
        // The key is a UUID, so it cannot contain path separators or "..".
        return root.resolve(key.toString());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public static final class ChunkExistsException extends RuntimeException {
        public ChunkExistsException(UUID key) {
            super("chunk already stored: " + key);
        }
    }

    public static final class ChunkNotFoundException extends RuntimeException {
        public ChunkNotFoundException(UUID key) {
            super("chunk not found: " + key);
        }
    }

    public static final class ChunkIntegrityException extends RuntimeException {
        public ChunkIntegrityException(String expected, String actual) {
            super("checksum mismatch: expected " + expected + " but received " + actual);
        }
    }

    public static final class ChunkTooLargeException extends RuntimeException {
        public ChunkTooLargeException(long limit) {
            super("chunk exceeds the limit of " + limit + " bytes");
        }
    }
}
