export const isCurrentLoginChallenge = (challenge, challengeUsername, username) => {
    const normalizedUsername = String(username ?? '').trim();
    return Boolean(challenge)
        && normalizedUsername.length > 0
        && String(challengeUsername ?? '').trim() === normalizedUsername;
};

export const loginChallengeRefreshDelayMs = challenge => {
    if (challenge?.mode === 'COOLDOWN') {
        const seconds = Number(challenge.retryAfterSeconds);
        return (Number.isFinite(seconds) ? Math.max(1, Math.min(seconds, 300)) : 1) * 1000;
    }
    const expiresInSeconds = Number(challenge?.expiresInSeconds);
    if (!challenge?.challengeId || challenge.captchaRequired || !Number.isFinite(expiresInSeconds) || expiresInSeconds <= 0) return null;
    return Math.max(1, expiresInSeconds - 5) * 1000;
};
