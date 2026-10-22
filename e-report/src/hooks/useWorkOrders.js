import { useState, useCallback, useEffect } from 'react';
import { searchWorkOrders } from '../services/workOrderApiService';
import { formatApiErrorMessage } from '../utils/formatApiErrorMessage';

const INITIAL_FILTERS = {
    searchTerm: '',
    location: '',
    statuses: [],
    reportTypes: [],
    contactPerson: '',
    phone: '',
    reason: '',
    queues: [],
};

const INITIAL_PAGINATION = {
    currentPage: 0, // 0-indexed for API
    totalPages: 0,
    totalElements: 0,
    size: 10, // Default page size
};

const WORK_ORDER_LIST_CACHE_TTL_MS = 30 * 1000;
const WORK_ORDER_LIST_CACHE_MAX_SIZE = 24;
const workOrderListCache = new Map();

const isCanceledRequest = (error) => error?.code === 'ERR_CANCELED' || error?.name === 'CanceledError';

export function buildWorkOrderListParams(filtersToUse, pageToFetch, pageSize) {
    const { statuses, reportTypes, queues, ...scalarFilters } = filtersToUse;
    const params = {
        ...scalarFilters,
        status: statuses,
        reportType: reportTypes,
        queue: queues,
        page: pageToFetch,
        size: pageSize,
        sort: 'reportTime,desc',
    };
    Object.keys(params).forEach(key => {
        if (params[key] === '' || params[key] === null || params[key] === undefined
            || (Array.isArray(params[key]) && params[key].length === 0)) {
            delete params[key];
        }
    });
    return params;
}

export function buildWorkOrderExportParams(filtersToUse, selectedWorkOrderIds = []) {
    const params = buildWorkOrderListParams(filtersToUse, 0, 1);
    delete params.page;
    delete params.size;
    if (Array.isArray(selectedWorkOrderIds) && selectedWorkOrderIds.length > 0) {
        params.workOrderId = selectedWorkOrderIds;
    }
    return params;
}

function workOrderListCacheKey(params, cacheNamespace) {
    return `${cacheNamespace}:${JSON.stringify(Object.keys(params).sort().reduce((nextParams, key) => {
        nextParams[key] = params[key];
        return nextParams;
    }, {}))}`;
}

function readCachedWorkOrderPage(cacheKey) {
    const cacheEntry = workOrderListCache.get(cacheKey);
    if (!cacheEntry) {
        return null;
    }
    if (Date.now() - cacheEntry.savedAt > WORK_ORDER_LIST_CACHE_TTL_MS) {
        workOrderListCache.delete(cacheKey);
        return null;
    }
    return cacheEntry.data;
}

function writeCachedWorkOrderPage(cacheKey, data) {
    workOrderListCache.set(cacheKey, {
        savedAt: Date.now(),
        data,
    });
    if (workOrderListCache.size > WORK_ORDER_LIST_CACHE_MAX_SIZE) {
        const oldestKey = workOrderListCache.keys().next().value;
        workOrderListCache.delete(oldestKey);
    }
}

function useWorkOrders(
    initialFilters = INITIAL_FILTERS,
    initialPageSize = INITIAL_PAGINATION.size,
    fetchWorkOrders = searchWorkOrders,
    cacheNamespace = 'visible',
) {
    const [workOrders, setWorkOrders] = useState([]);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState(null);
    const [pagination, setPagination] = useState({ ...INITIAL_PAGINATION, size: initialPageSize });
    const [currentFilters, setCurrentFilters] = useState(initialFilters);

    const applyWorkOrderPage = useCallback((pageData) => {
        setWorkOrders(pageData.content || []);
        setPagination(prev => ({
            ...prev,
            currentPage: pageData.number,
            totalPages: pageData.totalPages,
            totalElements: pageData.totalElements,
        }));
    }, []);

    const fetchData = useCallback(async (filtersToUse, pageToFetch, pageSize, signal) => {
        const params = buildWorkOrderListParams(filtersToUse, pageToFetch, pageSize);
        const cacheKey = workOrderListCacheKey(params, cacheNamespace);
        const cachedPage = readCachedWorkOrderPage(cacheKey);
        if (cachedPage) {
            applyWorkOrderPage(cachedPage);
            setIsLoading(false);
        } else {
            setIsLoading(true);
        }
        setError(null);

        try {
            const response = await fetchWorkOrders(params, signal ? { signal } : {});
            if (response.data) {
                writeCachedWorkOrderPage(cacheKey, response.data);
                applyWorkOrderPage(response.data);
            } else {
                setWorkOrders([]);
                setPagination(prev => ({ ...prev, currentPage: 0, totalPages: 0, totalElements: 0 }));
                setError("未能获取工单数据。");
            }
        } catch (err) {
            if (isCanceledRequest(err) || signal?.aborted) {
                return;
            }
            console.error("Error fetching work orders in hook:", err.response?.data || err.message);
            setError(formatApiErrorMessage(err, "获取工单数据失败，请稍后重试。"));
            if (!cachedPage) {
                setWorkOrders([]);
                setPagination(prev => ({ ...prev, currentPage: 0, totalPages: 0, totalElements: 0 }));
            }
        } finally {
            if (!signal?.aborted) {
                setIsLoading(false);
            }
        }
    }, [applyWorkOrderPage, cacheNamespace, fetchWorkOrders]);

    useEffect(() => {
        const controller = new AbortController();
        fetchData(currentFilters, pagination.currentPage, pagination.size, controller.signal);
        return () => controller.abort();
    }, [fetchData, currentFilters, pagination.currentPage, pagination.size]);

    useEffect(() => {
        setCurrentFilters({
            ...INITIAL_FILTERS,
            ...(initialFilters || {}),
        });
        setPagination(prev => ({ ...prev, currentPage: 0, size: initialPageSize }));
    }, [initialFilters, initialPageSize]);

    const handleSearch = (newFilters) => {
        setCurrentFilters(newFilters);
        setPagination(prev => ({ ...prev, currentPage: 0 })); // Reset to first page
    };

    const handlePageChange = (newPage) => { // newPage is 0-indexed from our new Pagination component or direct call
        setPagination(prev => ({ ...prev, currentPage: newPage }));
    };

    const refreshData = () => {
        fetchData(currentFilters, pagination.currentPage, pagination.size);
    };

    return {
        workOrders,
        isLoading,
        error,
        pagination,
        currentFilters,
        handleSearch,
        handlePageChange,
        refreshData,
    };
}

export default useWorkOrders;
