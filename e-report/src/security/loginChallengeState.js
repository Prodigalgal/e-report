export const isCurrentLoginChallenge = (challenge, challengeUsername, username) => {
    const normalizedUsername = String(username ?? '').trim();
    return Boolean(challenge)
        && normalizedUsername.length > 0
        && String(challengeUsername ?? '').trim() === normalizedUsername;
};
