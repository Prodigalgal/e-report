import { createTheme } from '@mui/material/styles';
import { uiLayout } from './ui/layout';
import actionColors from './ui/actionColors';

const actionTone = color => actionColors[{
    error: 'danger', success: 'success', warning: 'warning', info: 'info',
    primary: 'primary', secondary: 'primary',
}[color] || 'neutral'];
const focusRing = { outline: '3px solid rgba(37, 99, 235, 0.28)', outlineOffset: 2 };
const touchMedia = muiTheme => `${muiTheme.breakpoints.down('sm')}, (pointer: coarse)`;

const theme = createTheme({
    palette: {
        primary: { main: '#2563EB' },
        secondary: { main: '#475569' },
        error: { main: '#B91C1C' },
        success: { main: '#15803D' },
        warning: { main: '#A8540D' },
        info: { main: '#0369A1' },
        background: { default: '#F5F5F7', paper: '#FFFFFF' },
        text: { primary: '#202124', secondary: '#656B76' },
        divider: '#E7E8EC',
        action: { hover: '#F5F5F7', selected: '#EEF3FF', focus: '#E8EEFC' },
    },
    typography: {
        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
        h1: { fontSize: '2rem', fontWeight: 650, lineHeight: 1.3 },
        h2: { fontSize: '1.5rem', fontWeight: 650, lineHeight: 1.4 },
        h5: { fontSize: '1.5rem', fontWeight: 650, lineHeight: 1.4 },
        h6: { fontSize: '1.125rem', fontWeight: 650, lineHeight: 1.5 },
        subtitle1: { fontWeight: 600 },
        body1: { fontSize: '0.9375rem', lineHeight: 1.6 },
        body2: { fontSize: '0.875rem', lineHeight: 1.6 },
        button: { fontWeight: 600, letterSpacing: 0, textTransform: 'none' },
        caption: { fontSize: '0.75rem', lineHeight: 1.5 },
    },
    shape: { borderRadius: uiLayout.cornerRadius },
    components: {
        MuiCssBaseline: {
            styleOverrides: {
                body: { WebkitFontSmoothing: 'antialiased' },
                '@media (prefers-reduced-motion: reduce)': {
                    '*, *::before, *::after': { animationDuration: '0.01ms !important', transitionDuration: '0.01ms !important' },
                },
            },
        },
        MuiButton: {
            defaultProps: { disableElevation: true },
            styleOverrides: {
                root: ({ theme: muiTheme, ownerState }) => {
                    const tone = actionTone(ownerState.color);
                    const contained = ownerState.variant === 'contained';
                    const outlined = ownerState.variant === 'outlined';
                    return {
                        minHeight: uiLayout.compactControlHeight,
                        paddingInline: 16,
                        borderRadius: uiLayout.cornerRadius,
                        fontWeight: 600,
                        lineHeight: `${uiLayout.bodyLineHeight}px`,
                        backgroundColor: contained ? tone.solid : outlined ? tone.surface : 'transparent',
                        color: contained ? '#FFFFFF' : tone.foreground,
                        border: outlined ? `1px solid ${tone.border}` : '1px solid transparent',
                        '&:hover': {
                            backgroundColor: contained ? tone.foreground : tone.hover,
                            borderColor: outlined ? tone.border : 'transparent',
                        },
                        '&.Mui-disabled': {
                            backgroundColor: contained || outlined ? actionColors.disabled.surface : 'transparent',
                            color: actionColors.disabled.foreground,
                            borderColor: 'transparent',
                        },
                        '&.Mui-focusVisible': focusRing,
                        [touchMedia(muiTheme)]: { minHeight: uiLayout.controlHeight },
                    };
                },
                sizeLarge: { minHeight: uiLayout.controlHeight },
            },
        },
        MuiIconButton: {
            styleOverrides: {
                root: ({ theme: muiTheme, ownerState }) => ({
                    minWidth: uiLayout.compactControlHeight, minHeight: uiLayout.compactControlHeight, borderRadius: uiLayout.cornerRadius,
                    color: ownerState.color === 'inherit' ? 'inherit' : actionTone(ownerState.color).foreground,
                    backgroundColor: 'transparent',
                    '&:hover': { backgroundColor: '#F0F2F6' },
                    '&.Mui-disabled': { color: actionColors.disabled.foreground },
                    '&.Mui-focusVisible': focusRing,
                    [touchMedia(muiTheme)]: { minWidth: uiLayout.controlHeight, minHeight: uiLayout.controlHeight },
                }),
            },
        },
        MuiToggleButton: {
            styleOverrides: {
                root: ({ theme: muiTheme }) => ({
                    minHeight: uiLayout.compactControlHeight, textTransform: 'none',
                    backgroundColor: 'transparent', borderColor: '#E0E2E8', color: '#656B76',
                    '&:hover': { backgroundColor: '#F5F5F7' },
                    '&.Mui-selected, &.Mui-selected:hover': { backgroundColor: '#EEF3FF', color: '#1D4ED8' },
                    '&.Mui-focusVisible': focusRing,
                    [touchMedia(muiTheme)]: { minHeight: uiLayout.controlHeight },
                }),
            },
        },
        MuiTabs: {
            styleOverrides: {
                root: ({ theme: muiTheme }) => ({ minHeight: 40, [touchMedia(muiTheme)]: { minHeight: uiLayout.controlHeight } }),
                indicator: { height: 2, borderRadius: 2 },
            },
        },
        MuiTab: {
            styleOverrides: {
                root: ({ theme: muiTheme }) => ({
                    minHeight: 40, padding: '8px 16px', minWidth: 88,
                    color: '#656B76', fontSize: 14, fontWeight: 600, textTransform: 'none',
                    '&:hover': { color: '#202124', backgroundColor: '#FAFAFB' },
                    '&.Mui-selected': { color: '#2563EB' },
                    '&.Mui-focusVisible': { ...focusRing, outlineOffset: -3 },
                    [touchMedia(muiTheme)]: { minHeight: uiLayout.controlHeight },
                }),
            },
        },
        MuiTabScrollButton: { styleOverrides: { root: { color: '#656B76', width: 32 } } },
        MuiAccordion: {
            defaultProps: { disableGutters: true, elevation: 0 },
            styleOverrides: {
                root: {
                    border: '1px solid #E7E8EC', borderRadius: 12, overflow: 'hidden',
                    '&:before': { display: 'none' }, '&:first-of-type, &:last-of-type': { borderRadius: 12 },
                },
            },
        },
        MuiAccordionSummary: {
            styleOverrides: {
                root: ({ theme: muiTheme }) => ({
                    minHeight: 44, padding: '0 16px',
                    '&:hover': { backgroundColor: '#FAFAFB' },
                    '&.Mui-focusVisible': { ...focusRing, outlineOffset: -3 },
                    [touchMedia(muiTheme)]: { minHeight: uiLayout.controlHeight },
                }),
                content: { margin: '10px 0' },
            },
        },
        MuiAccordionDetails: { styleOverrides: { root: { padding: '0 16px 16px' } } },
        MuiPaginationItem: {
            styleOverrides: {
                root: ({ theme: muiTheme }) => ({
                    color: '#656B76', borderRadius: 8,
                    '&:hover': { backgroundColor: '#EDEEF2' },
                    '&.Mui-selected': { backgroundColor: '#E8EEFC', color: '#1D4ED8', fontWeight: 700 },
                    '&.Mui-selected:hover': { backgroundColor: '#DBE5FC' },
                    '&.Mui-focusVisible': focusRing,
                    [muiTheme.breakpoints.down('sm')]: { minWidth: 36, height: 44 },
                }),
            },
        },
        MuiListItemButton: {
            styleOverrides: {
                root: {
                    color: '#656B76',
                    '&:hover': { backgroundColor: '#F0F1F4', color: '#202124' },
                    '&.Mui-selected, &.Mui-selected:hover': { backgroundColor: '#EAF0FE', color: '#1D4ED8' },
                    '&.Mui-focusVisible': { ...focusRing, outlineOffset: -3 },
                },
            },
        },
        MuiMenu: {
            styleOverrides: { paper: { padding: 4, border: '1px solid #E7E8EC', boxShadow: '0 8px 32px rgba(24, 34, 54, 0.12)' } },
        },
        MuiMenuItem: {
            styleOverrides: {
                root: {
                    margin: '2px 0', borderRadius: 6, fontSize: 14,
                    '&:hover': { backgroundColor: '#F5F5F7' },
                    '&.Mui-selected, &.Mui-selected:hover': { backgroundColor: '#EEF3FF', color: '#1D4ED8' },
                },
            },
        },
        MuiTextField: { defaultProps: { size: 'small' } },
        MuiFormControl: { defaultProps: { size: 'small' } },
        MuiOutlinedInput: {
            styleOverrides: {
                root: ({ theme: muiTheme }) => ({
                    minWidth: 0, lineHeight: `${uiLayout.bodyLineHeight}px`, backgroundColor: '#FFFFFF',
                    '& .MuiOutlinedInput-notchedOutline': { borderColor: '#D9DCE3' },
                    '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: '#A9AFBB' },
                    '&.Mui-focused': { boxShadow: '0 0 0 3px rgba(37, 99, 235, 0.10)' },
                    '&.MuiInputBase-sizeSmall .MuiOutlinedInput-input': {
                        paddingTop: 6, paddingBottom: 6,
                        [touchMedia(muiTheme)]: { paddingTop: 12, paddingBottom: 12 },
                    },
                    '&.MuiInputBase-multiline .MuiOutlinedInput-input': { padding: 0 },
                }),
                input: { paddingTop: 12.5, paddingBottom: 12.5 },
            },
        },
        MuiInputLabel: {
            styleOverrides: {
                outlined: ({ theme: muiTheme }) => ({
                    '&:not(.MuiInputLabel-shrink)': { transform: 'translate(14px, 13px) scale(1)' },
                    '&.MuiInputLabel-sizeSmall:not(.MuiInputLabel-shrink)': {
                        transform: 'translate(14px, 7px) scale(1)',
                        [touchMedia(muiTheme)]: { transform: 'translate(14px, 13px) scale(1)' },
                    },
                }),
            },
        },
        MuiPaper: {
            styleOverrides: {
                outlined: { borderColor: '#E7E8EC' },
                elevation1: { boxShadow: '0 1px 3px rgba(24, 34, 54, 0.04)', border: '1px solid #E7E8EC' },
            },
        },
        MuiCard: { styleOverrides: { root: { borderRadius: 14, boxShadow: 'none', border: '1px solid #E7E8EC' } } },
        MuiCardContent: { styleOverrides: { root: { padding: uiLayout.cardPadding, '&:last-child': { paddingBottom: uiLayout.cardPadding } } } },
        MuiChip: {
            styleOverrides: {
                root: ({ ownerState }) => ({
                    fontWeight: 500, fontSize: 12, borderRadius: 6,
                    ...(ownerState.color !== 'default' && ownerState.variant !== 'outlined' ? {
                        backgroundColor: actionTone(ownerState.color).surface,
                        color: actionTone(ownerState.color).foreground,
                    } : {}),
                }),
            },
        },
        MuiDialog: {
            styleOverrides: { paper: { margin: uiLayout.pageGutter, maxHeight: 'calc(100% - 32px)', borderRadius: 18, boxShadow: '0 20px 80px rgba(24, 34, 54, 0.18)' } },
        },
        MuiDialogTitle: { styleOverrides: { root: { padding: '24px 24px 20px', fontSize: 20, lineHeight: '28px', fontWeight: 650 } } },
        MuiDialogContent: { styleOverrides: { root: { padding: '20px 24px' } } },
        MuiDialogActions: { styleOverrides: { root: { padding: '16px 24px 24px', gap: 8, flexWrap: 'wrap', '& > :not(style) ~ :not(style)': { marginLeft: 0 } } } },
        MuiTableContainer: { styleOverrides: { root: { maxWidth: '100%', minWidth: 0, overflowX: 'auto' } } },
        MuiTableCell: {
            styleOverrides: {
                root: { padding: '10px 16px', verticalAlign: 'middle', borderColor: '#EDEEF2', fontSize: 14 },
                sizeSmall: { padding: '8px 12px' },
                head: { backgroundColor: '#FAFAFB', color: '#656B76', fontSize: 12, fontWeight: 600, whiteSpace: 'nowrap' },
            },
        },
        MuiAppBar: {
            styleOverrides: {
                root: {
                    backgroundColor: '#FFFFFF', color: '#202124', boxShadow: 'none', borderBottom: '1px solid #E7E8EC',
                    '& .MuiIconButton-root': { marginInline: 2, color: '#656B76' },
                    '& .MuiButton-text': { color: '#656B76' },
                },
            },
        },
    },
});

export default theme;
