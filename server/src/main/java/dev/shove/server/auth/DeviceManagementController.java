package dev.shove.server.auth;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/devices")
public final class DeviceManagementController {

    private final PairedDeviceStore devices;
    private final Clock clock;

    public DeviceManagementController(PairedDeviceStore devices) {
        this.devices = devices;
        this.clock = Clock.systemUTC();
    }

    @GetMapping
    Object list(HttpServletRequest request) {
        requireLoopback(request);
        return devices.list();
    }

    @DeleteMapping("/{deviceId}")
    ResponseEntity<Void> revoke(HttpServletRequest request, @PathVariable String deviceId) {
        requireLoopback(request);
        return devices.revoke(deviceId, clock.instant())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    private static void requireLoopback(HttpServletRequest request) {
        if (!isLoopback(request.getRemoteAddr())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Device management is available only on the server");
        }
    }

    private static boolean isLoopback(String address) {
        try {
            return InetAddress.getByName(address).isLoopbackAddress();
        } catch (UnknownHostException exception) {
            return false;
        }
    }
}
