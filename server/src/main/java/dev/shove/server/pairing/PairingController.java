package dev.shove.server.pairing;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dev.shove.server.pairing.PairingService.InvalidPairingCodeException;
import dev.shove.server.pairing.PairingService.PairedDevice;
import dev.shove.server.pairing.PairingService.PairingCode;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1")
public final class PairingController {

    private final PairingService pairing;

    public PairingController(PairingService pairing) {
        this.pairing = pairing;
    }

    @PostMapping("/pairing/sessions")
    PairingCode createSession(HttpServletRequest request) {
        if (!isLoopback(request.getRemoteAddr())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Pairing codes can only be created on the server");
        }
        return pairing.createSession();
    }

    @PostMapping("/pair")
    PairedDevice pair(@RequestBody PairRequest request) {
        return pairing.claim(request.code(), request.deviceName());
    }

    @ExceptionHandler(InvalidPairingCodeException.class)
    ResponseEntity<ErrorResponse> invalidCode(InvalidPairingCodeException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(exception.getMessage()));
    }

    record PairRequest(String code, String deviceName) {
    }

    record ErrorResponse(String error) {
    }

    private static boolean isLoopback(String address) {
        try {
            return InetAddress.getByName(address).isLoopbackAddress();
        } catch (UnknownHostException exception) {
            return false;
        }
    }
}
