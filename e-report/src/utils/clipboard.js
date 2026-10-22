import { isLiteClient, copyTextInLite } from '../services/liteBridge.js';

export async function copyText(text, sensitive = false) {
    if (isLiteClient()) return copyTextInLite(text, sensitive);
    if (navigator.clipboard?.writeText) return navigator.clipboard.writeText(text);
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    try {
        textarea.select();
        if (!document.execCommand('copy')) throw new Error('浏览器未允许复制，请手动复制。');
    } finally { textarea.remove(); }
}
