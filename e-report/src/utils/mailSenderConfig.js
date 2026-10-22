export const MAIL_SENDER_PROVIDER_PRESETS = {
    CUSTOM: {
        label: '自定义 SMTP',
        host: '',
        port: 465,
        sslEnabled: true,
        startTlsEnabled: false,
        startTlsRequired: false,
    },
    GMAIL: {
        label: 'Gmail',
        host: 'smtp.gmail.com',
        port: 587,
        sslEnabled: false,
        startTlsEnabled: true,
        startTlsRequired: true,
    },
    MICROSOFT_365: {
        label: 'Microsoft 365',
        host: 'smtp.office365.com',
        port: 587,
        sslEnabled: false,
        startTlsEnabled: true,
        startTlsRequired: true,
    },
    MOBILE_139: {
        label: '139邮箱',
        host: 'smtp.139.com',
        port: 465,
        sslEnabled: true,
        startTlsEnabled: false,
        startTlsRequired: false,
    },
};

export const MAIL_SENDER_PROVIDER_HELP = {
    CUSTOM: '按邮箱服务商提供的 SMTP 主机、端口、认证与加密参数填写；没有认证的内部中继可关闭 SMTP认证。',
    GMAIL: 'Gmail 使用 smtp.gmail.com；465 为 SSL 直连，587 使用 STARTTLS。可使用邮箱授权码或 OAuth2。',
    MICROSOFT_365: 'Microsoft 365 使用 smtp.office365.com:587 和 STARTTLS；建议使用 OAuth2。微软计划从 2026 年 12 月底起对现有租户默认关闭 Basic SMTP AUTH，新租户默认不可用；使用 SMTP AUTH 时需确认组织和邮箱已启用。',
    MOBILE_139: '139邮箱预设使用 smtp.139.com:465 SSL；按运营商邮箱控制台的 SMTP 开关和授权码配置。',
};

export const createEmptyMailSenderDraft = (providerId = 'CUSTOM') => {
    const preset = MAIL_SENDER_PROVIDER_PRESETS[providerId] || MAIL_SENDER_PROVIDER_PRESETS.CUSTOM;
    return {
        id: null,
        providerId,
        host: preset.host,
        port: preset.port,
        username: '',
        password: '',
        fromAddress: '',
        smtpAuth: true,
        sslEnabled: preset.sslEnabled,
        startTlsEnabled: preset.startTlsEnabled,
        startTlsRequired: preset.startTlsRequired,
        authenticationMode: 'PASSWORD',
        authMechanism: 'AUTO',
        ehloDomain: '',
        authDomain: '',
        saslRealm: '',
        sslProtocols: 'TLSv1.2 TLSv1.3',
        connectionTimeoutMs: 10000,
        readTimeoutMs: 30000,
        writeTimeoutMs: 30000,
        oauthClientId: '',
        oauthClientSecret: '',
        oauthRefreshToken: '',
        oauthTenantId: 'common',
        oauthScope: '',
        passwordConfigured: false,
        oauthClientSecretConfigured: false,
        oauthRefreshTokenConfigured: false,
    };
};

export const applyMailSenderProviderPreset = (sender, providerId) => {
    const preset = MAIL_SENDER_PROVIDER_PRESETS[providerId] || MAIL_SENDER_PROVIDER_PRESETS.CUSTOM;
    return {
        ...sender,
        providerId,
        authenticationMode: ['GMAIL', 'MICROSOFT_365'].includes(providerId)
            ? sender.authenticationMode
            : 'PASSWORD',
        host: preset.host,
        port: preset.port,
        sslEnabled: preset.sslEnabled,
        startTlsEnabled: preset.startTlsEnabled,
        startTlsRequired: preset.startTlsRequired,
    };
};
