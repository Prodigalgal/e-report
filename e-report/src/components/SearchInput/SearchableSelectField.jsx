import { Autocomplete, TextField } from '@mui/material';

export default function SearchableSelectField({ label, value, options, onChange, disabled = false, error = false, helperText, margin, sx }) {
    return (
        <Autocomplete
            fullWidth size="small" disableClearable autoHighlight
            value={options.find(option => option.value === value) || null}
            options={options} disabled={disabled}
            isOptionEqualToValue={(option, selected) => option.value === selected.value}
            getOptionLabel={option => option.label}
            onChange={(_, option) => { if (option) onChange(option.value); }}
            noOptionsText="没有匹配的选项" openText="展开选项" closeText="收起选项"
            slotProps={{ listbox: { sx: { maxHeight: 280 } } }}
            sx={sx}
            renderInput={params => <TextField {...params} label={label} margin={margin} error={error}
                helperText={helperText} placeholder="输入名称查找" autoComplete="off" />}
        />
    );
}
