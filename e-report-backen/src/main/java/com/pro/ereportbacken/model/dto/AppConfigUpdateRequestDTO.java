// com/pro/ereportbacken/model/dto/AppConfigUpdateRequestDTO.java
package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AppConfigUpdateRequestDTO {
    // configKey 在更新时通常不允许修改，所以不在此 DTO 中，通过路径变量传递

    private String configValue; // 允许更新值

    private boolean retainSensitiveValue;

    @Size(max = 255, message = "描述不能超过255字符")
    private String description; // 允许更新描述
}
