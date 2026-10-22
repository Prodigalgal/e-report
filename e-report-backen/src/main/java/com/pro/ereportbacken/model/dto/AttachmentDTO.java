package com.pro.ereportbacken.model.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentDTO {
    private UUID id;
    private String fileName; // 原始文件名
    private String storedFileName; // 内部存储键；下载链接使用附件 UUID，避免暴露目录路径
    private String fileType;
    private long fileSize;
    private String attachmentStage;
    private String imageRedactionStatus;
    private String imageRedactionMessage;
    // 注意：filePath 字段通常不在 DTO 中暴露，下载链接通过附件 UUID 构建
}

