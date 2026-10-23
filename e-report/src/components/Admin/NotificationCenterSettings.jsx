import React, { useEffect, useState } from 'react';
import {
    Alert,
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Box,
    Chip,
    CircularProgress,
    Divider,
    FormControlLabel,
    FormControl,
    Button,
    InputLabel,
    MenuItem,
    Select,
    Switch,
    TextField,
    Typography,
} from '@mui/material';
import SaveIcon from '@mui/icons-material/Save';
import AddIcon from '@mui/icons-material/Add';
import DeleteOutlinedIcon from '@mui/icons-material/DeleteOutlined';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import SendIcon from '@mui/icons-material/Send';

import apiClient from '../../services/apiClient';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';
import { notificationSettingSourceLabel } from '../../utils/notificationConfig';
import {
    applyMailSenderProviderPreset,
    createEmptyMailSenderDraft,
    mailSenderIntervalError,
    MAIL_SENDER_PROVIDER_HELP,
    MAIL_SENDER_PROVIDER_PRESETS,
} from '../../utils/mailSenderConfig';

const configRequest = (orgUnitId) => ({
    params: { orgUnitId: orgUnitId || undefined },
});

const senderDraftFromAccount = (sender) => {
    const defaults = createEmptyMailSenderDraft(sender.providerId || 'CUSTOM');
    return {
        ...defaults,
        ...sender,
        id: sender.id || null,
        providerId: sender.providerId || 'CUSTOM',
        host: sender.host || '',
        port: sender.port ?? defaults.port,
        username: sender.username || '',
        fromAddress: sender.fromAddress || '',
        smtpAuth: sender.smtpAuth ?? defaults.smtpAuth,
        sslEnabled: sender.sslEnabled ?? defaults.sslEnabled,
        startTlsEnabled: sender.startTlsEnabled ?? defaults.startTlsEnabled,
        startTlsRequired: sender.startTlsRequired ?? defaults.startTlsRequired,
        authenticationMode: sender.authenticationMode || defaults.authenticationMode,
        authMechanism: sender.authMechanism || defaults.authMechanism,
        ehloDomain: sender.ehloDomain || '',
        authDomain: sender.authDomain || '',
        saslRealm: sender.saslRealm || '',
        sslProtocols: sender.sslProtocols || defaults.sslProtocols,
        connectionTimeoutMs: sender.connectionTimeoutMs ?? defaults.connectionTimeoutMs,
        readTimeoutMs: sender.readTimeoutMs ?? defaults.readTimeoutMs,
        writeTimeoutMs: sender.writeTimeoutMs ?? defaults.writeTimeoutMs,
        sendIntervalSeconds: sender.sendIntervalSeconds ?? defaults.sendIntervalSeconds,
        oauthClientId: sender.oauthClientId || '',
        oauthTenantId: sender.oauthTenantId || defaults.oauthTenantId,
        oauthScope: sender.oauthScope || '',
        password: '',
        oauthClientSecret: '',
        oauthRefreshToken: '',
        passwordConfigured: sender.passwordConfigured === true,
    };
};

const senderDraftsFromSettings = (settings) => (settings?.localSenders || []).map(senderDraftFromAccount);

const senderRequestBody = (sender) => ({
    id: sender.id,
    providerId: sender.providerId,
    host: sender.host,
    port: Number(sender.port),
    username: sender.username,
    password: sender.password,
    fromAddress: sender.fromAddress,
    smtpAuth: sender.smtpAuth,
    sslEnabled: sender.sslEnabled,
    startTlsEnabled: sender.startTlsEnabled,
    startTlsRequired: sender.startTlsRequired,
    authenticationMode: sender.authenticationMode,
    authMechanism: sender.authMechanism,
    ehloDomain: sender.ehloDomain,
    authDomain: sender.authDomain,
    saslRealm: sender.saslRealm,
    sslProtocols: sender.sslProtocols,
    connectionTimeoutMs: Number(sender.connectionTimeoutMs),
    readTimeoutMs: Number(sender.readTimeoutMs),
    writeTimeoutMs: Number(sender.writeTimeoutMs),
    sendIntervalSeconds: Number(sender.sendIntervalSeconds),
    oauthClientId: sender.oauthClientId,
    oauthClientSecret: sender.oauthClientSecret,
    oauthRefreshToken: sender.oauthRefreshToken,
    oauthTenantId: sender.oauthTenantId,
    oauthScope: sender.oauthScope,
});

function NotificationSwitch({ setting, tenantName, disabled, saving, onChange }) {
    const neutralSource = setting.inherited || setting.sourceScope === 'UNCONFIGURED';
    return (
        <Box sx={{ py: 1.25, minWidth: 0 }}>
            <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 1 }}>
                <FormControlLabel
                    sx={{ m: 0, minWidth: 0, alignItems: 'flex-start' }}
                    control={(
                        <Switch
                            checked={setting.enabled === true}
                            onChange={(event) => onChange(event.target.checked)}
                            disabled={disabled || saving}
                            slotProps={{ input: { 'aria-label': setting.displayName } }}
                        />
                    )}
                    label={(
                        <Box sx={{ pt: 0.65, minWidth: 0 }}>
                            <Typography variant="body2" sx={{ fontWeight: 800 }}>{setting.displayName}</Typography>
                            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.25 }}>
                                {setting.description}
                            </Typography>
                        </Box>
                    )}
                />
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, flexShrink: 0, pt: 0.75 }}>
                    {saving && <CircularProgress size={16} />}
                    <Chip
                        size="small"
                        label={notificationSettingSourceLabel(setting, tenantName)}
                        color={neutralSource ? 'default' : 'primary'}
                        variant={neutralSource ? 'outlined' : 'filled'}
                    />
                </Box>
            </Box>
        </Box>
    );
}

export default function NotificationCenterSettings({
    orgUnitId,
    view = 'channels',
    scopeLabel,
    tenantName,
    canUpdateChannels,
    canUpdateSchedule,
    canUpdateSender,
    disabled,
    onSaved,
}) {
    const [settings, setSettings] = useState(null);
    const [loading, setLoading] = useState(true);
    const [savingKey, setSavingKey] = useState(null);
    const [error, setError] = useState(null);
    const [dailySendTime, setDailySendTime] = useState('18:00');
    const [senderDrafts, setSenderDrafts] = useState([]);
    const [senderStrategy, setSenderStrategy] = useState('INHERIT');
    const [inheritedTestRecipient, setInheritedTestRecipient] = useState('');
    const [senderTestResults, setSenderTestResults] = useState({});

    useEffect(() => {
        let active = true;
        const load = async () => {
            setLoading(true);
            setError(null);
            setSenderTestResults({});
            setInheritedTestRecipient('');
            try {
                const response = await apiClient.get('/admin/notifications/settings', configRequest(orgUnitId));
                if (!active) return;
                setSettings(response.data);
                setDailySendTime(response.data?.dailyOutstandingReport?.sendTime || '18:00');
                setSenderDrafts(senderDraftsFromSettings(response.data?.mailSender));
                setSenderStrategy(response.data?.mailSender?.strategy || 'INHERIT');
            } catch (requestError) {
                if (!active) return;
                setError(formatApiErrorMessage(requestError, '加载通知中心配置失败，请稍后重试。'));
            } finally {
                if (active) setLoading(false);
            }
        };
        load();
        return () => { active = false; };
    }, [orgUnitId]);

    const updateSwitch = async (setting, enabled) => {
        setSavingKey(setting.name);
        setError(null);
        try {
            const response = await apiClient.put(
                `/admin/notifications/settings/${encodeURIComponent(setting.name)}`,
                { enabled },
                configRequest(orgUnitId),
            );
            setSettings((previous) => ({
                ...previous,
                center: previous.center.name === response.data.name ? response.data : previous.center,
                channels: previous.channels.map((channel) => (
                    channel.name === response.data.name ? response.data : channel
                )),
                dailyOutstandingReport: previous.dailyOutstandingReport.switchSetting.name === response.data.name
                    ? { ...previous.dailyOutstandingReport, switchSetting: response.data }
                    : previous.dailyOutstandingReport,
            }));
            onSaved?.(`${setting.displayName}已${enabled ? '开启' : '关闭'}。`);
        } catch (requestError) {
            setError(formatApiErrorMessage(requestError, `更新${setting.displayName}失败，请稍后重试。`));
        } finally {
            setSavingKey(null);
        }
    };

    const updateSenderDraft = (index, field, value) => {
        setSenderDrafts(previous => previous.map((sender, senderIndex) => (
            senderIndex === index ? { ...sender, [field]: value } : sender
        )));
        setSenderTestResults(previous => ({ ...previous, [index]: null }));
    };

    const updateProviderPreset = (index, providerId) => {
        setSenderDrafts(previous => previous.map((sender, senderIndex) => (
            senderIndex === index ? applyMailSenderProviderPreset(sender, providerId) : sender
        )));
        setSenderTestResults(previous => ({ ...previous, [index]: null }));
    };

    const updateSmtpAuthentication = (index, enabled) => {
        setSenderTestResults(previous => ({ ...previous, [index]: null }));
        setSenderDrafts(previous => previous.map((sender, senderIndex) => (
            senderIndex === index
                ? {
                    ...sender,
                    smtpAuth: enabled,
                    ...(enabled ? {} : { authenticationMode: 'PASSWORD', authMechanism: 'AUTO' }),
                }
                : sender
        )));
    };

    const updateTlsMode = (index, mode, enabled) => {
        setSenderTestResults(previous => ({ ...previous, [index]: null }));
        setSenderDrafts(previous => previous.map((sender, senderIndex) => {
            if (senderIndex !== index) return sender;
            if (mode === 'ssl') {
                return {
                    ...sender,
                    sslEnabled: enabled,
                    ...(enabled ? { startTlsEnabled: false, startTlsRequired: false } : {}),
                };
            }
            return {
                ...sender,
                startTlsEnabled: enabled,
                startTlsRequired: enabled,
                ...(enabled ? { sslEnabled: false } : {}),
            };
        }));
    };

    const updateAuthenticationMechanism = (index, mechanism) => {
        setSenderTestResults(previous => ({ ...previous, [index]: null }));
        setSenderDrafts(previous => previous.map((sender, senderIndex) => (
            senderIndex === index
                ? { ...sender, authMechanism: mechanism, ...(mechanism === 'DIGEST_MD5' ? {} : { saslRealm: '' }) }
                : sender
        )));
    };

    const authenticationMechanisms = [
        ['AUTO', '自动协商'],
        ['LOGIN', 'LOGIN'],
        ['PLAIN', 'PLAIN'],
        ['CRAM_MD5', 'CRAM-MD5'],
        ['DIGEST_MD5', 'DIGEST-MD5'],
        ['NTLM', 'NTLM'],
    ];

    const updateSender = async () => {
        if (senderStrategy !== 'INHERIT' && senderDrafts.length === 0) {
            setError('请至少新增一个邮件发件方。');
            return;
        }
        for (const [index, sender] of senderDrafts.entries()) {
            if (!sender.host.trim() || !sender.fromAddress.trim()
                || (sender.smtpAuth && !sender.username.trim())) {
                setError(`发件方${index + 1}的服务器和发件地址不能为空；启用SMTP认证时还需填写用户名。`);
                return;
            }
            if (!Number.isInteger(Number(sender.port)) || Number(sender.port) < 1 || Number(sender.port) > 65535) {
                setError(`发件方${index + 1}的邮件服务器端口必须是1至65535之间的整数。`);
                return;
            }
            const usesOAuth = sender.authenticationMode === 'OAUTH2';
            if (sender.smtpAuth && !usesOAuth && !sender.passwordConfigured && !sender.password.trim()) {
                setError(`发件方${index + 1}首次配置密码认证时必须填写密码或授权码。`);
                return;
            }
            if (sender.smtpAuth && usesOAuth
                && (!sender.oauthClientId.trim()
                    || (!sender.oauthClientSecretConfigured && !sender.oauthClientSecret.trim())
                    || (!sender.oauthRefreshTokenConfigured && !sender.oauthRefreshToken.trim()))) {
                setError(`发件方${index + 1}的OAuth2客户端和刷新令牌配置不完整。`);
                return;
            }
            if (sender.sslEnabled && sender.startTlsEnabled) {
                setError(`发件方${index + 1}不能同时启用SSL直连和STARTTLS。`);
                return;
            }
            if (mailSenderIntervalError(sender.sendIntervalSeconds)) {
                setError(`发件方${index + 1}的单封发送间隔必须是0至3600秒之间的整数。`);
                return;
            }
            for (const field of ['connectionTimeoutMs', 'readTimeoutMs', 'writeTimeoutMs']) {
                const timeoutMs = Number(sender[field]);
                if (!Number.isInteger(timeoutMs) || timeoutMs < 1000 || timeoutMs > 120000) {
                    setError(`发件方${index + 1}的超时必须是1000至120000毫秒之间的整数。`);
                    return;
                }
            }
        }
        setSavingKey('mail-sender');
        setError(null);
        try {
            const response = await apiClient.put(
                '/admin/notifications/settings/sender',
                {
                    senders: senderDrafts.map(senderRequestBody),
                    strategy: senderStrategy,
                },
                configRequest(orgUnitId),
            );
            setSettings(previous => ({ ...previous, mailSender: response.data }));
            setSenderDrafts(senderDraftsFromSettings(response.data));
            setSenderStrategy(response.data.strategy);
            setSenderTestResults({});
            onSaved?.('邮件发件池配置已保存。');
        } catch (requestError) {
            setError(formatApiErrorMessage(requestError, '保存邮件发件池失败，请稍后重试。'));
        } finally {
            setSavingKey(null);
        }
    };

    const testSender = async (index, inheritedSender) => {
        const sender = inheritedSender || senderDrafts[index];
        const testRecipient = sender.testRecipient?.trim();
        if (!testRecipient) {
            setError('请填写测试收件地址。');
            return;
        }
        setSavingKey(`mail-sender-test-${index}`);
        setError(null);
        setSenderTestResults(previous => ({ ...previous, [index]: null }));
        try {
            const response = await apiClient.post(
                '/admin/notifications/settings/sender/test',
                inheritedSender
                    ? { inheritedSenderId: inheritedSender.id, testRecipient }
                    : { sender: senderRequestBody(sender), testRecipient },
                configRequest(orgUnitId),
            );
            setSenderTestResults(previous => ({ ...previous, [index]: response.data }));
        } catch (requestError) {
            setSenderTestResults(previous => ({
                ...previous,
                [index]: {
                    success: false,
                    message: formatApiErrorMessage(requestError, '测试邮件发送失败，请检查服务器配置。'),
                },
            }));
        } finally {
            setSavingKey(null);
        }
    };

    const deleteSender = async () => {
        if (!settings.mailSender.localOverride || !window.confirm('确认撤销当前作用域的邮件发件方配置？')) return;
        setSavingKey('mail-sender-delete');
        setError(null);
        try {
            const response = await apiClient.delete(
                '/admin/notifications/settings/sender',
                configRequest(orgUnitId),
            );
            setSettings(previous => ({ ...previous, mailSender: response.data }));
            setSenderDrafts(senderDraftsFromSettings(response.data));
            setSenderStrategy(response.data.strategy);
            setSenderTestResults({});
            onSaved?.(response.data.configured
                ? `已撤销本级配置，当前使用${notificationSettingSourceLabel(response.data, tenantName)}。`
                : '已撤销本级发件池配置。');
        } catch (requestError) {
            setError(formatApiErrorMessage(requestError, '撤销邮件发件池失败，请稍后重试。'));
        } finally {
            setSavingKey(null);
        }
    };

    const updateDailySchedule = async () => {
        if (!/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(dailySendTime)) {
            setError('发送时间必须使用HH:mm格式。');
            return;
        }
        setSavingKey('daily-outstanding-report-schedule');
        setError(null);
        try {
            const response = await apiClient.put(
                '/admin/notifications/settings/daily-outstanding-report/schedule',
                { sendTime: dailySendTime },
                configRequest(orgUnitId),
            );
            setSettings((previous) => ({ ...previous, dailyOutstandingReport: response.data }));
            setDailySendTime(response.data.sendTime);
            onSaved?.(`日报发送时间已更新为${response.data.sendTime}。`);
        } catch (requestError) {
            setError(formatApiErrorMessage(requestError, '更新日报发送时间失败，请稍后重试。'));
        } finally {
            setSavingKey(null);
        }
    };

    return (
        <Box component="section" aria-labelledby="notification-center-title" sx={{ mb: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 1, mb: 0.5 }}>
                <Typography id="notification-center-title" variant="subtitle1" sx={{ fontWeight: 800 }}>
                    {view === 'sender' ? '邮件发件池' : '通知与汇总'}
                </Typography>
                <Typography variant="caption" color="text.secondary">{scopeLabel}</Typography>
            </Box>
            {error && <Alert severity="error" sx={{ mb: 1 }}>{error}</Alert>}
            {loading || !settings ? (
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, py: 2 }}>
                    <CircularProgress size={20} />
                    <Typography variant="body2" color="text.secondary">正在加载通知渠道...</Typography>
                </Box>
            ) : (
                <>
                    <Box sx={{ display: view === 'channels' ? 'block' : 'none' }}>
                    <NotificationSwitch
                        setting={settings.center}
                        tenantName={tenantName}
                        disabled={disabled || !canUpdateChannels || savingKey !== null}
                        saving={savingKey === settings.center.name}
                        onChange={(enabled) => updateSwitch(settings.center, enabled)}
                    />
                    <Divider />
                    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'repeat(3, minmax(0, 1fr))' }, columnGap: 2 }}>
                        {settings.channels.map((setting) => (
                            <NotificationSwitch
                                key={setting.name}
                                setting={setting}
                                tenantName={tenantName}
                                disabled={disabled || !canUpdateChannels || savingKey !== null}
                                saving={savingKey === setting.name}
                                onChange={(enabled) => updateSwitch(setting, enabled)}
                            />
                        ))}
                    </Box>
                    <Divider />
                    <Box sx={{ py: 1.25 }}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>定时汇总</Typography>
                        <NotificationSwitch
                            setting={settings.dailyOutstandingReport.switchSetting}
                            tenantName={tenantName}
                            disabled={disabled || !canUpdateSchedule || savingKey !== null}
                            saving={savingKey === settings.dailyOutstandingReport.switchSetting.name}
                            onChange={(enabled) => updateSwitch(settings.dailyOutstandingReport.switchSetting, enabled)}
                        />
                        <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1.25, pl: { xs: 0, sm: 6 } }}>
                            <TextField
                                label="发送时间"
                                type="time"
                                size="small"
                                value={dailySendTime}
                                onChange={(event) => setDailySendTime(event.target.value)}
                                disabled={disabled || !canUpdateSchedule || savingKey !== null}
                                slotProps={{ htmlInput: { step: 60 } }}
                                sx={{ width: 150 }}
                            />
                            <Button
                                variant="outlined"
                                startIcon={savingKey === 'daily-outstanding-report-schedule'
                                    ? <CircularProgress size={16} color="inherit" />
                                    : <SaveIcon />}
                                onClick={updateDailySchedule}
                                disabled={disabled
                                    || !canUpdateSchedule
                                    || savingKey !== null
                                    || dailySendTime === settings.dailyOutstandingReport.sendTime}
                            >
                                保存时间
                            </Button>
                            <Chip
                                size="small"
                                label={notificationSettingSourceLabel(settings.dailyOutstandingReport, tenantName)}
                                color={settings.dailyOutstandingReport.inherited
                                    || settings.dailyOutstandingReport.sourceScope === 'UNCONFIGURED' ? 'default' : 'primary'}
                                variant={settings.dailyOutstandingReport.inherited
                                    || settings.dailyOutstandingReport.sourceScope === 'UNCONFIGURED' ? 'outlined' : 'filled'}
                            />
                            <Typography variant="caption" color="text.secondary">Asia/Shanghai</Typography>
                        </Box>
                    </Box>
                    </Box>
        <Box sx={{ display: view === 'sender' ? 'block' : 'none', py: 1.5 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1, mb: 0.75 }}>
                <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>生效配置</Typography>
                <Chip
                    size="small"
                    label={notificationSettingSourceLabel(settings.mailSender, tenantName)}
                    color={settings.mailSender.configured ? 'primary' : 'default'}
                    variant={settings.mailSender.configured ? 'filled' : 'outlined'}
                />
            </Box>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.25 }}>
                当前生效 {settings.mailSender.senders.length} 个发件方。工单通知和未处理工单日报使用同一个有效发件池，按账号轮询发送。
            </Typography>
            {!settings.mailSender.configured && (
                <Alert severity="warning" sx={{ mb: 1.5 }}>
                    {settings.mailSender.configurationError || '当前作用域没有可用的邮件发件方。'}
                </Alert>
            )}
            <FormControl fullWidth size="small" sx={{ mb: 1.5 }} disabled={disabled || !canUpdateSender || savingKey !== null}>
                <InputLabel id="mail-sender-strategy-label">发件池使用策略</InputLabel>
                <Select labelId="mail-sender-strategy-label" label="发件池使用策略" value={senderStrategy}
                    onChange={event => setSenderStrategy(event.target.value)}>
                    <MenuItem value="INHERIT">仅继承上级发件池</MenuItem>
                    <MenuItem value="REPLACE">仅使用本级发件池</MenuItem>
                    <MenuItem value="MERGE">本级与上级共同使用</MenuItem>
                </Select>
            </FormControl>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                {senderStrategy === 'INHERIT' ? '使用上级有效发件池；本级账号保留，不参与发送。'
                    : senderStrategy === 'MERGE' ? '本级与上级共同轮询；相同账号优先使用本级配置。'
                        : '仅使用下方本级账号；上级账号不参与发送。'}
                {' '}保存后生效，继承账号只读。
            </Typography>
            <Accordion disableGutters sx={{ mb: 1.5 }}>
                <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                    <Typography variant="body2">继承的发件方（{settings.mailSender.inheritedSenders?.length || 0}）</Typography>
                </AccordionSummary>
                <AccordionDetails>
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                        {!!settings.mailSender.inheritedSenders?.length && canUpdateSender && (
                            <TextField label="继承发件方的测试收件地址" type="email" size="small"
                                value={inheritedTestRecipient} onChange={event => {
                                    setInheritedTestRecipient(event.target.value);
                                    setSenderTestResults({});
                                }}
                                disabled={disabled || savingKey !== null} placeholder="仅用于本次测试，不会保存" />
                        )}
                        {(settings.mailSender.inheritedSenders || []).map(sender => (
                            <Box key={sender.id} sx={{ minWidth: 0, overflowWrap: 'anywhere' }}>
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>{sender.fromAddress}</Typography>
                                <Typography variant="caption" color="text.secondary">
                                    {notificationSettingSourceLabel(sender, tenantName)} · {sender.host}:{sender.port}
                                </Typography>
                                <Accordion disableGutters elevation={0}>
                                    <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                                        <Typography variant="caption">查看连接配置</Typography>
                                    </AccordionSummary>
                                    <AccordionDetails>
                                        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'repeat(2, minmax(0, 1fr))' }, gap: 1 }}>
                                            {[
                                                ['服务商', sender.providerId], ['用户名', sender.username],
                                                ['认证方式', sender.authenticationMode], ['认证机制', sender.authMechanism],
                                                ['SMTP 认证', sender.smtpAuth ? '开启' : '关闭'],
                                                ['SSL 直连', sender.sslEnabled ? '开启' : '关闭'],
                                                ['STARTTLS', sender.startTlsEnabled ? (sender.startTlsRequired ? '强制' : '启用') : '关闭'],
                                                ['TLS 协议', sender.sslProtocols], ['EHLO 域名', sender.ehloDomain],
                                                ['认证域', sender.authDomain], ['SASL Realm', sender.saslRealm],
                                                ['单封发送间隔（秒）', String(sender.sendIntervalSeconds ?? 10)],
                                                ['连接超时（毫秒）', sender.connectionTimeoutMs],
                                                ['读取超时（毫秒）', sender.readTimeoutMs], ['写入超时（毫秒）', sender.writeTimeoutMs],
                                                ['OAuth Client ID', sender.oauthClientId], ['OAuth 租户', sender.oauthTenantId],
                                                ['OAuth Scope', sender.oauthScope],
                                                ['密码或授权码', sender.passwordConfigured ? '已配置' : '未配置'],
                                                ['OAuth Client Secret', sender.oauthClientSecretConfigured ? '已配置' : '未配置'],
                                                ['OAuth Refresh Token', sender.oauthRefreshTokenConfigured ? '已配置' : '未配置'],
                                            ].map(([label, value]) => (
                                                <Typography key={label} variant="caption" sx={{ minWidth: 0, overflowWrap: 'anywhere' }}>
                                                    {label}：{value || '未配置'}
                                                </Typography>
                                            ))}
                                        </Box>
                                    </AccordionDetails>
                                </Accordion>
                                {canUpdateSender && (
                                    <Button size="small" startIcon={<SendIcon />}
                                        onClick={() => testSender(`inherited-${sender.id}`, {
                                            ...senderDraftFromAccount(sender), testRecipient: inheritedTestRecipient,
                                        })}
                                        disabled={disabled || savingKey !== null}>
                                        测试发送
                                    </Button>
                                )}
                                {senderTestResults[`inherited-${sender.id}`] && (
                                    <Alert severity={senderTestResults[`inherited-${sender.id}`].success ? 'success' : 'error'}>
                                        {senderTestResults[`inherited-${sender.id}`].message}
                                    </Alert>
                                )}
                            </Box>
                        ))}
                        {settings.mailSender.inheritedConfigurationError && (
                            <Alert severity={senderStrategy === 'INHERIT' ? 'warning' : 'info'}>
                                {settings.mailSender.inheritedConfigurationError}
                                {senderStrategy === 'MERGE' && ' 本级账号完整时仍可独立发送。'}
                            </Alert>
                        )}
                    </Box>
                </AccordionDetails>
            </Accordion>
            <Typography variant="body2" sx={{ fontWeight: 800, mb: 1 }}>本级发件方（{senderDrafts.length}）</Typography>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.25 }}>
                {senderDrafts.map((sender, index) => {
                    const usesOAuth = sender.authenticationMode === 'OAUTH2';
                    const senderTestResult = senderTestResults[index];
                    const smtpSettingsDisabled = disabled || !canUpdateSender || savingKey !== null;
                    return (
                        <Accordion key={sender.id || `new-sender-${index}`} defaultExpanded={!sender.id}
                                   disableGutters variant="outlined" sx={{ minWidth: 0 }}>
                            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                                <Box sx={{ minWidth: 0, overflowWrap: 'anywhere' }}>
                                    <Typography variant="body2" sx={{ fontWeight: 800 }}>
                                        发件方 {index + 1}{sender.fromAddress ? ` · ${sender.fromAddress}` : ' · 待配置'}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary">
                                        {sender.host || '未设置服务器'}{sender.port ? `:${sender.port}` : ''}
                                    </Typography>
                                </Box>
                            </AccordionSummary>
                            <AccordionDetails>
                            <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 1 }}>
                                {canUpdateSender && (
                                    <Button
                                        color="error"
                                        size="small"
                                        startIcon={<DeleteOutlinedIcon />}
                                        onClick={() => {
                                            setSenderDrafts(previous => previous.filter((_, senderIndex) => senderIndex !== index));
                                            setSenderTestResults({});
                                        }}
                                        disabled={disabled || savingKey !== null}
                                    >
                                        移除
                                    </Button>
                                )}
                            </Box>
                            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(12, minmax(0, 1fr))' }, gap: 1.25 }}>
                                <FormControl size="small" sx={{ gridColumn: { md: 'span 4' } }}>
                                    <InputLabel id={`sender-provider-${index}`}>服务商预设</InputLabel>
                                    <Select
                                        labelId={`sender-provider-${index}`}
                                        label="服务商预设"
                                        value={sender.providerId || 'CUSTOM'}
                                        onChange={event => updateProviderPreset(index, event.target.value)}
                                        disabled={smtpSettingsDisabled}
                                    >
                                        {Object.entries(MAIL_SENDER_PROVIDER_PRESETS).map(([providerId, preset]) => (
                                            <MenuItem key={providerId} value={providerId}>{preset.label}</MenuItem>
                                        ))}
                                    </Select>
                                </FormControl>
                                <TextField
                                    label="邮件服务器"
                                    size="small"
                                    value={sender.host}
                                    onChange={event => updateSenderDraft(index, 'host', event.target.value)}
                                    disabled={smtpSettingsDisabled}
                                    required
                                    sx={{ gridColumn: { md: 'span 6' } }}
                                />
                                <TextField
                                    label="端口"
                                    type="number"
                                    size="small"
                                    value={sender.port}
                                    onChange={event => updateSenderDraft(index, 'port', event.target.value)}
                                    disabled={smtpSettingsDisabled}
                                    slotProps={{ htmlInput: { min: 1, max: 65535, step: 1 } }}
                                    required
                                    sx={{ gridColumn: { md: 'span 2' } }}
                                />
                                <Typography
 variant="caption"
 color="text.secondary"
 sx={{ gridColumn: { md: 'span 12' } }}
 >
                                    {MAIL_SENDER_PROVIDER_HELP[sender.providerId] || MAIL_SENDER_PROVIDER_HELP.CUSTOM}
                                    {' '}预设值可继续修改。
                                </Typography>
                                <TextField
                                    label="发件地址"
                                    type="email"
                                    size="small"
                                    value={sender.fromAddress}
                                    onChange={event => updateSenderDraft(index, 'fromAddress', event.target.value)}
                                    disabled={smtpSettingsDisabled}
                                    autoComplete="off"
                                    required
                                    sx={{ gridColumn: { md: 'span 6' } }}
                                />
                                <TextField
                                    label="SMTP用户名"
                                    size="small"
                                    value={sender.username}
                                    onChange={event => updateSenderDraft(index, 'username', event.target.value)}
                                    disabled={smtpSettingsDisabled || !sender.smtpAuth}
                                    autoComplete="off"
                                    required={sender.smtpAuth}
                                    sx={{ gridColumn: { md: 'span 6' } }}
                                />
                                <FormControl size="small" sx={{ gridColumn: { md: 'span 4' } }}>
                                    <InputLabel id={`sender-auth-mode-${index}`}>认证方式</InputLabel>
                                    <Select
                                        labelId={`sender-auth-mode-${index}`}
                                        label="认证方式"
                                        value={sender.authenticationMode || 'PASSWORD'}
                                        onChange={event => updateSenderDraft(index, 'authenticationMode', event.target.value)}
                                        disabled={smtpSettingsDisabled || !sender.smtpAuth}
                                    >
                                        <MenuItem value="PASSWORD">密码或授权码</MenuItem>
                                        <MenuItem value="OAUTH2" disabled={!['GMAIL', 'MICROSOFT_365'].includes(sender.providerId)}>
                                            OAuth2（Gmail / Microsoft 365）
                                        </MenuItem>
                                    </Select>
                                </FormControl>
                                {(!usesOAuth || !sender.smtpAuth) && (
                                    <TextField
                                        label="密码或授权码"
                                        type="password"
                                        size="small"
                                        value={sender.password}
                                        onChange={event => updateSenderDraft(index, 'password', event.target.value)}
                                        disabled={smtpSettingsDisabled || !sender.smtpAuth}
                                        autoComplete="new-password"
                                        placeholder={sender.passwordConfigured ? '留空则保留当前凭据' : '请输入密码或邮箱授权码'}
                                        required={sender.smtpAuth && !sender.passwordConfigured}
                                        sx={{ gridColumn: { md: 'span 8' } }}
                                    />
                                )}
                                {sender.smtpAuth && usesOAuth && (
                                    <>
                                        <TextField
                                            label="OAuth Client ID"
                                            size="small"
                                            value={sender.oauthClientId || ''}
                                            onChange={event => updateSenderDraft(index, 'oauthClientId', event.target.value)}
                                            disabled={smtpSettingsDisabled}
                                            required
                                            sx={{ gridColumn: { md: 'span 4' } }}
                                        />
                                        <TextField
                                            label="OAuth Client Secret"
                                            type="password"
                                            size="small"
                                            value={sender.oauthClientSecret}
                                            onChange={event => updateSenderDraft(index, 'oauthClientSecret', event.target.value)}
                                            disabled={smtpSettingsDisabled}
                                            autoComplete="new-password"
                                            placeholder={sender.oauthClientSecretConfigured ? '留空则保留当前密钥' : '请输入 Client Secret'}
                                            required={!sender.oauthClientSecretConfigured}
                                            sx={{ gridColumn: { md: 'span 4' } }}
                                        />
                                        <TextField
                                            label="OAuth Refresh Token"
                                            type="password"
                                            size="small"
                                            value={sender.oauthRefreshToken}
                                            onChange={event => updateSenderDraft(index, 'oauthRefreshToken', event.target.value)}
                                            disabled={smtpSettingsDisabled}
                                            autoComplete="new-password"
                                            placeholder={sender.oauthRefreshTokenConfigured ? '留空则保留当前令牌' : '请输入 Refresh Token'}
                                            required={!sender.oauthRefreshTokenConfigured}
                                            sx={{ gridColumn: { md: 'span 4' } }}
                                        />
                                        {sender.providerId === 'MICROSOFT_365' && (
                                            <TextField
                                                label="Microsoft租户 ID / 域名"
                                                size="small"
                                                value={sender.oauthTenantId || 'common'}
                                                onChange={event => updateSenderDraft(index, 'oauthTenantId', event.target.value)}
                                                disabled={smtpSettingsDisabled}
                                                helperText="使用 common、组织租户 ID 或验证过的租户域名"
                                                sx={{ gridColumn: { md: 'span 6' } }}
                                            />
                                        )}
                                        <TextField
                                            label="OAuth Scope（可选）"
                                            size="small"
                                            value={sender.oauthScope || ''}
                                            onChange={event => updateSenderDraft(index, 'oauthScope', event.target.value)}
                                            disabled={smtpSettingsDisabled}
                                            helperText="留空使用服务商默认 SMTP Scope"
                                            sx={{ gridColumn: { md: 'span 6' } }}
                                        />
                                    </>
                                )}
                                <Box sx={{ gridColumn: { md: 'span 12' }, display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: { xs: 0.5, sm: 2 } }}>
                                    <TextField
                                        label="单封发送间隔（秒）"
                                        type="number"
                                        size="small"
                                        value={sender.sendIntervalSeconds}
                                        onChange={event => updateSenderDraft(index, 'sendIntervalSeconds', event.target.value)}
                                        disabled={smtpSettingsDisabled}
                                        error={Boolean(mailSenderIntervalError(sender.sendIntervalSeconds))}
                                        helperText={mailSenderIntervalError(sender.sendIntervalSeconds) || '同账号共享冷却；0秒仍保持串行发送'}
                                        slotProps={{ htmlInput: { min: 0, max: 3600, step: 1 } }}
                                        sx={{ width: { xs: '100%', sm: 280 }, my: 0.5 }}
                                    />
                                    <FormControlLabel
                                        control={<Switch checked={sender.smtpAuth} onChange={event => updateSmtpAuthentication(index, event.target.checked)} />}
                                        label="SMTP认证"
                                        disabled={smtpSettingsDisabled}
                                    />
                                    <FormControlLabel
                                        control={<Switch checked={sender.sslEnabled} onChange={event => updateTlsMode(index, 'ssl', event.target.checked)} />}
                                        label="SSL直连"
                                        disabled={smtpSettingsDisabled}
                                    />
                                    <FormControlLabel
                                        control={<Switch checked={sender.startTlsEnabled} onChange={event => updateTlsMode(index, 'startTls', event.target.checked)} />}
                                        label="STARTTLS"
                                        disabled={smtpSettingsDisabled}
                                    />
                                    <FormControlLabel
                                        control={<Switch checked={sender.startTlsRequired} onChange={event => updateSenderDraft(index, 'startTlsRequired', event.target.checked)} />}
                                        label="强制STARTTLS"
                                        disabled={smtpSettingsDisabled || !sender.startTlsEnabled}
                                    />
                                </Box>
                                <Accordion disableGutters sx={{ gridColumn: { md: 'span 12' }, boxShadow: 'none', border: 1, borderColor: 'divider' }}>
                                    <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                                        <Typography variant="body2">高级 SMTP 选项</Typography>
                                    </AccordionSummary>
                                    <AccordionDetails>
                                        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(12, minmax(0, 1fr))' }, gap: 1.25 }}>
                                            <FormControl size="small" sx={{ gridColumn: { md: 'span 4' } }}>
                                                <InputLabel id={`sender-auth-mechanism-${index}`}>认证机制</InputLabel>
                                                <Select
                                                    labelId={`sender-auth-mechanism-${index}`}
                                                    label="认证机制"
                                                    value={usesOAuth ? 'XOAUTH2' : (sender.authMechanism || 'AUTO')}
                                                    onChange={event => updateAuthenticationMechanism(index, event.target.value)}
                                                    disabled={smtpSettingsDisabled || !sender.smtpAuth || usesOAuth}
                                                >
                                                    {authenticationMechanisms.map(([mechanism, label]) => (
                                                        <MenuItem key={mechanism} value={mechanism}>{label}</MenuItem>
                                                    ))}
                                                    {usesOAuth && <MenuItem value="XOAUTH2">XOAUTH2</MenuItem>}
                                                </Select>
                                            </FormControl>
                                            <TextField
                                                label="EHLO域名"
                                                size="small"
                                                value={sender.ehloDomain || ''}
                                                onChange={event => updateSenderDraft(index, 'ehloDomain', event.target.value)}
                                                disabled={smtpSettingsDisabled}
                                                sx={{ gridColumn: { md: 'span 4' } }}
                                            />
                                            <TextField
                                                label="NTLM认证域"
                                                size="small"
                                                value={sender.authDomain || ''}
                                                onChange={event => updateSenderDraft(index, 'authDomain', event.target.value)}
                                                disabled={smtpSettingsDisabled || sender.authMechanism !== 'NTLM'}
                                                sx={{ gridColumn: { md: 'span 4' } }}
                                            />
                                            <TextField
                                                label="SASL Realm"
                                                size="small"
                                                value={sender.saslRealm || ''}
                                                onChange={event => updateSenderDraft(index, 'saslRealm', event.target.value)}
                                                disabled={smtpSettingsDisabled || sender.authMechanism !== 'DIGEST_MD5'}
                                                helperText="仅 DIGEST-MD5 认证使用"
                                                sx={{ gridColumn: { md: 'span 4' } }}
                                            />
                                            <TextField
                                                label="TLS协议"
                                                size="small"
                                                value={sender.sslProtocols || 'TLSv1.2 TLSv1.3'}
                                                onChange={event => updateSenderDraft(index, 'sslProtocols', event.target.value)}
                                                disabled={smtpSettingsDisabled}
                                                helperText="支持 TLSv1.2、TLSv1.3，可用空格或逗号分隔"
                                                sx={{ gridColumn: { md: 'span 8' } }}
                                            />
                                            {[
                                                ['connectionTimeoutMs', '连接超时（毫秒）'],
                                                ['readTimeoutMs', '读取超时（毫秒）'],
                                                ['writeTimeoutMs', '写入超时（毫秒）'],
                                            ].map(([field, label]) => (
                                                <TextField
                                                    key={field}
                                                    label={label}
                                                    type="number"
                                                    size="small"
                                                    value={sender[field]}
                                                    onChange={event => updateSenderDraft(index, field, event.target.value)}
                                                    disabled={smtpSettingsDisabled}
                                                    slotProps={{ htmlInput: { min: 1000, max: 120000, step: 1000 } }}
                                                    sx={{ gridColumn: { md: 'span 4' } }}
                                                />
                                            ))}
                                        </Box>
                                    </AccordionDetails>
                                </Accordion>
                                <TextField
                                    label="测试收件地址"
                                    type="email"
                                    size="small"
                                    value={sender.testRecipient || ''}
                                    onChange={event => updateSenderDraft(index, 'testRecipient', event.target.value)}
                                    disabled={disabled || !canUpdateSender || savingKey !== null}
                                    placeholder="仅用于本次测试，不会保存"
                                    sx={{ gridColumn: { md: 'span 8' } }}
                                />
                                <Button
                                    variant="outlined"
                                    startIcon={savingKey === `mail-sender-test-${index}`
                                        ? <CircularProgress size={16} color="inherit" />
                                        : <SendIcon />}
                                    onClick={() => testSender(index)}
                                    disabled={disabled || !canUpdateSender || savingKey !== null}
                                    sx={{ gridColumn: { md: 'span 4' } }}
                                >
                                    测试发送
                                </Button>
                                {senderTestResult && (
                                    <Alert severity={senderTestResult.success ? 'success' : 'error'} sx={{ gridColumn: { md: 'span 12' } }}>
                                        {senderTestResult.message}
                                    </Alert>
                                )}
                            </Box>
                            </AccordionDetails>
                        </Accordion>
                    );
                })}
            </Box>
            {canUpdateSender && (
                <>
                <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1, mt: 1.25 }}>
                    <Button
                        variant="outlined"
                        startIcon={<AddIcon />}
                        onClick={() => setSenderDrafts(previous => [...previous, createEmptyMailSenderDraft()])}
                        disabled={disabled || savingKey !== null}
                    >
                        新增发件方
                    </Button>
                    <Typography variant="caption" color="text.secondary">可按需要添加多个发件方；不同发件方可使用不同服务商和认证方式。</Typography>
                </Box>
                    <Box sx={{ position: 'sticky', bottom: 16, zIndex: 2, mt: 2, display: 'flex', justifyContent: 'flex-end', flexWrap: 'wrap', gap: 1, bgcolor: 'background.paper', border: 1, borderColor: 'divider', borderRadius: '12px', p: 1.5, boxShadow: '0 4px 20px rgba(24,34,54,0.06)' }}>
                        {settings.mailSender.localOverride && (
                            <Button
                                color="error"
                                variant="text"
                                startIcon={savingKey === 'mail-sender-delete' ? <CircularProgress size={16} color="inherit" /> : <DeleteOutlinedIcon />}
                                onClick={deleteSender}
                                disabled={disabled || savingKey !== null}
                            >
                                撤销本级配置
                            </Button>
                        )}
                        <Button
                            variant="contained"
                            startIcon={savingKey === 'mail-sender' ? <CircularProgress size={16} color="inherit" /> : <SaveIcon />}
                            onClick={updateSender}
                            disabled={disabled || savingKey !== null || (senderStrategy !== 'INHERIT' && senderDrafts.length === 0)}
                        >
                            保存发件池
                        </Button>
                    </Box>
                </>
            )}
        </Box>
                    {!canUpdateChannels && !canUpdateSchedule && !canUpdateSender && (
                        <Typography variant="caption" color="text.secondary">
                            当前账号只有查看权限。
                        </Typography>
                    )}
                </>
            )}
        </Box>
    );
}
