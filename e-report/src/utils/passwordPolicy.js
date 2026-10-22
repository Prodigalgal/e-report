export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 100;

export const DEFAULT_PASSWORD_POLICY = Object.freeze({
    minLength: PASSWORD_MIN_LENGTH,
    maxLength: PASSWORD_MAX_LENGTH,
    requireUppercase: true,
    requireLowercase: true,
    requireDigit: true,
    requireSpecial: true,
});

const COMMON_PASSWORDS = new Set([
    '123456789012345',
    'passwordpassword',
    'password123456',
    'admin1234567890',
    'qwertyuiop12345',
    'welcome123456789',
]);

export const normalizePasswordPolicy = (policy = DEFAULT_PASSWORD_POLICY) => ({
    minLength: clampInteger(policy?.minLength, DEFAULT_PASSWORD_POLICY.minLength),
    maxLength: DEFAULT_PASSWORD_POLICY.maxLength,
    requireUppercase: policy?.requireUppercase !== false,
    requireLowercase: policy?.requireLowercase !== false,
    requireDigit: policy?.requireDigit !== false,
    requireSpecial: policy?.requireSpecial !== false,
});

export const evaluatePassword = (password, context = {}, policy = DEFAULT_PASSWORD_POLICY) => {
    const value = String(password || '');
    const activePolicy = normalizePasswordPolicy(policy);
    const normalized = value.normalize('NFKC').toLowerCase();
    const contextValues = [context.username, context.name, context.phone, emailLocalPart(context.email)]
        .map(item => String(item || '').normalize('NFKC').toLowerCase())
        .filter(item => [...item].length >= 4);
    const requirements = [
        {
            key: 'length',
            label: `长度为 ${activePolicy.minLength} 到 ${activePolicy.maxLength} 个字符`,
            met: [...value].length >= activePolicy.minLength && [...value].length <= activePolicy.maxLength,
        },
        {
            key: 'context',
            label: '不包含账号、姓名、手机号或邮箱账号部分',
            met: value.length > 0 && !contextValues.some(item => normalized.includes(item)),
        },
        {
            key: 'guessable',
            label: '不是常见口令、连续字符或大量重复字符',
            met: value.length > 0
                && !COMMON_PASSWORDS.has(normalized)
                && new Set([...normalized]).size > 2
                && !hasLongSequentialRun(normalized),
        },
        ...compositionRequirements(value, activePolicy),
    ];
    return {
        valid: requirements.every(item => item.met),
        requirements,
    };
};

const compositionRequirements = (value, policy) => [
    policy.requireUppercase && {
        key: 'uppercase',
        label: '包含大写字母',
        met: [...value].some(character => /[A-Z]/.test(character)),
    },
    policy.requireLowercase && {
        key: 'lowercase',
        label: '包含小写字母',
        met: [...value].some(character => /[a-z]/.test(character)),
    },
    policy.requireDigit && {
        key: 'digit',
        label: '包含数字',
        met: [...value].some(character => /\d/.test(character)),
    },
    policy.requireSpecial && {
        key: 'special',
        label: '包含特殊字符',
        met: [...value].some(character => /[^\p{L}\p{N}\s]/u.test(character)),
    },
].filter(Boolean);

const clampInteger = (value, fallback) => {
    const parsed = Number(value);
    if (!Number.isFinite(parsed)) return fallback;
    return Math.min(PASSWORD_MAX_LENGTH, Math.max(PASSWORD_MIN_LENGTH, Math.trunc(parsed)));
};

export const passwordsMatch = (password, confirmation) => (
    String(password || '').length > 0 && String(password) === String(confirmation || '')
);

const emailLocalPart = email => {
    const value = String(email || '');
    const separator = value.indexOf('@');
    return separator > 0 ? value.slice(0, separator) : value;
};

const hasLongSequentialRun = value => {
    let ascending = 1;
    let descending = 1;
    for (let index = 1; index < value.length; index += 1) {
        const difference = value.charCodeAt(index) - value.charCodeAt(index - 1);
        ascending = difference === 1 ? ascending + 1 : 1;
        descending = difference === -1 ? descending + 1 : 1;
        if (ascending >= 8 || descending >= 8) return true;
    }
    return false;
};
