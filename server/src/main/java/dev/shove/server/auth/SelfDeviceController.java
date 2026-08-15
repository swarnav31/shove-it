package dev.shove.server.auth;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/device")
public final class SelfDeviceController {

    private final PairedDeviceAuthenticator authenticator;
    private final PairedDeviceStore devices;
    private final Clock clock;

    @Autowired
    public SelfDeviceController(PairedDeviceAuthenticator authenticator, PairedDeviceStore devices) {
        this(authenticator, devices, Clock.systemUTC());
    }

    SelfDeviceController(PairedDeviceAuthenticator authenticator, PairedDeviceStore devices, Clock clock) {
        this.authenticator = authenticator;
        this.devices = devices;
        this.clock = clock;
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

    @PutMapping("/status")
    ResponseEntity<Void> updateCurrentDeviceStatus(
            @RequestBody DeviceStatusRequest status,
            HttpServletRequest request) {
        AuthenticationResult authentication = authenticator.authenticate(request);
        if (!authentication.authenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String platform = normalizedPlatform(status.platform());
        if (platform == null
                || status.totalBytes() <= 0
                || status.availableBytes() < 0
                || status.availableBytes() >= status.totalBytes()) {
            return ResponseEntity.badRequest().build();
        }

        boolean updated = devices.updateStatus(
                authentication.deviceId(),
                platform,
                status.availableBytes(),
                status.totalBytes(),
                clock.instant());
        return updated
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    private static String normalizedPlatform(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.length() <= 32 && normalized.matches("[a-z0-9._-]+") ? normalized : null;
    }

    record DeviceStatusRequest(String platform, long availableBytes, long totalBytes) {
    }
}
