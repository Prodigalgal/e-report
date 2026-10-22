import assert from 'node:assert/strict';
import test from 'node:test';

import { isCurrentLoginChallenge, loginChallengeRefreshDelayMs } from './loginChallengeState.js';

test('login cannot use a challenge before one has loaded for the current username', () => {
    assert.equal(isCurrentLoginChallenge(null, '', 'superAdmin'), false);
    assert.equal(isCurrentLoginChallenge({ challengeId: 'challenge-1' }, 'oldAdmin', 'superAdmin'), false);
    assert.equal(isCurrentLoginChallenge({ challengeId: 'challenge-2' }, ' superAdmin ', 'superAdmin'), true);
});

test('cooldown challenge belongs to the current username even without a challenge id', () => {
    assert.equal(isCurrentLoginChallenge({ mode: 'COOLDOWN', retryAfterSeconds: 10 }, 'superAdmin', 'superAdmin'), true);
});

test('cooldown refreshes after the server delay so login can become available again', () => {
    assert.equal(loginChallengeRefreshDelayMs({ mode: 'COOLDOWN', retryAfterSeconds: 2 }), 2000);
    assert.equal(loginChallengeRefreshDelayMs({ mode: 'COOLDOWN', retryAfterSeconds: 0 }), 1000);
    assert.equal(loginChallengeRefreshDelayMs({ mode: 'COOLDOWN', retryAfterSeconds: Number.NaN }), 1000);
});

test('silent verification refreshes before expiry while a visible math question stays stable', () => {
    assert.equal(loginChallengeRefreshDelayMs({ challengeId: 'silent', expiresInSeconds: 180, captchaRequired: false }), 175000);
    assert.equal(loginChallengeRefreshDelayMs({ challengeId: 'math', expiresInSeconds: 180, captchaRequired: true }), null);
    assert.equal(loginChallengeRefreshDelayMs(null), null);
    assert.equal(loginChallengeRefreshDelayMs({ challengeId: 'invalid-expiry' }), null);
});
