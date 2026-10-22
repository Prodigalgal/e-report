package com.pro.ereportbacken.security;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApiTokenAuthenticationDetails(
        UUID tokenId,
        String tokenName,
        LocalDateTime expiresAt
) {
}
