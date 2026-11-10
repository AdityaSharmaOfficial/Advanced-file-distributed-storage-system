package dev.storage.os;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Registers this node with the coordinator when it starts. Retries while the coordinator comes up. */
@Component
class NodeRegistrar {

    private static final Logger log = LoggerFactory.getLogger(NodeRegistrar.class);
    private static final int MAX_ATTEMPTS = 10;

    private final ChunkStore store;
    private final RestClient http = RestClient.create();
    private final String coordinatorUrl;
    private final String nodeName;
    private final String publicUrl;

    NodeRegistrar(ChunkStore store,
                  @Value("${COORDINATOR_URL}") String coordinatorUrl,
                  @Value("${NODE_NAME}") String nodeName,
                  @Value("${NODE_PUBLIC_URL}") String publicUrl) {
        this.store = store;
        this.coordinatorUrl = coordinatorUrl;
        this.nodeName = nodeName;
        this.publicUrl = publicUrl;
    }

    @EventListener(ApplicationReadyEvent.class)
    void registerWithCoordinator() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                http.post()
                        .uri(coordinatorUrl + "/api/nodes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new RegistrationBody(nodeName, publicUrl, store.capacityBytes()))
                        .retrieve()
                        .toBodilessEntity();
                log.info("Registered storage node {} with the coordinator", nodeName);
                return;
            } catch (RestClientException | IOException e) {
                log.warn("Registration attempt {} of {} failed: {}", attempt, MAX_ATTEMPTS, e.getMessage());
                pause(2000L * attempt);
            }
        }
        log.error("Gave up registering {}. Restart the node after the coordinator is running.", nodeName);
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    record RegistrationBody(String name, String baseUrl, long capacityBytes) {
    }
}
