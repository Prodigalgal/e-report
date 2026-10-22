import test from 'node:test';
import assert from 'node:assert/strict';

import {
    applyMailSenderProviderPreset,
    createEmptyMailSenderDraft,
    MAIL_SENDER_PROVIDER_PRESETS,
} from './mailSenderConfig.js';

test('SMTP provider presets are editable starting values with TLS appropriate defaults', () => {
    assert.equal(MAIL_SENDER_PROVIDER_PRESETS.GMAIL.host, 'smtp.gmail.com');
    assert.deepEqual(
        [MAIL_SENDER_PROVIDER_PRESETS.GMAIL.port, MAIL_SENDER_PROVIDER_PRESETS.GMAIL.startTlsRequired],
        [587, true],
    );
    assert.deepEqual(
        [MAIL_SENDER_PROVIDER_PRESETS.MICROSOFT_365.host, MAIL_SENDER_PROVIDER_PRESETS.MICROSOFT_365.port],
        ['smtp.office365.com', 587],
    );
    assert.deepEqual(
        [MAIL_SENDER_PROVIDER_PRESETS.MOBILE_139.host, MAIL_SENDER_PROVIDER_PRESETS.MOBILE_139.sslEnabled],
        ['smtp.139.com', true],
    );
});

test('changing provider preserves account identity and secrets for further editing', () => {
    const sender = { ...createEmptyMailSenderDraft(), fromAddress: 'sender@example.com', password: 'secret' };
    const updated = applyMailSenderProviderPreset(sender, 'GMAIL');

    assert.equal(updated.fromAddress, sender.fromAddress);
    assert.equal(updated.password, sender.password);
    assert.equal(updated.host, 'smtp.gmail.com');
    assert.equal(updated.startTlsRequired, true);
});
