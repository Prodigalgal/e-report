import React from 'react';
import { IconButton, InputAdornment, TextField, Tooltip } from '@mui/material';
import ClearIcon from '@mui/icons-material/Clear';
import SearchIcon from '@mui/icons-material/Search';

function SearchInput({
    value = '',
    onChange,
    onClear,
    isSearching = false,
    resultCount,
    helperText,
    label = '搜索',
    placeholder,
    onSearch,
    onKeyDown,
    slotProps,
    ...textFieldProps
}) {
    const hasSearchTerm = String(value).trim().length > 0;
    const feedbackText = hasSearchTerm
        ? (isSearching
            ? '正在搜索...'
            : Number.isFinite(resultCount) ? `找到 ${resultCount} 条结果` : helperText)
        : helperText;

    return (
        <TextField
            {...textFieldProps}
            label={label}
            value={value}
            onChange={onChange}
            onKeyDown={(event) => {
                onKeyDown?.(event);
                if (!event.defaultPrevented && event.key === 'Enter' && onSearch) {
                    event.preventDefault();
                    onSearch();
                }
            }}
            placeholder={placeholder}
            helperText={feedbackText}
            autoComplete="off"
            inputMode="search"
            slotProps={{
                ...slotProps,
                input: {
                    ...(slotProps?.input || {}),
                    startAdornment: (
                        <InputAdornment position="start">
                            <SearchIcon color="action" fontSize="small" />
                        </InputAdornment>
                    ),
                    endAdornment: hasSearchTerm && onClear ? (
                        <InputAdornment position="end">
                            <Tooltip title="清除搜索">
                                <span>
                                    <IconButton
                                        aria-label="清除搜索内容"
                                        edge="end"
                                        onMouseDown={(event) => event.preventDefault()}
                                        onClick={onClear}
                                        disabled={textFieldProps.disabled}
                                        size="small"
                                    >
                                        <ClearIcon fontSize="small" />
                                    </IconButton>
                                </span>
                            </Tooltip>
                        </InputAdornment>
                    ) : null,
                },
            }}
        />
    );
}

export default SearchInput;
