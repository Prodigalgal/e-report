import PageTitle from '../../components/layout/PageTitle';
import { PageTabs, PageTabPanel } from '../../components/layout/PageTabs';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
    Alert,
    Box,
    Button,
    Chip,
    CircularProgress,
    Container,
    Divider,
    Pagination,
    Paper,
    Stack,
    TextField,
    Typography,
} from '@mui/material';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import LockResetRoundedIcon from '@mui/icons-material/LockResetRounded';
import LogoutRoundedIcon from '@mui/icons-material/LogoutRounded';
import SaveIcon from '@mui/icons-material/Save';

import PasswordPolicyHint from '../../components/Auth/PasswordPolicyHint.jsx';
import ApiTokenPanel from '../../components/Profile/ApiTokenPanel.jsx';
import EmailNotificationPreferencesPanel from '../../components/Profile/EmailNotificationPreferencesPanel.jsx';
import { PERMISSIONS } from '../../constants/permissions.js';
import { useAuth } from '../../hooks/useAuth';
import {
    changeCurrentPasswordAPI,
    fetchAuthSessionsAPI,
    revokeAuthSessionAPI,
    revokeOtherAuthSessionsAPI,
} from '../../services/authApiService.js';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage.js';
import { formatDateTime } from '../../utils/formatDate.js';
import { evaluatePassword } from '../../utils/passwordPolicy.js';
import { validateUserProfile } from '../../utils/userProfileValidation.js';
import { getPaginationItemAriaLabel } from '../../utils/paginationAriaLabel';

export default function UserProfilePage() {
    const { currentUser, updateProfile, logout, hasPermission, passwordPolicy } = useAuth();
    const [form, setForm] = useState({ name: '', phone: '', email: '' });
    const [touched, setTouched] = useState({});
    const [saving, setSaving] = useState(false);
    const [profileMessage, setProfileMessage] = useState(null);
    const [passwords, setPasswords] = useState({ current: '', next: '', confirm: '' });
    const [changingPassword, setChangingPassword] = useState(false);
    const [securityMessage, setSecurityMessage] = useState(null);
    const [sessionMessage, setSessionMessage] = useState(null);
    const [activeSection, setActiveSection] = useState(null);
    const selectedSection = activeSection ?? (currentUser?.passwordChangeRequired ? 'security' : 'profile');
    const [sessionsPage, setSessionsPage] = useState({
        content: [],
        totalElements: 0,
        totalPages: 0,
        number: 0,
        size: 10,
    });
    const [sessionsLoading, setSessionsLoading] = useState(true);
    const [sessionPageNumber, setSessionPageNumber] = useState(0);
    const [revokingSessionId, setRevokingSessionId] = useState('');
    const [revokingOtherSessions, setRevokingOtherSessions] = useState(false);

    useEffect(() => {
        setForm({
            name: currentUser?.name || '',
            phone: currentUser?.phone || '',
            email: currentUser?.email || '',
        });
        setTouched({});
    }, [currentUser?.email, currentUser?.name, currentUser?.phone]);

    const loadSessions = useCallback(async (pageNumber = 0) => {
        setSessionsLoading(true);
        try {
            const response = await fetchAuthSessionsAPI(pageNumber, 10);
            const page = response.data?.page;
            setSessionsPage({
                ...(page || { content: [], totalElements: 0, totalPages: 0, number: pageNumber, size: 10 }),
                activeOtherSessionCount: Number(response.data?.activeOtherSessionCount || 0),
            });
            setSessionPageNumber(page?.number ?? pageNumber);
        } catch (error) {
            setSessionMessage({ severity: 'error', text: formatApiErrorMessage(error, '登录设备加载失败，请稍后重试。') });
        } finally {
            setSessionsLoading(false);
        }
    }, []);

    useEffect(() => {
        loadSessions(0).catch(() => {});
    }, [loadSessions]);

    const validation = useMemo(() => validateUserProfile(form), [form]);
    const hasChanges = validation.normalized.name !== (currentUser?.name || '')
        || validation.normalized.phone !== (currentUser?.phone || '')
        || validation.normalized.email !== (currentUser?.email || '');
    const passwordContext = useMemo(() => ({
        username: currentUser?.username,
        name: currentUser?.name,
        phone: currentUser?.phone,
        email: currentUser?.email,
    }), [currentUser]);
    const passwordEvaluation = useMemo(
        () => evaluatePassword(passwords.next, passwordContext, passwordPolicy),
        [passwordContext, passwordPolicy, passwords.next],
    );
    const passwordMatches = passwords.next.length > 0 && passwords.next === passwords.confirm;
    const canViewApiTokens = hasPermission(PERMISSIONS.API_TOKEN_VIEW);
    const canCreateApiTokens = hasPermission(PERMISSIONS.API_TOKEN_CREATE);
    const canRevokeApiTokens = hasPermission(PERMISSIONS.API_TOKEN_REVOKE);
    const canManageApiTokens = canViewApiTokens || canCreateApiTokens || canRevokeApiTokens;

    const change = field => event => {
        setProfileMessage(null);
        setForm(previous => ({ ...previous, [field]: event.target.value }));
    };

    const submitProfile = async event => {
        event.preventDefault();
        setTouched({ name: true, phone: true, email: true });
        if (!validation.valid || !hasChanges || saving) return;
        setSaving(true);
        setProfileMessage(null);
        try {
            await updateProfile({ ...validation.normalized, email: validation.normalized.email || null });
            setProfileMessage({ severity: 'success', text: '个人信息已保存。' });
        } catch (error) {
            setProfileMessage({ severity: 'error', text: formatApiErrorMessage(error, '个人信息保存失败，请稍后重试。') });
        } finally {
            setSaving(false);
        }
    };

    const submitPassword = async event => {
        event.preventDefault();
        if (!passwords.current || !passwordEvaluation.valid || !passwordMatches || changingPassword) return;
        setChangingPassword(true);
        setSecurityMessage(null);
        try {
            await changeCurrentPasswordAPI(passwords.current, passwords.next);
            setPasswords({ current: '', next: '', confirm: '' });
            await logout();
        } catch (error) {
            setSecurityMessage({ severity: 'error', text: formatApiErrorMessage(error, '密码修改失败，请检查后重试。') });
        } finally {
            setChangingPassword(false);
        }
    };

    const revokeSession = async sessionId => {
        setRevokingSessionId(sessionId);
        setSessionMessage(null);
        try {
            await revokeAuthSessionAPI(sessionId);
            await loadSessions(sessionPageNumber);
            setSessionMessage({ severity: 'success', text: '登录会话已撤销。' });
        } catch (error) {
            setSessionMessage({ severity: 'error', text: formatApiErrorMessage(error, '会话撤销失败，请稍后重试。') });
        } finally {
            setRevokingSessionId('');
        }
    };

    const revokeOtherSessions = async () => {
        setRevokingOtherSessions(true);
        setSessionMessage(null);
        try {
            await revokeOtherAuthSessionsAPI();
            await loadSessions(sessionPageNumber);
            setSessionMessage({ severity: 'success', text: '其他登录会话已全部撤销。' });
        } catch (error) {
            setSessionMessage({ severity: 'error', text: formatApiErrorMessage(error, '其他会话撤销失败，请稍后重试。') });
        } finally {
            setRevokingOtherSessions(false);
        }
    };

    return (
        <Container maxWidth="md" disableGutters sx={{ display: 'grid', gap: 2 }}>
            {currentUser?.passwordChangeRequired && (
                <Alert severity="warning">首次登录或密码已由管理员重置，请先设置新的个人密码。</Alert>
            )}

            <Box>
                <PageTitle>个人信息</PageTitle>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>管理联系方式、通知偏好与账号安全</Typography>
            </Box>
            <PageTabs id="profile" label="个人设置分类" value={selectedSection} onChange={setActiveSection}
                options={[
                    { value: 'profile', label: '基本资料' },
                    ...(!currentUser?.passwordChangeRequired ? [{ value: 'notifications', label: '邮件通知' }] : []),
                    { value: 'security', label: '登录密码' },
                    ...(!currentUser?.passwordChangeRequired && canManageApiTokens ? [{ value: 'tokens', label: 'API Token' }] : []),
                    { value: 'sessions', label: '登录设备' },
                ]} />
            <PageTabPanel id="profile" value="profile" selected={selectedSection}>
                <Paper component="form" onSubmit={submitProfile} elevation={1} sx={{ p: { xs: 2, sm: 3 } }}>
                    <Typography variant="h6" component="h2" sx={{ fontWeight: 800 }}>基本资料</Typography>
                    <Stack direction="row" useFlexGap spacing={1} sx={{ flexWrap: 'wrap', mt: 1.5, mb: 2 }}>
                        <Chip label={`用户名：${currentUser?.username || ''}`} variant="outlined" />
                        <Chip label={currentUser?.tenantName || '未识别市公司'} variant="outlined" />
                        <Chip label={`${currentUser?.permissions?.length || 0} 项有效权限`} variant="outlined" />
                    </Stack>
                    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'repeat(2, minmax(0, 1fr))' }, gap: 2 }}>
                        {profileMessage && <Alert severity={profileMessage.severity} sx={{ gridColumn: '1 / -1' }}>{profileMessage.text}</Alert>}
                        <TextField label="姓名" value={form.name} onChange={change('name')}
                                   onBlur={() => setTouched(previous => ({ ...previous, name: true }))}
                                   error={!!touched.name && !!validation.errors.name}
                                   helperText={touched.name ? validation.errors.name : ''}
                                   slotProps={{ htmlInput: { maxLength: 100 } }} required fullWidth />
                        <TextField label="手机号" value={form.phone} onChange={change('phone')}
                                   onBlur={() => setTouched(previous => ({ ...previous, phone: true }))}
                                   error={!!touched.phone && !!validation.errors.phone}
                                   helperText={touched.phone ? validation.errors.phone : ''}
                                   slotProps={{ htmlInput: { inputMode: 'numeric', maxLength: 11 } }} required fullWidth />
                        <TextField label="邮箱" type="email" value={form.email} onChange={change('email')}
                                   onBlur={() => setTouched(previous => ({ ...previous, email: true }))}
                                   error={!!touched.email && !!validation.errors.email}
                                   helperText={touched.email ? validation.errors.email : '可选；邮件通知优先使用此地址'}
                                   slotProps={{ htmlInput: { maxLength: 254 } }} fullWidth sx={{ gridColumn: '1 / -1' }} />
                        <Button type="submit" variant="contained"
                                startIcon={saving ? <CircularProgress size={18} color="inherit" /> : <SaveIcon />}
                                disabled={!validation.valid || !hasChanges || saving} sx={{ justifySelf: 'start' }}>
                            {saving ? '保存中' : '保存个人信息'}
                        </Button>
                    </Box>
                </Paper>

            </PageTabPanel>
            <PageTabPanel id="profile" value="notifications" selected={selectedSection}>
                {!currentUser?.passwordChangeRequired && (
                    <EmailNotificationPreferencesPanel key={currentUser?.membershipId || currentUser?.username} />
                )}
            </PageTabPanel>
            <PageTabPanel id="profile" value="security" selected={selectedSection}>
                <Paper component="form" onSubmit={submitPassword} elevation={1} sx={{ p: { xs: 2, sm: 3 } }}>
                    <Typography variant="h6" component="h2" sx={{ fontWeight: 800 }}>登录密码</Typography>
                    <Stack spacing={2} sx={{ mt: 2 }}>
                        {securityMessage && <Alert severity={securityMessage.severity}>{securityMessage.text}</Alert>}
                        <TextField label="当前密码" type="password" autoComplete="current-password"
                                   value={passwords.current}
                                   onChange={event => setPasswords(previous => ({ ...previous, current: event.target.value }))}
                                   slotProps={{ htmlInput: { maxLength: 100 } }} required fullWidth />
                        <TextField label="新密码" type="password" autoComplete="new-password"
                                   value={passwords.next}
                                   onChange={event => setPasswords(previous => ({ ...previous, next: event.target.value }))}
                                    slotProps={{ htmlInput: { minLength: passwordPolicy.minLength, maxLength: passwordPolicy.maxLength } }} required fullWidth />
                        <PasswordPolicyHint password={passwords.next} context={passwordContext} policy={passwordPolicy} />
                        <TextField label="确认新密码" type="password" autoComplete="new-password"
                                   value={passwords.confirm}
                                   onChange={event => setPasswords(previous => ({ ...previous, confirm: event.target.value }))}
                                   error={passwords.confirm.length > 0 && !passwordMatches}
                                   helperText={passwords.confirm.length > 0 && !passwordMatches ? '两次输入的新密码不一致。' : ''}
                                    slotProps={{ htmlInput: { minLength: passwordPolicy.minLength, maxLength: passwordPolicy.maxLength } }} required fullWidth />
                        <Button type="submit" variant="contained" startIcon={<LockResetRoundedIcon />}
                                disabled={!passwords.current || !passwordEvaluation.valid || !passwordMatches || changingPassword}
                                sx={{ justifySelf: 'start', alignSelf: 'flex-start' }}>
                            {changingPassword ? '修改中' : '修改密码'}
                        </Button>
                    </Stack>
                </Paper>

            </PageTabPanel>
            <PageTabPanel id="profile" value="tokens" selected={selectedSection}>
                {!currentUser?.passwordChangeRequired && canManageApiTokens && (
                    <ApiTokenPanel
                        key={currentUser?.membershipId || currentUser?.username}
                        canView={canViewApiTokens}
                        canCreate={canCreateApiTokens}
                        canRevoke={canRevokeApiTokens}
                    />
                )}

            </PageTabPanel>
            <PageTabPanel id="profile" value="sessions" selected={selectedSection}>
                <Paper elevation={1} sx={{ p: { xs: 2, sm: 3 } }}>
                    {sessionMessage && <Alert severity={sessionMessage.severity} sx={{ mb: 2 }}>{sessionMessage.text}</Alert>}
                    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ justifyContent: "space-between", alignItems: { xs: 'stretch', sm: 'center' } }}>
                        <Box>
                            <Typography variant="h6" component="h2" sx={{ fontWeight: 800 }}>登录设备</Typography>
                            <Typography variant="body2" color="text.secondary">共 {sessionsPage.totalElements || 0} 个登录会话</Typography>
                        </Box>
                        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                            <Button onClick={() => loadSessions(sessionPageNumber)} disabled={sessionsLoading}>刷新</Button>
                            {sessionsPage.activeOtherSessionCount > 0 && (
                                <Button color="error" variant="outlined" startIcon={<DeleteOutlineRoundedIcon />}
                                        disabled={revokingOtherSessions} onClick={revokeOtherSessions}>
                                    {revokingOtherSessions ? '撤销中' : `撤销其他全部会话（${sessionsPage.activeOtherSessionCount}）`}
                                </Button>
                            )}
                            <Button color="inherit" startIcon={<LogoutRoundedIcon />} onClick={() => logout()}>
                                退出当前账号
                            </Button>
                        </Stack>
                    </Stack>
                    <Stack divider={<Divider flexItem />} sx={{ mt: 1.5 }}>
                        {sessionsLoading && <Box sx={{ py: 3, textAlign: 'center' }}><CircularProgress size={24} /></Box>}
                        {!sessionsLoading && sessionsPage.content.length === 0 && (
                            <Typography color="text.secondary" sx={{ py: 2 }}>暂无登录会话。</Typography>
                        )}
                        {!sessionsLoading && sessionsPage.content.map(session => (
                            <Stack
                                key={session.id}
                                direction={{ xs: 'column', sm: 'row' }}
                                spacing={1.5}
                                sx={{ justifyContent: "space-between", alignItems: { xs: 'stretch', sm: 'center' }, py: 1.75 }}
                            >
                                <Box sx={{ minWidth: 0 }}>
                                    <Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap" }}>
                                        <Typography sx={{ fontWeight: 750 }}>{session.deviceName || '未知设备'}</Typography>
                                        {session.current && <Chip label="当前会话" size="small" color="primary" variant="outlined" />}
                                        <Chip label={sessionStatusLabel(session.status)} size="small"
                                              color={session.status === 'ACTIVE' ? 'success' : 'default'} variant="outlined" />
                                    </Stack>
                                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                                        {[session.osVersion, session.appVersion && `应用 ${session.appVersion}`].filter(Boolean).join(' · ') || '网页会话'}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary">
                                        最近活动：{formatDateTime(session.lastSeenAt, 'zh-CN', {}, '未知')}
                                    </Typography>
                                </Box>
                                {!session.current && session.status === 'ACTIVE' && (
                                    <Button color="error" variant="outlined" startIcon={<DeleteOutlineRoundedIcon />}
                                            disabled={revokingSessionId === session.id}
                                            onClick={() => revokeSession(session.id)}>
                                        {revokingSessionId === session.id ? '撤销中' : '撤销'}
                                    </Button>
                                )}
                            </Stack>
                        ))}
                    </Stack>
                    {sessionsPage.totalPages > 1 && (
                        <Stack direction="row" spacing={2} sx={{ justifyContent: "center", alignItems: "center", mt: 2 }}>
                            <Pagination getItemAriaLabel={getPaginationItemAriaLabel} count={sessionsPage.totalPages} page={sessionPageNumber + 1}
                                        onChange={(_, value) => loadSessions(value - 1)} disabled={sessionsLoading} />
                        </Stack>
                    )}
                </Paper>
            </PageTabPanel>
        </Container>
    );
}

const sessionStatusLabel = status => ({
    ACTIVE: '有效',
    REVOKED: '已撤销',
    EXPIRED: '已过期',
    COMPROMISED: '存在风险',
}[status] || '未知');
