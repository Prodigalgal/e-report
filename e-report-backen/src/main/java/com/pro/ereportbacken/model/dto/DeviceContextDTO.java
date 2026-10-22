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
public class DeviceContextDTO {

    @NotBlank(message = "安装实例标识不能为空")
    @Size(max = 100, message = "安装实例标识过长")
    private String installationId;

    @Size(max = 80)
    private String manufacturer;
    @Size(max = 80)
    private String brand;
    @Size(max = 120)
    private String model;
    @Size(max = 120)
    private String deviceName;
    @Size(max = 120)
    private String productName;
    @Size(max = 120)
    private String hardwareName;
    @Size(max = 120)
    private String boardName;
    @Size(max = 10)
    private Set<@Size(max = 40) String> supportedAbis = new LinkedHashSet<>();
    @Size(max = 50)
    private String osRelease;
    @Min(21)
    @Max(100)
    private Integer sdkInt;
    @Pattern(regexp = "^$|[0-9]{4}-[0-9]{2}-[0-9]{2}$", message = "安全补丁日期格式不正确")
    private String securityPatch;
    @Size(max = 512)
    private String buildFingerprint;
    @Min(1)
    @Max(20000)
    private Integer screenWidth;
    @Min(1)
    @Max(20000)
    private Integer screenHeight;
    @Min(1)
    @Max(2000)
    private Integer screenDensity;
    @Min(0)
    @Max(1048576)
    private Integer memoryMbBucket;
    @Min(0)
    @Max(16777216)
    private Integer storageMbBucket;
    @Size(max = 50)
    private String localeTag;
    @Size(max = 80)
    private String timeZone;
    @Size(max = 30)
    private String appVersion;
    @Size(max = 20)
    private String appBuild;
    @Size(max = 120)
    private String installerPackage;
    @Pattern(regexp = "^$|[a-fA-F0-9]{64}$", message = "应用签名摘要格式不正确")
    private String appSignatureSha256;
    private boolean hardwareBacked;
    @Size(max = 20)
    private Set<@Size(max = 50) String> securitySignals = new LinkedHashSet<>();
    @Pattern(regexp = "^$|[a-fA-F0-9]{64}$", message = "设备环境摘要格式不正确")
    private String environmentFingerprintHash;
    @Min(1)
    @Max(10)
    private Integer environmentSchemaVersion;
    @Size(max = 80)
    private String socManufacturer;
    @Size(max = 120)
    private String socModel;
    @Size(max = 10)
    private Set<@Size(max = 40) String> supported32BitAbis = new LinkedHashSet<>();
    @Size(max = 10)
    private Set<@Size(max = 40) String> supported64BitAbis = new LinkedHashSet<>();
    @Min(1)
    @Max(1024)
    private Integer cpuCoreCount;
    private Boolean lowRamDevice;
}
