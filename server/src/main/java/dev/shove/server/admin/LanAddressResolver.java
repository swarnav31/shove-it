package dev.shove.server.admin;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Comparator;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public final class LanAddressResolver {

    public Optional<String> findPrivateIpv4Address() {
        try {
            return NetworkInterface.networkInterfaces()
                    .filter(this::isUsable)
                    .flatMap(networkInterface -> networkInterface.inetAddresses()
                            .filter(Inet4Address.class::isInstance)
                            .map(address -> new AddressCandidate(
                                    address.getHostAddress(),
                                    interfacePreference(networkInterface))))
                    .filter(candidate -> isPrivate(candidate.address()))
                    .sorted(Comparator
                            .comparingInt(AddressCandidate::interfacePreference)
                            .thenComparingInt(candidate -> addressPreference(candidate.address())))
                    .map(AddressCandidate::address)
                    .findFirst();
        } catch (SocketException exception) {
            return Optional.empty();
        }
    }

    private boolean isUsable(NetworkInterface networkInterface) {
        try {
            return networkInterface.isUp() && !networkInterface.isLoopback() && !networkInterface.isVirtual();
        } catch (SocketException exception) {
            return false;
        }
    }

    private static boolean isPrivate(String address) {
        if (address.startsWith("10.") || address.startsWith("192.168.")) {
            return true;
        }
        if (!address.startsWith("172.")) {
            return false;
        }
        String[] parts = address.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        int second = Integer.parseInt(parts[1]);
        return second >= 16 && second <= 31;
    }

    private static int addressPreference(String address) {
        return address.startsWith("192.168.") ? 0 : address.startsWith("10.") ? 1 : 2;
    }

    private static int interfacePreference(NetworkInterface networkInterface) {
        String identity = (networkInterface.getName() + " " + networkInterface.getDisplayName()).toLowerCase();
        if (identity.contains("wi-fi") || identity.contains("wifi") || identity.contains("wireless") || identity.contains("wlan")) {
            return 0;
        }
        if (identity.contains("vethernet") || identity.contains("wsl") || identity.contains("hyper-v")
                || identity.contains("virtual") || identity.contains("vpn") || identity.contains("tunnel")) {
            return 3;
        }
        return 1;
    }

    private record AddressCandidate(String address, int interfacePreference) {
    }
}
