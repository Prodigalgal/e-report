// com/pro/ereportbacken/model/dto/AppConfigDTO.java
package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppConfigDTO {
    @NotBlank(message = "配置键不能为空")
    @Size(max = 100, message = "配置键不能超过100字符")
    private String configKey;

    private String configValue; // 值可以是长文本

    @Size(max = 255, message = "描述不能超过255字符")
    private String description;

    private LocalDateTime lastUpdatedTime; // 通常只读

    // 用于前端显示，后端填充
    private boolean isSensitive;
    private String displayValue; // 如果 isSensitive 为 true，显示遮掩后的值
}
