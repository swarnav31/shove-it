package dev.shove.server.upload;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.shove.server.auth.AuthenticationResult;
import dev.shove.server.auth.PairedDeviceAuthenticator;
import dev.shove.server.storage.StorageDestinationRegistry;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1")
public final class UploadController {

    private final PairedDeviceAuthenticator authenticator;
    private final UploadService uploads;

    public UploadController(PairedDeviceAuthenticator authenticator, UploadService uploads) {
        this.authenticator = authenticator;
        this.uploads = uploads;
    }

    @PostMapping("/upload")
    ResponseEntity<?> upload(
            HttpServletRequest request,
            @RequestHeader(
                    name = "X-Shove-Destination",
                    defaultValue = StorageDestinationRegistry.DEFAULT_DESTINATION_ID) String destinationId,
            @RequestHeader(name = "X-Shove-Filename", defaultValue = "upload.bin") String filename)
            throws IOException {
        AuthenticationResult authentication = authenticator.authenticate(request);
        if (!authentication.authenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UploadReceipt receipt = uploads.receive(
                authentication.deviceId(),
                destinationId,
                filename,
                request.getContentLengthLong(),
                request.getInputStream());
        return ResponseEntity.status(HttpStatus.CREATED).body(receipt);
    }

    @GetMapping("/uploads")
    ResponseEntity<?> history(HttpServletRequest request) {
        AuthenticationResult authentication = authenticator.authenticate(request);
        if (!authentication.authenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(uploads.listReceipts(authentication.deviceId()));
    }

    @GetMapping("/uploads/{id}")
    ResponseEntity<?> status(HttpServletRequest request, @PathVariable String id) {
        AuthenticationResult authentication = authenticator.authenticate(request);
        if (!authentication.authenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return uploads.findReceipt(id)
                .filter(receipt -> authentication.deviceId().equals(receipt.deviceId()))
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
