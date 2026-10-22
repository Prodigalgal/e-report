import { sha256 } from 'js-sha256';

import type { AuthChallenge } from '@/types/api';

const YIELD_INTERVAL = 16384;
const MAXIMUM_DIFFICULTY = 6;

export async function solveProofOfWork(challenge: AuthChallenge): Promise<string> {
  const difficulty = Number(challenge.powDifficulty);
  if (!challenge.challengeId
    || !challenge.powPrefix
    || !Number.isInteger(difficulty)
    || difficulty < 0
    || difficulty > MAXIMUM_DIFFICULTY) {
    throw new Error('安全挑战数据不完整。');
  }

  const targetPrefix = '0'.repeat(difficulty);
  const payloadPrefix = `${challenge.challengeId}:${challenge.powPrefix}:`;
  for (let nonce = 0; nonce < Number.MAX_SAFE_INTEGER; nonce += 1) {
    if (sha256(`${payloadPrefix}${nonce}`).startsWith(targetPrefix)) return String(nonce);
    if (nonce > 0 && nonce % YIELD_INTERVAL === 0) {
      await new Promise(resolve => setTimeout(resolve, 0));
    }
  }
  throw new Error('安全挑战计算失败，请刷新后重试。');
}
