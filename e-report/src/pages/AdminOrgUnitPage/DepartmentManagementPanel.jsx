import React, { useEffect, useMemo, useState } from 'react';
import {
    Alert, Box, Button, Chip, CircularProgress, Collapse, Dialog, DialogActions,
    DialogContent, DialogTitle, Divider, FormControl, IconButton, InputAdornment,
    InputLabel, List, ListItem, ListItemText, Menu, MenuItem, Paper, Select, Stack,
    TextField, Tooltip, Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import AccountTreeOutlinedIcon from '@mui/icons-material/AccountTreeOutlined';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import FolderOpenOutlinedIcon from '@mui/icons-material/FolderOpenOutlined';
import FolderOutlinedIcon from '@mui/icons-material/FolderOutlined';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import SearchIcon from '@mui/icons-material/Search';
import UnfoldLessIcon from '@mui/icons-material/UnfoldLess';
import UnfoldMoreIcon from '@mui/icons-material/UnfoldMore';

import apiClient from '../../services/apiClient';
import { PERMISSIONS } from '../../constants/permissions';
import { useAuth } from '../../hooks/useAuth';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';
import { buildHierarchy, collectHierarchyIds, countHierarchyDescendants, filterHierarchy } from './hierarchyTree.js';

const EMPTY_FORM = { orgUnitId: '', parentId: '', code: '', name: '' };

function PreviewDiff({ preview }) {
    if (!preview) return null;
    return (
        <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
            <Box sx={{ px: 2, py: 1.25, bgcolor: 'grey.50' }}>
                <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>实际变化预览</Typography>
            </Box>
            <Divider />
            <List dense disablePadding>
                {preview.changes.map((change, index) => (
                    <ListItem key={`${change}-${index}`} sx={{ fontFamily: 'monospace', alignItems: 'flex-start' }}>
                        <Box component="span" sx={{ color: 'warning.main', mr: 1, fontWeight: 800 }}>~</Box>
                        <ListItemText primary={change} />
                    </ListItem>
                ))}
            </List>
            {preview.warnings?.length ? (
                <Stack spacing={0.75} sx={{ p: 1.5, pt: 0 }}>
                    {preview.warnings.map(warning => <Alert key={warning} severity="warning">{warning}</Alert>)}
                </Stack>
            ) : null}
        </Paper>
    );
}

function DepartmentTreeNode({ node, depth, expandedIds, selectedId, canOpenMenu, onToggle, onSelect, onOpenMenu }) {
    const hasChildren = node.children.length > 0;
    const expanded = expandedIds.has(node.id);
    const selected = selectedId === node.id;
    const statusLabel = node.status === 'ACTIVE' ? '启用' : node.status === 'MERGED' ? '已合并' : '停用';
    const statusColor = node.status === 'ACTIVE' ? 'success' : node.status === 'MERGED' ? 'default' : 'warning';

    return (
        <Box sx={{ position: 'relative', ml: depth ? 2.5 : 0 }}>
            {depth ? <Box sx={{ position: 'absolute', left: -12, top: 0, bottom: 0, borderLeft: '1px solid', borderColor: 'divider' }} /> : null}
            <Paper
                variant="outlined"
                onClick={() => onSelect(node)}
                sx={{
                    position: 'relative', mb: 0.75, px: 1, py: 0.75, cursor: 'pointer',
                    borderColor: selected ? 'primary.main' : 'divider',
                    bgcolor: selected ? 'action.selected' : 'background.paper',
                    boxShadow: selected ? theme => `0 0 0 1px ${theme.palette.primary.main}` : 'none',
                    transition: 'border-color 120ms ease, background-color 120ms ease',
                    '&:hover': { borderColor: selected ? 'primary.light' : 'divider', bgcolor: selected ? 'action.selected' : 'action.hover' },
                    '&::before': depth ? { content: '""', position: 'absolute', left: -12, top: 25, width: 12, borderTop: '1px solid', borderColor: 'divider' } : undefined,
                }}
            >
                <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
                    <IconButton
                        size="small"
                        aria-label={expanded ? `收起${node.name}` : `展开${node.name}`}
                        disabled={!hasChildren}
                        onClick={event => { event.stopPropagation(); onToggle(node.id); }}
                        sx={{ width: 30, height: 30, visibility: hasChildren ? 'visible' : 'hidden' }}
                    >
                        {expanded ? <ExpandMoreIcon fontSize="small" /> : <ChevronRightIcon fontSize="small" />}
                    </IconButton>
                    {expanded && hasChildren ? <FolderOpenOutlinedIcon color={selected ? 'primary' : 'action'} /> : <FolderOutlinedIcon color={selected ? 'primary' : 'action'} />}
                    <Box sx={{ minWidth: 0, flex: 1 }}>
                        <Stack direction="row" spacing={0.75} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                            <Typography variant="body2" sx={{ fontWeight: 800 }}>{node.name}</Typography>
                            <Chip size="small" variant="outlined" color={statusColor} label={statusLabel} sx={{ height: 22 }} />
                        </Stack>
                        <Typography variant="caption" color="text.secondary" noWrap>
                            {node.code}{hasChildren ? ` · ${countHierarchyDescendants(node)} 个下级` : ''}
                        </Typography>
                    </Box>
                    {canOpenMenu ? (
                        <IconButton
                            size="small"
                            aria-label={`管理${node.name}`}
                            onClick={event => { event.stopPropagation(); onOpenMenu(event.currentTarget, node); }}
                        >
                            <MoreVertIcon fontSize="small" />
                        </IconButton>
                    ) : null}
                </Stack>
            </Paper>
            {hasChildren ? (
                <Collapse in={expanded} timeout="auto" unmountOnExit>
                    {node.children.map(child => (
                        <DepartmentTreeNode
                            key={child.id}
                            node={child}
                            depth={depth + 1}
                            expandedIds={expandedIds}
                            selectedId={selectedId}
                            canOpenMenu={canOpenMenu}
                            onToggle={onToggle}
                            onSelect={onSelect}
                            onOpenMenu={onOpenMenu}
                        />
                    ))}
                </Collapse>
            ) : null}
        </Box>
    );
}

export default function DepartmentManagementPanel({
    departments,
    organizations,
    selectedOrgUnitId,
    loading,
    loadError,
    onReload,
}) {
    const { hasPermission } = useAuth();
    const canCreate = hasPermission(PERMISSIONS.DEPARTMENT_CREATE);
    const canUpdate = hasPermission(PERMISSIONS.DEPARTMENT_UPDATE);
    const canMove = hasPermission(PERMISSIONS.DEPARTMENT_MOVE);
    const canEnable = hasPermission(PERMISSIONS.DEPARTMENT_ENABLE);
    const canDisable = hasPermission(PERMISSIONS.DEPARTMENT_DISABLE);
    const canMerge = hasPermission(PERMISSIONS.DEPARTMENT_MERGE);
    const canDelete = hasPermission(PERMISSIONS.DEPARTMENT_DELETE);
    const canOpenDepartmentMenu = canCreate || canUpdate || canMove || canEnable || canDisable || canMerge || canDelete;
    const canUseMoreOperations = canUpdate || canMove || canEnable || canDisable || canMerge || canDelete;
    const [error, setError] = useState('');
    const [saving, setSaving] = useState(false);
    const [menu, setMenu] = useState({ anchor: null, department: null });
    const [editor, setEditor] = useState(null);
    const [form, setForm] = useState(EMPTY_FORM);
    const [operation, setOperation] = useState(null);
    const [operationDraft, setOperationDraft] = useState({ targetId: '', parentId: '', reason: '' });
    const [preview, setPreview] = useState(null);
    const [searchTerm, setSearchTerm] = useState('');
    const [expandedIds, setExpandedIds] = useState(new Set());
    const [selectedDepartmentId, setSelectedDepartmentId] = useState(null);

    const selectedOrganization = organizations.find(item => item.id === selectedOrgUnitId) || null;
    const scopedDepartments = useMemo(() => departments.filter(item => item.orgUnitId === selectedOrgUnitId), [departments, selectedOrgUnitId]);
    const departmentTree = useMemo(() => buildHierarchy(scopedDepartments), [scopedDepartments]);
    const visibleTree = useMemo(() => filterHierarchy(departmentTree, searchTerm), [departmentTree, searchTerm]);
    const selectedDepartment = scopedDepartments.find(item => item.id === selectedDepartmentId) || null;
    const departmentNameById = useMemo(() => new Map(scopedDepartments.map(item => [item.id, item.name])), [scopedDepartments]);
    const activeCount = scopedDepartments.filter(item => item.status === 'ACTIVE').length;
    const suspendedCount = scopedDepartments.filter(item => item.status === 'SUSPENDED').length;
    const mergedCount = scopedDepartments.filter(item => item.status === 'MERGED').length;

    useEffect(() => {
        setSearchTerm('');
        setSelectedDepartmentId(null);
        setExpandedIds(new Set(collectHierarchyIds(buildHierarchy(departments.filter(item => item.orgUnitId === selectedOrgUnitId)))));
    }, [departments, selectedOrgUnitId]);

    useEffect(() => {
        if (searchTerm.trim()) setExpandedIds(new Set(collectHierarchyIds(visibleTree)));
    }, [searchTerm, visibleTree]);

    const parentOptions = useMemo(() => departments.filter(item => (
        item.orgUnitId === form.orgUnitId && item.status === 'ACTIVE' && item.id !== editor?.id
    )), [departments, editor?.id, form.orgUnitId]);

    const closeMenu = () => setMenu({ anchor: null, department: null });
    const openCreate = (parent = null) => {
        setEditor({ mode: 'create', id: null });
        setForm({
            ...EMPTY_FORM,
            orgUnitId: parent?.orgUnitId || selectedOrgUnitId || organizations[0]?.id || '',
            parentId: parent?.id || '',
        });
        closeMenu();
    };
    const openEdit = department => {
        setEditor({ mode: 'edit', id: department.id });
        setForm({ orgUnitId: department.orgUnitId, parentId: department.parentId || '', code: department.code, name: department.name });
        closeMenu();
    };
    const openOperation = (type, department) => {
        setOperation({ type, department });
        setOperationDraft({ targetId: '', parentId: '', reason: '' });
        setPreview(null);
        closeMenu();
    };

    const saveEditor = async () => {
        setSaving(true);
        setError('');
        try {
            if (editor.mode === 'create') {
                await apiClient.post('/admin/departments', {
                    orgUnitId: form.orgUnitId,
                    parentId: form.parentId || null,
                    code: form.code.trim(),
                    name: form.name.trim(),
                });
            } else {
                await apiClient.put(`/admin/departments/${editor.id}`, { name: form.name.trim() });
            }
            setEditor(null);
            await onReload();
        } catch (requestError) {
            setError(formatApiErrorMessage(requestError, '保存部门失败。'));
        } finally {
            setSaving(false);
        }
    };

    const previewDestructiveOperation = async () => {
        setSaving(true);
        setError('');
        try {
            const response = operation.type === 'merge'
                ? await apiClient.post(`/admin/departments/${operation.department.id}/merge/preview`, {
                    targetDepartmentId: operationDraft.targetId,
                    reason: operationDraft.reason.trim(),
                })
                : await apiClient.get(`/admin/departments/${operation.department.id}/delete-preview`);
            setPreview(response.data);
        } catch (requestError) {
            setError(formatApiErrorMessage(requestError, '计算部门变化失败。'));
        } finally {
            setSaving(false);
        }
    };

    const executeOperation = async () => {
        setSaving(true);
        setError('');
        const departmentId = operation.department.id;
        try {
            if (operation.type === 'move') {
                await apiClient.post(`/admin/departments/${departmentId}/move`, {
                    targetParentId: operationDraft.parentId || null,
                    reason: operationDraft.reason.trim(),
                });
            } else if (operation.type === 'enable' || operation.type === 'disable') {
                await apiClient.post(`/admin/departments/${departmentId}/${operation.type}`, {
                    reason: operationDraft.reason.trim(),
                });
            } else if (operation.type === 'merge') {
                await apiClient.post(`/admin/departments/${departmentId}/merge`, {
                    targetDepartmentId: operationDraft.targetId,
                    reason: operationDraft.reason.trim(),
                });
            } else if (operation.type === 'delete') {
                await apiClient.post(`/admin/departments/${departmentId}/delete`, {
                    reason: operationDraft.reason.trim(),
                });
            }
            setOperation(null);
            setSelectedDepartmentId(null);
            await onReload();
        } catch (requestError) {
            setError(formatApiErrorMessage(requestError, '部门操作失败。'));
        } finally {
            setSaving(false);
        }
    };

    const operationDepartment = operation?.department;
    const moveTargets = operationDepartment ? departments.filter(item => (
        item.orgUnitId === operationDepartment.orgUnitId
        && item.status === 'ACTIVE'
        && !item.path.startsWith(operationDepartment.path)
    )) : [];
    const mergeTargets = operationDepartment ? departments.filter(item => (
        item.orgUnitId === operationDepartment.orgUnitId
        && item.status === 'ACTIVE'
        && item.id !== operationDepartment.id
        && !item.path.startsWith(operationDepartment.path)
    )) : [];
    const needsPreview = operation?.type === 'merge' || operation?.type === 'delete';
    const operationReady = Boolean(operationDraft.reason.trim()) && (
        operation?.type === 'move' || operation?.type === 'enable' || operation?.type === 'disable'
        || operation?.type === 'delete' || (operation?.type === 'merge' && operationDraft.targetId)
    );
    const toggleExpanded = id => setExpandedIds(current => {
        const next = new Set(current);
        if (next.has(id)) next.delete(id);
        else next.add(id);
        return next;
    });

    return (
        <Stack spacing={1.5} sx={{ minWidth: 0 }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}>
                <Box sx={{ minWidth: 0 }}>
                    <Typography variant="h6" sx={{ fontWeight: 800 }}>{selectedOrganization?.name || '请选择组织'} · 部门结构</Typography>
                    <Stack direction="row" spacing={0.75} useFlexGap sx={{ mt: 0.5, flexWrap: 'wrap' }}>
                        <Chip size="small" label={`${scopedDepartments.length} 个部门`} />
                        <Chip size="small" color="success" variant="outlined" label={`${activeCount} 个启用`} />
                        {suspendedCount ? <Chip size="small" color="warning" variant="outlined" label={`${suspendedCount} 个停用`} /> : null}
                        {mergedCount ? <Chip size="small" variant="outlined" label={`${mergedCount} 个已合并`} /> : null}
                    </Stack>
                </Box>
                {canCreate && selectedOrgUnitId ? <Button variant="contained" startIcon={<AddIcon />} onClick={() => openCreate()}>新增部门</Button> : null}
            </Stack>

            {loadError ? <Alert severity="error">{loadError}</Alert> : null}
            {error ? <Alert severity="error" onClose={() => setError('')}>{error}</Alert> : null}
            {!selectedOrgUnitId ? <Alert severity="info">请先从左侧组织树选择一个组织。</Alert> : null}

            {selectedOrgUnitId ? <>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'center' } }}>
                    <TextField
                        size="small"
                        value={searchTerm}
                        onChange={event => setSearchTerm(event.target.value)}
                        placeholder="搜索部门名称或编码"
                        sx={{ flex: 1, minWidth: 0 }}
                        slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }}
                    />
                    <Stack direction="row" spacing={0.5}>
                        <Tooltip title="展开全部"><IconButton aria-label="展开全部部门" onClick={() => setExpandedIds(new Set(collectHierarchyIds(departmentTree)))}><UnfoldMoreIcon /></IconButton></Tooltip>
                        <Tooltip title="收起全部"><IconButton aria-label="收起全部部门" onClick={() => setExpandedIds(new Set())}><UnfoldLessIcon /></IconButton></Tooltip>
                    </Stack>
                </Stack>

                <Paper variant="outlined" sx={{ p: { xs: 1, sm: 1.5 }, minHeight: 210, maxHeight: 480, overflow: 'auto', bgcolor: 'grey.50' }}>
                    {loading ? <Stack sx={{ minHeight: 180, alignItems: 'center', justifyContent: 'center' }}><CircularProgress size={28} /></Stack> : null}
                    {!loading && scopedDepartments.length === 0 ? (
                        <Stack spacing={1} sx={{ minHeight: 180, textAlign: 'center', alignItems: 'center', justifyContent: 'center' }}>
                            <AccountTreeOutlinedIcon color="disabled" sx={{ fontSize: 42 }} />
                            <Typography sx={{ fontWeight: 700 }}>该组织尚未建立部门</Typography>
                            <Typography variant="body2" color="text.secondary">可先创建直属部门，再按实际需要增加下级班组。</Typography>
                        </Stack>
                    ) : null}
                    {!loading && scopedDepartments.length > 0 && visibleTree.length === 0 ? <Alert severity="info">没有匹配的部门。</Alert> : null}
                    {!loading ? visibleTree.map(node => (
                        <DepartmentTreeNode
                            key={node.id}
                            node={node}
                            depth={0}
                            expandedIds={expandedIds}
                            selectedId={selectedDepartmentId}
                            canOpenMenu={canOpenDepartmentMenu}
                            onToggle={toggleExpanded}
                            onSelect={department => setSelectedDepartmentId(department.id)}
                            onOpenMenu={(anchor, department) => setMenu({ anchor, department })}
                        />
                    )) : null}
                </Paper>

                {selectedDepartment ? (
                    <Paper variant="outlined" sx={{ p: 1.5 }}>
                        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}>
                            <Box>
                                <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>{selectedDepartment.name}</Typography>
                                <Typography variant="body2" color="text.secondary">
                                    编码 {selectedDepartment.code} · 上级 {departmentNameById.get(selectedDepartment.parentId) || selectedOrganization?.name || '组织直属'}
                                </Typography>
                            </Box>
                            <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
                                {canCreate && selectedDepartment.status === 'ACTIVE' ? <Button size="small" startIcon={<AddIcon />} onClick={() => openCreate(selectedDepartment)}>新增下级</Button> : null}
                                {canUpdate && selectedDepartment.status !== 'MERGED' ? <Button size="small" onClick={() => openEdit(selectedDepartment)}>修改名称</Button> : null}
                                {canUseMoreOperations ? <Button size="small" endIcon={<MoreVertIcon />} onClick={event => setMenu({ anchor: event.currentTarget, department: selectedDepartment })}>更多操作</Button> : null}
                            </Stack>
                        </Stack>
                    </Paper>
                ) : null}
            </> : null}

            <Menu anchorEl={menu.anchor} open={Boolean(menu.anchor)} onClose={closeMenu}>
                {canCreate && menu.department?.status === 'ACTIVE' ? <MenuItem onClick={() => openCreate(menu.department)}>新增下级部门</MenuItem> : null}
                {canUpdate && menu.department?.status !== 'MERGED' ? <MenuItem onClick={() => openEdit(menu.department)}>修改名称</MenuItem> : null}
                {canMove && menu.department?.status !== 'MERGED' ? <MenuItem onClick={() => openOperation('move', menu.department)}>调整层级</MenuItem> : null}
                {canEnable && menu.department?.status === 'SUSPENDED' ? <MenuItem onClick={() => openOperation('enable', menu.department)}>启用</MenuItem> : null}
                {canDisable && menu.department?.status === 'ACTIVE' ? <MenuItem onClick={() => openOperation('disable', menu.department)}>停用</MenuItem> : null}
                {canMerge && menu.department?.status !== 'MERGED' ? <MenuItem onClick={() => openOperation('merge', menu.department)}>合并到其他部门</MenuItem> : null}
                {canDelete && menu.department?.status !== 'ACTIVE' ? <MenuItem onClick={() => openOperation('delete', menu.department)} sx={{ color: 'error.main' }}>删除</MenuItem> : null}
            </Menu>

            <Dialog open={Boolean(editor)} onClose={saving ? undefined : () => setEditor(null)} fullWidth maxWidth="sm">
                <DialogTitle>{editor?.mode === 'create' ? '新增部门' : '修改部门'}</DialogTitle>
                <DialogContent dividers><Stack spacing={2}>
                    <FormControl fullWidth disabled={editor?.mode === 'edit'}><InputLabel>所属组织</InputLabel>
                        <Select label="所属组织" value={form.orgUnitId} onChange={event => setForm(current => ({ ...current, orgUnitId: event.target.value, parentId: '' }))}>
                            {organizations.map(org => <MenuItem key={org.id} value={org.id}>{org.name}</MenuItem>)}
                        </Select>
                    </FormControl>
                    <FormControl fullWidth disabled={editor?.mode === 'edit'}><InputLabel>上级部门</InputLabel>
                        <Select label="上级部门" value={form.parentId} onChange={event => setForm(current => ({ ...current, parentId: event.target.value }))}>
                            <MenuItem value="">无，作为组织直属部门</MenuItem>
                            {parentOptions.map(item => <MenuItem key={item.id} value={item.id}>{item.name}</MenuItem>)}
                        </Select>
                    </FormControl>
                    <TextField label="部门编码" value={form.code} disabled={editor?.mode === 'edit'} onChange={event => setForm(current => ({ ...current, code: event.target.value }))} required />
                    <TextField label="部门名称" value={form.name} onChange={event => setForm(current => ({ ...current, name: event.target.value }))} required />
                </Stack></DialogContent>
                <DialogActions><Button onClick={() => setEditor(null)} disabled={saving}>取消</Button><Button variant="contained" onClick={saveEditor} disabled={saving || !form.name.trim() || (editor?.mode === 'create' && (!form.orgUnitId || !form.code.trim()))}>保存</Button></DialogActions>
            </Dialog>

            <Dialog open={Boolean(operation)} onClose={saving ? undefined : () => setOperation(null)} fullWidth maxWidth="sm">
                <DialogTitle>{operation?.type === 'move' ? '调整部门层级' : operation?.type === 'merge' ? '合并部门' : operation?.type === 'delete' ? '删除部门' : operation?.type === 'enable' ? '启用部门' : '停用部门'}</DialogTitle>
                <DialogContent dividers><Stack spacing={2}>
                    <Alert severity={needsPreview ? 'warning' : 'info'}>当前部门：{operationDepartment?.name}</Alert>
                    {operation?.type === 'move' ? <FormControl fullWidth><InputLabel>新的上级部门</InputLabel><Select label="新的上级部门" value={operationDraft.parentId} onChange={event => setOperationDraft(current => ({ ...current, parentId: event.target.value }))}><MenuItem value="">无，调整为组织直属部门</MenuItem>{moveTargets.map(item => <MenuItem key={item.id} value={item.id}>{item.name}</MenuItem>)}</Select></FormControl> : null}
                    {operation?.type === 'merge' ? <FormControl fullWidth required><InputLabel>目标部门</InputLabel><Select label="目标部门" value={operationDraft.targetId} onChange={event => { setOperationDraft(current => ({ ...current, targetId: event.target.value })); setPreview(null); }}>{mergeTargets.map(item => <MenuItem key={item.id} value={item.id}>{item.name}</MenuItem>)}</Select></FormControl> : null}
                    <TextField label="操作原因" multiline minRows={3} value={operationDraft.reason} onChange={event => { setOperationDraft(current => ({ ...current, reason: event.target.value })); setPreview(null); }} required />
                    <PreviewDiff preview={preview} />
                </Stack></DialogContent>
                <DialogActions>
                    <Button onClick={() => setOperation(null)} disabled={saving}>取消</Button>
                    {needsPreview && !preview ? <Button variant="contained" onClick={previewDestructiveOperation} disabled={saving || !operationReady}>预览变化</Button> : <Button variant="contained" color={needsPreview ? 'error' : 'primary'} onClick={executeOperation} disabled={saving || !operationReady}>{saving ? '正在处理…' : '确认执行'}</Button>}
                </DialogActions>
            </Dialog>
        </Stack>
    );
}
