import assert from 'node:assert/strict';
import test from 'node:test';

import {
    buildHierarchy,
    collectHierarchyIds,
    countHierarchyDescendants,
    filterHierarchy,
} from './hierarchyTree.js';

const items = [
    { id: 'county', parentId: 'root', name: '南靖县', code: '350627', path: '/root/county/' },
    { id: 'root', parentId: null, name: '漳州市公司本部', code: '3506', path: '/root/' },
    { id: 'orphan', parentId: 'missing', name: '孤立节点', code: 'ORPHAN', path: '/orphan/' },
    { id: 'team', parentId: 'county', name: '乡镇班组', code: 'TEAM', path: '/root/county/team/' },
];

test('buildHierarchy 根据 parentId 建树并保留孤立节点', () => {
    const roots = buildHierarchy(items);
    const root = roots.find(node => node.id === 'root');
    assert.equal(root.children[0].id, 'county');
    assert.equal(root.children[0].children[0].id, 'team');
    assert.ok(roots.some(node => node.id === 'orphan'));
    assert.equal(countHierarchyDescendants(root), 2);
});

test('filterHierarchy 命中子节点时保留完整祖先路径', () => {
    const filtered = filterHierarchy(buildHierarchy(items), '乡镇');
    assert.deepEqual(collectHierarchyIds(filtered), ['root', 'county', 'team']);
});

test('filterHierarchy 同时匹配名称和编码', () => {
    const filtered = filterHierarchy(buildHierarchy(items), '350627');
    assert.deepEqual(collectHierarchyIds(filtered), ['root', 'county']);
});
