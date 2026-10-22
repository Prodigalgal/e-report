import assert from 'node:assert/strict';
import test from 'node:test';

import {
    canAccessAdminPath,
    resolveAdminLoginDestination,
    resolveFirstAccessibleAdminPath,
} from './authRedirects.js';

const adminWith = (...permissions) => ({
    permissions,
});

test('limited administrator lands on the first management page they can access', () => {
    const userManager = adminWith('user:manage:view');

    assert.equal(resolveFirstAccessibleAdminPath(userManager), '/admin/user-management');
    assert.equal(resolveAdminLoginDestination(userManager), '/admin/user-management');
    assert.equal(canAccessAdminPath(userManager, '/admin/user-management/019ff9c4'), true);
    assert.equal(canAccessAdminPath(userManager, '/admin/dashboard'), false);
});

test('admin login returns to a requested page only when the current grant allows it', () => {
    const userManager = adminWith('user:manage:view');
    const requestedDashboard = { pathname: '/admin/dashboard', search: '?county=3506', hash: '' };
    const requestedUser = { pathname: '/admin/user-management', search: '?status=ACTIVE', hash: '#users' };

    assert.equal(resolveAdminLoginDestination(userManager, requestedDashboard), '/admin/user-management');
    assert.equal(resolveAdminLoginDestination(userManager, requestedUser), '/admin/user-management?status=ACTIVE#users');
});

test('dashboard-capable administrator keeps dashboard as the primary destination', () => {
    const cityAdministrator = adminWith('dashboard:statistics:view', 'user:manage:view');

    assert.equal(resolveFirstAccessibleAdminPath(cityAdministrator), '/admin/dashboard');
    assert.equal(resolveAdminLoginDestination(cityAdministrator), '/admin/dashboard');
});

test('没有任何管理动作权限时回到用户工作区', () => {
    assert.equal(resolveFirstAccessibleAdminPath(adminWith()), '/');
});
