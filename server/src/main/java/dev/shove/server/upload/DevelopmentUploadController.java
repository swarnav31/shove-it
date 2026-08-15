package dev.shove.server.upload;

import java.io.IOException;

import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.shove.server.auth.AuthenticationResult;
import dev.shove.server.auth.DeviceAuthenticator;
import dev.shove.server.storage.StorageDestinationRegistry;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/dev/uploads")
@Profile("dev")
public final class DevelopmentUploadController {

    private final DeviceAuthenticator authenticator;
    private final UploadService uploads;

    public DevelopmentUploadController(
            @Qualifier("developmentDeviceAuthenticator") DeviceAuthenticator authenticator,
            UploadService uploads) {
        this.authenticator = authenticator;
        this.uploads = uploads;
    }

    @PostMapping
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
}
