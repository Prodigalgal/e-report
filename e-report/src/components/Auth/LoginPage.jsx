import React, { useEffect, useRef, useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import {
    Alert,
    Box,
    Button,
    Checkbox,
    CircularProgress,
    FormControlLabel,
    IconButton,
    InputAdornment,
    Paper,
    TextField,
    Tooltip,
    Typography,
} from '@mui/material';
import AccountCircleOutlinedIcon from '@mui/icons-material/AccountCircleOutlined';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded';
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';

import BrandLogo from '../Brand/BrandLogo';
import BrandLoading from '../Brand/BrandLoading';
import { APP_NAME } from '../../constants/appMetadata';
import { loadRememberLoginPreference } from '../../constants/authStorage';
import { useAuth } from '../../hooks/useAuth';
import { useLoginChallenge } from '../../hooks/useLoginChallenge';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';

function LoginPage({ title, resolveDestination }) {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [captchaAnswer, setCaptchaAnswer] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [error, setError] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [loginStage, setLoginStage] = useState('IDLE');
    const [rememberMe, setRememberMe] = useState(loadRememberLoginPreference);
    const captchaInputRef = useRef(null);
    const focusCaptchaAfterRefreshRef = useRef(false);
    const navigate = useNavigate();
    const location = useLocation();
    const { login, isAuthenticated, loadingAuth, currentUser } = useAuth();
    const {
        challenge,
        challengeReady,
        loadingChallenge,
        challengeError,
        refreshChallenge,
        buildLoginProof,
    } = useLoginChallenge(username);

    useEffect(() => {
        if (!focusCaptchaAfterRefreshRef.current || loadingChallenge || isSubmitting) return;
        focusCaptchaAfterRefreshRef.current = false;
        if (challenge?.captchaRequired) {
            captchaInputRef.current?.focus({ preventScroll: false });
        }
    }, [challenge, isSubmitting, loadingChallenge]);

    if (loadingAuth) {
        return <BrandLoading minHeight="100dvh" message="正在检查登录状态..." />;
    }

    if (isAuthenticated()) {
        return <Navigate to={resolveDestination(currentUser, location.state?.from)} replace />;
    }

    const handleSubmit = async event => {
        event.preventDefault();
        setError('');

        const normalizedUsername = username.trim();
        const normalizedCaptchaAnswer = captchaAnswer.trim();
        if (!normalizedUsername || !password) {
            setError('请输入用户名和密码。');
            return;
        }
        if (!challengeReady) {
            setError(challengeError ? '请先重试安全验证。' : '安全验证正在准备，请稍候再试。');
            return;
        }
        if (challenge?.captchaRequired && !normalizedCaptchaAnswer) {
            setError('请先填写上方的数学验证码答案。');
            captchaInputRef.current?.focus();
            return;
        }

        setIsSubmitting(true);
        setLoginStage('SECURITY_CHECK');
        let loginRequestStarted = false;
        try {
            const challengeProof = await buildLoginProof(normalizedCaptchaAnswer, normalizedUsername);
            setLoginStage('AUTHENTICATING');
            loginRequestStarted = true;
            const result = await login(normalizedUsername, password, challengeProof, rememberMe);
            setLoginStage('ENTERING');
            navigate(result.user.passwordChangeRequired
                ? '/profile'
                : resolveDestination(result.user, location.state?.from), { replace: true });
        } catch (loginError) {
            setError(formatApiErrorMessage(loginError, '登录失败，请检查账号、密码和验证码后重试。'));
            if (loginRequestStarted) {
                setCaptchaAnswer('');
                focusCaptchaAfterRefreshRef.current = true;
                await refreshChallenge(normalizedUsername).catch(() => {});
            }
        } finally {
            setIsSubmitting(false);
            setLoginStage('IDLE');
        }
    };

    const busy = isSubmitting || loadingChallenge;

    return (
        <Box
            component="main"
            data-testid="login-page"
            sx={{
                minHeight: '100dvh',
                display: 'grid',
                gridTemplateColumns: { xs: '1fr', md: 'minmax(320px, 0.82fr) minmax(520px, 1.18fr)' },
                bgcolor: '#F8FAFC',
            }}
        >
            <Box
                sx={{
                    display: { xs: 'none', md: 'flex' },
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                    p: { md: 5, lg: 7 },
                    bgcolor: '#0F172A',
                    color: '#fff',
                    borderRight: '1px solid rgba(255,255,255,0.08)',
                }}
            >
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                    <BrandLogo size={48} />
                    <Typography sx={{ fontSize: 18, fontWeight: 800 }}>{APP_NAME}</Typography>
                </Box>

                <Box sx={{ maxWidth: 420 }}>
                    <Typography component="p" sx={{ fontSize: { md: 36, lg: 44 }, lineHeight: 1.2, fontWeight: 800 }}>
                        网络运维管理
                    </Typography>
                    <Box sx={{ width: 56, height: 4, bgcolor: 'primary.main', mt: 3, mb: 3 }} />
                    <Typography sx={{ color: '#CBD5E1', fontSize: 17, lineHeight: 1.8 }}>
                        网络现场工单统一入口
                    </Typography>
                </Box>

                <Typography sx={{ color: '#94A3B8', fontSize: 13 }}>
                    仅限已授权账号访问
                </Typography>
            </Box>

            <Box
                sx={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    px: { xs: 2, sm: 4, lg: 8 },
                    py: { xs: 3, sm: 5 },
                }}
            >
                <Box sx={{ width: '100%', maxWidth: 460 }}>
                    <Box sx={{ display: { xs: 'flex', md: 'none' }, alignItems: 'center', gap: 1.5, mb: 4 }}>
                        <BrandLogo size={44} />
                        <Typography sx={{ color: '#0F172A', fontSize: 18, fontWeight: 800 }}>{APP_NAME}</Typography>
                    </Box>

                    <Paper
                        elevation={0}
                        sx={{
                            border: '1px solid #E2E8F0',
                            borderRadius: 1,
                            p: { xs: 2, sm: 3 },
                            boxShadow: '0 18px 45px rgba(15, 23, 42, 0.08)',
                        }}
                    >
                        <Typography component="h1" sx={{ color: '#0F172A', fontSize: { xs: 28, sm: 32 }, fontWeight: 800 }}>
                            {title}
                        </Typography>
                        <Typography sx={{ color: '#64748B', mt: 1, mb: 3 }}>
                            使用系统账号继续
                        </Typography>

                        <Box component="form" onSubmit={handleSubmit} noValidate sx={{ display: 'grid', gap: 2 }}>
                            <TextField
                                required
                                fullWidth
                                id="username"
                                size="medium"
                                label="用户名"
                                name="username"
                                autoComplete="username"
                                autoFocus
                                disabled={isSubmitting}
                                value={username}
                                onChange={event => {
                                    setUsername(event.target.value);
                                    setCaptchaAnswer('');
                                    setError('');
                                }}
                                slotProps={{
                                    input: {
                                        startAdornment: (
                                            <InputAdornment position="start"><AccountCircleOutlinedIcon color="action" /></InputAdornment>
                                        ),
                                    },
                                }}
                            />

                            <TextField
                                required
                                fullWidth
                                id="password"
                                size="medium"
                                label="密码"
                                name="password"
                                type={showPassword ? 'text' : 'password'}
                                autoComplete="current-password"
                                disabled={isSubmitting}
                                value={password}
                                onChange={event => setPassword(event.target.value)}
                                slotProps={{
                                    input: {
                                        startAdornment: (
                                            <InputAdornment position="start"><LockOutlinedIcon color="action" /></InputAdornment>
                                        ),
                                        endAdornment: (
                                            <InputAdornment position="end">
                                                <Tooltip title={showPassword ? '隐藏密码' : '显示密码'}>
                                                    <IconButton
                                                        aria-label={showPassword ? '隐藏密码' : '显示密码'}
                                                        edge="end"
                                                        onClick={() => setShowPassword(value => !value)}
                                                    >
                                                        {showPassword ? <VisibilityOffOutlinedIcon /> : <VisibilityOutlinedIcon />}
                                                    </IconButton>
                                                </Tooltip>
                                            </InputAdornment>
                                        ),
                                    },
                                }}
                            />

                            <FormControlLabel
                                control={(
                                    <Checkbox
                                        checked={rememberMe}
                                        onChange={event => setRememberMe(event.target.checked)}
                                        disabled={isSubmitting}
                                    />
                                )}
                                label="记住登录状态"
                                sx={{ m: 0, alignSelf: 'start' }}
                            />

                            {challenge?.captchaRequired && (
                                <Box sx={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) auto', gap: 1 }}>
                                    <Alert severity="info" role="status" sx={{ gridColumn: '1 / -1' }}>
                                        安全验证已升级，请回答数学题后再次登录。
                                    </Alert>
                                    <TextField
                                        required
                                        fullWidth
                                        id="captchaAnswer"
                                        name="captchaAnswer"
                                        size="medium"
                                        label="验证码答案"
                                        inputRef={captchaInputRef}
                                        value={captchaAnswer}
                                        onChange={event => setCaptchaAnswer(event.target.value)}
                                        disabled={busy}
                                        helperText={`请计算：${challenge.captchaQuestion}`}
                                        slotProps={{ htmlInput: { inputMode: 'numeric' } }}
                                    />
                                    <Tooltip title="刷新验证码">
                                        <span>
                                            <IconButton
                                                aria-label="刷新验证码"
                                                disabled={busy}
                                                onClick={() => {
                                                    setCaptchaAnswer('');
                                                    setError('');
                                                    refreshChallenge(username).catch(() => {});
                                                }}
                                                sx={{ width: 48, height: 48, border: '1px solid #CBD5E1', borderRadius: 1 }}
                                            >
                                                {loadingChallenge ? <CircularProgress size={22} /> : <RefreshRoundedIcon />}
                                            </IconButton>
                                        </span>
                                    </Tooltip>
                                </Box>
                            )}

                            {challenge?.mode === 'COOLDOWN' && (
                                <Alert severity="warning">
                                    登录保护已启用，请在{challenge.retryAfterSeconds || 1}秒后重试。
                                </Alert>
                            )}

                            {challengeError && (
                                <Alert severity="warning" action={
                                    <Button color="inherit" size="small" disabled={busy} onClick={() => {
                                        setError('');
                                        refreshChallenge(username).catch(() => {});
                                    }}>重试</Button>
                                }>
                                    {challengeError}
                                </Alert>
                            )}
                            {error && <Alert severity="error" aria-live="polite">{error}</Alert>}

                            <Button
                                type="submit"
                                fullWidth
                                variant="contained"
                                size="large"
                                disabled={busy || !challengeReady || challenge?.mode === 'COOLDOWN'}
                                loading={isSubmitting}
                                sx={{ minHeight: 48, mt: 0.5 }}
                            >
                                {loginStageLabel(
                                    loginStage,
                                    loadingChallenge,
                                    challengeReady,
                                    Boolean(username.trim()),
                                    challengeError,
                                )}
                            </Button>
                        </Box>
                    </Paper>
                </Box>
            </Box>
        </Box>
    );
}

function loginStageLabel(stage, loadingChallenge, challengeReady, hasUsername, challengeError) {
    if (loadingChallenge || (hasUsername && !challengeReady && !challengeError)) return '正在准备安全验证';
    if (challengeError) return '安全验证未就绪';
    if (stage === 'SECURITY_CHECK') return '正在进行安全校验';
    if (stage === 'AUTHENTICATING') return '正在验证账号';
    if (stage === 'ENTERING') return '正在进入系统';
    return '登录';
}

export default LoginPage;
