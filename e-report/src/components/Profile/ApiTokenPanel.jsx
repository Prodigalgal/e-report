import { useCallback, useEffect, useMemo, useState } from 'react';
import { copyText } from '../../utils/clipboard.js';
import {
    Alert,
    Box,
    Button,
    Checkbox,
    Chip,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Divider,
    FormControlLabel,
    IconButton,
    InputAdornment,
    List,
    ListItemButton,
    ListItemText,
    Paper,
    Stack,
    Switch,
    TextField,
    Tooltip,
    Typography,
} from '@mui/material';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import ContentCopyRoundedIcon from '@mui/icons-material/ContentCopyRounded';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import KeyRoundedIcon from '@mui/icons-material/KeyRounded';
import SearchRoundedIcon from '@mui/icons-material/SearchRounded';

import {
    createApiTokenAPI,
    fetchApiTokenOptionsAPI,
    fetchApiTokensAPI,
    revokeApiTokenAPI,
} from '../../services/apiTokenService.js';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage.js';
import { formatDateTime } from '../../utils/formatDate.js';

const MAX_EXPIRY_DAYS = 3650;

export default function ApiTokenPanel({ canView, canCreate, canRevoke }) {
    const [tokens, setTokens] = useState([]);
    const [options, setOptions] = useState([]);
    const [loading, setLoading] = useState(true);
    const [message, setMessage] = useState(null);
    const [createOpen, setCreateOpen] = useState(false);
    const [creating, setCreating] = useState(false);
    const [form, setForm] = useState({ name: '', longLived: false, expiryDays: 30 });
    const [selectedPermissions, setSelectedPermissions] = useState(new Set());
    const [permissionSearch, setPermissionSearch] = useState('');
    const [createdToken, setCreatedToken] = useState(null);
    const [copied, setCopied] = useState(false);
    const [revokeTarget, setRevokeTarget] = useState(null);
    const [revoking, setRevoking] = useState(false);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const [tokenResponse, optionResponse] = await Promise.all([
                canView ? fetchApiTokensAPI() : Promise.resolve({ data: [] }),
                canCreate ? fetchApiTokenOptionsAPI() : Promise.resolve({ data: [] }),
            ]);
            setTokens(Array.isArray(tokenResponse.data) ? tokenResponse.data : []);
            setOptions(Array.isArray(optionResponse.data) ? optionResponse.data : []);
        } catch (error) {
            setMessage({ severity: 'error', text: formatApiErrorMessage(error, 'API Token 加载失败，请稍后重试。') });
        } finally {
            setLoading(false);
        }
    }, [canCreate, canView]);

    useEffect(() => {
        load().catch(() => {});
    }, [load]);

    const optionByName = useMemo(
        () => new Map(options.map(option => [option.name, option])),
        [options],
    );
    const filteredOptions = useMemo(() => {
        const keyword = permissionSearch.trim().toLowerCase();
        if (!keyword) return options;
        return options.filter(option => [option.displayName, option.name, option.description]
            .some(value => String(value || '').toLowerCase().includes(keyword)));
    }, [options, permissionSearch]);
    const expiryDays = Number(form.expiryDays);
    const validExpiry = form.longLived
        || (Number.isInteger(expiryDays) && expiryDays >= 1 && expiryDays <= MAX_EXPIRY_DAYS);
    const selectedHighRiskCount = useMemo(
        () => [...selectedPermissions].filter(name => ['HIGH', 'CRITICAL'].includes(optionByName.get(name)?.riskLevel)).length,
        [optionByName, selectedPermissions],
    );

    const openCreate = () => {
        setForm({ name: '', longLived: false, expiryDays: 30 });
        setSelectedPermissions(new Set());
        setPermissionSearch('');
        setMessage(null);
        setCreateOpen(true);
    };

    const togglePermission = permissionName => {
        setSelectedPermissions(previous => {
            const next = new Set(previous);
            if (next.has(permissionName)) next.delete(permissionName);
            else next.add(permissionName);
            return next;
        });
    };

    const create = async () => {
        if (!form.name.trim() || selectedPermissions.size === 0 || !validExpiry || creating) return;
        setCreating(true);
        setMessage(null);
        try {
            const expiresAt = form.longLived
                ? null
                : new Date(Date.now() + expiryDays * 24 * 60 * 60 * 1000).toISOString();
            const response = await createApiTokenAPI({
                name: form.name.trim(),
                expiresAt,
                permissionNames: [...selectedPermissions],
            });
            setCreateOpen(false);
            setCreatedToken(response.data);
            setCopied(false);
            await load();
        } catch (error) {
            setMessage({ severity: 'error', text: formatApiErrorMessage(error, 'API Token 创建失败，请检查后重试。') });
        } finally {
            setCreating(false);
        }
    };

    const copyToken = async () => {
        if (!createdToken?.token) return;
        try {
            await copyText(createdToken.token, true);
            setCopied(true);
        } catch {
            setCopied(false);
        }
    };

    const revoke = async () => {
        if (!revokeTarget || revoking) return;
        setRevoking(true);
        setMessage(null);
        try {
            await revokeApiTokenAPI(revokeTarget.id);
            setRevokeTarget(null);
            await load();
            setMessage({ severity: 'success', text: 'API Token 已撤销。' });
        } catch (error) {
            setMessage({ severity: 'error', text: formatApiErrorMessage(error, 'API Token 撤销失败，请稍后重试。') });
        } finally {
            setRevoking(false);
        }
    };

    return (
        <Paper elevation={1} sx={{ p: { xs: 2, sm: 3 } }}>
            <Stack
                direction={{ xs: 'column', sm: 'row' }}
                spacing={1.5}
                sx={{ justifyContent: "space-between", alignItems: { xs: 'stretch', sm: 'center' } }}
            >
                <Box>
                    <Typography variant="h6" component="h2" sx={{ fontWeight: 800 }}>API Token</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                        用于大模型或自动化接口调用，权限不会超过当前账号。
                    </Typography>
                </Box>
                {canCreate && (
                    <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={openCreate}
                            disabled={loading || options.length === 0}>
                        创建 Token
                    </Button>
                )}
            </Stack>
            {message && <Alert severity={message.severity} sx={{ mt: 2 }}>{message.text}</Alert>}
            {loading && <Box sx={{ py: 4, textAlign: 'center' }}><CircularProgress size={26} /></Box>}
            {!loading && !canView && (
                <Typography color="text.secondary" sx={{ py: 3 }}>当前仅可执行已授权的 Token 操作，未授予 Token 查看权限。</Typography>
            )}
            {!loading && canView && tokens.length === 0 && (
                <Typography color="text.secondary" sx={{ py: 3 }}>暂无 API Token。</Typography>
            )}
            {!loading && canView && tokens.length > 0 && (
                <Stack divider={<Divider flexItem />} sx={{ mt: 1.5 }}>
                    {tokens.map(token => (
                        <Stack
                            key={token.id}
                            direction={{ xs: 'column', sm: 'row' }}
                            spacing={1.5}
                            sx={{ justifyContent: "space-between", alignItems: { xs: 'stretch', sm: 'center' }, py: 1.75 }}
                        >
                            <Box sx={{ minWidth: 0 }}>
                                <Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap" }}>
                                    <KeyRoundedIcon color="action" fontSize="small" />
                                    <Typography sx={{ fontWeight: 750 }}>{token.name}</Typography>
                                    <Chip size="small" variant="outlined" label={tokenStatusLabel(token.status)}
                                          color={token.status === 'ACTIVE' ? 'success' : 'default'} />
                                    <Chip size="small" variant="outlined"
                                          label={token.expiresAt ? `到期 ${formatDateTime(token.expiresAt)}` : '长期有效'} />
                                </Stack>
                                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.65 }}>
                                    {token.tokenPrefix} · {token.permissionNames.length} 项权限
                                </Typography>
                                <Typography variant="caption" color="text.secondary">
                                    最近使用：{token.lastUsedAt ? formatDateTime(token.lastUsedAt) : '尚未使用'}
                                </Typography>
                            </Box>
                            {canRevoke && token.status === 'ACTIVE' && (
                                <Button color="error" variant="outlined" startIcon={<DeleteOutlineRoundedIcon />}
                                        onClick={() => setRevokeTarget(token)}>
                                    撤销
                                </Button>
                            )}
                        </Stack>
                    ))}
                </Stack>
            )}

            <Dialog open={createOpen} onClose={creating ? undefined : () => setCreateOpen(false)}
                    fullWidth maxWidth="md">
                <DialogTitle>创建 API Token</DialogTitle>
                <DialogContent dividers>
                    <Stack spacing={2}>
                        <TextField label="Token 名称" value={form.name} required autoFocus
                                   onChange={event => setForm(previous => ({ ...previous, name: event.target.value }))}
                                   slotProps={{ htmlInput: { maxLength: 80 } }} />
                        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ alignItems: { sm: 'center' } }}>
                            <FormControlLabel control={(
                                <Switch checked={form.longLived}
                                        onChange={event => setForm(previous => ({ ...previous, longLived: event.target.checked }))} />
                            )} label="长期有效" />
                            {!form.longLived && (
                                <TextField label="有效天数" type="number" value={form.expiryDays}
                                           error={!validExpiry}
                                           helperText={!validExpiry ? `请输入 1-${MAX_EXPIRY_DAYS} 天` : ''}
                                           onChange={event => setForm(previous => ({ ...previous, expiryDays: event.target.value }))}
                                           slotProps={{ htmlInput: { min: 1, max: MAX_EXPIRY_DAYS, step: 1 } }}
                                           sx={{ width: { xs: '100%', sm: 180 } }} />
                            )}
                        </Stack>
                        {form.longLived && <Alert severity="warning">长期 Token 不会自动到期，只应在受控环境使用。</Alert>}
                        <TextField placeholder="搜索权限名称或编码" value={permissionSearch}
                                   onChange={event => setPermissionSearch(event.target.value)}
                                   slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchRoundedIcon /></InputAdornment> } }} />
                        <Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center" }}>
                            <Typography sx={{ fontWeight: 750 }}>访问权限</Typography>
                            <Chip size="small" label={`已选 ${selectedPermissions.size} 项`} />
                        </Stack>
                        {selectedHighRiskCount > 0 && (
                            <Alert severity="warning">已选择 {selectedHighRiskCount} 项高风险权限，请确认大模型确实需要执行写操作。</Alert>
                        )}
                        <List disablePadding sx={{ border: 1, borderColor: 'divider', maxHeight: 360, overflowY: 'auto' }}>
                            {filteredOptions.map(option => (
                                <ListItemButton key={option.name} onClick={() => togglePermission(option.name)}
                                                selected={selectedPermissions.has(option.name)}>
                                    <Checkbox edge="start" checked={selectedPermissions.has(option.name)} tabIndex={-1} />
                                    <ListItemText primary={option.displayName}
                                                  secondary={`${option.name} · ${riskLabel(option.riskLevel)} · ${option.description}`} />
                                </ListItemButton>
                            ))}
                            {filteredOptions.length === 0 && (
                                <Typography color="text.secondary" sx={{ p: 2 }}>没有匹配的权限。</Typography>
                            )}
                        </List>
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setCreateOpen(false)} disabled={creating}>取消</Button>
                    <Button variant="contained" onClick={create}
                            disabled={!form.name.trim() || selectedPermissions.size === 0 || !validExpiry || creating}>
                        {creating ? '创建中' : '创建'}
                    </Button>
                </DialogActions>
            </Dialog>

            <Dialog open={!!createdToken} onClose={() => setCreatedToken(null)} fullWidth maxWidth="sm">
                <DialogTitle>API Token 已创建</DialogTitle>
                <DialogContent dividers>
                    <Alert severity="warning">Token 明文只显示这一次，关闭后无法再次查看。</Alert>
                    <TextField value={createdToken?.token || ''} fullWidth multiline minRows={2} sx={{ mt: 2 }}
                               slotProps={{ input: { readOnly: true, endAdornment: (
                                   <InputAdornment position="end">
                                       <Tooltip title="复制 Token">
                                           <IconButton onClick={copyToken}><ContentCopyRoundedIcon /></IconButton>
                                       </Tooltip>
                                   </InputAdornment>
                               ) } }} />
                    {copied && <Alert severity="success" sx={{ mt: 1.5 }}>已复制到剪贴板。</Alert>}
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
                        MCP 服务地址：/api/mcp。请在 MCP 客户端使用个人 API Token 作为 Bearer Token；工具范围随当前授权动态更新。
                    </Typography>
                </DialogContent>
                <DialogActions>
                    <Button variant="contained" onClick={() => setCreatedToken(null)}>我已保存</Button>
                </DialogActions>
            </Dialog>

            <Dialog open={!!revokeTarget} onClose={revoking ? undefined : () => setRevokeTarget(null)}>
                <DialogTitle>撤销 API Token</DialogTitle>
                <DialogContent>
                    <Typography>撤销“{revokeTarget?.name}”后，使用该 Token 的大模型或脚本会立即失去访问能力。</Typography>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setRevokeTarget(null)} disabled={revoking}>取消</Button>
                    <Button color="error" variant="contained" onClick={revoke} disabled={revoking}>
                        {revoking ? '撤销中' : '确认撤销'}
                    </Button>
                </DialogActions>
            </Dialog>
        </Paper>
    );
}

const tokenStatusLabel = status => ({ ACTIVE: '有效', EXPIRED: '已过期', REVOKED: '已撤销' }[status] || '未知');
const riskLabel = risk => ({ LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', CRITICAL: '关键风险' }[risk] || '风险未知');
