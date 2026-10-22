export const PHONE_PATTERN = /^1[3-9][0-9]{9}$/;
export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const normalizeUserProfile = ({ name, phone, email }) => ({
    name: String(name || '').trim(),
    phone: String(phone || '').trim(),
    email: String(email || '').trim(),
});

export const validateUserProfile = (profile) => {
    const normalized = normalizeUserProfile(profile);
    const errors = {};
    if (!normalized.name) errors.name = '姓名不能为空';
    if (!PHONE_PATTERN.test(normalized.phone)) errors.phone = '请输入11位中国大陆手机号';
    if (normalized.email && !EMAIL_PATTERN.test(normalized.email)) errors.email = '请输入正确的邮箱地址';
    return { normalized, errors, valid: Object.keys(errors).length === 0 };
};
