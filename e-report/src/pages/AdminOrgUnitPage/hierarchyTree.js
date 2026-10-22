const DEFAULT_LABEL = item => `${item.name || ''} ${item.code || ''}`;

function compareHierarchyItems(left, right) {
    const leftPath = String(left.path || '');
    const rightPath = String(right.path || '');
    if (leftPath !== rightPath) return leftPath.localeCompare(rightPath, 'zh-CN');
    return String(left.name || '').localeCompare(String(right.name || ''), 'zh-CN');
}

export function buildHierarchy(items) {
    const nodesById = new Map((items || []).map(item => [item.id, { ...item, children: [] }]));
    const roots = [];

    nodesById.forEach(node => {
        const parent = node.parentId ? nodesById.get(node.parentId) : null;
        if (parent && parent.id !== node.id) parent.children.push(node);
        else roots.push(node);
    });

    const sortNodes = nodes => {
        nodes.sort(compareHierarchyItems);
        nodes.forEach(node => sortNodes(node.children));
        return nodes;
    };
    return sortNodes(roots);
}

export function filterHierarchy(nodes, searchTerm, labelSelector = DEFAULT_LABEL) {
    const normalized = String(searchTerm || '').trim().toLocaleLowerCase('zh-CN');
    if (!normalized) return nodes;

    return nodes.flatMap(node => {
        const children = filterHierarchy(node.children, normalized, labelSelector);
        const matches = String(labelSelector(node) || '').toLocaleLowerCase('zh-CN').includes(normalized);
        return matches || children.length ? [{ ...node, children }] : [];
    });
}

export function collectHierarchyIds(nodes) {
    return nodes.flatMap(node => [node.id, ...collectHierarchyIds(node.children)]);
}

export function countHierarchyDescendants(node) {
    return node.children.reduce((total, child) => total + 1 + countHierarchyDescendants(child), 0);
}
