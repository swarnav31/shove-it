package dev.shove.server.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;

class LocalAdminAccessFilterTest {

    private final LocalAdminAccessFilter filter = new LocalAdminAccessFilter();

    @Test
    void rejectsAdminPageRequestsFromTheLan() throws ServletException, IOException {
        MockHttpServletRequest request = request("GET", "/admin/index.html");
        request.setRemoteAddr("192.168.1.20");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void allowsAdminPageRequestsFromThisComputer() throws ServletException, IOException {
        MockHttpServletRequest request = request("GET", "/admin/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsPairingCreationFromAnUntrustedWebsite() throws ServletException, IOException {
        MockHttpServletRequest request = request("POST", "/api/v1/pairing/sessions");
        request.addHeader("Origin", "https://example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void allowsSameOriginPairingCreation() throws ServletException, IOException {
        MockHttpServletRequest request = request("POST", "/api/v1/pairing/sessions");
        request.addHeader("Origin", "http://127.0.0.1:8787");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("127.0.0.1");
        request.setScheme("http");
        request.setServerPort(8787);
        return request;
    }
}
