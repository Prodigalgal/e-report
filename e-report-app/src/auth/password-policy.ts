import type { PasswordPolicy } from '@/types/api';

export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 100;

export const DEFAULT_PASSWORD_POLICY: PasswordPolicy = Object.freeze({
  minLength: PASSWORD_MIN_LENGTH,
  maxLength: PASSWORD_MAX_LENGTH,
  requireUppercase: true,
  requireLowercase: true,
  requireDigit: true,
  requireSpecial: true,
});

export type PasswordContext = {
  username?: string;
  name?: string;
  phone?: string;
  email?: string;
};

export function normalizePasswordPolicy(policy: Partial<PasswordPolicy> | null | undefined): PasswordPolicy {
  const parsedLength = Number(policy?.minLength);
  const minLength = Number.isFinite(parsedLength)
    ? Math.min(PASSWORD_MAX_LENGTH, Math.max(PASSWORD_MIN_LENGTH, Math.trunc(parsedLength)))
    : PASSWORD_MIN_LENGTH;
  return {
    minLength,
    maxLength: PASSWORD_MAX_LENGTH,
    requireUppercase: policy?.requireUppercase !== false,
    requireLowercase: policy?.requireLowercase !== false,
    requireDigit: policy?.requireDigit !== false,
    requireSpecial: policy?.requireSpecial !== false,
  };
}

export type PasswordRequirement = {
  key: string;
  label: string;
  met: boolean;
};

const COMMON_PASSWORDS = new Set([
  '123456789012345',
  'passwordpassword',
  'password123456',
  'admin1234567890',
  'qwertyuiop12345',
  'welcome123456789',
]);

export function evaluatePassword(
  password: string,
  context: PasswordContext = {},
  policy: Partial<PasswordPolicy> | null | undefined = DEFAULT_PASSWORD_POLICY,
) {
  const activePolicy = normalizePasswordPolicy(policy);
  const normalized = password.normalize('NFKC').toLowerCase();
  const contextValues = [context.username, context.name, context.phone, emailLocalPart(context.email)]
    .map(value => String(value || '').normalize('NFKC').toLowerCase())
    .filter(value => [...value].length >= 4);
  const requirements: PasswordRequirement[] = [
    {
      key: 'length',
      label: `长度为 ${activePolicy.minLength} 到 ${activePolicy.maxLength} 个字符`,
      met: [...password].length >= activePolicy.minLength && [...password].length <= activePolicy.maxLength,
    },
    {
      key: 'context',
      label: '不包含账号、姓名、手机号或邮箱账号部分',
      met: password.length > 0 && !contextValues.some(value => normalized.includes(value)),
    },
    {
      key: 'guessable',
      label: '不是常见口令、连续字符或大量重复字符',
      met: password.length > 0
        && !COMMON_PASSWORDS.has(normalized)
        && new Set([...normalized]).size > 2
        && !hasLongSequentialRun(normalized),
    },
    ...compositionRequirements(password, activePolicy),
  ];
  return { valid: requirements.every(requirement => requirement.met), requirements };
}

function compositionRequirements(password: string, policy: PasswordPolicy): PasswordRequirement[] {
  return [
    policy.requireUppercase && {
      key: 'uppercase',
      label: '包含大写字母',
      met: [...password].some(character => /[A-Z]/.test(character)),
    },
    policy.requireLowercase && {
      key: 'lowercase',
      label: '包含小写字母',
      met: [...password].some(character => /[a-z]/.test(character)),
    },
    policy.requireDigit && {
      key: 'digit',
      label: '包含数字',
      met: [...password].some(character => /\d/.test(character)),
    },
    policy.requireSpecial && {
      key: 'special',
      label: '包含特殊字符',
      met: [...password].some(character => /[^\p{L}\p{N}\s]/u.test(character)),
    },
  ].filter((requirement): requirement is PasswordRequirement => Boolean(requirement));
}

export function passwordsMatch(password: string, confirmation: string) {
  return password.length > 0 && password === confirmation;
}

function emailLocalPart(email?: string) {
  if (!email) return '';
  const separator = email.indexOf('@');
  return separator > 0 ? email.slice(0, separator) : email;
}

function hasLongSequentialRun(value: string) {
  let ascending = 1;
  let descending = 1;
  for (let index = 1; index < value.length; index += 1) {
    const difference = value.charCodeAt(index) - value.charCodeAt(index - 1);
    ascending = difference === 1 ? ascending + 1 : 1;
    descending = difference === -1 ? descending + 1 : 1;
    if (ascending >= 8 || descending >= 8) return true;
  }
  return false;
}
