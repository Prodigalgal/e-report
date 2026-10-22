import CheckCircleOutlineRoundedIcon from '@mui/icons-material/CheckCircleOutlineRounded';
import RadioButtonUncheckedRoundedIcon from '@mui/icons-material/RadioButtonUncheckedRounded';
import { Stack, Typography } from '@mui/material';

import { evaluatePassword, normalizePasswordPolicy } from '../../utils/passwordPolicy.js';

export default function PasswordPolicyHint({ password, context, policy }) {
    const activePolicy = normalizePasswordPolicy(policy);
    const evaluation = evaluatePassword(password, context, activePolicy);
    const composition = [
        activePolicy.requireUppercase && '大写字母',
        activePolicy.requireLowercase && '小写字母',
        activePolicy.requireDigit && '数字',
        activePolicy.requireSpecial && '特殊字符',
    ].filter(Boolean);
    return (
        <Stack spacing={0.75} aria-live="polite">
            {evaluation.requirements.map(requirement => (
                <Stack key={requirement.key} direction="row" spacing={1} sx={{ alignItems: "center" }}>
                    {requirement.met
                        ? <CheckCircleOutlineRoundedIcon color="success" fontSize="small" />
                        : <RadioButtonUncheckedRoundedIcon color="disabled" fontSize="small" />}
                    <Typography variant="body2" color={requirement.met ? 'success.main' : 'text.secondary'}>
                        {requirement.label}
                    </Typography>
                </Stack>
            ))}
            <Typography variant="caption" color="text.secondary">
                {composition.length > 0
                    ? `当前策略要求至少 ${activePolicy.minLength} 位，并包含${composition.join('、')}。`
                    : `当前策略要求至少 ${activePolicy.minLength} 位；未启用固定字符组合要求。`}
                建议使用密码管理器生成随机密码。
            </Typography>
        </Stack>
    );
}
