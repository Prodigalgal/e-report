package com.pro.ereportbacken.model.pojo;

public enum UserStatus {
    ACTIVE,     // 激活状态，可以登录和操作
    INACTIVE,   // 未激活
    DISABLED,   // 已禁用 (管理员操作，可恢复)
    LOCKED,     // 账户被锁定
    DELETED     // 已软删除 (逻辑删除，通常不可恢复或恢复流程复杂)
}