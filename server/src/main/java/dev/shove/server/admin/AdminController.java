package dev.shove.server.admin;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import dev.shove.server.config.ShoveProperties;
import dev.shove.server.storage.StorageDestinationRegistry;
import dev.shove.server.storage.StorageDestinationRegistry.StorageDestinationStatus;

@Controller
final class AdminPageController {

    @GetMapping({"/admin", "/admin/"})
    String admin() {
        return "redirect:/admin/index.html";
    }
}

@RestController
@RequestMapping("/api/v1/admin")
public final class AdminController {

    private final ShoveProperties properties;
    private final StorageDestinationRegistry destinations;
    private final LanAddressResolver lanAddressResolver;
    private final int serverPort;
    private final int expoPort;

    public AdminController(
            ShoveProperties properties,
            StorageDestinationRegistry destinations,
            LanAddressResolver lanAddressResolver,
            @Value("${server.port:8787}") int serverPort,
            @Value("${shove.expo-port:8081}") int expoPort) {
        this.properties = properties;
        this.destinations = destinations;
        this.lanAddressResolver = lanAddressResolver;
        this.serverPort = serverPort;
        this.expoPort = expoPort;
    }

    @GetMapping("/overview")
    Overview overview() {
        String lanAddress = lanAddressResolver.findPrivateIpv4Address().orElse(null);
        String serverUrl = lanAddress == null ? null : "http://" + lanAddress + ":" + serverPort;
        String expoProjectUrl = lanAddress == null ? null : "exp://" + lanAddress + ":" + expoPort;
        return new Overview(
                properties.serverName(),
                serverUrl,
                expoProjectUrl,
                portIsOpen(expoPort),
                destinations.statuses());
    }

    @GetMapping(value = "/expo-qr.svg", produces = "image/svg+xml")
    ResponseEntity<String> expoQr() throws WriterException {
        String lanAddress = lanAddressResolver.findPrivateIpv4Address().orElse(null);
        if (lanAddress == null) {
            return ResponseEntity.notFound().build();
        }
        String payload = "exp://" + lanAddress + ":" + expoPort;
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.valueOf("image/svg+xml"))
                .body(toSvg(new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 33, 33)));
    }

    private static boolean portIsOpen(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static String toSvg(BitMatrix matrix) {
        int quietZone = 2;
        int size = matrix.getWidth() + quietZone * 2;
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                if (matrix.get(x, y)) {
                    path.append('M').append(x + quietZone).append(' ').append(y + quietZone).append("h1v1h-1z");
                }
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + size + " " + size
                + "\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"Expo Go project QR code\">"
                + "<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/><path d=\"" + path
                + "\" fill=\"#07110e\"/></svg>";
    }

    public record Overview(
            String serverName,
            String serverUrl,
            String expoProjectUrl,
            boolean expoReady,
            List<StorageDestinationStatus> destinations) {
    }
}
