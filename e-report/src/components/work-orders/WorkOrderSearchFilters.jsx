import React, { useEffect, useState } from 'react';
import {
    Box,
    Button,
    Checkbox,
    Collapse,
    Divider,
    Drawer,
    FormControl,
    IconButton,
    InputLabel,
    ListItemText,
    MenuItem,
    OutlinedInput,
    Select,
    TextField,
    Tooltip,
    Typography,
    useMediaQuery,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import RotateLeftIcon from '@mui/icons-material/RotateLeft';
import TuneIcon from '@mui/icons-material/Tune';
import { WORK_ORDER_TYPE_OPTIONS } from '../../constants/workOrderCatalog';
import { WORK_ORDER_QUEUE_OPTIONS, WORK_ORDER_STATUS_OPTIONS } from '../../constants/workOrderLifecycle';
import SearchInput from '../SearchInput/SearchInput';

const defaultFiltersBase = {
    searchTerm: '',
    location: '',
    statuses: [],
    reportTypes: [],
    queues: [],
    contactPerson: '',
    phone: '',
    reason: '',
    reportDateFrom: '',
    reportDateTo: '',
};

function normalizeSelections(value) {
    const values = Array.isArray(value) ? value : value && value !== 'all' ? [value] : [];
    return [...new Set(values.filter(item => item && item !== 'all'))];
}

function normalizeFilters(filters, fixedReportType) {
    return {
        ...defaultFiltersBase,
        ...(filters || {}),
        statuses: normalizeSelections(filters?.statuses),
        reportTypes: fixedReportType ? [fixedReportType] : normalizeSelections(filters?.reportTypes),
        queues: normalizeSelections(filters?.queues),
        reportDateFrom: filters?.reportDateFrom || '',
        reportDateTo: filters?.reportDateTo || '',
    };
}

function MultiSelectFilter({ label, name, value, options, allLabel, disabled, onChange }) {
    const selectableOptions = options.filter(option => option.value !== 'all' && option.value !== '');
    const optionLabels = new Map(selectableOptions.map(option => [option.value, option.label]));
    const handleChange = (event) => {
        const selected = typeof event.target.value === 'string' ? event.target.value.split(',') : event.target.value;
        onChange({
            target: {
                name,
                value: selected.includes('all') ? [] : normalizeSelections(selected),
            },
        });
    };

    return (
        <FormControl fullWidth size="small" disabled={disabled}>
            <InputLabel shrink>{label}</InputLabel>
            <Select
                multiple
                name={name}
                value={value}
                label={label}
                onChange={handleChange}
                input={<OutlinedInput label={label} />}
                displayEmpty
                renderValue={(selected) => selected.length === 0
                    ? allLabel
                    : selected.map(item => optionLabels.get(item) || item).join('、')}
                MenuProps={{ PaperProps: { sx: { maxHeight: 360 } } }}
            >
                <MenuItem value="all">
                    <Checkbox checked={value.length === 0} />
                    <ListItemText primary={allLabel} />
                </MenuItem>
                {selectableOptions.map(option => (
                    <MenuItem key={option.value} value={option.value}>
                        <Checkbox checked={value.includes(option.value)} />
                        <ListItemText primary={option.label} />
                    </MenuItem>
                ))}
            </Select>
        </FormControl>
    );
}

function WorkOrderSearchFilters({ onSearch, isLoading, initialFilters: initialFiltersProp, fixedReportType, actions, availableReportTypes = WORK_ORDER_TYPE_OPTIONS }) {
    const [advancedOpen, setAdvancedOpen] = useState(false);
    const [mobileOpen, setMobileOpen] = useState(false);
    const [filters, setFilters] = useState(() => normalizeFilters(initialFiltersProp, fixedReportType));
    const isCompactLayout = useMediaQuery('(max-width:899.95px)');

    useEffect(() => {
        setFilters(normalizeFilters(initialFiltersProp, fixedReportType));
    }, [initialFiltersProp, fixedReportType]);

    const handleChange = (event) => {
        const { name, value } = event.target;
        setFilters(previous => ({ ...previous, [name]: value }));
    };

    const submitFilters = () => {
        onSearch({
            ...filters,
            reportTypes: fixedReportType ? [fixedReportType] : filters.reportTypes,
        });
    };

    const handleSubmit = (event) => {
        event.preventDefault();
        submitFilters();
    };

    const handleReset = () => {
        const resetFilters = {
            ...defaultFiltersBase,
            reportTypes: fixedReportType ? [fixedReportType] : [],
        };
        setFilters(resetFilters);
        setAdvancedOpen(false);
        onSearch(resetFilters);
    };

    const handleClearSearchTerm = () => {
        const nextFilters = {
            ...filters,
            searchTerm: '',
            reportTypes: fixedReportType ? [fixedReportType] : filters.reportTypes,
        };
        setFilters(nextFilters);
        onSearch(nextFilters);
    };

    const advancedFilterCount = [
        filters.location,
        filters.reportDateFrom,
        filters.reportDateTo,
        filters.contactPerson,
        filters.phone,
        filters.reason,
    ].filter(Boolean).length;
    const activeFilterCount = filters.queues.length + filters.reportTypes.length + filters.statuses.length + advancedFilterCount;

    const renderMobileFilterFields = () => (
        <Box sx={{ display: 'grid', gap: 1.5 }}>
            <MultiSelectFilter
                label="工作队列"
                name="queues"
                value={filters.queues}
                options={WORK_ORDER_QUEUE_OPTIONS}
                allLabel="全部工单"
                disabled={isLoading}
                onChange={handleChange}
            />
            {!fixedReportType && (
                <MultiSelectFilter
                    label="工单类型"
                    name="reportTypes"
                    value={filters.reportTypes}
                    options={availableReportTypes}
                    allLabel="全部类型"
                    disabled={isLoading}
                    onChange={handleChange}
                />
            )}
            <MultiSelectFilter
                label="处理状态"
                name="statuses"
                value={filters.statuses}
                options={WORK_ORDER_STATUS_OPTIONS}
                allLabel="全部状态"
                disabled={isLoading}
                onChange={handleChange}
            />
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' }, gap: 1.5 }}>
                <TextField label="工单地点" name="location" value={filters.location} onChange={handleChange} disabled={isLoading} size="small" fullWidth autoComplete="off" />
                <TextField label="联系人" name="contactPerson" value={filters.contactPerson} onChange={handleChange} disabled={isLoading} size="small" fullWidth autoComplete="off" />
                <TextField label="联系电话" name="phone" value={filters.phone} onChange={handleChange} disabled={isLoading} size="small" fullWidth autoComplete="off" />
                <TextField label="工单内容" name="reason" value={filters.reason} onChange={handleChange} disabled={isLoading} size="small" fullWidth autoComplete="off" />
                <TextField label="上报日期从" name="reportDateFrom" type="date" value={filters.reportDateFrom} onChange={handleChange} disabled={isLoading} size="small" fullWidth slotProps={{ inputLabel: { shrink: true } }} />
                <TextField label="上报日期至" name="reportDateTo" type="date" value={filters.reportDateTo} onChange={handleChange} disabled={isLoading} size="small" slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: filters.reportDateFrom || undefined } }} />
            </Box>
        </Box>
    );

    return (
        <Box component="form" onSubmit={handleSubmit} sx={{ display: 'grid', gap: 1.5 }}>
            <Box
                sx={{
                    display: 'flex',
                    flexWrap: 'wrap',
                    gap: 1.5,
                    alignItems: 'center',
                }}
            >
                <Box sx={{ minWidth: 0, flex: '1 1 300px' }}>
                    <SearchInput
                        label="关键词"
                        name="searchTerm"
                        value={filters.searchTerm}
                        onChange={handleChange}
                        onSearch={submitFilters}
                        onClear={handleClearSearchTerm}
                        disabled={isLoading}
                        size="small"
                        fullWidth
                        placeholder="#工单号、地点、联系人、原因、报单人"
                    />
                </Box>

                <Box
                    sx={{
                        display: { xs: 'grid', sm: 'flex' },
                        gridTemplateColumns: actions ? 'minmax(48px, 1fr) minmax(64px, 1.3fr) 48px 48px' : 'minmax(48px, 1fr) minmax(64px, 1.3fr) 48px',
                        gridTemplateAreas: actions ? '"search more export reset"' : '"search more reset"',
                        gap: 1,
                        alignItems: 'center',
                        flexWrap: 'wrap',
                        width: { xs: '100%', sm: 'auto' },
                        flex: { xs: '1 1 auto', sm: '0 1 auto' },
                        minWidth: 0,
                        justifyContent: { xs: 'stretch', md: 'flex-end' },
                    }}
                >
                    <Button
                        type="submit"
                        variant="contained"
                        startIcon={<SearchIcon />}
                        disabled={isLoading}
                        sx={{ minWidth: 0, px: { xs: 1, sm: 1.5 }, whiteSpace: 'nowrap', '& .MuiButton-startIcon': { display: { xs: 'none', sm: 'inherit' } }, flexShrink: 0, gridArea: 'search' }}
                    >
                        查询
                    </Button>
                    <Button
                        type="button"
                        variant="outlined"
                        startIcon={<TuneIcon />}
                        onClick={() => (isCompactLayout ? setMobileOpen(true) : setAdvancedOpen(value => !value))}
                        disabled={isLoading}
                        aria-expanded={isCompactLayout ? mobileOpen : advancedOpen}
                        aria-label={`${isCompactLayout ? '筛选条件' : '更多条件'}${activeFilterCount > 0 ? `，已选 ${activeFilterCount} 项` : ''}`}
                        sx={{ minWidth: 0, px: { xs: 1, sm: 1.5 }, whiteSpace: 'nowrap', '& .MuiButton-startIcon': { display: { xs: 'none', sm: 'inherit' } }, flexShrink: 0, gridArea: 'more' }}
                    >
                        <Box component="span" sx={{ display: { xs: 'inline', sm: 'none' } }}>筛选</Box>
                        <Box component="span" sx={{ display: { xs: 'none', sm: 'inline' } }}>{isCompactLayout ? '筛选条件' : '更多条件'}</Box>
                        {activeFilterCount > 0 ? ` (${activeFilterCount})` : ''}
                    </Button>
                    {actions}
                    <Tooltip title="重置查询条件"><Box component="span" sx={{ gridArea: 'reset', flexShrink: 0 }}>
                        <IconButton aria-label="重置查询条件" onClick={handleReset} disabled={isLoading} sx={{ height: { xs: 48, sm: 36 }, width: { xs: 48, sm: 36 }, border: '1px solid', borderColor: 'divider' }}>
                            <RotateLeftIcon />
                        </IconButton>
                    </Box></Tooltip>
                </Box>
            </Box>

            <Box sx={{ display: { xs: 'none', md: 'grid' }, gridTemplateColumns: `repeat(${fixedReportType ? 2 : 3}, minmax(0, 1fr))`, gap: 1.5 }}>
                <Box sx={{ minWidth: 0 }}>
                    <MultiSelectFilter
                        label="工作队列"
                        name="queues"
                        value={filters.queues}
                        options={WORK_ORDER_QUEUE_OPTIONS}
                        allLabel="全部工单"
                        disabled={isLoading}
                        onChange={handleChange}
                    />
                </Box>
                {!fixedReportType && (
                    <Box sx={{ minWidth: 0 }}>
                        <MultiSelectFilter
                            label="工单类型"
                            name="reportTypes"
                            value={filters.reportTypes}
                            options={availableReportTypes}
                            allLabel="全部类型"
                            disabled={isLoading}
                            onChange={handleChange}
                        />
                    </Box>
                )}
                <Box sx={{ minWidth: 0 }}>
                    <MultiSelectFilter
                        label="处理状态"
                        name="statuses"
                        value={filters.statuses}
                        options={WORK_ORDER_STATUS_OPTIONS}
                        allLabel="全部状态"
                        disabled={isLoading}
                        onChange={handleChange}
                    />
                </Box>
            </Box>

            <Collapse in={advancedOpen && !isCompactLayout} unmountOnExit>
                <Box
                    sx={{
                        display: 'grid',
                        gridTemplateColumns: {
                            xs: 'minmax(0, 1fr)',
                            sm: 'repeat(2, minmax(0, 1fr))',
                            lg: 'repeat(12, minmax(0, 1fr))',
                        },
                        gap: 1.5,
                        p: 1.5,
                        bgcolor: 'action.hover',
                        border: '1px solid',
                        borderColor: 'divider',
                        borderRadius: 2,
                    }}
                >
                    <Box sx={{ minWidth: 0, gridColumn: { lg: 'span 4' } }}>
                        <TextField label="工单地点" name="location" value={filters.location} onChange={handleChange} disabled={isLoading} size="small" fullWidth autoComplete="off" />
                    </Box>
                    <Box sx={{ minWidth: 0, gridColumn: { lg: 'span 2' } }}>
                        <TextField label="上报日期从" name="reportDateFrom" type="date" value={filters.reportDateFrom} onChange={handleChange} disabled={isLoading} size="small" fullWidth slotProps={{ inputLabel: { shrink: true } }} />
                    </Box>
                    <Box sx={{ minWidth: 0, gridColumn: { lg: 'span 2' } }}>
                        <TextField label="上报日期至" name="reportDateTo" type="date" value={filters.reportDateTo} onChange={handleChange} disabled={isLoading} size="small" fullWidth slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: filters.reportDateFrom || undefined } }} />
                    </Box>
                    <Box sx={{ minWidth: 0, gridColumn: { lg: 'span 2' } }}>
                        <TextField label="联系人" name="contactPerson" value={filters.contactPerson} onChange={handleChange} disabled={isLoading} size="small" fullWidth autoComplete="off" />
                    </Box>
                    <Box sx={{ minWidth: 0, gridColumn: { lg: 'span 2' } }}>
                        <TextField label="联系电话" name="phone" value={filters.phone} onChange={handleChange} disabled={isLoading} size="small" fullWidth autoComplete="off" />
                    </Box>
                    <Box sx={{ minWidth: 0, gridColumn: { sm: '1 / -1', lg: 'span 12' } }}>
                        <TextField label="工单内容" name="reason" value={filters.reason} onChange={handleChange} disabled={isLoading} size="small" fullWidth autoComplete="off" />
                    </Box>
                </Box>
            </Collapse>

            <Drawer
                anchor="bottom"
                open={mobileOpen}
                onClose={() => setMobileOpen(false)}
                slotProps={{ paper: { sx: { maxHeight: '90vh', borderTopLeftRadius: 16, borderTopRightRadius: 16 } } }}
            >
                <Box sx={{ display: 'grid', gap: 1.5, p: 2 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                        <Box>
                            <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>筛选条件</Typography>
                            <Typography variant="caption" color="text.secondary">
                                {activeFilterCount > 0 ? `已选择 ${activeFilterCount} 项` : '未设置筛选条件'}
                            </Typography>
                        </Box>
                        <IconButton aria-label="关闭筛选条件" onClick={() => setMobileOpen(false)}>
                            <span aria-hidden="true">×</span>
                        </IconButton>
                    </Box>
                    <Divider />
                    <Box sx={{ overflowY: 'auto', pr: 0.5 }}>{renderMobileFilterFields()}</Box>
                    <Box sx={{ display: 'flex', gap: 1 }}>
                        <Button type="button" variant="outlined" onClick={() => { handleReset(); setMobileOpen(false); }} disabled={isLoading} sx={{ flex: 1 }}>
                            重置
                        </Button>
                        <Button type="button" variant="contained" onClick={() => { submitFilters(); setMobileOpen(false); }} disabled={isLoading} sx={{ flex: 1 }}>
                            应用筛选
                        </Button>
                    </Box>
                </Box>
            </Drawer>
        </Box>
    );
}

export default WorkOrderSearchFilters;
