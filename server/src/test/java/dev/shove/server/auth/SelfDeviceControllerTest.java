package dev.shove.server.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SelfDeviceControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");

    private final PairedDeviceAuthenticator authenticator = mock(PairedDeviceAuthenticator.class);
    private final PairedDeviceStore devices = mock(PairedDeviceStore.class);
    private final SelfDeviceController controller = new SelfDeviceController(
            authenticator,
            devices,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @Test
    void storesAnAuthenticatedValidStatusUsingTheServerClock() {
        when(authenticator.authenticate(request)).thenReturn(AuthenticationResult.authenticated("device-1"));
        when(devices.updateStatus("device-1", "iPhone 13 mini", "ios", 40, 100, NOW)).thenReturn(true);

        var response = controller.updateCurrentDeviceStatus(
                new SelfDeviceController.DeviceStatusRequest(" iPhone 13 mini ", " iOS ", 40, 100),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(devices).updateStatus("device-1", "iPhone 13 mini", "ios", 40, 100, NOW);
    }

    @Test
    void rejectsTheEqualFreeAndTotalReadingProducedByTheBrokenClientApi() {
        when(authenticator.authenticate(request)).thenReturn(AuthenticationResult.authenticated("device-1"));

        var response = controller.updateCurrentDeviceStatus(
                new SelfDeviceController.DeviceStatusRequest("Phone", "ios", 732, 732),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(devices, never()).updateStatus(any(), any(), any(), eq(732L), eq(732L), any());
    }

    @Test
    void requiresAValidPairingToken() {
        when(authenticator.authenticate(request)).thenReturn(AuthenticationResult.denied());

        var response = controller.updateCurrentDeviceStatus(
                new SelfDeviceController.DeviceStatusRequest("Pixel 2", "android", 40, 100),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        verify(devices, never()).updateStatus(any(), any(), any(), anyLong(), anyLong(), any());
    }
}
