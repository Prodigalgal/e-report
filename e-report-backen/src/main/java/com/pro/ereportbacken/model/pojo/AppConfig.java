package com.pro.ereportbacken.model.pojo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp; // 用于记录更新时间
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_config")
@Getter
@Setter
public class AppConfig extends UuidV7Entity {

    @Column(name = "config_key", length = 100, nullable = false, unique = true)
    private String configKey; // 例如："spring.mail.password", "feature.x.enabled"

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "config_value")
    private String configValue; // 配置值

    @Column(length = 255)
    private String description; // 对此配置项的描述 (可选)

    @UpdateTimestamp // 每次更新时自动设置时间戳
    private LocalDateTime lastUpdatedTime;

    public AppConfig() {
    }

    public AppConfig(String configKey, String configValue, String description) {
        this.configKey = configKey;
        this.configValue = configValue;
        this.description = description;
    }
}
