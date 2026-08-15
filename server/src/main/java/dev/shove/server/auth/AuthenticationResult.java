package dev.shove.server.auth;

public record AuthenticationResult(boolean authenticated, String deviceId) {

    public static AuthenticationResult authenticated(String deviceId) {
        return new AuthenticationResult(true, deviceId);
    }

    public static AuthenticationResult denied() {
        return new AuthenticationResult(false, null);
    }
}

