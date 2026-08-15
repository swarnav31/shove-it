package dev.shove.server.admin;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Keeps the owner control panel on this computer and rejects cross-site mutations. */
@Component
public final class LocalAdminAccessFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean adminSurface = path.equals("/admin")
                || path.startsWith("/admin/")
                || path.startsWith("/api/v1/admin/");
        if (adminSurface && !isLoopback(request.getRemoteAddr())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "The Shove control panel is available only on this computer");
            return;
        }

        if (isOwnerMutation(request, path) && !hasTrustedOrigin(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Untrusted browser origin");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isOwnerMutation(HttpServletRequest request, String path) {
        if (HttpMethod.GET.matches(request.getMethod()) || HttpMethod.HEAD.matches(request.getMethod())) {
            return false;
        }
        return path.equals("/api/v1/pairing/sessions")
                || path.startsWith("/api/v1/devices/")
                || path.startsWith("/api/v1/admin/");
    }

    private static boolean hasTrustedOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            // Native tools and the existing PowerShell launcher do not send Origin.
            return true;
        }
        try {
            URI uri = URI.create(origin);
            int originPort = uri.getPort() >= 0
                    ? uri.getPort()
                    : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
            return uri.getScheme() != null
                    && uri.getScheme().equalsIgnoreCase(request.getScheme())
                    && uri.getHost() != null
                    && isLoopback(uri.getHost())
                    && originPort == request.getServerPort();
        } catch (IllegalArgumentException exception) {
            return false;
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
