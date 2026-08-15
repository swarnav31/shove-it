package dev.shove.server.auth;

import jakarta.servlet.http.HttpServletRequest;

/** Request-pipeline boundary; pairing can change the implementation, not uploads. */
public interface DeviceAuthenticator {

    AuthenticationResult authenticate(HttpServletRequest request);
}

