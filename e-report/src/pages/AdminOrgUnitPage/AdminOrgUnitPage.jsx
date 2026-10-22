import PageHeading from '../../components/layout/PageHeading';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
    Alert, Box, Button, Chip, CircularProgress, Collapse, Dialog, DialogActions,
    DialogContent, DialogTitle, FormControl, IconButton, InputAdornment, InputLabel,
    MenuItem, Paper, Select, Stack, TextField, Tooltip, Typography,
} from '@mui/material';
import AccountBalanceOutlinedIcon from '@mui/icons-material/AccountBalanceOutlined';
import AddIcon from '@mui/icons-material/Add';
import BusinessOutlinedIcon from '@mui/icons-material/BusinessOutlined';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import DriveFileMoveIcon from '@mui/icons-material/DriveFileMove';
import EditIcon from '@mui/icons-material/Edit';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import SearchIcon from '@mui/icons-material/Search';
import UnfoldLessIcon from '@mui/icons-material/UnfoldLess';
import UnfoldMoreIcon from '@mui/icons-material/UnfoldMore';

import apiClient from '../../services/apiClient';
import { PERMISSIONS } from '../../constants/permissions';
import { useAuth } from '../../hooks/useAuth';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';
import DepartmentManagementPanel from './DepartmentManagementPanel.jsx';
import { buildHierarchy, collectHierarchyIds, countHierarchyDescendants, filterHierarchy } from './hierarchyTree.js';

const UNIT_TYPES = [
    { value: 'COUNTY', label: '区县公司' },
];

const emptyForm = { code: '', name: '', parentId: '', unitType: 'COUNTY', status: 'ACTIVE' };

function deriveVisibleOrganizations(departments) {
    const organizationsById = new Map();
    departments.forEach(department => {
        if (!department.orgUnitId || organizationsById.has(department.orgUnitId)) return;
        organizationsById.set(department.orgUnitId, {
            id: department.orgUnitId,
            parentId: null,
            code: '',
            name: department.orgUnitName || '可见组织',
            path: `/${department.orgUnitId}/`,
            status: 'ACTIVE',
            tenantRoot: false,
            unitType: 'COUNTY',
        });
    });
    return Array.from(organizationsById.values());
}

function unitTypeLabel(orgUnit) {
    return orgUnit.tenantRoot ? '市公司' : UNIT_TYPES.find(item => item.value === orgUnit.unitType)?.label || '组织';
}

function organizationTreeMeta(orgUnit, hasChildren) {
    return [
        orgUnit.code,
        unitTypeLabel(orgUnit),
        hasChildren ? `${countHierarchyDescendants(orgUnit)} 个下级` : '',
    ].filter(Boolean).join(' · ');
}

function OrganizationTreeNode({ node, depth, expandedIds, selectedId, departmentCounts, onToggle, onSelect }) {
    const hasChildren = node.children.length > 0;
    const expanded = expandedIds.has(node.id);
    const selected = selectedId === node.id;

    return (
        <Box sx={{ position: 'relative', ml: depth ? 2.25 : 0 }}>
            {depth ? <Box sx={{ position: 'absolute', left: -11, top: 0, bottom: 0, borderLeft: '1px solid', borderColor: 'divider' }} /> : null}
            <Box
                component="button"
                type="button"
                onClick={() => onSelect(node.id)}
                sx={{
                    position: 'relative', width: '100%', border: '1px solid', borderRadius: 1.5, mb: 0.75,
                    borderColor: selected ? 'primary.light' : 'divider', bgcolor: selected ? 'action.selected' : 'background.paper',
                    p: 0.75, textAlign: 'left', font: 'inherit', color: 'inherit', cursor: 'pointer',
                    '&:hover': { borderColor: selected ? 'primary.light' : 'divider', bgcolor: selected ? 'action.selected' : 'action.hover' },
                    '&::before': depth ? { content: '""', position: 'absolute', left: -11, top: 26, width: 11, borderTop: '1px solid', borderColor: 'divider' } : undefined,
                }}
            >
                <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
                    <IconButton
                        component="span"
                        size="small"
                        aria-label={expanded ? `收起${node.name}` : `展开${node.name}`}
                        disabled={!hasChildren}
                        onClick={event => { event.stopPropagation(); onToggle(node.id); }}
                        sx={{ width: 30, height: 30, visibility: hasChildren ? 'visible' : 'hidden' }}
                    >
                        {expanded ? <ExpandMoreIcon fontSize="small" /> : <ChevronRightIcon fontSize="small" />}
                    </IconButton>
                    {node.tenantRoot ? <AccountBalanceOutlinedIcon color={selected ? 'primary' : 'action'} /> : <BusinessOutlinedIcon color={selected ? 'primary' : 'action'} />}
                    <Box sx={{ minWidth: 0, flex: 1 }}>
                        <Typography variant="body2" sx={{ fontWeight: 800 }} noWrap>{node.name}</Typography>
                        <Typography variant="caption" color="text.secondary" noWrap>
                            {organizationTreeMeta(node, hasChildren)}
                        </Typography>
                    </Box>
                    {departmentCounts.get(node.id) ? <Chip size="small" color="primary" variant="outlined" label={`${departmentCounts.get(node.id)} 部门`} sx={{ height: 23 }} /> : null}
                    <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: node.status === 'ACTIVE' ? 'success.main' : 'warning.main', flex: '0 0 auto' }} />
                </Stack>
            </Box>
            {hasChildren ? (
                <Collapse in={expanded} timeout="auto" unmountOnExit>
                    {node.children.map(child => (
                        <OrganizationTreeNode
                            key={child.id}
                            node={child}
                            depth={depth + 1}
                            expandedIds={expandedIds}
                            selectedId={selectedId}
                            departmentCounts={departmentCounts}
                            onToggle={onToggle}
                            onSelect={onSelect}
                        />
                    ))}
                </Collapse>
            ) : null}
        </Box>
    );
}

function AdminOrgUnitPage() {
    const { currentUser, hasPermission } = useAuth();
    const canViewOrganizations = hasPermission(PERMISSIONS.ORG_MANAGE_VIEW);
    const canViewDepartments = hasPermission(PERMISSIONS.DEPARTMENT_VIEW);
    const canCreate = hasPermission(PERMISSIONS.ORG_MANAGE_CREATE);
    const canUpdate = hasPermission(PERMISSIONS.ORG_MANAGE_UPDATE);
    const canEnable = hasPermission(PERMISSIONS.ORG_MANAGE_ENABLE);
    const canDisable = hasPermission(PERMISSIONS.ORG_MANAGE_DISABLE);
    const canUpdateStatus = canEnable || canDisable;
    const canMove = hasPermission(PERMISSIONS.ORG_MANAGE_MOVE);
    const [orgUnits, setOrgUnits] = useState([]);
    const [departments, setDepartments] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [editing, setEditing] = useState(null);
    const [form, setForm] = useState(emptyForm);
    const [submitting, setSubmitting] = useState(false);
    const [moving, setMoving] = useState(null);
    const [moveForm, setMoveForm] = useState({ targetParentId: '', reason: '' });
    const [selectedOrgUnitId, setSelectedOrgUnitId] = useState('');
    const [searchTerm, setSearchTerm] = useState('');
    const [expandedIds, setExpandedIds] = useState(new Set());

    const loadData = useCallback(async () => {
        setLoading(true);
        setError('');
        try {
            const [organizationResponse, departmentResponse] = await Promise.all([
                canViewOrganizations ? apiClient.get('/admin/org-units') : Promise.resolve({ data: [] }),
                canViewDepartments ? apiClient.get('/admin/departments') : Promise.resolve({ data: [] }),
            ]);
            const nextDepartments = departmentResponse.data || [];
            const organizationData = organizationResponse.data || [];
            const nextOrganizations = organizationData.length > 0
                ? organizationData
                : deriveVisibleOrganizations(nextDepartments);
            setOrgUnits(nextOrganizations);
            setDepartments(nextDepartments);
            setSelectedOrgUnitId(current => {
                if (current && nextOrganizations.some(item => item.id === current)) return current;
                if (currentUser?.orgUnitId && nextOrganizations.some(item => item.id === currentUser.orgUnitId)) return currentUser.orgUnitId;
                return nextOrganizations.find(item => item.tenantRoot)?.id || nextOrganizations[0]?.id || '';
            });
            setExpandedIds(new Set(collectHierarchyIds(buildHierarchy(nextOrganizations))));
        } catch (requestError) {
            setError(formatApiErrorMessage(requestError, '加载组织与部门失败。'));
        } finally {
            setLoading(false);
        }
    }, [canViewDepartments, canViewOrganizations, currentUser?.orgUnitId]);

    useEffect(() => { loadData(); }, [loadData]);

    const orgNameById = useMemo(() => new Map(orgUnits.map(item => [item.id, item.name])), [orgUnits]);
    const organizationTree = useMemo(() => buildHierarchy(orgUnits), [orgUnits]);
    const visibleOrganizationTree = useMemo(() => filterHierarchy(organizationTree, searchTerm), [organizationTree, searchTerm]);
    const selectedOrganization = orgUnits.find(item => item.id === selectedOrgUnitId) || null;
    const departmentCountByOrg = useMemo(() => {
        const counts = new Map();
        departments.forEach(department => counts.set(department.orgUnitId, (counts.get(department.orgUnitId) || 0) + 1));
        return counts;
    }, [departments]);
    const activeOrganizationCount = orgUnits.filter(item => item.status === 'ACTIVE').length;
    const activeDepartmentCount = departments.filter(item => item.status === 'ACTIVE').length;

    useEffect(() => {
        if (searchTerm.trim()) setExpandedIds(new Set(collectHierarchyIds(visibleOrganizationTree)));
    }, [searchTerm, visibleOrganizationTree]);

    const openCreate = () => {
        const root = orgUnits.find(item => item.tenantRoot);
        setEditing({ id: null });
        setForm({ ...emptyForm, parentId: root?.id ? String(root.id) : '' });
    };

    const openEdit = orgUnit => {
        setEditing(orgUnit);
        setForm({
            code: orgUnit.code,
            name: orgUnit.name,
            parentId: orgUnit.parentId ? String(orgUnit.parentId) : '',
            unitType: orgUnit.unitType,
            status: orgUnit.status,
        });
    };

    const submit = async () => {
        setSubmitting(true);
        setError('');
        const payload = {
            code: form.code.trim(),
            name: form.name.trim(),
            parentId: form.parentId || null,
            unitType: form.unitType,
            status: form.status,
        };
        try {
            if (editing?.id) await apiClient.put(`/admin/org-units/${editing.id}`, payload);
            else await apiClient.post('/admin/org-units', payload);
            setEditing(null);
            await loadData();
        } catch (requestError) {
            setError(formatApiErrorMessage(requestError, '保存组织节点失败。'));
        } finally {
            setSubmitting(false);
        }
    };

    const openMove = orgUnit => {
        setMoving(orgUnit);
        setMoveForm({ targetParentId: '', reason: '' });
    };

    const submitMove = async () => {
        if (!moving || !moveForm.targetParentId || !moveForm.reason.trim()) return;
        setSubmitting(true);
        setError('');
        try {
            await apiClient.post(`/admin/org-units/${moving.id}/move`, {
                targetParentId: moveForm.targetParentId,
                reason: moveForm.reason.trim(),
            });
            setMoving(null);
            await loadData();
        } catch (requestError) {
            setError(formatApiErrorMessage(requestError, '移动组织节点失败。'));
        } finally {
            setSubmitting(false);
        }
    };

    const moveTargets = useMemo(() => moving
        ? orgUnits.filter(item => !item.path.startsWith(moving.path))
        : [], [moving, orgUnits]);
    const toggleExpanded = id => setExpandedIds(current => {
        const next = new Set(current);
        if (next.has(id)) next.delete(id);
        else next.add(id);
        return next;
    });

    return (
        <Box sx={{ display: 'grid', gap: 1.5 }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}>
                <Box>
                    <PageHeading title="组织与部门" description="从组织树定位数据边界，在同一视图维护组织内部部门层级。" />
                    <Stack direction="row" spacing={0.75} useFlexGap sx={{ mt: 0.75, flexWrap: 'wrap' }}>
                        <Chip size="small" label={`${orgUnits.length} 个组织`} />
                        <Chip size="small" color="success" variant="outlined" label={`${activeOrganizationCount} 个启用`} />
                        <Chip size="small" color="primary" variant="outlined" label={`${activeDepartmentCount} 个部门`} />
                    </Stack>
                </Box>
                {canCreate ? <Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>新增区县组织</Button> : null}
            </Stack>

            {error ? <Alert severity="error" onClose={() => setError('')}>{error}</Alert> : null}

            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'minmax(300px, 360px) minmax(0, 1fr)' }, gap: 1.5, alignItems: 'start' }}>
                <Paper variant="outlined" sx={{ p: 1.5, minWidth: 0 }}>
                    <Stack spacing={1.25}>
                        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                            <Box>
                                <Typography variant="h6" sx={{ fontWeight: 800 }}>组织树</Typography>
                                <Typography variant="caption" color="text.secondary">点击节点查看并维护其部门</Typography>
                            </Box>
                            <Stack direction="row" spacing={0.25}>
                                <Tooltip title="展开全部"><IconButton aria-label="展开全部组织" onClick={() => setExpandedIds(new Set(collectHierarchyIds(organizationTree)))}><UnfoldMoreIcon /></IconButton></Tooltip>
                                <Tooltip title="收起全部"><IconButton aria-label="收起全部组织" onClick={() => setExpandedIds(new Set())}><UnfoldLessIcon /></IconButton></Tooltip>
                            </Stack>
                        </Stack>
                        <TextField
                            size="small"
                            value={searchTerm}
                            onChange={event => setSearchTerm(event.target.value)}
                            placeholder="搜索组织名称或编码"
                            slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }}
                        />
                        <Box sx={{ minHeight: 260, maxHeight: { xs: 390, lg: 'calc(100vh - 310px)' }, overflow: 'auto', bgcolor: 'grey.50', borderRadius: 1.5, p: 1 }}>
                            {loading ? <Stack sx={{ minHeight: 220, alignItems: 'center', justifyContent: 'center' }}><CircularProgress size={28} /></Stack> : null}
                            {!loading && visibleOrganizationTree.length === 0 ? <Alert severity="info">没有匹配的组织。</Alert> : null}
                            {!loading ? visibleOrganizationTree.map(node => (
                                <OrganizationTreeNode
                                    key={node.id}
                                    node={node}
                                    depth={0}
                                    expandedIds={expandedIds}
                                    selectedId={selectedOrgUnitId}
                                    departmentCounts={departmentCountByOrg}
                                    onToggle={toggleExpanded}
                                    onSelect={setSelectedOrgUnitId}
                                />
                            )) : null}
                        </Box>
                    </Stack>
                </Paper>

                <Paper variant="outlined" sx={{ p: { xs: 1.5, md: 2 }, minWidth: 0 }}>
                    {selectedOrganization ? (
                        <Stack spacing={2}>
                            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}>
                                <Box sx={{ minWidth: 0 }}>
                                    <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                                        <Typography variant="h5" sx={{ fontWeight: 800 }}>{selectedOrganization.name}</Typography>
                                        <Chip size="small" color={selectedOrganization.status === 'ACTIVE' ? 'success' : 'warning'} variant="outlined" label={selectedOrganization.status === 'ACTIVE' ? '启用' : '停用'} />
                                    </Stack>
                                    <Typography variant="body2" color="text.secondary" mt={0.5}>
                                        {[selectedOrganization.code, unitTypeLabel(selectedOrganization), `上级 ${orgNameById.get(selectedOrganization.parentId) || '无'}`].filter(Boolean).join(' · ')}
                                    </Typography>
                                </Box>
                                <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
                                    {canMove && !selectedOrganization.tenantRoot ? <Button size="small" startIcon={<DriveFileMoveIcon />} onClick={() => openMove(selectedOrganization)}>调整层级</Button> : null}
                                    {(canUpdate || canUpdateStatus) ? <Button size="small" variant="outlined" startIcon={<EditIcon />} onClick={() => openEdit(selectedOrganization)}>编辑组织</Button> : null}
                                </Stack>
                            </Stack>

                            {canViewDepartments ? (
                                <DepartmentManagementPanel
                                    departments={departments}
                                    organizations={orgUnits}
                                    selectedOrgUnitId={selectedOrgUnitId}
                                    loading={loading}
                                    loadError=""
                                    onReload={loadData}
                                />
                            ) : <Alert severity="info">当前账号没有查看内部部门的权限。</Alert>}
                        </Stack>
                    ) : <Alert severity="info">请从左侧组织树选择一个组织节点。</Alert>}
                </Paper>
            </Box>

            <Dialog open={Boolean(editing)} onClose={submitting ? undefined : () => setEditing(null)} maxWidth="sm" fullWidth>
                <DialogTitle>{editing?.id ? '编辑组织节点' : '新增组织节点'}</DialogTitle>
                <DialogContent dividers>
                    <Stack spacing={2}>
                        <TextField label="组织编码" value={form.code} onChange={event => setForm(current => ({ ...current, code: event.target.value }))} disabled={Boolean(editing?.id)} required />
                        <TextField label="组织名称" value={form.name} onChange={event => setForm(current => ({ ...current, name: event.target.value }))} disabled={Boolean(editing?.id) && !canUpdate} required />
                        <FormControl fullWidth disabled={Boolean(editing?.id)}>
                            <InputLabel>上级组织</InputLabel>
                            <Select value={form.parentId} label="上级组织" onChange={event => setForm(current => ({ ...current, parentId: event.target.value }))}>
                                {orgUnits.map(item => <MenuItem key={item.id} value={String(item.id)}>{item.name}</MenuItem>)}
                            </Select>
                        </FormControl>
                        <FormControl fullWidth disabled={Boolean(editing?.id) && !canUpdate}>
                            <InputLabel>组织类型</InputLabel>
                            <Select value={form.unitType} label="组织类型" onChange={event => setForm(current => ({ ...current, unitType: event.target.value }))}>
                                {UNIT_TYPES.map(item => <MenuItem key={item.value} value={item.value}>{item.label}</MenuItem>)}
                            </Select>
                        </FormControl>
                        <FormControl fullWidth disabled={Boolean(editing?.id) && !canUpdateStatus}>
                            <InputLabel>状态</InputLabel>
                            <Select value={form.status} label="状态" onChange={event => setForm(current => ({ ...current, status: event.target.value }))}>
                                <MenuItem value="ACTIVE" disabled={editing?.id && editing.status !== 'ACTIVE' && !canEnable}>启用</MenuItem>
                                <MenuItem value="SUSPENDED" disabled={editing?.id && editing.status === 'ACTIVE' && !canDisable}>停用</MenuItem>
                            </Select>
                        </FormControl>
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setEditing(null)} disabled={submitting}>取消</Button>
                    <Button variant="contained" onClick={submit} disabled={!form.code.trim() || !form.name.trim() || !form.parentId || submitting}>保存</Button>
                </DialogActions>
            </Dialog>

            <Dialog open={Boolean(moving)} onClose={submitting ? undefined : () => setMoving(null)} maxWidth="sm" fullWidth>
                <DialogTitle>移动组织节点</DialogTitle>
                <DialogContent dividers>
                    <Stack spacing={2}>
                        <Alert severity="warning">
                            将“{moving?.name}”及其全部下级移动到新的上级组织。用户和工单归属组织不变，授权范围将按新的组织树重新计算。
                        </Alert>
                        <FormControl fullWidth required>
                            <InputLabel>新的上级组织</InputLabel>
                            <Select value={moveForm.targetParentId} label="新的上级组织" onChange={event => setMoveForm(current => ({ ...current, targetParentId: event.target.value }))}>
                                {moveTargets.map(item => <MenuItem key={item.id} value={item.id}>{item.name}</MenuItem>)}
                            </Select>
                        </FormControl>
                        <TextField label="移动原因" value={moveForm.reason} onChange={event => setMoveForm(current => ({ ...current, reason: event.target.value }))} multiline minRows={3} required />
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setMoving(null)} disabled={submitting}>取消</Button>
                    <Button variant="contained" color="warning" onClick={submitMove} disabled={!moveForm.targetParentId || !moveForm.reason.trim() || submitting}>确认移动</Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
}

export default AdminOrgUnitPage;
