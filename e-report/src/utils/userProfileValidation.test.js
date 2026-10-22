import assert from 'node:assert/strict';
import test from 'node:test';

import { validateUserProfile } from './userProfileValidation.js';

test('个人信息校验会归一化合法手机号和可选邮箱', () => {
    const result = validateUserProfile({ name: ' 张三 ', phone: ' 13900000000 ', email: ' user@example.com ' });
    assert.equal(result.valid, true);
    assert.deepEqual(result.normalized, { name: '张三', phone: '13900000000', email: 'user@example.com' });
});

test('个人信息校验拒绝空姓名、非法手机号和非法邮箱', () => {
    const result = validateUserProfile({ name: '', phone: '123', email: 'invalid' });
    assert.equal(result.valid, false);
    assert.equal(result.errors.name, '姓名不能为空');
    assert.equal(result.errors.phone, '请输入11位中国大陆手机号');
    assert.equal(result.errors.email, '请输入正确的邮箱地址');
});

test('邮箱为空时允许保存', () => {
    const result = validateUserProfile({ name: '李四', phone: '13000000095', email: '' });
    assert.equal(result.valid, true);
    assert.equal(result.normalized.email, '');
});
