package com.pro.ereportbacken.security;

import com.pro.ereportbacken.model.pojo.UserStatus;

public final class UserStatusPermissionPolicy {

    private UserStatusPermissionPolicy() {
    }

    public static String permissionForTransition(UserStatus currentStatus, UserStatus targetStatus) {
        if (targetStatus == UserStatus.LOCKED) {
            return PermissionCatalog.USER_MANAGE_STATUS_LOCK;
        }
        if (currentStatus == UserStatus.LOCKED && targetStatus != UserStatus.LOCKED) {
            return PermissionCatalog.USER_MANAGE_STATUS_UNLOCK;
        }
        if (targetStatus == UserStatus.ACTIVE) {
            return PermissionCatalog.USER_MANAGE_STATUS_ACTIVATE;
        }
        if (targetStatus == UserStatus.INACTIVE || targetStatus == UserStatus.DISABLED) {
            return PermissionCatalog.USER_MANAGE_STATUS_DEACTIVATE;
        }
        throw new IllegalArgumentException("用户删除和恢复必须使用对应的独立操作。" );
    }
}
