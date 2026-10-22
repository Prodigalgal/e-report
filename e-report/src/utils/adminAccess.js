import { MANAGEMENT_ENTRY_PERMISSIONS, hasAnyPermission } from '../constants/permissions.js';

export function isAdminUser(user) {
    return hasAnyPermission(user, MANAGEMENT_ENTRY_PERMISSIONS);
}
