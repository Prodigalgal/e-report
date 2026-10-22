import { sha256 } from 'js-sha256';

const DEFAULT_YIELD_INTERVAL = 16384;
const MAXIMUM_DIFFICULTY = 6;

export async function solveProofOfWork(challenge, yieldInterval = DEFAULT_YIELD_INTERVAL) {
    const challengeId = String(challenge?.challengeId || '');
    const powPrefix = String(challenge?.powPrefix || '');
    const difficulty = Number(challenge?.powDifficulty);
    if (!challengeId || !powPrefix || !Number.isInteger(difficulty) || difficulty < 0 || difficulty > MAXIMUM_DIFFICULTY) {
        throw new Error('安全挑战数据不完整。');
    }

    const targetPrefix = '0'.repeat(difficulty);
    const payloadPrefix = `${challengeId}:${powPrefix}:`;
    for (let nonce = 0; nonce < Number.MAX_SAFE_INTEGER; nonce += 1) {
        if (sha256(`${payloadPrefix}${nonce}`).startsWith(targetPrefix)) {
            return String(nonce);
        }
        if (nonce > 0 && nonce % yieldInterval === 0) {
            await yieldToBrowser();
        }
    }
    throw new Error('安全校验计算失败，请刷新验证码后重试。');
}

const yieldToBrowser = () => new Promise(resolve => globalThis.setTimeout(resolve, 0));
