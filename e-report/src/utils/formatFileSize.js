// src/utils/formatFileSize.js
export const formatFileSize = (bytes) => {
    if (bytes === 0) return '0 字节';
    const k = 1024;
    const sizes = ['字节', '千字节', '兆字节', '吉字节', '太字节'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};
