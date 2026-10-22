const FILTER_QUERY_KEYS = {
    searchTerm: 'q',
    location: 'location',
    contactPerson: 'contact',
    phone: 'phone',
    reason: 'content',
    reportDateFrom: 'from',
    reportDateTo: 'to',
};

const MULTI_FILTER_QUERY_KEYS = {
    statuses: ['status'],
    reportTypes: ['type', 'reportType'],
    queues: ['queue'],
};

function readMultiValues(searchParams, queryKeys) {
    const values = queryKeys.flatMap(queryKey => searchParams.getAll(queryKey))
        .flatMap(value => value.split(','))
        .map(value => value.trim())
        .filter(value => value && value !== 'all');
    return [...new Set(values)];
}

export function readWorkOrderSearchParams(searchParams, overrides = {}) {
    const filters = {};
    Object.entries(FILTER_QUERY_KEYS).forEach(([filterKey, queryKey]) => {
        const value = searchParams.get(queryKey);
        if (value) {
            filters[filterKey] = value;
        }
    });
    Object.entries(MULTI_FILTER_QUERY_KEYS).forEach(([filterKey, queryKeys]) => {
        filters[filterKey] = readMultiValues(searchParams, queryKeys);
    });
    return { ...filters, ...overrides };
}

export function writeWorkOrderSearchParams(filters) {
    const searchParams = new URLSearchParams();
    Object.entries(FILTER_QUERY_KEYS).forEach(([filterKey, queryKey]) => {
        const value = filters[filterKey];
        if (value && value !== 'all') {
            searchParams.set(queryKey, value);
        }
    });
    Object.entries(MULTI_FILTER_QUERY_KEYS).forEach(([filterKey, [queryKey]]) => {
        const values = Array.isArray(filters[filterKey]) ? filters[filterKey] : [];
        values.filter(value => value && value !== 'all').forEach(value => searchParams.append(queryKey, value));
    });
    return searchParams;
}
