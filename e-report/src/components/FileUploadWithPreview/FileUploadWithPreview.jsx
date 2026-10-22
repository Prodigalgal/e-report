// src/components/FileUploadWithPreview/FileUploadWithPreview.jsx
import React, { useState, useEffect, useCallback } from 'react';
import {Button, Box, Typography, List, ListItem, ListItemText, IconButton, Paper, Tooltip, Alert} from '@mui/material';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import DeleteIcon from '@mui/icons-material/Delete';
import InsertDriveFileIcon from '@mui/icons-material/InsertDriveFile';
import {formatFileSize} from "../../utils/formatFileSize";


function FileUploadWithPreview({
                                   id = "file-input-mui",
                                   onFilesChange,
                                   maxFiles,
                                   maxFileSizeMB,
                                   maxTotalSizeMB,
                                   multiple = true, // 确保这个 prop 被正确使用
                                   accept = "image/*,video/*",
                                   capture,
                                   initialFiles = [], // 用于编辑场景
                               }) {
    // selectedFiles state 存储用户实际选择的 File 对象数组
    const [selectedFiles, setSelectedFiles] = useState(() => {
        // 初始化时，如果 initialFiles 是 File 对象的数组，则使用它
        // 否则，如果它是其他格式（例如服务器返回的已上传文件信息），这里可能需要转换
        // 为简单起见，我们假设 initialFiles 已经是 File[]
        return Array.isArray(initialFiles) ? initialFiles : [];
    });

    // previews state 存储用于UI渲染的预览对象数组
    const [previews, setPreviews] = useState([]);
    // internalFileError state 存储此组件内部发生的文件校验错误
    const [internalFileError, setInternalFileError] = useState(null);

    // useCallback 记忆化 generatePreviews 函数
    const generatePreviews = useCallback((filesToPreview) => {
        return filesToPreview.map(file => ({
            name: file.name,
            type: file.type.split('/')[0],
            url: URL.createObjectURL(file),
            size: file.size,
            fileObject: file, // 保留对原始 File 对象的引用
        }));
    }, []);

    const calculateTotalSize = (filesToMeasure) => filesToMeasure.reduce((total, file) => total + file.size, 0);

    // useEffect Hook 用于在 selectedFiles 变化时生成或清理预览
    useEffect(() => {
        const nextPreviews = selectedFiles && selectedFiles.length > 0
            ? generatePreviews(selectedFiles)
            : [];

        setPreviews(nextPreviews);

        return () => {
            nextPreviews.forEach(preview => URL.revokeObjectURL(preview.url));
        };
    }, [selectedFiles, generatePreviews]);

    // 处理文件选择输入框内容变化的事件 - **关键修改在这里**
    const handleFileChange = (event) => {
        const newlySelectedRawFiles = Array.from(event.target.files); // 新选择的文件
        let filesToActuallyAdd = [];
        let fileValidationError = null;

        setInternalFileError(null); // 清除之前的内部错误

        // 1. 文件去重：基于文件名和大小，避免重复添加完全相同的文件
        // （更严格的去重可能需要比较文件内容摘要，但通常文件名+大小足够）
        const uniqueNewFiles = newlySelectedRawFiles.filter(newFile =>
            !selectedFiles.some(existingFile =>
                existingFile.name === newFile.name && existingFile.size === newFile.size
            )
        );

        if (uniqueNewFiles.length < newlySelectedRawFiles.length) {
            fileValidationError = "部分选中的文件已存在，已自动忽略重复项。";
        }

        // 2. 应用文件大小限制到新选择的、不重复的文件
        let validatedUniqueNewFiles = uniqueNewFiles;
        if (maxFileSizeMB) {
            const oversizedFilesObjects = uniqueNewFiles.filter(file => (file.size / 1024 / 1024) > maxFileSizeMB);
            if (oversizedFilesObjects.length > 0) {
                const oversizedFileNames = oversizedFilesObjects.map(f => f.name).join(', ');
                const currentError = fileValidationError ? `${fileValidationError}\n` : "";
                fileValidationError = `${currentError}新选择的文件“${oversizedFileNames}”超过了${maxFileSizeMB}兆字节的大小限制，已被移除。`;
                validatedUniqueNewFiles = uniqueNewFiles.filter(file => (file.size / 1024 / 1024) <= maxFileSizeMB);
            }
        }
        filesToActuallyAdd = validatedUniqueNewFiles;


        // 3. 计算追加后的文件总数，并应用最大文件数量限制
        let combinedFiles = [...selectedFiles, ...filesToActuallyAdd];

        if (maxFiles && combinedFiles.length > maxFiles) {
            const numAllowedToAdd = maxFiles - selectedFiles.length;
            const currentError = fileValidationError ? `${fileValidationError}\n` : "";
            if (numAllowedToAdd <= 0) {
                fileValidationError = `${currentError}已达到最大文件数量（${maxFiles}个），无法再添加新文件。`;
                filesToActuallyAdd = []; // 不能再添加了
            } else {
                fileValidationError = `${currentError}已达到最大文件数量（${maxFiles}个）。本次只能添加${numAllowedToAdd}个新文件。`;
                filesToActuallyAdd = filesToActuallyAdd.slice(0, numAllowedToAdd);
            }
            combinedFiles = [...selectedFiles, ...filesToActuallyAdd]; // 重新计算合并列表
        }

        if (maxTotalSizeMB) {
            const maxTotalBytes = maxTotalSizeMB * 1024 * 1024;
            const acceptedFiles = [];
            const selectedTotalBytes = calculateTotalSize(selectedFiles);
            let remainingBytes = Math.max(0, maxTotalBytes - selectedTotalBytes);
            filesToActuallyAdd.forEach((file) => {
                if (file.size <= remainingBytes) {
                    acceptedFiles.push(file);
                    remainingBytes -= file.size;
                }
            });
            if (acceptedFiles.length < filesToActuallyAdd.length) {
                const rejectedFileNames = filesToActuallyAdd
                    .filter(file => !acceptedFiles.includes(file))
                    .map(file => file.name)
                    .join(', ');
                const currentError = fileValidationError ? `${fileValidationError}\n` : "";
                fileValidationError = `${currentError}上传材料总大小不能超过${maxTotalSizeMB}兆字节。当前已选${formatFileSize(selectedTotalBytes)}，还可添加${formatFileSize(Math.max(0, maxTotalBytes - selectedTotalBytes))}；已忽略超出剩余容量的文件：${rejectedFileNames}`;
            }
            filesToActuallyAdd = acceptedFiles;
            combinedFiles = [...selectedFiles, ...filesToActuallyAdd];
        }

        // 如果确实有文件被添加或错误信息需要更新
        if (filesToActuallyAdd.length > 0 || fileValidationError) {
            setSelectedFiles(combinedFiles); // 更新 selectedFiles state (包含旧文件和新验证过的文件)
            if (fileValidationError) {
                setInternalFileError(fileValidationError);
            }
            if (onFilesChange) {
                onFilesChange(combinedFiles, fileValidationError);
            }
        } else if (newlySelectedRawFiles.length > 0 && uniqueNewFiles.length === 0 && !fileValidationError) {
            // 如果用户选择了一些文件，但它们都是重复的，也给个提示
            setInternalFileError("选择的文件已存在列表中。");
        }


        // 重置 input[type="file"] 的值
        if (event.target) {
            event.target.value = null;
        }
    };

    // 处理移除单个文件的事件
    const removeFile = (fileToRemoveInPreview) => {
        const updatedFiles = selectedFiles.filter(file => file !== fileToRemoveInPreview.fileObject);
        setSelectedFiles(updatedFiles);
        setInternalFileError(null); // 清除可能存在的因移除文件而解决的错误

        if (onFilesChange) {
            onFilesChange(updatedFiles, null);
        }
    };

    // 处理 initialFiles (与之前类似)
    useEffect(() => {
        if (initialFiles && initialFiles.length > 0 && JSON.stringify(selectedFiles) !== JSON.stringify(initialFiles)) {
            // 确保 initialFiles 是 File 对象的数组，或者进行相应转换
            // 这里的比较可能不完美，因为 File 对象比较复杂
            // 更可靠的方式可能是比较文件名和大小列表
            const initialFileSignatures = initialFiles.map(f => `${f.name}-${f.size}`).join(',');
            const selectedFileSignatures = selectedFiles.map(f => `${f.name}-${f.size}`).join(',');
            if(initialFileSignatures !== selectedFileSignatures){
                setSelectedFiles(initialFiles);
            }
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [initialFiles]);


    return (
        <Box>
            <Button
                variant="outlined"
                component="label"
                startIcon={<CloudUploadIcon/>}
                fullWidth
                sx={{
                    mb: previews.length > 0 || internalFileError ? 2 : 0,
                    minHeight: 48,
                }}
                disabled={(maxFiles && selectedFiles.length >= maxFiles)
                    || (maxTotalSizeMB && calculateTotalSize(selectedFiles) >= maxTotalSizeMB * 1024 * 1024)}
            >
                {/* multiple prop 在 input 上控制是否可以选择多个文件 */}
                {multiple ? '选择或追加文件' : '选择文件（替换）'}
                <input
                    id={id}
                    type="file"
                    hidden
                    multiple={multiple} // 这个 prop 决定了文件选择对话框是否允许选择多个
                    accept={accept}
                    capture={capture}
                    onChange={handleFileChange}
                />
            </Button>
            {(maxFiles || maxTotalSizeMB) && <Typography variant="caption" display="block" color="textSecondary" sx={{
                textAlign: 'right',
                mt: -1,
                mb: internalFileError ? 1 : 0
            }}>
                已选 {selectedFiles.length}{maxFiles ? ` / ${maxFiles}` : ''} 个
                {maxTotalSizeMB ? ` · 合计${formatFileSize(calculateTotalSize(selectedFiles))} / ${maxTotalSizeMB}兆字节` : ''}
            </Typography>}

            {internalFileError && <Alert severity="warning" sx={{mt: 1, mb: 1}}>{internalFileError}</Alert>}

            {previews.length > 0 && (
                <Paper variant="outlined" sx={{mt: 1, p: 1, borderColor: 'divider'}}>
                    <Typography variant="subtitle2" gutterBottom sx={{pl: 1, color: 'text.secondary'}}>
                        已选文件列表：
                    </Typography>
                    <List dense disablePadding>
                        {previews.map((preview, index) => (
                            <ListItem
                                key={`${preview.name}-${preview.size}-${index}`} // 使用更唯一的key，防止同名文件问题
                                secondaryAction={
                                    <Tooltip title={`移除文件 ${preview.name}`}>
                                        <IconButton edge="end" aria-label={`移除 ${preview.name}`}
                                                    onClick={() => removeFile(preview)}
                                                    sx={{ width: 44, height: 44 }}>
                                            <DeleteIcon/>
                                        </IconButton>
                                    </Tooltip>
                                }
                                sx={{
                                    borderBottom: index < previews.length - 1 ? '1px dashed' : 'none',
                                    borderColor: 'divider',
                                    pl: { xs: 0.5, sm: 1 },
                                    pr: 6,
                                    alignItems: 'flex-start',
                                }}
                            >
                                <Box sx={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    width: '100%',
                                    minWidth: 0,
                                    overflow: 'hidden'
                                }}>
                                    {preview.type === 'image' ? (
                                        <img src={preview.url} alt={preview.name} style={{
                                            width: 50,
                                            height: 50,
                                            objectFit: 'cover',
                                            marginRight: '12px',
                                            borderRadius: '4px',
                                            border: '1px solid #eee'
                                        }}/>
                                    ) : preview.type === 'video' ? (
                                        <video src={preview.url} style={{
                                            width: 70,
                                            height: 50,
                                            objectFit: 'cover',
                                            marginRight: '12px',
                                            borderRadius: '4px',
                                            backgroundColor: '#f0f0f0'
                                        }} controlsList="nodownload nofullscreen" controls={false}/>
                                    ) : (
                                        <InsertDriveFileIcon sx={{fontSize: 40, color: 'text.secondary', mr: 1.5}}/>
                                    )}
                                    <ListItemText
                                        primary={preview.name}
                                        secondary={formatFileSize(preview.size)}
                                        slotProps={{
                                            primary: {
                                                sx: {
                                                    overflow: 'hidden',
                                                    textOverflow: 'ellipsis',
                                                    whiteSpace: 'nowrap',
                                                    fontWeight: 500,
                                                },
                                            },
                                            secondary: {
                                                sx: { fontSize: '0.75rem' },
                                            },
                                        }}
                                    />
                                </Box>
                            </ListItem>
                        ))}
                    </List>
                </Paper>
            )}
        </Box>
    );
}

FileUploadWithPreview.defaultProps = {
    multiple: true, // 默认允许多选追加
};

export default FileUploadWithPreview;
