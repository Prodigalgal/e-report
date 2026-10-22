import apiClient from './apiClient.js';
import { webClientInstanceId } from '../constants/authStorage.js';
import { getWebAuthenticationEnvironment } from '../security/webEnvironment.js';
import { solveProofOfWork } from '../security/proofOfWork.js';

export const fetchAuthChallenge = async username => {
    const webEnvironment = await getWebAuthenticationEnvironment();
    const response = await apiClient.post('/auth/challenge', {
        username,
        clientType: 'WEB',
        clientInstanceId: webClientInstanceId(),
        webFingerprintHash: webEnvironment.fingerprintHash,
        webSecuritySignals: webEnvironment.securitySignals,
    });
    return response.data;
};

export { getWebAuthenticationEnvironment };

export const solvePow = async (challenge) => {
    return solveProofOfWork(challenge);
};
