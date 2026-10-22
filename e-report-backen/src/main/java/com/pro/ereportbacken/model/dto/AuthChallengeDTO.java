package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.AuthChallengeMode;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthChallengeDTO {
    private String challengeId;
    private String captchaQuestion;
    private String powAlgorithm;
    private String powPrefix;
    private int powDifficulty;
    private int expiresInSeconds;
    private AuthChallengeMode mode;
    private boolean captchaRequired;
    private String serverNonce;
    private long retryAfterSeconds;
}
