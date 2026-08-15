package dev.shove.server.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

@Component
@Profile("dev")
public final class DevelopmentDeviceAuthenticator implements DeviceAuthenticator {

    private static final String DEVELOPMENT_DEVICE_ID = "local-development-device";

    @Override
    public AuthenticationResult authenticate(HttpServletRequest request) {
        return AuthenticationResult.authenticated(DEVELOPMENT_DEVICE_ID);
    }
}
