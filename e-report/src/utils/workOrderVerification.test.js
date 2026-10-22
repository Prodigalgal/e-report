import assert from 'node:assert/strict';
import test from 'node:test';

import { buildVerificationCommand, isVerificationCommentRequired } from './workOrderVerification.js';

test('验收通过只提交一个必填验收意见', () => {
    const command = buildVerificationCommand({
        responseId: 'response-id',
        decision: 'APPROVED',
        comment: '  夜间应急自验  ',
        expectedVersion: 7,
    });

    assert.equal(command.comment, '夜间应急自验');
    assert.equal('overrideReason' in command, false);
    assert.equal(isVerificationCommentRequired('APPROVED'), true);
});

test('验收通过和驳回都要求非空意见', () => {
    const command = buildVerificationCommand({
        responseId: 'response-id',
        decision: 'APPROVED',
        comment: '  ',
        expectedVersion: 8,
    });

    assert.equal(command.comment, '');
    assert.equal(isVerificationCommentRequired('APPROVED'), true);
    assert.equal(isVerificationCommentRequired('REJECTED'), true);
});
