export function normalizeWorkOrderIds(value) {
    const values = value instanceof Set ? [...value] : Array.isArray(value) ? value : [];
    return [...new Set(values
        .map(item => String(item || '').trim())
        .filter(Boolean))];
}

export function toggleWorkOrderSelection(selectedIds, workOrderId) {
    const id = String(workOrderId || '').trim();
    const next = new Set(normalizeWorkOrderIds(selectedIds));
    if (!id) return next;
    if (next.has(id)) {
        next.delete(id);
    } else {
        next.add(id);
    }
    return next;
}

export function togglePageWorkOrderSelection(selectedIds, pageWorkOrderIds) {
    const pageIds = [...new Set(normalizeWorkOrderIds(pageWorkOrderIds))];
    const next = new Set(normalizeWorkOrderIds(selectedIds));
    if (pageIds.length === 0) return next;
    const allSelected = pageIds.every(id => next.has(id));
    pageIds.forEach(id => {
        if (allSelected) {
            next.delete(id);
        } else {
            next.add(id);
        }
    });
    return next;
}

export function selectedIdsForExport(selectedIds) {
    return normalizeWorkOrderIds(selectedIds);
}
