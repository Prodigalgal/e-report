import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import path from 'node:path';
import test from 'node:test';

const require = createRequire(import.meta.url);
const expoRequire = createRequire(require.resolve('expo/package.json'));
const cliRoot = path.dirname(expoRequire.resolve('@expo/cli/package.json'));
const { PluginSchema, DevToolsPluginOutputSchema } = require(path.join(cliRoot, 'build/src/start/server/DevToolsPlugin.schema.js'));
const { createMCPDevToolsExtensionSchema } = require(path.join(cliRoot, 'build/src/start/server/createMCPDevToolsExtensionSchema.js'));

const plugin = {
  packageName: 'acceptance-plugin', packageRoot: '/acceptance',
  cliExtensions: {
    description: 'Schema compatibility verification', entryPoint: 'index.js',
    commands: [{ name: 'inspect', title: 'Inspect', environments: ['cli', 'mcp'], parameters: [
      { name: 'limit', type: 'number' }, { name: 'verbose', type: 'confirm' }, { name: 'query', type: 'text' },
    ] }],
  },
};

test('Expo CLI 插件和输出 schema 在安全依赖升级后保持校验能力', () => {
  assert.equal(PluginSchema.safeParse(plugin).success, true);
  assert.equal(PluginSchema.safeParse({ ...plugin, packageName: '' }).success, false);
  assert.equal(DevToolsPluginOutputSchema.safeParse([{ type: 'text', text: 'ready', level: 'info' }]).success, true);
  assert.equal(DevToolsPluginOutputSchema.safeParse([{ type: 'audio', url: 'invalid' }]).success, false);
});

test('Expo CLI 生成的 MCP schema 保留参数类型、命令范围与未知字段限制', () => {
  const schema = createMCPDevToolsExtensionSchema(PluginSchema.parse(plugin));
  assert.equal(schema.safeParse({ command: 'inspect', limit: 3, verbose: true, query: 'work-order' }).success, true);
  for (const input of [{ command: 'missing' }, { command: 'inspect', limit: '3' }, { command: 'inspect', unexpected: true }]) {
    assert.equal(schema.safeParse(input).success, false);
  }
});
