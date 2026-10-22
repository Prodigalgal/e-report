export function getPaginationItemAriaLabel(type, page, selected) {
    if (type === 'first') return '前往第一页';
    if (type === 'last') return '前往最后一页';
    if (type === 'next') return '前往下一页';
    if (type === 'previous') return '前往上一页';
    if (type === 'page') return selected ? `第 ${page} 页，当前页` : `前往第 ${page} 页`;
    return '分页导航';
}
