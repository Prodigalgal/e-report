import { Text, View } from 'react-native';

import { evaluatePassword, normalizePasswordPolicy, type PasswordContext } from '@/auth/password-policy';
import type { PasswordPolicy } from '@/types/api';
import { colors } from '@/ui/theme';

export function PasswordPolicyHint({ password, context, policy }: { password: string; context?: PasswordContext; policy?: PasswordPolicy }) {
  const activePolicy = normalizePasswordPolicy(policy);
  const evaluation = evaluatePassword(password, context, activePolicy);
  const composition = [
    activePolicy.requireUppercase && '大写字母',
    activePolicy.requireLowercase && '小写字母',
    activePolicy.requireDigit && '数字',
    activePolicy.requireSpecial && '特殊字符',
  ].filter(Boolean);
  return (
    <View style={{ gap: 7 }}>
      {evaluation.requirements.map(requirement => (
        <View key={requirement.key} style={{ flexDirection: 'row', alignItems: 'flex-start', gap: 8 }}>
          <Text style={{ color: requirement.met ? colors.success : colors.placeholder, fontWeight: '900' }}>
            {requirement.met ? '✓' : '○'}
          </Text>
          <Text style={{ color: requirement.met ? colors.success : colors.muted, flex: 1 }}>
            {requirement.label}
          </Text>
        </View>
      ))}
      <Text style={{ color: colors.muted, fontSize: 13, lineHeight: 19 }}>
        {composition.length > 0
          ? `当前策略要求至少 ${activePolicy.minLength} 位，并包含${composition.join('、')}。`
          : `当前策略要求至少 ${activePolicy.minLength} 位；未启用固定字符组合要求。`}
        建议使用密码管理器生成随机密码。
      </Text>
    </View>
  );
}
