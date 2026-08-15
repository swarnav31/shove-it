package dev.shove.server.serverinfo;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.shove.server.config.ShoveProperties;
import dev.shove.server.storage.StorageDestinationRegistry;

@RestController
@RequestMapping
public final class ServerInfoController {

    private final ShoveProperties properties;
    private final StorageDestinationRegistry destinations;

    public ServerInfoController(ShoveProperties properties, StorageDestinationRegistry destinations) {
        this.properties = properties;
        this.destinations = destinations;
    }

    @GetMapping("/healthz")
    ResponseEntity<HealthResponse> health() {
        if (!destinations.anyAvailable()) {
            return ResponseEntity.internalServerError()
                    .body(new HealthResponse("degraded", false));
        }
        return ResponseEntity.ok(new HealthResponse("ok", true));
    }

    @GetMapping("/api/v1/server")
    ServerInfoResponse serverInfo() {
        return new ServerInfoResponse(
                properties.serverName(),
                1,
                List.of("health", "basic-pairing", "whole-file-upload", "multiple-destinations"));
    }

    record HealthResponse(String status, boolean storageWritable) {
    }

    record ServerInfoResponse(String name, int protocolVersion, List<String> capabilities) {
    }
}
