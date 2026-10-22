package com.pro.ereportbacken.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Service
public class ClientRequestMetadataService {

    private final SecurityHashingService hashingService;

    public ClientRequestMetadataService(SecurityHashingService hashingService) {
        this.hashingService = hashingService;
    }

    public RequestMetadata resolve(HttpServletRequest request) {
        String remoteAddress = StringUtils.hasText(request.getRemoteAddr()) ? request.getRemoteAddr() : "unknown";
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        if (!StringUtils.hasText(userAgent)) {
            userAgent = "unknown";
        }
        return new RequestMetadata(
                hashingService.protectedHash("request-ip", remoteAddress),
                hashingService.protectedHash("request-network", networkPrefix(remoteAddress)),
                hashingService.protectedHash("request-user-agent", limit(userAgent, 512))
        );
    }

    private String networkPrefix(String remoteAddress) {
        try {
            byte[] address = InetAddress.getByName(remoteAddress).getAddress();
            if (address.length == 4) {
                return (address[0] & 0xff) + "." + (address[1] & 0xff) + "." + (address[2] & 0xff) + ".0/24";
            }
            if (address.length == 16) {
                StringBuilder prefix = new StringBuilder();
                for (int index = 0; index < 8; index += 2) {
                    if (!prefix.isEmpty()) {
                        prefix.append(':');
                    }
                    prefix.append(String.format("%02x%02x", address[index] & 0xff, address[index + 1] & 0xff));
                }
                return prefix + "::/64";
            }
        } catch (UnknownHostException ignored) {
            // The value is protected before leaving this service even when parsing fails.
        }
        return limit(remoteAddress, 100);
    }

    private String limit(String value, int maximumLength) {
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }

    public record RequestMetadata(String ipHash, String networkHash, String userAgentHash) {
    }
}
