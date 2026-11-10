package dev.storage.os;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class StorageNodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(StorageNodeApplication.class, args);
    }

    @Bean
    ChunkStore chunkStore(@Value("${DATA_DIR:/data/chunks}") String dataDir,
                          @Value("${MAX_CHUNK_BYTES:67108864}") long maxChunkBytes) throws IOException {
        return new ChunkStore(Path.of(dataDir), maxChunkBytes);
    }
}
