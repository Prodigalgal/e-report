import assert from 'node:assert/strict';
import test from 'node:test';
import { defaultEmailDeliveryRange, validateEmailDeliveryRange } from '../../../shared/email-delivery.ts';
import { canAccessAdminPath } from './authRedirects.js';
import { PERMISSIONS } from '../constants/permissions.js';

test('邮件查询默认日期按 UTC+8 午夜切换并覆盖七个业务日', () => {
    assert.deepEqual(defaultEmailDeliveryRange(new Date('2026-10-21T15:59:59Z')), { from: '2026-10-15', to: '2026-10-21' });
    assert.deepEqual(defaultEmailDeliveryRange(new Date('2026-10-21T16:00:00Z')), { from: '2026-10-16', to: '2026-10-22' });
});

test('邮件查询拒绝无效日期、反向日期和超过 93 天的范围', () => {
    assert.ok(validateEmailDeliveryRange('2026-02-30', '2026-03-01'));
    assert.ok(validateEmailDeliveryRange('', '2026-10-22'));
    assert.ok(validateEmailDeliveryRange('2026-10-22', '2026-10-21'));
    assert.ok(validateEmailDeliveryRange('2026-01-01', '2026-04-04'));
    assert.equal(validateEmailDeliveryRange('2026-01-01', '2026-04-03'), null);
});

test('通知配置或运行诊断权限不能打开邮件台账', () => {
    const administrator = { permissions: [PERMISSIONS.USER_MANAGE_VIEW, PERMISSIONS.NOTIFICATION_CONFIG_VIEW, PERMISSIONS.OBSERVABILITY_VIEW] };
    assert.equal(canAccessAdminPath(administrator, '/admin/email-deliveries'), false);
    assert.equal(canAccessAdminPath({ permissions: [PERMISSIONS.EMAIL_DELIVERY_VIEW] }, '/admin/email-deliveries'), true);
});
