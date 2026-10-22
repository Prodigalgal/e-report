import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchAuthChallenge, getWebAuthenticationEnvironment, solvePow } from '../services/authChallengeService.js';
import { isCurrentLoginChallenge, loginChallengeRefreshDelayMs } from '../security/loginChallengeState.js';
import { formatApiErrorMessage } from '../utils/formatApiErrorMessage.js';

export const useLoginChallenge = username => {
    const [challenge, setChallenge] = useState(null);
    const [challengeUsername, setChallengeUsername] = useState('');
    const [loadingChallenge, setLoadingChallenge] = useState(false);
    const [challengeError, setChallengeError] = useState('');
    const requestSequence = useRef(0);

    const refreshChallenge = useCallback(async requestedUsername => {
        const normalizedUsername = String(requestedUsername ?? username).trim();
        if (!normalizedUsername) {
            requestSequence.current += 1;
            setChallenge(null);
            setChallengeUsername('');
            setLoadingChallenge(false);
            setChallengeError('');
            return null;
        }
        const sequence = ++requestSequence.current;
        setChallenge(null);
        setChallengeUsername('');
        setLoadingChallenge(true);
        setChallengeError('');
        try {
            const nextChallenge = await fetchAuthChallenge(normalizedUsername);
            if (sequence !== requestSequence.current) return null;
            setChallenge(nextChallenge);
            setChallengeUsername(normalizedUsername);
            return nextChallenge;
        } catch (error) {
            if (sequence === requestSequence.current) {
                setChallenge(null);
                setChallengeUsername('');
                setChallengeError(formatApiErrorMessage(error, '安全验证加载失败，请检查网络后重试。'));
            }
            throw error;
        } finally {
            if (sequence === requestSequence.current) setLoadingChallenge(false);
        }
    }, [username]);

    useEffect(() => {
        const normalizedUsername = username.trim();
        if (!normalizedUsername) {
            requestSequence.current += 1;
            setChallenge(null);
            setChallengeUsername('');
            setChallengeError('');
            setLoadingChallenge(false);
            return undefined;
        }
        const timeout = globalThis.setTimeout(() => {
            refreshChallenge(normalizedUsername).catch(() => {});
        }, 350);
        return () => {
            globalThis.clearTimeout(timeout);
            requestSequence.current += 1;
        };
    }, [refreshChallenge, username]);

    const normalizedUsername = String(username ?? '').trim();
    const challengeReady = isCurrentLoginChallenge(challenge, challengeUsername, normalizedUsername);
    const currentChallenge = challengeReady ? challenge : null;

    useEffect(() => {
        const delay = loginChallengeRefreshDelayMs(currentChallenge);
        if (delay === null) return undefined;
        const timer = globalThis.setTimeout(() => {
            refreshChallenge(normalizedUsername).catch(() => {});
        }, delay);
        return () => globalThis.clearTimeout(timer);
    }, [currentChallenge, normalizedUsername, refreshChallenge]);

    const buildLoginProof = useCallback(async (captchaAnswer, submittedUsername) => {
        const normalizedUsername = submittedUsername.trim();
        const activeChallenge = currentChallenge && challengeUsername === normalizedUsername
            ? currentChallenge
            : await refreshChallenge(normalizedUsername);
        if (!activeChallenge?.challengeId) {
            const seconds = Number(activeChallenge?.retryAfterSeconds) || 0;
            throw new Error(seconds > 0 ? `登录保护已启用，请在${seconds}秒后重试。` : '安全验证未加载，请重试。');
        }
        if (activeChallenge.captchaRequired && !captchaAnswer.trim()) {
            throw new Error('请输入数学验证码答案。');
        }
        const powNonce = await solvePow(activeChallenge);
        const webEnvironment = await getWebAuthenticationEnvironment();
        return {
            challengeId: activeChallenge.challengeId,
            captchaAnswer: activeChallenge.captchaRequired ? captchaAnswer.trim() : null,
            powNonce,
            webEnvironment,
        };
    }, [challengeUsername, currentChallenge, refreshChallenge]);

    return {
        challenge: currentChallenge,
        challengeReady,
        loadingChallenge,
        challengeError,
        refreshChallenge,
        buildLoginProof,
    };
};
