package dev.shove.server.pairing;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import dev.shove.server.auth.PairedDeviceAuthenticator;
import dev.shove.server.auth.PairedDeviceStore;

@Service
public final class PairingService {

    private static final Duration SESSION_LIFETIME = Duration.ofMinutes(2);

    private final SecureRandom random = new SecureRandom();
    private final Map<String, PairingSession> sessions = new ConcurrentHashMap<>();
    private final PairedDeviceStore devices;
    private final Clock clock;

    @Autowired
    public PairingService(PairedDeviceStore devices) {
        this(devices, Clock.systemUTC());
    }

    PairingService(PairedDeviceStore devices, Clock clock) {
        this.devices = devices;
        this.clock = clock;
    }

    public PairingCode createSession() {
        Instant expiresAt = clock.instant().plus(SESSION_LIFETIME);
        String code;
        do {
            code = "%06d".formatted(random.nextInt(1_000_000));
        } while (sessions.putIfAbsent(code, new PairingSession(expiresAt)) != null);
        removeExpiredSessions();
        return new PairingCode(code, expiresAt);
    }

    public PairedDevice claim(String code, String requestedDeviceName) {
        PairingSession session = sessions.remove(normalizeCode(code));
        if (session == null || !session.expiresAt().isAfter(clock.instant())) {
            throw new InvalidPairingCodeException();
        }

        String deviceId = UUID.randomUUID().toString();
        String token = newToken();
        String name = normalizeName(requestedDeviceName);
        devices.add(deviceId, name, PairedDeviceAuthenticator.sha256(token), clock.instant());
        return new PairedDevice(deviceId, name, token);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void removeExpiredSessions() {
        Instant now = clock.instant();
        sessions.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private static String normalizeCode(String code) {
        return code == null ? "" : code.replaceAll("\\D", "");
    }

    private static String normalizeName(String name) {
        String normalized = name == null ? "Mobile device" : name.trim();
        if (normalized.isEmpty()) normalized = "Mobile device";
        return normalized.substring(0, Math.min(normalized.length(), 80));
    }

    private record PairingSession(Instant expiresAt) {
    }

    public record PairingCode(String code, Instant expiresAt) {
    }

    public record PairedDevice(String deviceId, String deviceName, String token) {
    }

    public static final class InvalidPairingCodeException extends RuntimeException {
        public InvalidPairingCodeException() {
            super("Pairing code is invalid or expired");
        }
    }
}
