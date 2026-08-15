package dev.shove.server.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shove")
public record ShoveProperties(
        String serverName,
        Path storageRoot,
        String externalStorageRoot,
        String externalStorageName
) {
}
