import assert from 'node:assert/strict';
import test from 'node:test';

import { isCurrentLoginChallenge } from './loginChallengeState.js';

test('login cannot use a challenge before one has loaded for the current username', () => {
    assert.equal(isCurrentLoginChallenge(null, '', 'superAdmin'), false);
    assert.equal(isCurrentLoginChallenge({ challengeId: 'challenge-1' }, 'oldAdmin', 'superAdmin'), false);
    assert.equal(isCurrentLoginChallenge({ challengeId: 'challenge-2' }, ' superAdmin ', 'superAdmin'), true);
});

test('cooldown challenge belongs to the current username even without a challenge id', () => {
    assert.equal(isCurrentLoginChallenge({ mode: 'COOLDOWN', retryAfterSeconds: 10 }, 'superAdmin', 'superAdmin'), true);
});
