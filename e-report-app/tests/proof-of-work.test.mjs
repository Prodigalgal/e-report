import assert from 'node:assert/strict';
import test from 'node:test';

import { sha256 } from 'js-sha256';

import { solveProofOfWork } from '../src/auth/proof-of-work.ts';

test('APP 批量 PoW 保持服务端挑战摘要契约', async () => {
  const challenge = {
    challengeId: 'challenge',
    powPrefix: 'prefix',
    powDifficulty: 3,
  };
  const nonce = await solveProofOfWork(challenge);
  assert.match(sha256(`${challenge.challengeId}:${challenge.powPrefix}:${nonce}`), /^000/);
});

test('APP 拒绝异常挑战难度', async () => {
  await assert.rejects(
    () => solveProofOfWork({ challengeId: 'a', powPrefix: 'b', powDifficulty: 7 }),
    /数据不完整/,
  );
});
