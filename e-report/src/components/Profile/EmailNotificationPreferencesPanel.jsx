import { useCallback, useEffect, useState } from 'react';
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, LinearProgress, Paper, Switch, Typography } from '@mui/material';

import options from '../../../../shared/email-notification-preferences.json';
import {
    fetchEmailNotificationPreferencesAPI, updateEmailNotificationPreferencesAPI,
    fetchAdminEmailNotificationPreferencesAPI, updateAdminEmailNotificationPreferencesAPI,
} from '../../services/emailNotificationApiService';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';

export default function EmailNotificationPreferencesPanel({ targetUserId, targetName, onClose }) {
    const [preferences, setPreferences] = useState(null);
    const [savedPreferences, setSavedPreferences] = useState(null);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const [saved, setSaved] = useState(false);

    const load = useCallback(async signal => {
        setLoading(true);
        setError('');
        try {
            const response = targetUserId === undefined
                ? await fetchEmailNotificationPreferencesAPI(signal)
                : await fetchAdminEmailNotificationPreferencesAPI(targetUserId, signal);
            if (!signal?.aborted) {
                setPreferences(response.data);
                setSavedPreferences(response.data);
            }
        } catch (loadError) {
            if (!signal?.aborted) setError(formatApiErrorMessage(loadError, '邮件订阅加载失败。'));
        } finally {
            if (!signal?.aborted) setLoading(false);
        }
    }, [targetUserId]);

    useEffect(() => {
        const controller = new AbortController();
        void load(controller.signal);
        return () => controller.abort();
    }, [load]);

    const changed = preferences && Object.keys(options).some(key => preferences[key] !== savedPreferences?.[key]);
    const save = async event => {
        event.preventDefault();
        if (!changed || saving) return;
        setSaving(true);
        setError('');
        setSaved(false);
        try {
            const response = targetUserId === undefined
                ? await updateEmailNotificationPreferencesAPI(preferences)
                : await updateAdminEmailNotificationPreferencesAPI(targetUserId, preferences);
            setPreferences(response.data);
            setSavedPreferences(response.data);
            setSaved(true);
        } catch (saveError) {
            setError(formatApiErrorMessage(saveError, '邮件订阅保存失败，请重试。'));
        } finally {
            setSaving(false);
        }
    };

    const content = (
        <>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, mb: 2 }}>
                {targetUserId === undefined
                    ? '仅影响本人收信。实际通知仍受组织设置、收件权限及可用邮箱限制。'
                    : `正在编辑 ${targetName || '该用户'} 的邮件订阅，本人仍可自行修改。实际通知仍受组织设置、收件权限及可用邮箱限制。`}
            </Typography>
            {loading && <LinearProgress aria-label="加载邮件订阅" />}
            {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
            {saved && <Alert severity="success" sx={{ mb: 2 }}>邮件订阅已保存。</Alert>}
            {!loading && !preferences && <Button onClick={() => load()}>重新加载</Button>}
            {preferences && <Box sx={{ display: 'grid', gap: 1.5 }}>
                {Object.entries(options).map(([key, option]) => (
                    <FormControlLabel
                        key={key}
                        labelPlacement="start"
                        sx={{ m: 0, justifyContent: 'space-between', gap: 2, minHeight: 48 }}
                        label={<Box>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>{option.title}</Typography>
                            <Typography variant="body2" color="text.secondary">{option.description}</Typography>
                        </Box>}
                        control={<Switch
                            checked={preferences[key]}
                            disabled={saving || loading}
                            slotProps={{ input: { 'aria-label': option.title } }}
                            onChange={event => {
                                setPreferences(current => ({ ...current, [key]: event.target.checked }));
                                setSaved(false);
                            }}
                        />}
                    />
                ))}
            </Box>}
        </>
    );
    const saveButton = <Button type="submit" variant="contained" disabled={!changed || saving || loading}>
        {saving ? '保存中' : '保存通知偏好'}
    </Button>;

    if (onClose) {
        return <Dialog
            open onClose={() => { if (!saving) onClose(); }} fullWidth maxWidth="sm"
            aria-labelledby="email-notifications-dialog-title"
            slotProps={{ paper: { component: 'form', onSubmit: save } }}
        >
            <DialogTitle id="email-notifications-dialog-title">邮件通知偏好</DialogTitle>
            <DialogContent>{content}</DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={saving}>关闭</Button>
                {saveButton}
            </DialogActions>
        </Dialog>;
    }

    return (
        <Paper component="form" onSubmit={save} elevation={1} sx={{ p: { xs: 2, sm: 3 } }}>
            <Typography variant="h6" component="h2" sx={{ fontWeight: 800 }}>邮件通知偏好</Typography>
            {content}
            {preferences && <Box sx={{ mt: 1.5 }}>{saveButton}</Box>}
        </Paper>
    );
}
