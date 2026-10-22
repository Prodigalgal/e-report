package com.pro.ereportbacken.model.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore; // 确保导入
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@DynamicUpdate
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_username", columnNames = "username"),
                @UniqueConstraint(name = "uk_users_phone", columnNames = "phone"),
                @UniqueConstraint(name = "uk_users_email", columnNames = "email")
        },
        indexes = @Index(name = "idx_users_tenant_status", columnList = "tenant_id,status")
)
@Getter
@Setter
@NoArgsConstructor
public class User extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private java.util.UUID tenantId;

    @Column(name = "org_unit_id")
    private java.util.UUID orgUnitId;

    @Column(name = "department_id")
    private java.util.UUID departmentId;

    @Column(name = "authorization_version", nullable = false)
    private Long authorizationVersion = 1L;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_key", unique = true, length = 40)
    private UserSystemKey systemKey;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 255)
    @JsonIgnore // <--- 添加此注解
    private String password;

    @Column(name = "password_change_required", nullable = false)
    private Boolean passwordChangeRequired = false;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(length = 100)
    private String name;

    @JsonIgnore
    @Column(name = "name_pinyin", length = UserNameSearchKeys.FULL_PINYIN_MAX_LENGTH)
    private String namePinyin;

    @JsonIgnore
    @Column(name = "name_pinyin_initials", length = UserNameSearchKeys.INITIALS_MAX_LENGTH)
    private String namePinyinInitials;

    @Column(length = 20)
    private String phone;

    @Column(length = 254)
    private String email;

    @JsonIgnore
    @Column(name = "email_work_order_created", nullable = false)
    private boolean emailWorkOrderCreated = true;

    @JsonIgnore
    @Column(name = "email_work_order_sla_breached", nullable = false)
    private boolean emailWorkOrderSlaBreached = true;

    @JsonIgnore
    @Column(name = "email_work_order_assigned", nullable = false)
    private boolean emailWorkOrderAssigned = true;

    @JsonIgnore
    @Column(name = "email_daily_outstanding_report", nullable = false)
    private boolean emailDailyOutstandingReport = true;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @CreationTimestamp
    @Column(updatable = false) // nullable = false 已移除 (临时)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false) // nullable = false 已移除 (临时)
    private LocalDateTime updatedAt;

    public User(String username, String password, String name) {
        this.username = username;
        this.password = password;
        this.name = name;
        this.status = UserStatus.ACTIVE;
    }

    @PrePersist
    @PreUpdate
    public void refreshNameSearchKeys() {
        UserNameSearchKeys searchKeys = UserNameSearchKeys.from(name);
        namePinyin = searchKeys.fullPinyin();
        namePinyinInitials = searchKeys.initials();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return getUsername() != null ? getUsername().equals(user.getUsername()) : user.getUsername() == null;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(getUsername());
    }
}
