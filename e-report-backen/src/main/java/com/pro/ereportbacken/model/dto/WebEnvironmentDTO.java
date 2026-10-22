package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class WebEnvironmentDTO {

    @Min(1)
    @Max(10)
    private Integer schemaVersion;

    @NotBlank(message = "WEB 环境摘要不能为空")
    @Pattern(regexp = "[a-f0-9]{64}", message = "WEB 环境摘要格式不正确")
    private String fingerprintHash;

    @Size(max = 80)
    private String browserName;
    @Size(max = 50)
    private String browserVersion;
    @Size(max = 50)
    private String osName;
    @Size(max = 120)
    private String osVersion;
    @Size(max = 120)
    private String deviceName;
    @Size(max = 80)
    private String manufacturer;
    @Size(max = 120)
    private String model;
    @Size(max = 50)
    private String localeTag;
    @Size(max = 80)
    private String timeZone;
    @Size(max = 50)
    private String appVersion;
    @Pattern(regexp = "^$|[a-f0-9]{64}$", message = "Canvas 摘要格式不正确")
    private String canvasHash;
    @Pattern(regexp = "^$|[a-f0-9]{64}$", message = "WebGL 摘要格式不正确")
    private String webglHash;
    @Pattern(regexp = "^$|[a-f0-9]{64}$", message = "Audio 摘要格式不正确")
    private String audioHash;
    @Pattern(regexp = "^$|[a-f0-9]{64}$", message = "字体摘要格式不正确")
    private String fontHash;
    @Size(max = 20)
    private Set<@Size(max = 50) String> securitySignals = new LinkedHashSet<>();
}
