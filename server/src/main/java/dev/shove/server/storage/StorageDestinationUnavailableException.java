package dev.shove.server.storage;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public final class StorageDestinationUnavailableException extends RuntimeException {
    public StorageDestinationUnavailableException(String displayName) {
        super("Storage destination is unavailable: " + displayName);
    }
}
