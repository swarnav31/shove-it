package dev.shove.server.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

@Component
public final class PairedDeviceAuthenticator implements DeviceAuthenticator {

    private static final String BEARER_PREFIX = "Bearer ";

    private final PairedDeviceStore devices;
    private final Clock clock;

    @Autowired
    public PairedDeviceAuthenticator(PairedDeviceStore devices) {
        this(devices, Clock.systemUTC());
    }

    PairedDeviceAuthenticator(PairedDeviceStore devices, Clock clock) {
        this.devices = devices;
        this.clock = clock;
    }

    @Override
    public AuthenticationResult authenticate(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return AuthenticationResult.denied();
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return AuthenticationResult.denied();
        }

        return devices.findDeviceId(sha256(token))
                .map(deviceId -> {
                    devices.markSeen(deviceId, clock.instant());
                    return AuthenticationResult.authenticated(deviceId);
                })
                .orElseGet(AuthenticationResult::denied);
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide SHA-256", exception);
        }
    }
}
