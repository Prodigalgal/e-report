import assert from 'node:assert/strict';
import test from 'node:test';

import { normalizeSelectedValues, toggleSelectedValue } from '../src/utils/multi-select.ts';

test('normalizes repeated and comma-separated selections against allowed values', () => {
  assert.deepEqual(
    normalizeSelectedValues(['SUBMITTED,CLOSED', 'SUBMITTED', 'UNKNOWN'], ['SUBMITTED', 'CLOSED']),
    ['SUBMITTED', 'CLOSED'],
  );
});

test('toggles one value without changing the remaining selections', () => {
  assert.deepEqual(toggleSelectedValue(['A', 'B'], 'A'), ['B']);
  assert.deepEqual(toggleSelectedValue(['A'], 'B'), ['A', 'B']);
});
