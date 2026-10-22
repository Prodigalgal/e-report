package com.pro.ereportbacken.model.dto;

import java.util.UUID;

public record TokenRefreshResponseDTO(
        UUID sessionId,
        String token,
        String refreshToken,
        long accessTokenExpiresInSeconds
) {
}
