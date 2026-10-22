import * as Crypto from 'expo-crypto';
import {
  createTraceContextFromUuids,
  type TelemetryTraceContext,
} from './trace-context-core';

export { isValidSpanId, isValidTraceId, parseTraceparent } from './trace-context-core';
export type { TelemetryTraceContext } from './trace-context-core';

export function createTraceContext(parentTraceId?: string): TelemetryTraceContext {
  return createTraceContextFromUuids(Crypto.randomUUID(), Crypto.randomUUID(), parentTraceId);
}
