import assert from 'node:assert/strict';
import test from 'node:test';

import { formatApiErrorMessage } from './formatApiErrorMessage.js';

test('空字段错误不会覆盖后端业务原因', () => {
    const message = formatApiErrorMessage({
        response: {
            status: 400,
            data: {
                message: '授权到期时间必须晚于生效时间。',
                traceId: 'authorization-trace-id',
                errors: {},
            },
        },
    }, '授权变更预检失败，请修正后重试。');

    assert.equal(message, '授权到期时间必须晚于生效时间。\n错误编号：authorization-trace-id');
});

test('非空字段错误继续显示逐字段校验原因', () => {
    const message = formatApiErrorMessage({
        response: {
            status: 400,
            data: {
                message: '提交内容不完整或格式不正确。',
                errors: {
                    reason: '调整权限必须填写原因。',
                },
            },
        },
    }, '授权变更预检失败，请修正后重试。');

    assert.equal(message, '授权变更预检失败，请修正后重试。\n调整权限必须填写原因。');
});

test('附件容量超限明确提示100兆字节上限', () => {
    const message = formatApiErrorMessage({ response: { status: 413 } });

    assert.match(message, /100兆字节/);
    assert.doesNotMatch(message, /50兆字节/);
});

test('附件数量字段错误保留服务端具体原因', () => {
    const message = formatApiErrorMessage({
        response: {
            status: 400,
            data: {
                message: '提交内容不完整或格式不正确。',
                errors: { uploadedAttachmentIds: '现场附件不能超过10份' },
            },
        },
    }, '工单提交失败。');

    assert.equal(message, '工单提交失败。\n现场附件不能超过10份');
});
