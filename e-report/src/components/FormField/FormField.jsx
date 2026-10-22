// src/components/FormField/FormField.jsx
import React from 'react';
import { TextField, FormControl, FormLabel, FormHelperText, Box } from '@mui/material';

function FormField({
                       id,
                       name, // Ensure name is passed for form handling
                       label,
                       type = 'text',
                       value,
                       onChange,
                       required = false,
                       placeholder = '',
                       textarea = false,
                       errorMessage,
                       rows,
                       multiline, // MUI uses multiline for textarea behavior
                       InputProps, // To pass props like startAdornment for icons
                       children, // For custom elements like file previews inside the form field layout
                       autoComplete
                   }) {
    if (type === 'file') {
        // MUI TextField doesn't directly support type="file" in the same way.
        // You usually create a custom component or use a hidden input triggered by a button.
        // For simplicity, we'll render the children (e.g., FileUploadWithPreview) directly within its own layout.
        // The FileUploadWithPreview component itself will use an <input type="file">.
        // This FormField won't render a TextField for type="file".
        // Instead, FileUploadWithPreview will be responsible for its label and input.
        return (
            <FormControl fullWidth margin="normal" error={!!errorMessage} required={required}>
                {label && <FormLabel component="legend" sx={{ mb: 1 }}>{label}</FormLabel>}
                {children} {/* This is where FileUploadWithPreview will render its input and previews */}
                {errorMessage && <FormHelperText>{errorMessage}</FormHelperText>}
            </FormControl>
        );
    }

    return (
        <TextField
            fullWidth
            variant="outlined" // Standard MUI style
            margin="normal" // Adds some vertical margin
            id={id}
            name={name}
            label={label}
            type={type}
            value={value}
            onChange={onChange}
            required={required}
            placeholder={placeholder}
            multiline={multiline || textarea} // Use multiline prop
            rows={multiline || textarea ? rows || (textarea ? 3 : undefined) : undefined}
            error={!!errorMessage}
            helperText={errorMessage}
            slotProps={InputProps ? { input: InputProps } : undefined}
            autoComplete={autoComplete}
        />
    );
}

export default FormField;
