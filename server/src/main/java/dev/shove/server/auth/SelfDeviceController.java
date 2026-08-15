package dev.shove.server.auth;

import java.time.Clock;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/device")
public final class SelfDeviceController {

    private final PairedDeviceAuthenticator authenticator;
    private final PairedDeviceStore devices;
    private final Clock clock;

    public SelfDeviceController(PairedDeviceAuthenticator authenticator, PairedDeviceStore devices) {
        this.authenticator = authenticator;
        this.devices = devices;
        this.clock = Clock.systemUTC();
    }

    @DeleteMapping
    ResponseEntity<Void> revokeCurrentDevice(HttpServletRequest request) {
        AuthenticationResult authentication = authenticator.authenticate(request);
        if (!authentication.authenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        devices.revoke(authentication.deviceId(), clock.instant());
        return ResponseEntity.noContent().build();
    }
}
