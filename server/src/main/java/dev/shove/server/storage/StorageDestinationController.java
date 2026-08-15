package dev.shove.server.storage;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.shove.server.auth.AuthenticationResult;
import dev.shove.server.auth.PairedDeviceAuthenticator;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/destinations")
public final class StorageDestinationController {

    private final PairedDeviceAuthenticator authenticator;
    private final StorageDestinationRegistry destinations;

    public StorageDestinationController(
            PairedDeviceAuthenticator authenticator,
            StorageDestinationRegistry destinations) {
        this.authenticator = authenticator;
        this.destinations = destinations;
    }

    @GetMapping
    ResponseEntity<?> list(HttpServletRequest request) {
        AuthenticationResult authentication = authenticator.authenticate(request);
        if (!authentication.authenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(destinations.statuses());
    }
}
