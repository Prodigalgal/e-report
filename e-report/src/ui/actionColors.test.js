import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const palette = JSON.parse(fs.readFileSync(new URL('../../../shared/ui-action-colors.json', import.meta.url), 'utf8'));

function luminance(hex) {
    const channels = hex.slice(1).match(/../g).map(channel => {
        const value = Number.parseInt(channel, 16) / 255;
        return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
    });
    return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722;
}

function contrast(first, second) {
    const values = [luminance(first), luminance(second)].sort((a, b) => b - a);
    return (values[0] + 0.05) / (values[1] + 0.05);
}

for (const [name, tone] of Object.entries(palette).filter(([name]) => name !== 'disabled')) {
    test(`${name} action labels remain readable on normal, hover and solid surfaces`, () => {
        assert.ok(contrast(tone.foreground, tone.surface) >= 4.5, `${name} surface label contrast`);
        assert.ok(contrast(tone.foreground, tone.hover) >= 4.5, `${name} hover label contrast`);
        assert.ok(contrast('#ffffff', tone.solid) >= 4.5, `${name} solid label contrast`);
        assert.ok(contrast('#ffffff', tone.foreground) >= 4.5, `${name} solid hover label contrast`);
    });
}
