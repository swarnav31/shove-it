package dev.shove.server.storage;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public final class UnknownStorageDestinationException extends RuntimeException {
    public UnknownStorageDestinationException(String destinationId) {
        super("Unknown storage destination: " + destinationId);
    }
}
