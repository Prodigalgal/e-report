import * as SQLite from 'expo-sqlite';

import { getCurrentApiEnvironment } from '@/config/api-environment';
import type { ApiEnvironment } from '@/config/public-endpoints';
import type { TelemetryEvent, TelemetrySeverity } from './telemetry-types';

const PRODUCTION_DATABASE_NAME = 'e-report-telemetry.db';
const TEST_DATABASE_NAME = 'e-report-telemetry-test.db';
const MAX_QUEUE_SIZE = 500;
const databasePromises = new Map<ApiEnvironment, Promise<SQLite.SQLiteDatabase>>();
const memoryQueues = new Map<ApiEnvironment, TelemetryEvent[]>();

type QueueRow = { id: string; payload: string };

export async function enqueueTelemetryEvent(event: TelemetryEvent) {
  const environment = getCurrentApiEnvironment();
  try {
    const database = await databaseInstance(environment);
    await database.runAsync(
      'INSERT OR REPLACE INTO telemetry_events (id, payload, priority, created_at) VALUES (?, ?, ?, ?)',
      event.clientEventId,
      JSON.stringify(event),
      priority(event.severity),
      Date.now(),
    );
    await pruneDatabaseQueue(database);
  } catch {
    const queue = memoryQueues.get(environment) ?? [];
    memoryQueues.set(environment, limitMemoryQueue([...queue, event]));
  }
}

export async function readTelemetryBatch(maximumSize: number, environment = getCurrentApiEnvironment()) {
  const limit = Math.max(1, Math.min(maximumSize, 50));
  let databaseEvents: TelemetryEvent[] = [];
  try {
    const database = await databaseInstance(environment);
    const rows = await database.getAllAsync<QueueRow>(
      'SELECT id, payload FROM telemetry_events ORDER BY priority DESC, created_at ASC LIMIT ?',
      limit,
    );
    databaseEvents = rows.flatMap((row) => parseEvent(row.payload));
  } catch {
    // 内存队列仍可承担当前进程内的短时降级。
  }
  const ids = new Set(databaseEvents.map((event) => event.clientEventId));
  const memoryQueue = memoryQueues.get(environment) ?? [];
  return [...databaseEvents, ...memoryQueue.filter((event) => !ids.has(event.clientEventId))].slice(0, limit);
}

export async function deleteTelemetryEvents(
  clientEventIds: readonly string[],
  environment = getCurrentApiEnvironment(),
) {
  const idSet = new Set(clientEventIds);
  const memoryQueue = memoryQueues.get(environment) ?? [];
  memoryQueues.set(environment, memoryQueue.filter((event) => !idSet.has(event.clientEventId)));
  try {
    const database = await databaseInstance(environment);
    await database.withTransactionAsync(async () => {
      for (const clientEventId of clientEventIds) {
        await database.runAsync('DELETE FROM telemetry_events WHERE id = ?', clientEventId);
      }
    });
  } catch {
    // 删除失败会造成幂等重传，服务端 client_event_id 唯一键会消解重复。
  }
}

export async function telemetryQueueSize(environment = getCurrentApiEnvironment()) {
  let databaseCount = 0;
  try {
    const database = await databaseInstance(environment);
    const row = await database.getFirstAsync<{ total: number }>('SELECT COUNT(*) AS total FROM telemetry_events');
    databaseCount = row?.total ?? 0;
  } catch {
    // 使用内存队列计数作为降级结果。
  }
  return databaseCount + (memoryQueues.get(environment)?.length ?? 0);
}

export async function clearTelemetryQueue(environment = getCurrentApiEnvironment()) {
  memoryQueues.set(environment, []);
  try {
    const database = await databaseInstance(environment);
    await database.runAsync('DELETE FROM telemetry_events');
  } catch {
    // 数据库恢复后会再次读取策略并完成清理。
  }
}

async function databaseInstance(environment: ApiEnvironment) {
  let databasePromise = databasePromises.get(environment);
  if (!databasePromise) {
    const databaseName = environment === 'test' ? TEST_DATABASE_NAME : PRODUCTION_DATABASE_NAME;
    databasePromise = SQLite.openDatabaseAsync(databaseName).then(async (database) => {
      await database.execAsync(`
        PRAGMA journal_mode = WAL;
        PRAGMA synchronous = NORMAL;
        CREATE TABLE IF NOT EXISTS telemetry_events (
          id TEXT PRIMARY KEY NOT NULL,
          payload TEXT NOT NULL,
          priority INTEGER NOT NULL,
          created_at INTEGER NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_telemetry_priority_created
          ON telemetry_events (priority DESC, created_at ASC);
      `);
      return database;
    }).catch((error) => {
      databasePromises.delete(environment);
      throw error;
    });
    databasePromises.set(environment, databasePromise);
  }
  return databasePromise;
}

async function pruneDatabaseQueue(database: SQLite.SQLiteDatabase) {
  const row = await database.getFirstAsync<{ total: number }>('SELECT COUNT(*) AS total FROM telemetry_events');
  const overflow = Math.max(0, (row?.total ?? 0) - MAX_QUEUE_SIZE);
  if (overflow > 0) {
    await database.runAsync(`
      DELETE FROM telemetry_events
      WHERE id IN (
        SELECT id FROM telemetry_events
        ORDER BY priority ASC, created_at ASC
        LIMIT ?
      )
    `, overflow);
  }
}

function parseEvent(payload: string) {
  try {
    const parsed = JSON.parse(payload) as TelemetryEvent;
    return parsed?.clientEventId ? [parsed] : [];
  } catch {
    return [];
  }
}

function limitMemoryQueue(events: TelemetryEvent[]) {
  if (events.length <= MAX_QUEUE_SIZE) return events;
  return events
    .map((event, index) => ({ event, index }))
    .sort((left, right) => priority(right.event.severity) - priority(left.event.severity) || right.index - left.index)
    .slice(0, MAX_QUEUE_SIZE)
    .sort((left, right) => left.index - right.index)
    .map((item) => item.event);
}

function priority(severity: TelemetrySeverity) {
  return { INFO: 1, WARN: 2, ERROR: 3, FATAL: 4 }[severity];
}
